import { ensureUserDoc } from "../src/auth";
import { awardPoints } from "../src/points";
import { reverseTransferFor, setTransfersFrozenFor, staffAdjustPointsFor, transferPointsFor, updatePointsConfigFor } from "../src/transfer";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const now = new Date("2026-11-12T06:00:00Z");
const admin = { role: "admin" };
const swamiji = { role: "swamiji" };
const member = { role: "member" };

const user = async (uid: string) => (await db.doc(`users/${uid}`).get()).data() ?? {};

beforeEach(async () => {
  await clearDb(db);
  for (const uid of ["a", "b"]) await ensureUserDoc(db, uid);
  await awardPoints(db, "a", { eventKey: "seed", amount: 150, reason: "gift", milestone: false });
  // After the award, which recomputes the Level from (zero) active days
  await db.doc("users/a").set({ displayName: "Asha", userLevel: 2 }, { merge: true });
  await db.doc("users/b").set({ displayName: "Bhavin" }, { merge: true });
});

test("sending moves the balance but not the Level points", async () => {
  const a0 = await user("a");
  const b0 = await user("b");
  const r = await transferPointsFor(db, "a", { toUid: "b", amount: 40, note: "For your seva" }, now);
  const a1 = await user("a");
  const b1 = await user("b");
  expect(r.balance).toBe(a0.mokshaPoints - 40);
  expect(a1.mokshaPoints).toBe(a0.mokshaPoints - 40);
  expect(b1.mokshaPoints).toBe(b0.mokshaPoints + 40);
  // Level points: unchanged on both sides
  expect(a1.earnedPoints).toBe(a0.earnedPoints);
  expect(b1.earnedPoints ?? 0).toBe(b0.earnedPoints ?? b0.mokshaPoints ?? 0);
  // Both histories and the audit record exist
  expect((await db.doc(`users/a/pointEvents/transfer_out:${r.transferId}`).get()).get("amount")).toBe(-40);
  expect((await db.doc(`users/b/pointEvents/transfer_in:${r.transferId}`).get()).get("note")).toBe("For your seva");
  expect((await db.doc(`pointTransfers/${r.transferId}`).get()).get("amount")).toBe(40);
});

test("limits: not to self, not more than you have, not over the per-gift and daily caps", async () => {
  await expect(transferPointsFor(db, "a", { toUid: "a", amount: 5 }, now)).rejects.toThrow(/yourself/);
  await expect(transferPointsFor(db, "a", { toUid: "b", amount: 101 }, now)).rejects.toThrow(/between 1 and 100/);
  await expect(transferPointsFor(db, "a", { toUid: "nobody", amount: 5 }, now)).rejects.toThrow(/not found/);
  await transferPointsFor(db, "a", { toUid: "b", amount: 100 }, now);
  await expect(transferPointsFor(db, "a", { toUid: "b", amount: 1 }, now)).rejects.toThrow(/up to 100 points a day/);
  // A new day resets the daily cap, but the balance is now too low for 100
  await expect(transferPointsFor(db, "a", { toUid: "b", amount: 100 }, new Date("2026-11-13T06:00:00Z"))).rejects.toThrow(/enough points/);
});

test("Level 1 members can receive but not send", async () => {
  await awardPoints(db, "b", { eventKey: "seed-b", amount: 20, reason: "gift", milestone: false });
  await db.doc("users/b").set({ userLevel: 1 }, { merge: true });
  await expect(transferPointsFor(db, "b", { toUid: "a", amount: 5 }, now)).rejects.toThrow(/Level 2/);
});

