import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { type Passage, questionLanguage } from "./knowledgeSearch";
import { awardPoints } from "./points";
import { loadPointsConfig } from "./pointsConfig";
import { istDay } from "./time";

/**
 * Ask Swamiji (AI): answers grounded in Ojas Healing's own teachings. The question is matched
 * against the passages in ai/knowledge.json (notes on the Ojas Healing videos and the about texts,
 * in English, Hindi and Gujarati) with keyword search, and a hosted model (NVIDIA's API, see
 * aiNvidia.ts) answers from those passages only, citing them as [1], [2]... The API key stays on
 * the server as a secret. Conversations are kept on the member's phone, not here.
 */

export interface AiConfig {
  enabled: boolean;
  model: string;
  dailyFree: number; // questions a day for free members
  dailyMember: number; // for paid members and staff
  /** Storage path of the passages that are searched. */
  knowledgePath: string;
  /** How many passages go into each prompt. */
  topK: number;
}

export const DEFAULT_AI: AiConfig = {
  enabled: true,
  model: "nvidia/nemotron-3-ultra-550b-a55b",
  dailyFree: 5,
  dailyMember: 5,
  knowledgePath: "ai/knowledge.json",
  topK: 5,
};

export async function loadAiConfig(db: Firestore): Promise<AiConfig> {
  const d = ((await db.doc("config/ai").get()).data() ?? {}) as Partial<AiConfig>;
  const num = (v: unknown, fallback: number) => (Number.isFinite(Number(v)) && v !== undefined ? Number(v) : fallback);
  const str = (v: unknown, fallback: string) => (typeof v === "string" && v.trim() ? v.trim() : fallback);
  return {
    enabled: d.enabled !== false,
    model: str(d.model, DEFAULT_AI.model),
    dailyFree: num(d.dailyFree, DEFAULT_AI.dailyFree),
    dailyMember: num(d.dailyMember, DEFAULT_AI.dailyMember),
    knowledgePath: str(d.knowledgePath, DEFAULT_AI.knowledgePath),
    topK: Math.max(1, Math.min(8, num(d.topK, DEFAULT_AI.topK))),
  };
}

export interface Source {
  n: number;
  title: string;
  url: string;
  videoId?: string;
  startSec?: number;
}

export interface Turn {
  role: "user" | "assistant";
  text: string;
}

/** The outside calls, passed in so tests can stand in for the search and the model. */
export interface AiDeps {
  retrieve(path: string, query: string, topK: number, lang: string): Promise<Passage[]>;
  generate(model: string, system: string, history: Turn[], prompt: string, onText: (piece: string) => void): Promise<string>;
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
    "Keep answers short for a phone screen: 2 to 6 short paragraphs or a few numbered steps. Plain words, no headings, no markdown symbols like ** or #.",
    `Write the answer in ${language}.`,
    "After the answer, on a new last line, write exactly: FOLLOWUPS: question one | question two | question three",
    `Those are three short follow-up questions the member might ask next, in ${language}, answerable from Ojas Healing teachings.`,
  ].join("\n");
}

export function buildPrompt(question: string, passages: Passage[], photo?: { text: string; labels: string[] }): string {
  const blocks = passages.map((p, i) => `[${i + 1}] ${p.text.trim()}`).join("\n\n");
  const seen = photo && (photo.text || photo.labels.length)
    ? `\n\nThe member shared a photo.${photo.labels.length ? ` It shows: ${photo.labels.join(", ")}.` : ""}${photo.text ? ` Words in it: "${photo.text}"` : ""}`
    : "";
  return `Passages from Ojas Healing:\n\n${blocks}${seen}\n\nMember's question: ${question}`;
}

/** Splits the model's text into the answer and the follow-up questions on its last line. */
export function splitFollowUps(raw: string): { answer: string; followUps: string[] } {
  const idx = raw.lastIndexOf("FOLLOWUPS:");
  if (idx < 0) return { answer: raw.trim(), followUps: [] };
  const followUps = raw.slice(idx + "FOLLOWUPS:".length).split("|").map((s) => s.trim()).filter(Boolean).slice(0, 3);
  return { answer: raw.slice(0, idx).trim(), followUps };
}

/** The passages the answer actually cited, numbered as in the answer. */
export function citedSources(answer: string, passages: Passage[]): Source[] {
  const used = new Set<number>();
  for (const m of answer.matchAll(/\[(\d{1,2})\]/g)) used.add(Number(m[1]));
  const out: Source[] = [];
  const seen = new Set<string>();
  [...used].sort((a, b) => a - b).forEach((n) => {
    const p = passages[n - 1];
    const key = p ? `${p.videoId ?? p.url ?? p.title}|${p.startSec ?? 0}` : "";
    if (!p || seen.has(key)) return;
    seen.add(key);
    out.push({ n, title: p.title, url: p.url ?? "", ...(p.videoId ? { videoId: p.videoId } : {}), ...(p.startSec ? { startSec: p.startSec } : {}) });
  });
  return out;
}

const CRISIS = [
  "suicide", "kill myself", "end my life", "want to die", "self harm", "hurt myself",
  "आत्महत्या", "मरना चाहता", "मरना चाहती", "ખુદને મારી", "આત્મહત્યા", "મરી જવું",
];

/** True when the words suggest someone may harm themselves: the app then shows help first. */
export function soundsLikeCrisis(text: string): boolean {
  const t = text.toLowerCase();
  return CRISIS.some((w) => t.includes(w));
}

