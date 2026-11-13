import { ensureUserDoc } from "../src/auth";
import { rateAiAnswerFor, remainingToday, startAiQuestionFor } from "../src/ai";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const now = new Date("2026-11-12T06:00:00Z");

beforeEach(async () => {
  await clearDb(db);
  await ensureUserDoc(db, "u1");
});

test("status tells the phone where the model and passages are", async () => {
  const s = await remainingToday(db, "u1", undefined, now);
  expect(s).toMatchObject({ enabled: true, remaining: 5, limit: 5, knowledgePath: "ai/knowledge.json", topK: 3 });
  expect(s.modelPath).toMatch(/gemma3-270m.*\.task$/);
  expect(s.modelBytes).toBeGreaterThan(100_000_000);
});

test("five questions a day for everyone; the first one pays the daily point", async () => {
  const first = await startAiQuestionFor(db, "u1", undefined, now);
  expect(first).toEqual({ remaining: 4, limit: 5, pointsAwarded: 1 });
  for (let i = 0; i < 4; i++) expect((await startAiQuestionFor(db, "u1", undefined, now)).pointsAwarded).toBe(0);
  await expect(startAiQuestionFor(db, "u1", undefined, now)).rejects.toThrow(/today's questions/);
  // Staff and paid members have the same five by default
  await ensureUserDoc(db, "s1");
  for (let i = 0; i < 5; i++) await startAiQuestionFor(db, "s1", "swamiji", now);
  await expect(startAiQuestionFor(db, "s1", "swamiji", now)).rejects.toThrow(/today's questions/);
  // A new day starts fresh
  expect((await startAiQuestionFor(db, "u1", undefined, new Date("2026-11-13T06:00:00Z"))).remaining).toBe(4);
});

test("admins can change the allowance or switch it off", async () => {
  await db.doc("config/ai").set({ dailyFree: 1, dailyMember: 3 });
  await startAiQuestionFor(db, "u1", undefined, now);
  await expect(startAiQuestionFor(db, "u1", undefined, now)).rejects.toThrow(/today's questions/);
  await db.doc("users/u1").set({ memberLevel: 1 }, { merge: true });
  expect((await remainingToday(db, "u1", undefined, now)).remaining).toBe(2);
  await db.doc("config/ai").set({ enabled: false });
  await expect(startAiQuestionFor(db, "u1", undefined, now)).rejects.toThrow(/resting/);
});

test("a rated answer is stored for admins; clearing the rating removes it", async () => {
  await rateAiAnswerFor(db, "u1", { messageId: "m-123", rating: -1, question: "Blue light?", answer: "Sit straight.", comment: "Too short" });
  const fb = (await db.doc("aiFeedback/u1_m-123").get()).data();
  expect(fb).toMatchObject({ question: "Blue light?", answer: "Sit straight.", comment: "Too short", rating: -1, onDevice: true });
  await rateAiAnswerFor(db, "u1", { messageId: "m-123", rating: 0 });
  expect((await db.doc("aiFeedback/u1_m-123").get()).exists).toBe(false);
  await expect(rateAiAnswerFor(db, "u1", { messageId: "../x", rating: 1, answer: "a" })).rejects.toThrow(/required/);
  await expect(rateAiAnswerFor(db, "u1", { messageId: "m-456", rating: 1 })).rejects.toThrow(/answer is required/);
});
