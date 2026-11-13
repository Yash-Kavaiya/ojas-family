import type { Firestore } from "firebase-admin/firestore";
import { awardPoints } from "./points";
import { loadPointsConfig } from "./pointsConfig";
import { istDay } from "./time";

// Moksha Points (config/points.dailyOpen) and +1 active day, once per IST calendar day
export async function claimDailyOpenFor(db: Firestore, uid: string, now: Date): Promise<{ awarded: boolean; points: number }> {
  const day = istDay(now);
  const cfg = await loadPointsConfig(db);
  const awarded = await awardPoints(db, uid, {
    eventKey: `daily:${day}`,
    amount: cfg.dailyOpen,
    reason: "daily",
    milestone: true,
    activeDaysDelta: 1,
    extra: { lastDailyOpen: day },
  });
  const points = (await db.doc(`users/${uid}`).get()).get("mokshaPoints") ?? 0;
  return { awarded, points };
}
