import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { awardPoints } from "./points";

/**
 * Awards a goal's reward once, the first time it is approved (by an admin, or automatically for 21-day
 * practice goals); the event key makes re-approval free. Approved goals are listed on the user so the
 * public profile can show them.
 */
export async function onGoalProgressWritten(
  db: Firestore, uid: string, goalId: string,
  before: { status?: string } | undefined, after: { status?: string } | undefined,
): Promise<void> {
  const userRef = db.doc(`users/${uid}`);
  if (before?.status === "approved" && after?.status !== "approved") {
    await userRef.set({ achievedGoalIds: FieldValue.arrayRemove(goalId) }, { merge: true });
    return;
  }
  if (after?.status !== "approved" || before?.status === "approved") return;
  await userRef.set({ achievedGoalIds: FieldValue.arrayUnion(goalId) }, { merge: true });
  const reward = (await db.doc(`goals/${goalId}`).get()).get("reward");
  if (typeof reward !== "number" || reward <= 0) return;
  await awardPoints(db, uid, { eventKey: `goal:${goalId}`, amount: reward, reason: "goal", milestone: true });
}
