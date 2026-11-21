import { createHash } from "node:crypto";
import { FieldValue, type DocumentReference, type DocumentSnapshot, type Firestore } from "firebase-admin/firestore";

/** Turns Hindi text into English or Gujarati. */
export type Translator = (text: string, lang: "en" | "gu") => Promise<string>;

const QUESTION_MAX = 500;
const ANSWER_MAX = 10_000;
const PIECE_MAX = 2000;
const PIECES_AT_ONCE = 2;
const TEXT_FIELDS = ["question_en", "answer_en", "question_gu", "answer_gu"];

/** Identifies the Hindi a translation came from, so a staff correction never causes a new one. */
export function readSourceHash(question: string, answer: string): string {
  return createHash("sha256").update(`${question}\n\0${answer}`).digest("hex");
}

/** Cuts [text] at line or sentence ends; [sep] is the white space that followed each piece. */
export function splitForTranslation(text: string, max = PIECE_MAX): { text: string; sep: string }[] {
  const parts: { text: string; sep: string }[] = [];
  let rest = text;
  while (rest.length > max) {
    const window = rest.slice(0, max);
    const at = ["\n", "।", "?", "!", ".", " "].map((c) => window.lastIndexOf(c)).find((i) => i > max / 3);
    const piece = rest.slice(0, at === undefined ? max : at + 1);
    const kept = piece.trimEnd();
    rest = rest.slice(piece.length);
    const space = rest.match(/^\s*/)?.[0] ?? "";
    rest = rest.slice(space.length);
    parts.push({ text: kept, sep: piece.slice(kept.length) + space });
  }
  if (rest) parts.push({ text: rest, sep: "" });
  return parts;
}

async function translated(translate: Translator, text: string, lang: "en" | "gu", max: number): Promise<string> {
  const parts = splitForTranslation(text);
  let out = "";
  for (let i = 0; i < parts.length; i += PIECES_AT_ONCE) {
    const batch = parts.slice(i, i + PIECES_AT_ONCE);
    const done = await Promise.all(batch.map((p) => translate(p.text, lang)));
    done.forEach((piece, j) => {
      if (!piece.trim()) throw new Error(`empty ${lang} translation`);
      out += piece.trim() + batch[j].sep;
    });
  }
  out = out.trim();
  if (!out) throw new Error(`empty ${lang} translation`);
  if (out.length > max) throw new Error(`${lang} translation is over ${max} characters`);
  return out;
}

/** The app removes the translations when the Hindi changes, so the same Hindi without them needs translating again. */
function alreadyTranslated(snap: DocumentSnapshot, hash: string): boolean {
  return snap.get("translatedHash") === hash && TEXT_FIELDS.some((f) => {
    const v = snap.get(f);
    return typeof v === "string" && v.trim() !== "";
  });
}

/** After a failure, translations of older Hindi must not stay on show: members fall back to the current Hindi. */
async function clearOutdated(db: Firestore, ref: DocumentReference, hash: string): Promise<void> {
  await db.runTransaction(async (tx) => {
    const now = await tx.get(ref);
    const from = now.get("translatedHash");
    if (!now.exists || typeof from !== "string" || from === hash) return;
    if (readSourceHash(String(now.get("question")), String(now.get("answer"))) !== hash) return;
    const remove = Object.fromEntries([...TEXT_FIELDS, "translatedHash"].map((f) => [f, FieldValue.delete()]));
    tx.update(ref, remove);
  });
}

/**
 * Fills in the English and Gujarati of a Read question when its Hindi is new or changed.
 * All four texts are saved together or not at all, and only if the Hindi is still the same.
 */
export async function translateReadQa(db: Firestore, id: string, translate: Translator): Promise<"translated" | "skipped"> {
  const ref = db.doc(`readQa/${id}`);
  const snap = await ref.get();
  const question = snap.get("question");
  const answer = snap.get("answer");
  if (!snap.exists || typeof question !== "string" || typeof answer !== "string") return "skipped";
  const hash = readSourceHash(question, answer);
  if (alreadyTranslated(snap, hash)) return "skipped";

  let texts: string[];
  try {
    texts = await Promise.all([
      translated(translate, question, "en", QUESTION_MAX),
      translated(translate, answer, "en", ANSWER_MAX),
      translated(translate, question, "gu", QUESTION_MAX),
      translated(translate, answer, "gu", ANSWER_MAX),
    ]);
  } catch (e) {
    await clearOutdated(db, ref, hash);
    throw e;
  }
  const [questionEn, answerEn, questionGu, answerGu] = texts;

  return db.runTransaction(async (tx) => {
    const now = await tx.get(ref);
    if (!now.exists || alreadyTranslated(now, hash)) return "skipped";
    if (readSourceHash(String(now.get("question")), String(now.get("answer"))) !== hash) return "skipped";
    tx.update(ref, { question_en: questionEn, answer_en: answerEn, question_gu: questionGu, answer_gu: answerGu, translatedHash: hash });
    return "translated";
  });
}
