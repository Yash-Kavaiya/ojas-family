import type { Firestore } from "firebase-admin/firestore";
import { awardPoints } from "./points";

/** Awards a goal's reward once, the first time an admin approves it (event key makes re-approval free). */
export async function onGoalProgressWritten(
  db: Firestore, uid: string, goalId: string,
  before: { status?: string } | undefined, after: { status?: string } | undefined,
): Promise<void> {
  if (after?.status !== "approved" || before?.status === "approved") return;
  const reward = (await db.doc(`goals/${goalId}`).get()).get("reward");
  if (typeof reward !== "number" || reward <= 0) return;
  await awardPoints(db, uid, { eventKey: `goal:${goalId}`, amount: reward, reason: "goal", milestone: true });
}