test("frozen accounts, blocked members and a global switch stop sending", async () => {
  await setTransfersFrozenFor(db, admin, "admin1", { uid: "a", frozen: true });
  await expect(transferPointsFor(db, "a", { toUid: "b", amount: 5 }, now)).rejects.toThrow(/paused/);
  await setTransfersFrozenFor(db, swamiji, "sw", { uid: "a", frozen: false });
  await db.doc("users/b/blocked/a").set({ at: 1 });
  await expect(transferPointsFor(db, "a", { toUid: "b", amount: 5 }, now)).rejects.toThrow(/can't send/);
  await db.doc("users/b/blocked/a").delete();
  await updatePointsConfigFor(db, admin, "admin1", { transfersEnabled: false });
  await expect(transferPointsFor(db, "a", { toUid: "b", amount: 5 }, now)).rejects.toThrow(/switched off/);
});

test("only Swamiji and admins adjust points; add, deduct and set", async () => {
  await expect(staffAdjustPointsFor(db, member, "a", { uid: "b", mode: "add", amount: 5, note: "x" })).rejects.toThrow(/Swamiji and admins/);
  await expect(staffAdjustPointsFor(db, admin, "admin1", { uid: "b", mode: "add", amount: 5 })).rejects.toThrow(/reason/);
  const b0 = (await user("b")).mokshaPoints ?? 0;
  expect((await staffAdjustPointsFor(db, swamiji, "sw", { uid: "b", mode: "add", amount: 50, note: "Seva" })).balance).toBe(b0 + 50);
  expect((await staffAdjustPointsFor(db, admin, "admin1", { uid: "b", mode: "deduct", amount: 20, note: "Mistake" })).balance).toBe(b0 + 30);
  expect((await staffAdjustPointsFor(db, admin, "admin1", { uid: "b", mode: "set", amount: 7, note: "Reset" })).balance).toBe(7);
  // Never below zero
  expect((await staffAdjustPointsFor(db, admin, "admin1", { uid: "b", mode: "deduct", amount: 999, note: "x" })).balance).toBe(0);
  expect((await db.collection("pointAdjustments").where("uid", "==", "b").get()).size).toBe(4);
});

test("adjusting earned points recomputes the Level", async () => {
  await db.doc("users/b").set({ activeDays: 100 }, { merge: true });
  const r = await staffAdjustPointsFor(db, admin, "admin1", { uid: "b", mode: "add", amount: 300, target: "earned", note: "Backfill" });
  expect(r.userLevel).toBe(3);
  expect((await user("b")).userLevel).toBe(3);
});

test("a transfer can be reversed once", async () => {
  const { transferId } = await transferPointsFor(db, "a", { toUid: "b", amount: 30 }, now);
  const a0 = (await user("a")).mokshaPoints;
  await expect(reverseTransferFor(db, member, "a", { transferId })).rejects.toThrow(/Swamiji and admins/);
  expect((await reverseTransferFor(db, admin, "admin1", { transferId })).returned).toBe(30);
  expect((await user("a")).mokshaPoints).toBe(a0 + 30);
  await expect(reverseTransferFor(db, admin, "admin1", { transferId })).rejects.toThrow(/Already/);
});

test("config/points edits are cleaned", async () => {
  await updatePointsConfigFor(db, swamiji, "sw", { practice: "5", like: -3, maxTransfer: 1e9, junk: "x" });
  const cfg = (await db.doc("config/points").get()).data() ?? {};
  expect(cfg.practice).toBe(5);
  expect(cfg.like).toBe(0);
  expect(cfg.maxTransfer).toBe(100000);
  expect(cfg.junk).toBeUndefined();
  await expect(updatePointsConfigFor(db, member, "a", { practice: 1 })).rejects.toThrow(/Swamiji and admins/);
});

test("gift recipients: by name prefix, never yourself or hidden profiles", async () => {
  await db.doc("publicProfiles/a").set({ displayName: "Asha", hidden: false, userLevel: 2 });
  await db.doc("publicProfiles/b").set({ displayName: "Bhavin", hidden: false, userLevel: 1 });
  await db.doc("publicProfiles/c").set({ displayName: "Bharat", hidden: true, userLevel: 3 });
  const { findGiftRecipientsFor } = await import("../src/transfer");
  expect((await findGiftRecipientsFor(db, "a", { prefix: "bh" })).members.map((m) => m.name)).toEqual(["Bhavin"]);
  expect((await findGiftRecipientsFor(db, "a", { prefix: "As" })).members).toEqual([]);
  expect((await findGiftRecipientsFor(db, "a", { prefix: "B" })).members).toEqual([]);
});
