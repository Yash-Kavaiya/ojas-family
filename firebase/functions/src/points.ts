import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { computeUserLevel, loadLevelConfig } from "./levels";

export interface Award {
  eventKey: string; // idempotency key, e.g. "daily:2026-10-29" or "like:thought:2026-10-29"
  amount: number;
  reason: string;
  milestone: boolean;
  note?: string; // shown in the member's points history, e.g. an admin's gift message
  activeDaysDelta?: number;
  extra?: Record<string, unknown>; // other server-only fields to set in the same transaction
  cap?: { action: "like" | "comment" | "question"; day: string }; // counts against config dailyCaps for that IST day
  countsForLevel?: boolean; // default true; false only adds to the spendable balance
}

/**
 * Points that count toward Level: everything earned, but not points received from other members.
 * Older accounts have no earnedPoints yet; until then their whole balance was earned.
 */
export function earnedOf(user: Record<string, unknown> | undefined): number {
  const d = user ?? {};
  return Number(d.earnedPoints ?? d.mokshaPoints ?? 0);
}

/**
 * Writes mokshaPoints (the spendable balance) and earnedPoints (what Level uses). Transfers and
 * staff adjustments in transfer.ts are the only other writers. Records users/{uid}/pointEvents/{eventKey} and updates the
 * total and level atomically. Returns false, changing nothing, if the event was already awarded.
 */
export async function awardPoints(db: Firestore, uid: string, award: Award): Promise<boolean> {
  const cfg = await loadLevelConfig(db);
  const userRef = db.doc(`users/${uid}`);
  const eventRef = userRef.collection("pointEvents").doc(award.eventKey);
  const counterRef = award.cap ? userRef.collection("counters").doc(award.cap.day) : null;

  return db.runTransaction(async (tx) => {
    const [event, user, counter] = await Promise.all([
      tx.get(eventRef),
      tx.get(userRef),
      counterRef ? tx.get(counterRef) : Promise.resolve(null),
    ]);
    if (event.exists) return false;

    // Over the daily cap the action still happens; it just earns nothing
    if (award.cap && counterRef) {
      const used = (counter?.get(award.cap.action) as number | undefined) ?? 0;
      if (used >= cfg.dailyCaps[award.cap.action]) return false;
      tx.set(counterRef, { [award.cap.action]: used + 1 }, { merge: true });
    }

    const data = user.data() ?? {};
    const points = (data.mokshaPoints ?? 0) + award.amount;
    const earned = earnedOf(data) + (award.countsForLevel === false ? 0 : award.amount);
    const activeDays = (data.activeDays ?? 0) + (award.activeDaysDelta ?? 0);
    const userLevel = computeUserLevel({ activeDays, points: earned }, cfg);

    tx.set(eventRef, {
      amount: award.amount,
      reason: award.reason,
      milestone: award.milestone,
      ...(award.note ? { note: award.note } : {}),
      createdAt: FieldValue.serverTimestamp(),
    });
    tx.set(userRef, { ...award.extra, mokshaPoints: points, earnedPoints: earned, activeDays, userLevel }, { merge: true });
    return true;
  });
}
