import { completePracticeFor, startPracticeFor } from "../src/practiceMedia";
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

test("finishing a practice gives one point, once a day", async () => {
  const before = await points();
  await startPracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T04:00:00Z"));
  expect((await completePracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T04:10:00Z"))).awarded).toBe(true);
  expect(await points()).toBe(before + 1);
  // Watching again the same day earns nothing more
  expect((await completePracticeFor(db, "u1", undefined, "blue_light", at("2026-11-08T05:00:00Z"))).awarded).toBe(false);
  expect(await points()).toBe(before + 1);
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
