import { ensureUserDoc } from "../src/auth";
import { onGoalProgressWritten, tickGoalFor, unlockedLevels } from "../src/goals";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const points = async () => (await db.doc("users/u1").get()).get("mokshaPoints") ?? 0;

beforeEach(async () => {
  await clearDb(db);
  await ensureUserDoc(db, "u1");
  await db.doc("goals/tree").set({ title: "Plant a tree", kind: "photo", reward: 25, order: 1, active: true });
});

test("approval awards goal reward as milestone", async () => {
  await onGoalProgressWritten(db, "u1", "tree", { status: "submitted" }, { status: "approved" });
  expect(await points()).toBe(25);
  expect((await db.doc("users/u1/pointEvents/goal:tree").get()).get("milestone")).toBe(true);
});

test("second approval awards nothing", async () => {
  await onGoalProgressWritten(db, "u1", "tree", { status: "submitted" }, { status: "approved" });
  await onGoalProgressWritten(db, "u1", "tree", { status: "rejected" }, { status: "approved" });
  expect(await points()).toBe(25);
});

test("rejection awards nothing", async () => {
  await onGoalProgressWritten(db, "u1", "tree", { status: "submitted" }, { status: "rejected" });
  expect(await points()).toBe(0);
});

describe("goal photo in Community > Activity", () => {
  const sent = { status: "submitted", photoUrl: "https://x/p.jpg", note: "Dal and rice" };

  test("a submitted photo is posted to Activity; points wait for approval", async () => {
    await db.doc("users/u1").set({ displayName: "Asha", memberLevel: 1 }, { merge: true });
    await onGoalProgressWritten(db, "u1", "tree", undefined, sent);
    const post = (await db.doc("dailyPosts/goal_u1_tree").get()).data();
    expect(post).toMatchObject({ authorUid: "u1", authorName: "Asha", imageUrl: "https://x/p.jpg", text: "Dal and rice", goalId: "tree", status: "visible", authorMember: 1 });
    expect(await points()).toBe(0);
  });

  test("a note with a link waits for an admin, and a rejected photo is taken down", async () => {
    await onGoalProgressWritten(db, "u1", "tree", undefined, { ...sent, note: "see www.example.com" });
    expect((await db.doc("dailyPosts/goal_u1_tree").get()).get("status")).toBe("pending");
    await onGoalProgressWritten(db, "u1", "tree", sent, { ...sent, status: "rejected" });
    expect((await db.doc("dailyPosts/goal_u1_tree").get()).exists).toBe(false);
  });

  test("approval keeps the post and does not post again", async () => {
    await onGoalProgressWritten(db, "u1", "tree", undefined, sent);
    await db.doc("dailyPosts/goal_u1_tree").update({ likeCount: 3 });
    await onGoalProgressWritten(db, "u1", "tree", sent, { ...sent, status: "approved" });
    expect((await db.doc("dailyPosts/goal_u1_tree").get()).get("likeCount")).toBe(3);
    expect(await points()).toBe(25);
  });
});

describe("daily sadhana goals", () => {
  const at = (day: string) => new Date(`${day}T06:00:00Z`); // 11:30 IST
  beforeEach(async () => {
    await db.doc("goals/breakfast").set({ kind: "check", level: "easy", days: 3, reward: 20, order: 1 });
    await db.doc("goals/water").set({ kind: "check", level: "easy", days: 3, reward: 20, order: 2 });
    await db.doc("goals/yoga").set({ kind: "check", level: "medium", days: 21, reward: 50, order: 3 });
  });
  const progress = async (id: string) => (await db.doc(`users/u1/goalProgress/${id}`).get()).data() ?? {};

  test("ticking counts days in a row, once a day, and shows on the user", async () => {
    expect(await tickGoalFor(db, "u1", "breakfast", at("2026-11-10"))).toEqual({ streak: 1, days: 3, completed: false });
    expect((await tickGoalFor(db, "u1", "breakfast", at("2026-11-10"))).streak).toBe(1);
    expect((await tickGoalFor(db, "u1", "breakfast", at("2026-11-11"))).streak).toBe(2);
    expect(await progress("breakfast")).toMatchObject({ status: "active", streak: 2, lastDay: "2026-11-11" });
    expect((await db.doc("users/u1").get()).get("goalDays")).toEqual({ breakfast: { streak: 2, lastDay: "2026-11-11" } });
  });

  test("a missed day starts again from 1", async () => {
    await tickGoalFor(db, "u1", "breakfast", at("2026-11-10"));
    await tickGoalFor(db, "u1", "breakfast", at("2026-11-11"));
    expect((await tickGoalFor(db, "u1", "breakfast", at("2026-11-13"))).streak).toBe(1);
  });

  test("reaching the goal's days approves it, and the reward is given once", async () => {
    await tickGoalFor(db, "u1", "breakfast", at("2026-11-10"));
    await tickGoalFor(db, "u1", "breakfast", at("2026-11-11"));
    expect(await tickGoalFor(db, "u1", "breakfast", at("2026-11-12"))).toEqual({ streak: 3, days: 3, completed: true });
    expect((await progress("breakfast")).status).toBe("approved");
    await onGoalProgressWritten(db, "u1", "breakfast", { status: "active" }, { status: "approved" });
    expect(await points()).toBe(20);
    expect((await tickGoalFor(db, "u1", "breakfast", at("2026-11-13"))).completed).toBe(false);
    expect((await progress("breakfast")).status).toBe("approved");
  });

  test("the next level opens after two goals of the level before", async () => {
    await expect(tickGoalFor(db, "u1", "yoga", at("2026-11-10"))).rejects.toThrow("not open");
    await db.doc("users/u1").set({ achievedGoalIds: ["breakfast", "water"] }, { merge: true });
    expect((await tickGoalFor(db, "u1", "yoga", at("2026-11-10"))).streak).toBe(1);
  });

  test("photo goals cannot be ticked", async () => {
    await expect(tickGoalFor(db, "u1", "tree", at("2026-11-10"))).rejects.toThrow("No such daily goal");
  });

  test("unlockedLevels: a level with a single goal opens the next once that one is done", () => {
    const goals = [
      { id: "a", kind: "check", level: "easy", active: true },
      { id: "b", kind: "check", level: "medium", active: true },
      { id: "p", kind: "photo", level: "easy", active: true },
    ];
    expect([...unlockedLevels(goals, new Set())]).toEqual(["easy"]);
    expect([...unlockedLevels(goals, new Set(["p"]))]).toEqual(["easy"]);
    expect([...unlockedLevels(goals, new Set(["a"]))]).toEqual(["easy", "medium"]);
  });
});
