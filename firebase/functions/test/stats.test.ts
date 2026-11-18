import { Timestamp } from "firebase-admin/firestore";
import { backfillStatsFor, computeDay, dayRange, daysBefore, todayStatsFor, userCountsFor, writeDailyStats } from "../src/stats";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const NOW = new Date("2026-11-07T06:00:00Z"); // 11:30 IST on 7 Nov
const ADMIN = { role: "admin" };
const at = (day: string, hour: number) => Timestamp.fromDate(new Date(`${day}T${String(hour).padStart(2, "0")}:00:00+05:30`));

beforeEach(async () => { await clearDb(db); });

/** Two members using the app on 6 Nov, and something on other days that must not be counted. */
async function seed() {
  await db.doc("users/m1").set({ createdAt: at("2026-10-01", 9), language: "gu", country: "India", state: "Gujarat", userLevel: 2, memberLevel: 3 });
  await db.doc("users/m2").set({ createdAt: at("2026-11-06", 10), language: "en", country: "India", state: "Maharashtra", userLevel: 1 });
  await db.doc("users/m3").set({ createdAt: at("2026-11-07", 8), language: "hi" });
  for (const uid of ["m1", "m2"]) await db.doc(`users/${uid}/pointEvents/daily:2026-11-06`).set({ reason: "daily", amount: 1, createdAt: at("2026-11-06", 7) });
  await db.doc("users/m1/pointEvents/daily:2026-11-05").set({ reason: "daily", amount: 1, createdAt: at("2026-11-05", 7) });
  await db.doc("users/m1/pointEvents/x").set({ reason: "gift", amount: 5, createdAt: at("2026-11-06", 8) });

  await db.doc("users/m1/practiceSessions/blue_light_2026-11-06").set({ section: "blue_light", day: "2026-11-06", startedAt: at("2026-11-06", 6), completedAt: at("2026-11-06", 6) });
  await db.doc("users/m2/practiceSessions/blue_light_2026-11-06").set({ section: "blue_light", day: "2026-11-06", startedAt: at("2026-11-06", 7) });
  await db.doc("users/m2/practiceSessions/night_2026-11-06").set({ section: "night", day: "2026-11-06", startedAt: at("2026-11-06", 22), completedAt: at("2026-11-06", 22) });
  await db.doc("users/m1/practiceLog/2026-11-06").set({ any: true, blue_light: true, updatedAt: at("2026-11-06", 6) });
  await db.doc("users/m2/practiceLog/2026-11-06").set({ any: true, night: true, updatedAt: at("2026-11-06", 22) });

  await db.doc("users/m1/counters/2026-11-06").set({ ai: 3, day: "2026-11-06" });
  await db.doc("users/m2/counters/2026-11-06").set({ ai: 2, like: 4, day: "2026-11-06" });
  await db.doc("aiFeedback/m1_a").set({ rating: 1, createdAt: at("2026-11-06", 9) });
  await db.doc("aiFeedback/m1_b").set({ rating: -1, createdAt: at("2026-11-06", 9) });

  await db.doc("questions/q1").set({ createdAt: at("2026-11-06", 9) });
  await db.doc("questions/q1/answers/a1").set({ createdAt: at("2026-11-06", 10) });
  await db.doc("questions/q1/likes/m2").set({ createdAt: at("2026-11-06", 10) });
  await db.doc("dailyPosts/p1").set({ createdAt: at("2026-11-06", 11) });
  await db.doc("dailyPosts/p1/replies/r1").set({ createdAt: at("2026-11-06", 12) });
  await db.doc("thoughts/2026-11-06/comments/c1").set({ createdAt: at("2026-11-06", 13) });
  await db.doc("questions/q2").set({ createdAt: at("2026-11-07", 9) });

  await db.doc("calls/k1").set({ createdAt: at("2026-11-06", 15), seconds: 150 });
  await db.doc("calls/k2").set({ createdAt: at("2026-11-06", 16), seconds: 0 });
  await db.doc("pointTransfers/t1").set({ createdAt: at("2026-11-06", 17), amount: 20 });
  await db.doc("users/m1/entitlements/membership").set({ expiresAt: Timestamp.fromDate(new Date(NOW.getTime() + 30 * 86_400_000)) });
}

