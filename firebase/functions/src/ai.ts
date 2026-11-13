import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { awardPoints } from "./points";
import { loadPointsConfig } from "./pointsConfig";
import { istDay } from "./time";

/**
 * Ask Swamiji (AI): answers grounded in Ojas Healing's own teachings. Each question is embedded,
 * the closest passages are found in the `knowledge` collection (notes made from the Ojas Healing
 * YouTube videos and the about texts, see scripts/buildKnowledge.ts) and Gemini answers from those
 * passages only, citing them as [1], [2]... The app shows the cited videos under the answer.
 */

export interface AiConfig {
  enabled: boolean;
  model: string;
  embedModel: string;
  dailyFree: number; // questions a day for free members
  dailyMember: number; // for paid members and staff
  topK: number;
  maxDistance: number; // cosine distance; passages further than this are not used
}

export const DEFAULT_AI: AiConfig = {
  enabled: true,
  model: "gemini-3-flash-preview",
  embedModel: "gemini-embedding-001",
  dailyFree: 5,
  dailyMember: 5,
  topK: 6,
  maxDistance: 0.55,
};

export async function loadAiConfig(db: Firestore): Promise<AiConfig> {
  const d = ((await db.doc("config/ai").get()).data() ?? {}) as Partial<AiConfig>;
  const num = (v: unknown, fallback: number) => (Number.isFinite(Number(v)) && v !== undefined ? Number(v) : fallback);
  return {
    enabled: d.enabled !== false,
    model: typeof d.model === "string" && d.model ? d.model : DEFAULT_AI.model,
    embedModel: typeof d.embedModel === "string" && d.embedModel ? d.embedModel : DEFAULT_AI.embedModel,
    dailyFree: num(d.dailyFree, DEFAULT_AI.dailyFree),
    dailyMember: num(d.dailyMember, DEFAULT_AI.dailyMember),
    topK: Math.max(1, Math.min(12, num(d.topK, DEFAULT_AI.topK))),
    maxDistance: num(d.maxDistance, DEFAULT_AI.maxDistance),
  };
}

export interface Passage {
  id: string;
  text: string;
  title: string;
  url: string;
  videoId?: string;
  startSec?: number;
  sourceType: string;
  distance?: number;
}

export interface Source {
  n: number;
  title: string;
  url: string;
  videoId?: string;
  startSec?: number;
}

export interface Turn {
  role: "user" | "model";
  text: string;
}

/** The outside calls, passed in so tests can stand in for Gemini and vector search. */
export interface AiDeps {
  embedQuery(model: string, text: string): Promise<number[]>;
  retrieve(vector: number[], topK: number): Promise<Passage[]>;
  generate(
    model: string, system: string, history: Turn[], prompt: string, onText: (piece: string) => void,
  ): Promise<string>;
}

const LANG_NAMES: Record<string, string> = { en: "English", hi: "Hindi (Devanagari script)", gu: "Gujarati (Gujarati script)" };

export function systemPrompt(lang: string): string {
  const language = LANG_NAMES[lang] ?? "the same language as the question";
  return [
    "You are \"Ask Swamiji\", the guide inside the Ojas Family app of Ojas Healing.",
    "You speak with the warmth and simplicity of Swamiji's teaching: kind, calm, practical, never preachy.",
    "Answer ONLY from the numbered passages from Ojas Healing's teachings that are given with the question.",
    "Cite the passages you used with their numbers in square brackets, like [1] or [2][3], right after the sentence they support.",
    "If the passages don't cover the question, say so honestly in one or two sentences, suggest the closest practice from the passages if there is one, and suggest asking Swamiji personally in the app (Ask tab). Never invent teachings, quotes, video titles, dates or prices.",
    "For health problems: never diagnose or tell anyone to stop medicine. Meditation supports healing alongside a doctor's care; for serious, sudden or worsening symptoms advise seeing a doctor.",
    "If someone is in crisis or talks about harming themselves, gently urge them to contact a trusted person or local emergency help right now (India: Tele-MANAS 14416), before anything else.",
    "Keep answers short for a phone screen: 2 to 6 short paragraphs or a few numbered steps. Use plain words.",
    `Write the answer in ${language}.`,
    "After the answer, on a new last line, write exactly: FOLLOWUPS: question one | question two | question three",
    `Those are three short follow-up questions the member might ask next, in ${language}, answerable from Ojas Healing teachings.`,
  ].join("\n");
}

