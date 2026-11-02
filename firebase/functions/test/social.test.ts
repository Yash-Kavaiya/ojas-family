import { Timestamp } from "firebase-admin/firestore";
import { ensureUserDoc } from "../src/auth";
import { onAnswerWritten, onCommentWritten, onLikeChanged, onQuestionWritten } from "../src/social";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const NOW = new Date("2026-11-03T06:00:00Z"); // 11:30 IST
const YESTERDAY = new Date("2026-11-02T06:00:00Z");
const points = async (uid: string) => (await db.doc(`users/${uid}`).get()).get("mokshaPoints") ?? 0;

beforeEach(async () => {
  await clearDb(db);
  await ensureUserDoc(db, "u1");
  await ensureUserDoc(db, "author");
  await db.doc("thoughts/today").set({ title: "T", text: "x", publishAt: Timestamp.fromDate(NOW), likeCount: 0, commentCount: 0 });
  await db.doc("thoughts/yesterday").set({ title: "T", text: "x", publishAt: Timestamp.fromDate(YESTERDAY), likeCount: 0, commentCount: 0 });
});

test("like on today's thought: likeCount 1 and +1 point", async () => {
  await onLikeChanged(db, "thoughts/today", "u1", +1, NOW);
  expect((await db.doc("thoughts/today").get()).get("likeCount")).toBe(1);
  expect(await points("u1")).toBe(1);
});

test("relike earns nothing", async () => {
  await onLikeChanged(db, "thoughts/today", "u1", +1, NOW);
  await onLikeChanged(db, "thoughts/today", "u1", -1, NOW);
  await onLikeChanged(db, "thoughts/today", "u1", +1, NOW);
  expect(await points("u1")).toBe(1);
  expect((await db.doc("thoughts/today").get()).get("likeCount")).toBe(1);
});

test("like on yesterday's thought: no point", async () => {
  await onLikeChanged(db, "thoughts/yesterday", "u1", +1, NOW);
  expect(await points("u1")).toBe(0);
  expect((await db.doc("thoughts/yesterday").get()).get("likeCount")).toBe(1);
});

test("daily cap", async () => {
  await db.doc("config/levels").set({ dailyCaps: { like: 2, comment: 20, question: 5 } });
  for (const id of ["a", "b", "c"]) {
    await db.doc(`thoughts/${id}`).set({ title: "T", text: "x", publishAt: Timestamp.fromDate(NOW), likeCount: 0 });
    await onLikeChanged(db, `thoughts/${id}`, "u1", +1, NOW);
  }
  expect(await points("u1")).toBe(2);
});

test("comment increments commentCount and awards +1", async () => {
  await onCommentWritten(db, "thoughts/today", "c1", undefined, { authorUid: "author", status: "visible" }, NOW);
  expect((await db.doc("thoughts/today").get()).get("commentCount")).toBe(1);
  expect(await points("author")).toBe(1);
});

test("pending comment awards nothing until approved", async () => {
  await onCommentWritten(db, "thoughts/today", "c2", undefined, { authorUid: "author", status: "pending" }, NOW);
  expect(await points("author")).toBe(0);
  await onCommentWritten(db, "thoughts/today", "c2", { authorUid: "author", status: "pending" }, { authorUid: "author", status: "visible" }, NOW);
  expect(await points("author")).toBe(1);
  expect((await db.doc("thoughts/today").get()).get("commentCount")).toBe(1);
});

test("question awards +1 and answer awards +1 to answerer", async () => {
  await onQuestionWritten(db, "q1", undefined, { authorUid: "u1", status: "visible" }, NOW);
  await onAnswerWritten(db, "q1", "a1", undefined, { authorUid: "author", status: "visible" }, NOW);
  expect(await points("u1")).toBe(1);
  expect(await points("author")).toBe(1);
  expect((await db.doc("questions/q1").get()).get("answerCount")).toBe(1);
});
