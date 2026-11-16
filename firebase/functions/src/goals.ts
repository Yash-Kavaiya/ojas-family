import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { notifyUser, type Messenger } from "./notify";
import { awardPoints } from "./points";
import { dayBefore } from "./practice";
import { istDay } from "./time";

type Data = Record<string, unknown> | undefined;

/** Goal levels, easiest first. A goal with no level counts as easy. */
export const GOAL_LEVELS = ["easy", "medium", "hard"] as const;
/** Sadhana goals of the next level open after this many of the level before are achieved. */
export const UNLOCK_AFTER = 2;

const levelOf = (v: unknown) => (GOAL_LEVELS as readonly string[]).includes(String(v)) ? String(v) : "easy";
/** Sadhana goals: ticked each day, or completed by the morning/night practice log. Photo goals are "activities". */
const isSadhana = (kind: unknown) => kind === "check" || kind === "practice_morning_21" || kind === "practice_both_21";
const hasLink = (text: string) => /https?:\/\/|www\./i.test(text);

/**
 * The sadhana levels a member may work on. Easy is always open; each later level opens once
 * [UNLOCK_AFTER] goals of the level before it are achieved (or all of them, if it has fewer).
 * The app's `unlockedLevels` follows the same rule.
 */
export function unlockedLevels(goals: Array<{ id: string; kind: unknown; level: unknown; active: unknown }>, achieved: Set<string>): Set<string> {
  const open = new Set<string>();
  for (const level of GOAL_LEVELS) {
    open.add(level);
    const here = goals.filter((g) => g.active !== false && isSadhana(g.kind) && levelOf(g.level) === level);
    if (here.filter((g) => achieved.has(g.id)).length < Math.min(UNLOCK_AFTER, here.length)) break;
  }
  return open;
}

/** The Activity post that shows a member's goal photo; one per member and goal. */
const goalPostId = (uid: string, goalId: string) => `goal_${uid}_${goalId}`;

/**
 * A goal photo is also shared in Community > Activity as soon as it is sent; if an admin rejects the
 * photo the post is taken down again. Points still wait for the admin's approval.
 */
async function mirrorGoalPhoto(db: Firestore, uid: string, goalId: string, before: Data, after: Data): Promise<void> {
  const post = db.doc(`dailyPosts/${goalPostId(uid, goalId)}`);
  if (after?.status === "rejected" && before?.status !== "rejected") {
    await db.recursiveDelete(post);
    return;
  }
  const photoUrl = typeof after?.photoUrl === "string" ? after.photoUrl : "";
  const fresh = after?.status === "submitted" && (before?.status !== "submitted" || before?.photoUrl !== photoUrl);
  if (!fresh || !photoUrl) return;
  const goal = await db.doc(`goals/${goalId}`).get();
  if (goal.get("kind") !== "photo") return;
  const title = goal.get("title");
  const user = (await db.doc(`users/${uid}`).get()).data() ?? {};
  const note = typeof after?.note === "string" ? after.note : "";
  await post.set({
    authorUid: uid,
    authorName: String(user.displayName ?? ""),
    authorPhoto: String(user.photoUrl ?? ""),
    authorRole: String(user.role ?? "member"),
    ...(Number(user.userLevel ?? 0) > 0 ? { authorLevel: Number(user.userLevel) } : {}),
    ...(Number(user.memberLevel ?? 0) > 0 ? { authorMember: Number(user.memberLevel) } : {}),
    text: note,
    imageUrl: photoUrl,
    goalId,
    // The goal's name in each language, so the post says which activity the photo is for
    ...(title && typeof title === "object" ? { goalTitle: title } : {}),
    status: hasLink(note) ? "pending" : "visible",
    createdAt: FieldValue.serverTimestamp(),
  });
}

/**
 * Awards a goal's reward once, the first time it is approved (by an admin, or automatically for
 * sadhana goals); the event key makes re-approval free. Approved goals are listed on the user so the
 * public profile can show them.
 */
