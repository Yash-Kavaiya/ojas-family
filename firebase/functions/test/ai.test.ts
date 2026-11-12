import { ensureUserDoc } from "../src/auth";
import {
  type AiDeps, type Passage, askSwamijiFor, buildPrompt, citedSources, deleteAiChatFor, rateAiAnswerFor, remainingToday, splitFollowUps, systemPrompt,
} from "../src/ai";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const now = new Date("2026-11-12T06:00:00Z");

const passages: Passage[] = [
  { id: "1", text: "Sit straight and imagine blue light entering from the crown.", title: "Blue Light Meditation", url: "https://youtu.be/a", videoId: "a", sourceType: "video", distance: 0.2 },
  { id: "2", text: "Protection Vidhi 1 builds a shield of divine energy.", title: "Protection Vidhi 1", url: "https://youtu.be/b&t=60s", videoId: "b", startSec: 60, sourceType: "video", distance: 0.3 },
  { id: "3", text: "Unrelated passage", title: "Far away", url: "https://youtu.be/c", sourceType: "video", distance: 0.9 },
];

function fakeDeps(reply = "Imagine blue light from the crown [1]. Then build the shield [2].\nFOLLOWUPS: How long? | When? | Can children do it?"): AiDeps & { prompts: string[] } {
  const prompts: string[] = [];
  return {
    prompts,
    embedQuery: async () => [0.1, 0.2],
    retrieve: async () => passages,
    generate: async (_m, _s, _h, prompt, onText) => {
      prompts.push(prompt);
      onText(reply.split("FOLLOWUPS:")[0]);
      return reply;
    },
  };
}

beforeEach(async () => {
  await clearDb(db);
  await ensureUserDoc(db, "u1");
});

test("splits follow-ups off and keeps only cited sources", () => {
  const { answer, followUps } = splitFollowUps("Answer [2] text.\nFOLLOWUPS: a | b | c | d");
  expect(answer).toBe("Answer [2] text.");
  expect(followUps).toEqual(["a", "b", "c"]);
  expect(citedSources(answer, passages).map((s) => s.title)).toEqual(["Protection Vidhi 1"]);
  expect(citedSources("No citations", passages)).toEqual([]);
  expect(splitFollowUps("Just text").followUps).toEqual([]);
});

test("the prompt carries numbered passages and the language", () => {
  expect(buildPrompt("Q?", passages.slice(0, 2))).toContain("[2] Protection Vidhi 1");
  expect(buildPrompt("Q?", [])).toContain("No passages");
  expect(systemPrompt("hi")).toContain("Hindi");
  expect(systemPrompt("gu")).toContain("Gujarati");
});

test("answers from close passages only, saves the chat and pays the daily point once", async () => {
  const deps = fakeDeps();
  const streamed: string[] = [];
  const r = await askSwamijiFor(db, deps, "u1", undefined, { question: "How do I do blue light?", lang: "en" }, now, (p) => streamed.push(p));
  expect(deps.prompts[0]).toContain("[1] Blue Light Meditation");
  expect(deps.prompts[0]).not.toContain("Far away");
  expect(r.sources.map((s) => s.n)).toEqual([1, 2]);
  expect(r.sources[1].startSec).toBe(60);
  expect(r.followUps).toHaveLength(3);
  expect(r.pointsAwarded).toBe(1);
  expect(streamed.join("")).toContain("blue light");
  const msgs = await db.collection(`users/u1/aiChats/${r.chatId}/messages`).orderBy("createdAt").get();
  expect(msgs.docs.map((d) => d.get("role"))).toEqual(["user", "model"]);
  // The second question continues the same chat, sends its history and earns no new point
  const r2 = await askSwamijiFor(db, deps, "u1", undefined, { question: "And after?", chatId: r.chatId, lang: "en" }, now);
  expect(r2.chatId).toBe(r.chatId);
  expect(r2.pointsAwarded).toBe(0);
  expect((await db.doc(`users/u1/aiChats/${r.chatId}`).get()).get("title")).toBe("How do I do blue light?");
});

test("free members get the daily allowance; a failed answer gives the question back", async () => {
  await db.doc("config/ai").set({ dailyFree: 2 });
  const failing: AiDeps = { ...fakeDeps(), generate: async () => { throw new Error("model down"); } };
  await expect(askSwamijiFor(db, failing, "u1", undefined, { question: "Hello there" }, now)).rejects.toThrow(/could not answer/);
  expect((await remainingToday(db, "u1", undefined, now)).remaining).toBe(2);
  await askSwamijiFor(db, fakeDeps(), "u1", undefined, { question: "One" }, now);
  await askSwamijiFor(db, fakeDeps(), "u1", undefined, { question: "Two" }, now);
  await expect(askSwamijiFor(db, fakeDeps(), "u1", undefined, { question: "Three" }, now)).rejects.toThrow(/today's questions/);
  // Members get the larger allowance
  await db.doc("users/u1").set({ memberLevel: 1 }, { merge: true });
  expect((await remainingToday(db, "u1", undefined, now)).remaining).toBeGreaterThan(0);
});

test("switched off, or an empty question, is refused", async () => {
  await expect(askSwamijiFor(db, fakeDeps(), "u1", undefined, { question: " " }, now)).rejects.toThrow(/Type a question/);
  await db.doc("config/ai").set({ enabled: false });
  await expect(askSwamijiFor(db, fakeDeps(), "u1", undefined, { question: "Hello" }, now)).rejects.toThrow(/resting/);
});

test("rating an answer stores feedback for admins; deleting a chat removes it", async () => {
  const r = await askSwamijiFor(db, fakeDeps(), "u1", undefined, { question: "How do I do blue light?" }, now);
  await rateAiAnswerFor(db, "u1", { chatId: r.chatId, messageId: r.messageId, rating: -1, comment: "Too long" });
  const fb = (await db.doc(`aiFeedback/u1_${r.messageId}`).get()).data();
  expect(fb?.question).toBe("How do I do blue light?");
  expect(fb?.comment).toBe("Too long");
  await deleteAiChatFor(db, "u1", { chatId: r.chatId });
  expect((await db.collection(`users/u1/aiChats/${r.chatId}/messages`).get()).size).toBe(0);
});