function isPaidOrStaff(user: Record<string, unknown>, role: unknown): boolean {
  return role === "admin" || role === "swamiji" || role === "senior_member" || Number(user.memberLevel ?? 0) >= 1;
}

/** How many questions [uid] may still ask today. */
export async function remainingToday(db: Firestore, uid: string, role: unknown, now: Date): Promise<{ enabled: boolean; remaining: number; limit: number }> {
  const cfg = await loadAiConfig(db);
  const user = (await db.doc(`users/${uid}`).get()).data() ?? {};
  const limit = isPaidOrStaff(user, role) ? cfg.dailyMember : cfg.dailyFree;
  const used = Number((await db.doc(`users/${uid}/counters/${istDay(now)}`).get()).get("ai") ?? 0);
  return { enabled: cfg.enabled, remaining: Math.max(0, limit - used), limit };
}

export interface AskResult {
  /** "answer": the model answered. "crisis" and "noMatch": the app shows its own fixed words, and no question is used. */
  kind: "answer" | "crisis" | "noMatch";
  answer: string;
  sources: Source[];
  followUps: string[];
  /** -1 when no question was used. */
  remaining: number;
  pointsAwarded: number;
}

const clip = (v: unknown, max: number) => (typeof v === "string" ? v.trim().slice(0, max) : "");

export async function askSwamijiFor(
  db: Firestore, deps: AiDeps, uid: string, role: unknown,
  input: { question?: unknown; lang?: unknown; history?: unknown; photoText?: unknown; photoLabels?: unknown },
  now: Date, onText: (piece: string) => void = () => undefined,
): Promise<AskResult> {
  const photo = {
    text: clip(input.photoText, 600),
    labels: Array.isArray(input.photoLabels) ? input.photoLabels.filter((x): x is string => typeof x === "string").slice(0, 6).map((x) => x.slice(0, 40)) : [],
  };
  const hasPhoto = !!photo.text || photo.labels.length > 0;
  const question = clip(input.question, 1000);
  if (question.length < 2 && !hasPhoto) throw new HttpsError("invalid-argument", "Type a question first");
  const appLang = typeof input.lang === "string" && ["en", "hi", "gu"].includes(input.lang) ? input.lang : "en";
  const cfg = await loadAiConfig(db);
  if (!cfg.enabled) throw new HttpsError("unavailable", "Ask Swamiji AI is resting for now. Please try later.");
  const none = { answer: "", sources: [], followUps: [], remaining: -1, pointsAwarded: 0 };

  // Someone who may be in danger gets help straight away, before any teaching
  if (soundsLikeCrisis(question)) return { kind: "crisis", ...none };

  const history: Turn[] = (Array.isArray(input.history) ? input.history : []).slice(-4).flatMap((t): Turn[] => {
    const text = clip((t as { text?: unknown })?.text, 600);
    return text ? [{ role: (t as { fromMe?: unknown }).fromMe === true ? "user" : "assistant", text }] : [];
  });
  // Answer in the language the question is written in (Hindi typed in an English app gets Hindi)
  const lang = questionLanguage(question, appLang);
  const lastAsked = [...history].reverse().find((t) => t.role === "user")?.text ?? "";
  const query = [question, photo.labels.join(" "), photo.text, lastAsked].filter(Boolean).join(" ");
  const passages = await deps.retrieve(cfg.knowledgePath, query, cfg.topK, lang);
  // Nothing in the teachings: say so honestly instead of letting the model guess
  if (!passages.length) return { kind: "noMatch", ...none };

  // Daily allowance, counted only when the model will really answer; in a transaction so two quick taps can't both slip through
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

  let raw = "";
  try {
    raw = await deps.generate(cfg.model, systemPrompt(lang), history, buildPrompt(question || "What does Swamiji teach about this?", passages, hasPhoto ? photo : undefined), onText);
  } catch (e) {
    console.error("askSwamiji failed", e);
  }
  const { answer, followUps } = splitFollowUps(raw);
  if (!answer) {
    // Give the question back when the AI could not answer
    await counterRef.set({ ai: FieldValue.increment(-1) }, { merge: true });
    throw new HttpsError("unavailable", "Swamiji AI could not answer just now. Please try again.");
  }

  // The first question of the day earns a small Moksha Point reward
  const pointsCfg = await loadPointsConfig(db);
  let pointsAwarded = 0;
  if (pointsCfg.aiDaily > 0 && await awardPoints(db, uid, { eventKey: `ai:${day}`, amount: pointsCfg.aiDaily, reason: "ai_question", milestone: false })) {
    pointsAwarded = pointsCfg.aiDaily;
  }
  return { kind: "answer", answer, sources: citedSources(answer, passages), followUps, remaining: Math.max(0, limit - used), pointsAwarded };
}

const MESSAGE_ID = /^[A-Za-z0-9_-]{4,60}$/;

/**
 * Thumbs up or down on an answer. Chats live on the phone, so the app sends the question and
 * answer along with the rating, for admins to see what helped and what didn't.
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
  const answer = clip(input.answer, 4000);
  if (!answer) throw new HttpsError("invalid-argument", "answer is required");
  await fbRef.set({
    uid, messageId, rating,
    question: clip(input.question, 1000), answer, comment: clip(input.comment, 500), hadImage: input.hadImage === true,
    createdAt: FieldValue.serverTimestamp(),
  });
  return { ok: true };
}