export async function onGoalProgressWritten(
  db: Firestore, uid: string, goalId: string,
  before: Data, after: Data,
): Promise<void> {
  await mirrorGoalPhoto(db, uid, goalId, before, after);
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

/**
 * Ticks today (IST) for a daily sadhana goal. Days must be in a row: a missed day starts the count
 * again from 1. When the goal's number of days is reached the progress is approved, so the reward
 * goes through the same once-only trigger as every other goal. The running count is also kept on
 * the user (goalDays) so the profile shows which sadhana is being done and for how many days.
 */
export async function tickGoalFor(
  db: Firestore, uid: string, goalId: string, now: Date,
): Promise<{ streak: number; days: number; completed: boolean }> {
  if (!/^[A-Za-z0-9_-]{1,80}$/.test(goalId)) throw new HttpsError("invalid-argument", "goalId is not valid");
  const all = (await db.collection("goals").get()).docs;
  const goal = all.find((g) => g.id === goalId);
  if (!goal || goal.get("active") === false || goal.get("kind") !== "check") throw new HttpsError("not-found", "No such daily goal");
  const days = Math.min(400, Math.max(1, Math.floor(Number(goal.get("days")) || 21)));

  const userRef = db.doc(`users/${uid}`);
  const achieved = new Set<string>(((await userRef.get()).get("achievedGoalIds") as string[] | undefined) ?? []);
  const open = unlockedLevels(all.map((g) => ({ id: g.id, kind: g.get("kind"), level: g.get("level"), active: g.get("active") })), achieved);
  if (!open.has(levelOf(goal.get("level")))) throw new HttpsError("failed-precondition", "This level is not open yet");

  const today = istDay(now);
  const ref = userRef.collection("goalProgress").doc(goalId);
  return db.runTransaction(async (tx) => {
    const cur = await tx.get(ref);
    const was = Number(cur.get("streak") ?? 0);
    if (cur.get("status") === "approved") return { streak: days, days, completed: false };
    if (cur.get("lastDay") === today) return { streak: was, days, completed: false };
    const streak = cur.get("lastDay") === dayBefore(today, 1) ? was + 1 : 1;
    const completed = streak >= days;
    tx.set(ref, {
      status: completed ? "approved" : "active", auto: true, photoUrl: "", note: "", streak, lastDay: today,
      ...(streak === 1 ? { startedDay: today } : {}),
      ...(completed ? { reviewedAt: FieldValue.serverTimestamp() } : {}),
    }, { merge: true });
    tx.set(userRef, { goalDays: { [goalId]: { streak, lastDay: today } } }, { merge: true });
    return { streak, days, completed };
  });
}

/** A goal's name for a notification: English, else any language there is. */
function goalName(title: unknown, fallback: string): string {
  if (typeof title === "string") return title;
  if (title && typeof title === "object") {
    const t = title as Record<string, unknown>;
    const any = [t.en, t.hi, t.gu, ...Object.values(t)].find((v) => typeof v === "string" && v.length > 0);
    if (typeof any === "string") return any;
  }
  return fallback;
}

/**
 * Critical: the member is told when a goal is achieved (an admin approved its photo, or the days were
 * completed) or when a goal photo was not approved.
 */
export async function onGoalDecisionNotify(db: Firestore, m: Messenger, uid: string, goalId: string, before: Data, after: Data): Promise<void> {
  const now = after?.status;
  if (now === before?.status || (now !== "approved" && now !== "rejected")) return;
  const goal = await db.doc(`goals/${goalId}`).get();
  await notifyUser(db, m, uid, { group: "critical" }, {
    type: now === "approved" ? "goalApproved" : "goalRejected",
    body: goalName(goal.get("title"), goalId),
    target: "goals",
    ...(now === "approved" && typeof goal.get("reward") === "number" ? { points: String(goal.get("reward")) } : {}),
  }, `goal:${goalId}:${now}`);
}

/** Critical: the member is told whether their face photo for the opening animation was approved. */
export async function onFaceDecisionNotify(db: Firestore, m: Messenger, uid: string, before: Data, after: Data): Promise<void> {
  const now = after?.status;
  if (now === before?.status || (now !== "approved" && now !== "rejected")) return;
  await notifyUser(db, m, uid, { group: "critical" }, { type: now === "approved" ? "faceApproved" : "faceRejected", target: "profile" }, `face:${now}`);
}
