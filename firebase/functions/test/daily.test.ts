import { claimDailyOpenFor } from "../src/daily";
import { ensureUserDoc } from "../src/auth";
import { clearDb, testDb } from "./emulator";

const db = testDb();
beforeEach(async () => {
  await clearDb(db);
  await ensureUserDoc(db, "u1");
});

const user = async () => (await db.doc("users/u1").get()).data()!;

test("two calls on the same IST day award one point", async () => {
  const at = new Date("2026-10-29T05:00:00Z");
  expect(await claimDailyOpenFor(db, "u1", at)).toEqual({ awarded: true, points: 1 });
  expect(await claimDailyOpenFor(db, "u1", new Date("2026-10-29T10:00:00Z"))).toEqual({ awarded: false, points: 1 });
  const u = await user();
  expect(u.mokshaPoints).toBe(1);
  expect(u.lastDailyOpen).toBe("2026-10-29");
});

test("calls either side of IST midnight award two points", async () => {
  await claimDailyOpenFor(db, "u1", new Date("2026-10-29T18:29:00Z"));
  await claimDailyOpenFor(db, "u1", new Date("2026-10-29T18:31:00Z"));
  const u = await user();
  expect(u.mokshaPoints).toBe(2);
  expect(u.activeDays).toBe(2);
});

test("daily point is recorded as a milestone pointEvent", async () => {
  await claimDailyOpenFor(db, "u1", new Date("2026-10-29T05:00:00Z"));
  const ev = (await db.doc("users/u1/pointEvents/daily:2026-10-29").get()).data()!;
  expect(ev).toMatchObject({ amount: 1, milestone: true });
});

test("seventh day lifts a user to level 2", async () => {
  await db.doc("users/u1").update({ activeDays: 6, mokshaPoints: 6 });
  await claimDailyOpenFor(db, "u1", new Date("2026-10-29T05:00:00Z"));
  expect((await user()).userLevel).toBe(2);
});
