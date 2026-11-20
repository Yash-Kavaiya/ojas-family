import type { Firestore } from "firebase-admin/firestore";
import { allows, qaDailyLimit, RULES } from "./access";
import { istDay } from "./time";

/**
 * Holds a new Q&A question to the plan table's daily limit (1 with words only, 5 once photos, audio
 * and video open, 10 for Gold after a year). firestore.rules checks who may post at all; a count per
 * day can't be checked there, so a question past the limit is removed here. Returns false when it was.
 */
export async function enforceQuestionLimit(
  db: Firestore, questionId: string, data: Record<string, unknown>, now: Date,
): Promise<boolean> {
  const uid = typeof data.authorUid === "string" ? data.authorUid : "";
  if (!uid) return true;
  const userRef = db.doc(`users/${uid}`);
  const user = (await userRef.get()).data() ?? {};
  const role = user.role ?? data.authorRole;
  const withMedia = String(data.imageUrl ?? "") !== "" || String(data.mediaUrl ?? "") !== "";
  const limit = qaDailyLimit(user, role, now);
  const allowed = withMedia ? allows(user, role, RULES.askMedia, now) : limit > 0;
  const day = istDay(now);
  const counterRef = userRef.collection("counters").doc(day);
  const ok = allowed && await db.runTransaction(async (tx) => {
    const n = Number((await tx.get(counterRef)).get("questions") ?? 0);
    if (n >= limit) return false;
    tx.set(counterRef, { questions: n + 1, day }, { merge: true });
    return true;
  });
  if (!ok) await db.doc(`questions/${questionId}`).delete();
  return ok;
}
