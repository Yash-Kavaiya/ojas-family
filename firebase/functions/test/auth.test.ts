import { ensureUserDoc, deleteUserData, setRoleFor } from "../src/auth";
import { clearDb, testDb } from "./emulator";

const db = testDb();
beforeEach(async () => clearDb(db));

test("onUserCreate is idempotent and never resets points", async () => {
  await ensureUserDoc(db, "u1");
  await db.doc("users/u1").update({ mokshaPoints: 42 });
  await ensureUserDoc(db, "u1");
  const u = (await db.doc("users/u1").get()).data()!;
  expect(u.mokshaPoints).toBe(42);
  expect(u).toMatchObject({ userLevel: 1, memberLevel: 0, role: "member", activeDays: 0 });
});

test("onUserCreate fills server fields on a client-created profile", async () => {
  await db.doc("users/u2").set({ displayName: "Asha", language: "gu" });
  await ensureUserDoc(db, "u2");
  expect((await db.doc("users/u2").get()).data()).toMatchObject({ displayName: "Asha", mokshaPoints: 0, userLevel: 1 });
});

test("deleteAccount removes user data", async () => {
  await ensureUserDoc(db, "u1");
  await db.doc("users/u1/pointEvents/daily:2026-10-29").set({ amount: 1 });
  await deleteUserData(db, "u1");
  expect((await db.doc("users/u1").get()).exists).toBe(false);
  expect((await db.collection("users/u1/pointEvents").get()).size).toBe(0);
});

test("non-admin setRole is permission-denied", async () => {
  await expect(setRoleFor(db, { role: "member" }, "u1", "admin")).rejects.toMatchObject({ code: "permission-denied" });
});

test("setRole rejects unknown roles", async () => {
  await expect(setRoleFor(db, { role: "admin" }, "u1", "superuser")).rejects.toMatchObject({ code: "invalid-argument" });
});