describe("usage dashboard numbers", () => {
  test("days are Indian calendar days", () => {
    expect(daysBefore("2026-11-01", 1)).toBe("2026-10-31");
    const [s, e] = dayRange("2026-11-06");
    expect(s.toDate().toISOString()).toBe("2026-11-05T18:30:00.000Z");
    expect(e.toMillis() - s.toMillis()).toBe(86_400_000);
  });

  test("one day is counted from what members did that day only", async () => {
    await seed();
    const d = await computeDay(db, "2026-11-06");
    expect(d.people).toEqual({ active: 2, new: 1, total: 2 });
    expect(d.practice.people).toBe(2);
    expect(d.practice.sections).toEqual({
      blue_light: { started: 2, completed: 1 }, quick_protection: { started: 0, completed: 0 },
      morning: { started: 0, completed: 0 }, night: { started: 1, completed: 1 },
    });
    expect(d.ai).toEqual({ questions: 5, up: 1, down: 1 });
    expect(d.community).toEqual({ questions: 1, answers: 1, posts: 1, replies: 1, comments: 1, likes: 1, announcements: 0 });
    expect(d.calls).toEqual({ total: 2, connected: 1, minutes: 3, requests: 0 });
    expect(d.points).toEqual({ transfers: 1, transferred: 20 });
  });

  test("after midnight yesterday is stored with who is paid and where members are from", async () => {
    await seed();
    expect(await writeDailyStats(db, NOW)).toBe("2026-11-06");
    const s = (await db.doc("stats/2026-11-06").get()).data()!;
    expect(s.people.active).toBe(2);
    expect(s.snapshot.paid).toEqual({ membership: 1, chat: 0, call: 0 });
    expect(s.snapshot.languages).toEqual({ en: 1, hi: 1, gu: 1 });
    expect(s.snapshot.states).toEqual({ Gujarat: 1, Maharashtra: 1, "Not set": 1 });
    expect(s.snapshot.userLevels).toEqual([2, 1, 0, 0]);
    expect(s.snapshot.memberLevels).toEqual([0, 0, 1]);
  });

  test("only admins see today's numbers or rebuild past days, and stored days are kept", async () => {
    await seed();
    await expect(todayStatsFor(db, { role: "member" }, NOW)).rejects.toThrow(/Admins only/);
    await expect(backfillStatsFor(db, { role: "member" }, { days: 3 }, NOW)).rejects.toThrow(/Admins only/);
    const today = await todayStatsFor(db, ADMIN, NOW);
    expect(today.day).toBe("2026-11-07");
    expect(today.community.questions).toBe(1);

    await db.doc("stats/2026-11-05").set({ day: "2026-11-05", kept: true });
    const r = await backfillStatsFor(db, ADMIN, { days: 3 }, NOW);
    expect(r.filled).toEqual(["2026-11-06", "2026-11-04"]);
    expect((await db.doc("stats/2026-11-05").get()).get("kept")).toBe(true);
    expect((await db.doc("stats/2026-11-06").get()).get("rebuilt")).toBe(true);
  });
});

describe("people count on Home for Swamiji and admins", () => {
  test("counts everyone who signed up, and the members with a membership level", async () => {
    await db.doc("users/a").set({ userLevel: 1 });
    await db.doc("users/b").set({ userLevel: 2, memberLevel: 1 });
    await db.doc("users/c").set({ userLevel: 1, memberLevel: 3 });
    await db.doc("users/d").set({ userLevel: 1, memberLevel: 0 });
    expect(await userCountsFor(db, { role: "swamiji" })).toEqual({ users: 4, members: 2 });
    expect(await userCountsFor(db, ADMIN)).toEqual({ users: 4, members: 2 });
  });

  test("other people may not see it", async () => {
    for (const role of [undefined, "member", "senior_member"]) {
      await expect(userCountsFor(db, role ? { role } : undefined)).rejects.toThrow(/Swamiji and admins/);
    }
  });
});
