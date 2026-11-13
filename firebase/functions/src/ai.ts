import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { awardPoints } from "./points";
import { loadPointsConfig } from "./pointsConfig";
import { istDay } from "./time";

/**
 * Ask Swamiji (AI) runs on the member's phone: a small Gemma model answers from passages of
 * Ojas Healing's teachings that the phone searches itself (ai/knowledge.json in Storage, made by
 * scripts/buildKnowledge.ts). Questions and answers stay on the phone. The server only keeps the
 * daily allowance, pays the daily point, and stores the answers members rate for admins to review.
 */

export interface AiConfig {
  enabled: boolean;
  dailyFree: number; // questions a day for free members
  dailyMember: number; // for paid members and staff
  /** Storage path of the Gemma .task file the phone downloads once. */
  modelPath: string;
  /** Its size in bytes, shown before the download. */
  modelBytes: number;
  /** Storage path of the passages the phone searches. */
  knowledgePath: string;
  /** How many passages go into each prompt. The 270M model does best with only a few. */
  topK: number;
}

export const DEFAULT_AI: AiConfig = {
  enabled: true,
  dailyFree: 5,
  dailyMember: 5,
  modelPath: "ai/models/gemma3-270m-it-q8.task",
  modelBytes: 303950933,
  knowledgePath: "ai/knowledge.json",
  topK: 3,
};

export async function loadAiConfig(db: Firestore): Promise<AiConfig> {
  const d = ((await db.doc("config/ai").get()).data() ?? {}) as Partial<AiConfig>;
  const num = (v: unknown, fallback: number) => (Number.isFinite(Number(v)) && v !== undefined ? Number(v) : fallback);
  const str = (v: unknown, fallback: string) => (typeof v === "string" && v.trim() ? v.trim() : fallback);
  return {
    enabled: d.enabled !== false,
    dailyFree: num(d.dailyFree, DEFAULT_AI.dailyFree),
    dailyMember: num(d.dailyMember, DEFAULT_AI.dailyMember),
    modelPath: str(d.modelPath, DEFAULT_AI.modelPath),
    modelBytes: num(d.modelBytes, DEFAULT_AI.modelBytes),
    knowledgePath: str(d.knowledgePath, DEFAULT_AI.knowledgePath),
    topK: Math.max(1, Math.min(6, num(d.topK, DEFAULT_AI.topK))),
  };
}

function isPaidOrStaff(user: Record<string, unknown>, role: unknown): boolean {
  return role === "admin" || role === "swamiji" || role === "senior_member" || Number(user.memberLevel ?? 0) >= 1;
}

export interface AiStatus {
  enabled: boolean;
  remaining: number;
  limit: number;
  modelPath: string;
  modelBytes: number;
  knowledgePath: string;
  topK: number;
}

/** How many questions [uid] may still ask today, and where the phone finds the model and passages. */
export async function remainingToday(db: Firestore, uid: string, role: unknown, now: Date): Promise<AiStatus> {
  const cfg = await loadAiConfig(db);
  const user = (await db.doc(`users/${uid}`).get()).data() ?? {};
  const limit = isPaidOrStaff(user, role) ? cfg.dailyMember : cfg.dailyFree;
  const used = Number((await db.doc(`users/${uid}/counters/${istDay(now)}`).get()).get("ai") ?? 0);
  return {
    enabled: cfg.enabled, remaining: Math.max(0, limit - used), limit,
    modelPath: cfg.modelPath, modelBytes: cfg.modelBytes, knowledgePath: cfg.knowledgePath, topK: cfg.topK,
  };
}

/**
 * Counts one question against today's allowance before the phone answers it, so the limit holds
 * even though the answer is written on the phone. The first question of the day earns a point.
 */
export async function startAiQuestionFor(
  db: Firestore, uid: string, role: unknown, now: Date,
): Promise<{ remaining: number; limit: number; pointsAwarded: number }> {
  const cfg = await loadAiConfig(db);
  if (!cfg.enabled) throw new HttpsError("unavailable", "Ask Swamiji AI is resting for now. Please try later.");
  const day = istDay(now);
  const userRef = db.doc(`users/${uid}`);
  const counterRef = userRef.collection("counters").doc(day);
  const user = (await userRef.get()).data() ?? {};
  const limit = isPaidOrStaff(user, role) ? cfg.dailyMember : cfg.dailyFree;
  // Counted in a transaction so two quick taps can't both slip through
  const used = await db.runTransaction(async (tx) => {
    const n = Number((await tx.get(counterRef)).get("ai") ?? 0);
    if (n >= limit) throw new HttpsError("resource-exhausted", "You have asked today's questions. Come back tomorrow, or ask Swamiji personally.");
    tx.set(counterRef, { ai: n + 1 }, { merge: true });
    return n + 1;
  });
  const pointsCfg = await loadPointsConfig(db);
  let pointsAwarded = 0;
  if (pointsCfg.aiDaily > 0 && await awardPoints(db, uid, { eventKey: `ai:${day}`, amount: pointsCfg.aiDaily, reason: "ai_question", milestone: false })) {
    pointsAwarded = pointsCfg.aiDaily;
  }
  return { remaining: Math.max(0, limit - used), limit, pointsAwarded };
}

const MESSAGE_ID = /^[A-Za-z0-9_-]{4,60}$/;

/**
 * Thumbs up or down on an answer. Only a rated answer leaves the phone: the member chose to send
 * it, so admins can see what helped and what didn't.
 */
export async function rateAiAnswerFor(
  db: Firestore, uid: string,
  input: { messageId?: unknown; rating?: unknown; question?: unknown; answer?: unknown; comment?: unknown; hadImage?: unknown },
): Promise<{ ok: true }> {
  const messageId = typeof input.messageId === "string" && MESSAGE_ID.test(input.messageId) ? input.messageId : "";
  const rating = Number(input.rating);
  if (!messageId || (rating !== 1 && rating !== -1 && rating !== 0)) throw new HttpsError("invalid-argument", "messageId and rating are required");
  const fbRef = db.doc(`aiFeedback/${uid}_${messageId}`);
  if (rating === 0) {
    await fbRef.delete();
    return { ok: true };
  }
  const text = (v: unknown, max: number) => (typeof v === "string" ? v.slice(0, max) : "");
  const answer = text(input.answer, 4000);
  if (!answer) throw new HttpsError("invalid-argument", "answer is required");
  await fbRef.set({
    uid, messageId, rating, onDevice: true,
    question: text(input.question, 1000), answer, comment: text(input.comment, 500), hadImage: input.hadImage === true,
    createdAt: FieldValue.serverTimestamp(),
  });
  return { ok: true };
}
