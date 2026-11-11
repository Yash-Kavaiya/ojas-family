import { deleteAccountData } from "../src/auth";
import { ensureUserDoc } from "../src/auth";
import { clearDb, testDb } from "./emulator";

const db = testDb();
beforeEach(async () => clearDb(db));

test("a failing Storage cleanup does not stop data and auth deletion", async () => {
  await ensureUserDoc(db, "u1");
  const deletedAuth: string[] = [];
  await deleteAccountData(db, "u1", {
    deleteFiles: async () => { throw new Error("bucket missing"); },
    deleteAuthUser: async (uid: string) => { deletedAuth.push(uid); },
  });
  expect((await db.doc("users/u1").get()).exists).toBe(false);
  expect(deletedAuth).toEqual(["u1"]);
});

test("deleting an account removes the member's posts, conversations, bookings and files", async () => {
  await ensureUserDoc(db, "u1");
  await ensureUserDoc(db, "u2");
  await db.doc("questions/q1").set({ authorUid: "u1", status: "visible" });
  await db.doc("questions/q1/answers/a1").set({ authorUid: "u2", status: "visible" });
  await db.doc("questions/q2").set({ authorUid: "u2", status: "visible" });
  await db.doc("questions/q2/answers/a2").set({ authorUid: "u1", status: "visible" });
  await db.doc("thoughts/2026-11-01/comments/c1").set({ authorUid: "u1", status: "visible" });
  await db.doc("dailyPosts/d1").set({ authorUid: "u1", status: "visible" });
  await db.doc("dailyPosts/d2/replies/r1").set({ authorUid: "u1", status: "visible" });
  await db.doc("adminChats/u1/messages/m1").set({ senderUid: "u1", text: "hello" });
  await db.doc("chats/u1/messages/m1").set({ senderUid: "u1", text: "hello" });
  await db.doc("presence/u1").set({ lastActiveAt: new Date() });
  await db.doc("callRequests/r1").set({ memberUid: "u1", status: "pending" });
  await db.doc("calls/k1").set({ memberUid: "u1", staffUid: "u2", status: "ended" });
  await db.doc("calls/k1/candidates/x").set({ from: "u1" });
  await db.doc("calls/k2").set({ memberUid: "u2", staffUid: "s", status: "ended" });
  await db.doc("config/staff").set({ swamijiUid: "u1", supportUid: "x" });

  const prefixes: string[] = [];
  await deleteAccountData(db, "u1", {
    deleteFiles: async (prefix: string) => { prefixes.push(prefix); },
    deleteAuthUser: async () => {},
  });

  const gone = ["users/u1", "questions/q1", "questions/q1/answers/a1", "questions/q2/answers/a2", "thoughts/2026-11-01/comments/c1",
    "dailyPosts/d1", "dailyPosts/d2/replies/r1", "adminChats/u1/messages/m1", "chats/u1/messages/m1", "presence/u1",
    "callRequests/r1", "calls/k1", "calls/k1/candidates/x"];
  for (const path of gone) expect([path, (await db.doc(path).get()).exists]).toEqual([path, false]);
  for (const path of ["users/u2", "questions/q2", "calls/k2"]) expect([path, (await db.doc(path).get()).exists]).toEqual([path, true]);
  expect(prefixes).toEqual(["users/u1/", "chats/u1/", "adminChats/u1/"]);
  expect((await db.doc("config/staff").get()).data()).toEqual({ supportUid: "x" });
});
