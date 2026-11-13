import { PRACTICE_POINTS, completePracticeFor, startPracticeFor } from "../src/practiceMedia";
import { DEFAULT_POINTS } from "../src/pointsConfig";
import { ensureUserDoc } from "../src/auth";
import { clearDb, testDb } from "./emulator";

const db = testDb();
beforeEach(async () => {
  await clearDb(db);
  await ensureUserDoc(db, "u1");
  await db.doc("config/practiceMedia").set({
    blue_light: { url: "https://x/blue.mp4", kind: "video", durationSec: 600, title: "Blue" },
    night: { url: "https://x/night.mp4", kind: "video", durationSec: 300, title: "Night" },
  });
});

const points = async () => (await db.doc("users/u1").get()).get("mokshaPoints") as number;
const at = (iso: string) => new Date(iso);

test("finishing a practice gives the practice points, once a day", async () => {
  const before = await points();
  await startPracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T04:00:00Z"));
  const r = await completePracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T04:10:00Z"));
  expect(r.awarded).toBe(true);
  expect(r.points).toBe(PRACTICE_POINTS);
  expect(r.streak).toBe(1);
  expect(await points()).toBe(before + PRACTICE_POINTS);
  // Watching again the same day earns nothing more
  expect((await completePracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T05:00:00Z"))).awarded).toBe(false);
  expect(await points()).toBe(before + PRACTICE_POINTS);
});

test("practice amounts come from config/points", async () => {
  await db.doc("config/points").set({ practice: 7 });
  const before = await points();
  await startPracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T04:00:00Z"));
  await completePracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T04:10:00Z"));
  expect(await points()).toBe(before + 7);
});

test("a YouTube practice works like a file", async () => {
  await db.doc("config/practiceMedia").set({ quick_protection: { kind: "youtube", videoId: "abc123", durationSec: 300, title: "Vidhi 1" } }, { merge: true });
  await startPracticeFor(db, "u1", undefined, "quick_protection", at("2026-11-08T04:00:00Z"));
  expect((await completePracticeFor(db, "u1", undefined, "quick_protection", at("2026-11-08T04:05:00Z"))).awarded).toBe(true);
});

test("a YouTube fallback video counts with its own, shorter length", async () => {
  await db.doc("config/practiceMedia").set({
    quick_protection: { kind: "youtube", videoId: "abc123", durationSec: 1200, title: "Vidhi 1", fallbackVideoId: "QFNoym-jpbY", fallbackDurationSec: 282 },
  }, { merge: true });
  await startPracticeFor(db, "u1", undefined, "quick_protection", at("2026-11-08T04:00:00Z"));
  expect((await completePracticeFor(db, "u1", undefined, "quick_protection", at("2026-11-08T04:04:50Z"))).awarded).toBe(true);
});

test("the 7th day in a row gives the streak bonus once", async () => {
  for (let d = 1; d <= 6; d++) {
    await db.doc(`users/u1/practiceLog/2026-11-0${d}`).set({ any: true });
  }
  const before = await points();
  await startPracticeFor(db, "u1", undefined, "blue_light", at("2026-11-07T04:00:00Z"));
  const r = await completePracticeFor(db, "u1", undefined, "blue_light", at("2026-11-07T04:10:00Z"));
  expect(r.streak).toBe(7);
  expect(r.points).toBe(DEFAULT_POINTS.practice + DEFAULT_POINTS.streakBonus);
  expect(await points()).toBe(before + DEFAULT_POINTS.practice + DEFAULT_POINTS.streakBonus);
  expect((await db.doc("users/u1/practiceLog/2026-11-07").get()).get("any")).toBe(true);
});

test("all four practices in a day give the bonus", async () => {
  await db.doc("users/u1").set({ memberLevel: 1 }, { merge: true });
  await db.doc("config/practiceMedia").set({
    quick_protection: { url: "https://x/q.mp4", kind: "video", durationSec: 60 },
    morning: { url: "https://x/m.mp4", kind: "video", durationSec: 60 },
  }, { merge: true });
  let last = { points: 0 };
  for (const [i, s] of (["blue_light", "quick_protection", "morning", "night"] as const).entries()) {
    const start = new Date(Date.UTC(2026, 10, 8, 2 + i, 0));
    await startPracticeFor(db, "u1", undefined, s, start);
    last = await completePracticeFor(db, "u1", undefined, s, new Date(start.getTime() + 11 * 60_000));
  }
  expect(last.points).toBe(DEFAULT_POINTS.practice + DEFAULT_POINTS.allPracticesBonus);
});

test("skipping to the end earns nothing", async () => {
  await startPracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T04:00:00Z"));
  await expect(completePracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T04:02:00Z"))).rejects.toThrow(/Finish/);
});

test("resuming later keeps the first start of the day", async () => {
  await startPracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T04:00:00Z"));
  const again = await startPracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T04:08:00Z"));
  expect(again.startedAt).toBe(at("2026-11-08T04:00:00Z").getTime());
  expect((await completePracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T04:09:30Z"))).awarded).toBe(true);
});

test("night practice is locked below Level 4 without membership, and counts as the day's practice when open", async () => {
  await expect(startPracticeFor(db, "u1", undefined, "night", at("2026-11-08T15:00:00Z"))).rejects.toThrow(/Level 4/);
  await db.doc("users/u1").set({ memberLevel: 1 }, { merge: true });
  await startPracticeFor(db, "u1", undefined, "night", at("2026-11-08T15:00:00Z"));
  await completePracticeFor(db, "u1", undefined, "night", at("2026-11-08T15:05:00Z"));
  expect((await db.doc("users/u1/practiceLog/2026-11-08").get()).get("night")).toBe(true);
});

test("a practice with no media cannot start", async () => {
  await expect(startPracticeFor(db, "u1", undefined, "quick_protection", at("2026-11-08T04:00:00Z"))).rejects.toThrow(/no media/);
  await expect(startPracticeFor(db, "u1", undefined, "yoga", at("2026-11-08T04:00:00Z"))).rejects.toThrow(/Unknown/);
});