export function buildPrompt(question: string, passages: Passage[]): string {
  const blocks = passages.map((p, i) => `[${i + 1}] ${p.title}\n${p.text.trim()}`).join("\n\n");
  return passages.length
    ? `Passages from Ojas Healing:\n\n${blocks}\n\nMember's question: ${question}`
    : `No passages from Ojas Healing matched this question.\n\nMember's question: ${question}`;
}

/** Splits the model's text into the answer and the follow-up questions on its last line. */
export function splitFollowUps(raw: string): { answer: string; followUps: string[] } {
  const idx = raw.lastIndexOf("FOLLOWUPS:");
  if (idx < 0) return { answer: raw.trim(), followUps: [] };
  const followUps = raw.slice(idx + "FOLLOWUPS:".length).split("|").map((s) => s.trim()).filter(Boolean).slice(0, 3);
  return { answer: raw.slice(0, idx).trim(), followUps };
}

/** The passages the answer actually cited, renumbered as shown in the app. */
export function citedSources(answer: string, passages: Passage[]): Source[] {
  const used = new Set<number>();
  for (const m of answer.matchAll(/\[(\d{1,2})\]/g)) used.add(Number(m[1]));
  const out: Source[] = [];
  const seen = new Set<string>();
  [...used].sort((a, b) => a - b).forEach((n) => {
    const p = passages[n - 1];
    if (!p || seen.has(`${p.url}|${p.startSec ?? 0}`)) return;
    seen.add(`${p.url}|${p.startSec ?? 0}`);
    out.push({ n, title: p.title, url: p.url, ...(p.videoId ? { videoId: p.videoId } : {}), ...(p.startSec ? { startSec: p.startSec } : {}) });
  });
  return out;
}

function isPaidOrStaff(user: Record<string, unknown>, role: unknown): boolean {
  return role === "admin" || role === "swamiji" || role === "senior_member" || Number(user.memberLevel ?? 0) >= 1;
}

export interface AskResult {
  chatId: string;
  messageId: string;
  answer: string;
  sources: Source[];
  followUps: string[];
  remaining: number;
  pointsAwarded: number;
}

/** How many questions [uid] may still ask today. */
export async function remainingToday(db: Firestore, uid: string, role: unknown, now: Date): Promise<{ remaining: number; limit: number }> {
  const cfg = await loadAiConfig(db);
  const user = (await db.doc(`users/${uid}`).get()).data() ?? {};
  const limit = isPaidOrStaff(user, role) ? cfg.dailyMember : cfg.dailyFree;
  const used = Number((await db.doc(`users/${uid}/counters/${istDay(now)}`).get()).get("ai") ?? 0);
  return { remaining: Math.max(0, limit - used), limit };
}

