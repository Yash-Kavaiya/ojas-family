import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { istDay } from "./time";

export type PracticeKind = "morning" | "night";
export type DayLog = { morning?: boolean; night?: boolean };

export const STREAK_GOAL_DAYS = 21;

/** The IST day [offset] days before [day] ("yyyy-MM-dd"). */
export function dayBefore(day: string, offset: number): string {
  const [y, m, d] = day.split("-").map(Number);
  const t = new Date(Date.UTC(y, m - 1, d - offset));
  return t.toISOString().slice(0, 10);
}

/** Consecutive days ending on [today] whose log satisfies [done]. */
export function streak(logs: Record<string, DayLog>, today: string, done: (l: DayLog) => boolean): number {
  let n = 0;
  while (n < 400 && logs[dayBefore(today, n)] && done(logs[dayBefore(today, n)])) n++;
  return n;
}

// Same rule as the app's Permissions.canDoDailyPractices: free Level 4, any paid member, or staff
export function canPractise(user: Record<string, unknown>, role: unknown): boolean {
  if (role === "admin" || role === "swamiji" || role === "senior_member") return true;
  return Number(user.userLevel ?? 1) >= 4 || Number(user.memberLevel ?? 0) >= 1;
}

/**
 * Records today's (IST) morning or night practice, then completes the 21-day goals whose streak is
 * reached. A goal is completed by writing an approved goalProgress, so the reward goes through the
 * same once-only trigger as photo goals.
 */
export async function logPracticeFor(
  db: Firestore, uid: string, role: unknown, kind: string, now: Date,
): Promise<{ morningStreak: number; bothStreak: number; completed: string[] }> {
  if (kind !== "morning" && kind !== "night") throw new HttpsError("invalid-argument", "kind must be morning or night");
  const userRef = db.doc(`users/${uid}`);
  const user = (await userRef.get()).data() ?? {};
  if (!canPractise(user, role)) throw new HttpsError("permission-denied", "Daily practices unlock at Level 4 or with membership");

  const today = istDay(now);
  await userRef.collection("practiceLog").doc(today).set({ [kind]: true, updatedAt: FieldValue.serverTimestamp() }, { merge: true });

  const since = dayBefore(today, STREAK_GOAL_DAYS + 5);
  const snap = await userRef.collection("practiceLog").where("__name__", ">=", userRef.collection("practiceLog").doc(since)).get();
  const logs: Record<string, DayLog> = {};
  snap.forEach((d) => { logs[d.id] = d.data() as DayLog; });

  const morningStreak = streak(logs, today, (l) => l.morning === true);
  const bothStreak = streak(logs, today, (l) => l.morning === true && l.night === true);

  const completed: string[] = [];
  // Seeded goals carry no "active" field; only an explicit false switches a goal off
  const goals = await db.collection("goals").get();
  for (const g of goals.docs) {
    if (g.get("active") === false) continue;
    const k = g.get("kind");
    const reached = (k === "practice_morning_21" && morningStreak >= STREAK_GOAL_DAYS)
      || (k === "practice_both_21" && bothStreak >= STREAK_GOAL_DAYS);
    if (!reached) continue;
    const progress = userRef.collection("goalProgress").doc(g.id);
    if ((await progress.get()).get("status") === "approved") continue;
    await progress.set({ status: "approved", auto: true, photoUrl: "", note: "", reviewedAt: FieldValue.serverTimestamp() }, { merge: true });
    completed.push(g.id);
  }
  return { morningStreak, bothStreak, completed };
}