export async function askSwamijiFor(
  db: Firestore, deps: AiDeps, uid: string, role: unknown,
  input: { question?: unknown; chatId?: unknown; lang?: unknown }, now: Date, onText: (piece: string) => void = () => undefined,
): Promise<AskResult> {
  const question = typeof input.question === "string" ? input.question.trim().slice(0, 1000) : "";
  if (question.length < 2) throw new HttpsError("invalid-argument", "Type a question first");
  const lang = typeof input.lang === "string" && ["en", "hi", "gu"].includes(input.lang) ? input.lang : "en";
  const cfg = await loadAiConfig(db);
  if (!cfg.enabled) throw new HttpsError("unavailable", "Ask Swamiji AI is resting for now. Please try later.");

  // Daily allowance: the question is counted up front so two quick taps can't both slip through
  const day = istDay(now);
  const userRef = db.doc(`users/${uid}`);
  const counterRef = userRef.collection("counters").doc(day);
  const user = (await userRef.get()).data() ?? {};
  const limit = isPaidOrStaff(user, role) ? cfg.dailyMember : cfg.dailyFree;
  const used = await db.runTransaction(async (tx) => {
    const n = Number((await tx.get(counterRef)).get("ai") ?? 0);
    if (n >= limit) throw new HttpsError("resource-exhausted", "You have asked today's questions. Come back tomorrow, or ask Swamiji personally.");
    tx.set(counterRef, { ai: n + 1 }, { merge: true });
    return n + 1;
  });

  const chats = userRef.collection("aiChats");
  const chatRef = typeof input.chatId === "string" && /^[A-Za-z0-9_-]{6,40}$/.test(input.chatId) ? chats.doc(input.chatId) : chats.doc();
  const chatSnap = await chatRef.get();
  const history: Turn[] = [];
  if (chatSnap.exists) {
    const prev = await chatRef.collection("messages").orderBy("createdAt", "desc").limit(6).get();
    prev.docs.reverse().forEach((d) => history.push({ role: d.get("role") === "user" ? "user" : "model", text: String(d.get("text") ?? "") }));
  }

  let raw: string;
  let passages: Passage[];
  try {
    const vector = await deps.embedQuery(cfg.embedModel, question);
    passages = (await deps.retrieve(vector, cfg.topK)).filter((p) => p.distance === undefined || p.distance <= cfg.maxDistance);
    raw = await deps.generate(cfg.model, systemPrompt(lang), history, buildPrompt(question, passages), onText);
  } catch (e) {
    // Give the question back when the AI could not answer
    await counterRef.set({ ai: FieldValue.increment(-1) }, { merge: true });
    if (e instanceof HttpsError) throw e;
    console.error("askSwamiji failed", e);
    throw new HttpsError("unavailable", "Swamiji AI could not answer just now. Please try again.");
  }
  const { answer, followUps } = splitFollowUps(raw);
  if (!answer) {
    await counterRef.set({ ai: FieldValue.increment(-1) }, { merge: true });
    throw new HttpsError("unavailable", "Swamiji AI could not answer just now. Please try again.");
  }
  const sources = citedSources(answer, passages);

  const at = FieldValue.serverTimestamp();
  const batch = db.batch();
  const qRef = chatRef.collection("messages").doc();
  const aRef = chatRef.collection("messages").doc();
  // Explicit times one millisecond apart, so the answer always sorts right after its question
  const asked = Timestamp.fromMillis(Date.now());
  batch.set(qRef, { role: "user", text: question, createdAt: asked });
  batch.set(aRef, { role: "model", text: answer, sources, followUps, createdAt: Timestamp.fromMillis(asked.toMillis() + 1) });
  batch.set(chatRef, {
    ...(chatSnap.exists ? {} : { title: question.slice(0, 80), createdAt: at }),
    lastText: answer.slice(0, 140), updatedAt: at, lang, count: FieldValue.increment(2),
  }, { merge: true });
  await batch.commit();

  // The first question of the day earns a small Moksha Point reward
  const pointsCfg = await loadPointsConfig(db);
  let pointsAwarded = 0;
  if (pointsCfg.aiDaily > 0 && await awardPoints(db, uid, { eventKey: `ai:${day}`, amount: pointsCfg.aiDaily, reason: "ai_question", milestone: false })) {
    pointsAwarded = pointsCfg.aiDaily;
  }

  return { chatId: chatRef.id, messageId: aRef.id, answer, sources, followUps, remaining: Math.max(0, limit - used), pointsAwarded };
}

/** Thumbs up or down on an answer, kept for admins to review. */
export async function rateAiAnswerFor(
  db: Firestore, uid: string, input: { chatId?: unknown; messageId?: unknown; rating?: unknown; comment?: unknown },
): Promise<{ ok: true }> {
  const chatId = typeof input.chatId === "string" ? input.chatId : "";
  const messageId = typeof input.messageId === "string" ? input.messageId : "";
  const rating = Number(input.rating);
  if (!chatId || !messageId || (rating !== 1 && rating !== -1 && rating !== 0)) throw new HttpsError("invalid-argument", "chatId, messageId and rating are required");
  const msgRef = db.doc(`users/${uid}/aiChats/${chatId}/messages/${messageId}`);
  const msg = await msgRef.get();
  if (!msg.exists || msg.get("role") !== "model") throw new HttpsError("not-found", "Answer not found");
  await msgRef.set({ rating }, { merge: true });
  const fbRef = db.doc(`aiFeedback/${uid}_${messageId}`);
  if (rating === 0) {
    await fbRef.delete();
  } else {
    const q = await db.collection(`users/${uid}/aiChats/${chatId}/messages`).where("createdAt", "<=", msg.get("createdAt")).orderBy("createdAt", "desc").limit(2).get();
    const question = q.docs.find((d) => d.get("role") === "user")?.get("text") ?? "";
    await fbRef.set({
      uid, chatId, messageId, rating, question, answer: msg.get("text"),
      comment: typeof input.comment === "string" ? input.comment.slice(0, 500) : "",
      createdAt: FieldValue.serverTimestamp(),
    });
  }
  return { ok: true };
}

/** Removes one of the member's AI conversations. */
export async function deleteAiChatFor(db: Firestore, uid: string, input: { chatId?: unknown }): Promise<{ ok: true }> {
  const chatId = typeof input.chatId === "string" ? input.chatId : "";
  if (!/^[A-Za-z0-9_-]{6,40}$/.test(chatId)) throw new HttpsError("invalid-argument", "chatId is required");
  await db.recursiveDelete(db.doc(`users/${uid}/aiChats/${chatId}`));
  return { ok: true };
}
