import { ensureUserDoc } from "../src/auth";
import {
  type AiDeps, askSwamijiFor, buildPrompt, citedSources, rateAiAnswerFor, remainingToday, soundsLikeCrisis, splitFollowUps, systemPrompt,
} from "../src/ai";
import { KnowledgeSearch, type Passage, questionLanguage, searchWords, unit } from "../src/knowledgeSearch";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const now = new Date("2026-11-12T06:00:00Z");

const passages: Passage[] = [
  { id: "1", title: "Blue Light Meditation", text: "Blue Light Meditation\nSit straight and imagine blue healing light entering from the crown.", url: "https://youtu.be/a", videoId: "a", lang: "en" },
  { id: "2", title: "Protection Vidhi 1", text: "Protection Vidhi 1\nBuild a shield of divine energy around your body every morning.", url: "https://youtu.be/b&t=60s", videoId: "b", startSec: 60, lang: "en" },
  { id: "3", title: "Sleep", text: "Sleep\nBefore sleeping, thank the divine and relax each part of the body.", lang: "en" },
  { id: "4", title: "Blue Light Meditation", text: "Blue Light Meditation\nसीधे बैठें और नीली रोशनी की कल्पना करें।", url: "https://youtu.be/a", videoId: "a", lang: "hi" },
];
const search = new KnowledgeSearch(passages);

function fakeDeps(reply = "Imagine blue light from the crown [1]. Then build the shield [2].\nFOLLOWUPS: How long? | When? | Can children do it?"): AiDeps & { prompts: string[]; systems: string[] } {
  const prompts: string[] = [];
  const systems: string[] = [];
  return {
    prompts, systems,
    retrieve: async (_path, query, topK, lang) => search.search(query, topK, lang),
    generate: async (_m, system, _h, prompt, onText) => {
      systems.push(system);
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

test("keyword search finds the practice, in the member's language, or nothing", () => {
  expect(search.search("How do I do the blue light meditation?", 3, "en")[0].id).toBe("1");
  expect(search.search("blue light", 3, "hi").map((p) => p.id)).toEqual(["4"]);
  expect(search.search("protection shield", 3)[0].id).toBe("2");
  expect(search.search("cricket score", 3)).toEqual([]);
  expect(searchWords("How do I meditate?")).toEqual(["meditate"]);
  expect(questionLanguage("नीली रोशनी क्या है", "en")).toBe("hi");
  expect(questionLanguage("વાદળી પ્રકાશ", "en")).toBe("gu");
  expect(questionLanguage("blue light", "hi")).toBe("hi");
});

test("splits follow-ups off and keeps only cited sources", () => {
  const { answer, followUps } = splitFollowUps("Answer [2] text.\nFOLLOWUPS: a | b | c | d");
  expect(answer).toBe("Answer [2] text.");
  expect(followUps).toEqual(["a", "b", "c"]);
  expect(citedSources(answer, passages).map((s) => s.title)).toEqual(["Protection Vidhi 1"]);
  expect(citedSources("No citations", passages)).toEqual([]);
  expect(buildPrompt("Q?", passages.slice(0, 2), { text: "Om", labels: ["Candle"] })).toContain('It shows: Candle. Words in it: "Om"');
  expect(systemPrompt("gu")).toContain("Gujarati");
});

test("answers from the teachings, counts the question and pays the daily point once", async () => {
  const deps = fakeDeps();
  const streamed: string[] = [];
  const r = await askSwamijiFor(db, deps, "u1", undefined, { question: "How do I do the blue light meditation?", lang: "en" }, now, (p) => streamed.push(p));
  expect(r.kind).toBe("answer");
  expect(deps.prompts[0]).toContain("[1] Blue Light Meditation");
  expect(r.sources.map((s) => s.n)).toEqual([1]);
  expect(r.followUps).toHaveLength(3);
  expect(r).toMatchObject({ remaining: 4, pointsAwarded: 1 });
  expect(streamed.join("")).toContain("blue light");
  // A Hindi question in an English app is answered in Hindi; the day's point is paid only once
  const r2 = await askSwamijiFor(db, deps, "u1", undefined, { question: "नीली रोशनी ध्यान कैसे करें", lang: "en", history: [{ fromMe: true, text: "blue light" }] }, now);
  expect(deps.systems[1]).toContain("Hindi");
  expect(r2).toMatchObject({ remaining: 3, pointsAwarded: 0 });
});

test("no match and crisis words use no question and never reach the model", async () => {
  const deps = fakeDeps();
  expect(await askSwamijiFor(db, deps, "u1", undefined, { question: "cricket score today" }, now)).toMatchObject({ kind: "noMatch", remaining: -1 });
  expect(await askSwamijiFor(db, deps, "u1", undefined, { question: "I want to end my life" }, now)).toMatchObject({ kind: "crisis", remaining: -1 });
  expect(deps.prompts).toHaveLength(0);
  expect((await remainingToday(db, "u1", undefined, now)).remaining).toBe(5);
  expect(soundsLikeCrisis("मैं आत्महत्या के बारे में सोचता हूँ")).toBe(true);
});

test("a photo alone can be the question", async () => {
  const deps = fakeDeps();
  const r = await askSwamijiFor(db, deps, "u1", undefined, { question: "", photoText: "Blue Light Meditation", photoLabels: ["Poster"] }, now);
  expect(r.kind).toBe("answer");
  expect(deps.prompts[0]).toContain("The member shared a photo. It shows: Poster.");
  await expect(askSwamijiFor(db, deps, "u1", undefined, { question: " " }, now)).rejects.toThrow(/Type a question/);
});

test("five a day for everyone; a failed answer gives the question back; admins can change it or switch it off", async () => {
  const failing: AiDeps = { ...fakeDeps(), generate: async () => { throw new Error("model down"); } };
  await expect(askSwamijiFor(db, failing, "u1", undefined, { question: "blue light" }, now)).rejects.toThrow(/could not answer/);
  expect((await remainingToday(db, "u1", undefined, now)).remaining).toBe(5);
  for (let i = 0; i < 5; i++) await askSwamijiFor(db, fakeDeps(), "u1", "swamiji", { question: "blue light" }, now);
  await expect(askSwamijiFor(db, fakeDeps(), "u1", "swamiji", { question: "blue light" }, now)).rejects.toThrow(/today's questions/);
  expect((await askSwamijiFor(db, fakeDeps(), "u1", undefined, { question: "blue light" }, new Date("2026-11-13T06:00:00Z"))).remaining).toBe(4);
  await db.doc("config/ai").set({ enabled: false });
  await expect(askSwamijiFor(db, fakeDeps(), "u1", undefined, { question: "blue light" }, now)).rejects.toThrow(/resting/);
});

test("a rated answer is stored for admins; clearing the rating removes it", async () => {
  await rateAiAnswerFor(db, "u1", { messageId: "m-123", rating: -1, question: "Blue light?", answer: "Sit straight.", comment: "Too short" });
  expect((await db.doc("aiFeedback/u1_m-123").get()).data()).toMatchObject({ question: "Blue light?", answer: "Sit straight.", comment: "Too short", rating: -1 });
  await rateAiAnswerFor(db, "u1", { messageId: "m-123", rating: 0 });
  expect((await db.doc("aiFeedback/u1_m-123").get()).exists).toBe(false);
  await expect(rateAiAnswerFor(db, "u1", { messageId: "../x", rating: 1, answer: "a" })).rejects.toThrow(/required/);
  await expect(rateAiAnswerFor(db, "u1", { messageId: "m-456", rating: 1 })).rejects.toThrow(/answer is required/);
});

test("meaning search finds a teaching asked in other words, and nothing for an unrelated question", () => {
  // One made-up direction per topic: blue light, protection, sleep, blue light in Hindi
  const vectors = { dim: 3, data: new Float32Array([1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0]) };
  const near = unit([0.1, 0.95, 0.1]); // "shield myself from bad vibes": no shared word with passage 2
  expect(search.search("shield myself from bad vibes", 3)[0]?.id).toBe("2");
  expect(search.hybrid("keep bad vibes away", near, vectors, 3, "en").map((p) => p.id)).toEqual(["2"]);
  // Words match ("light") but the meaning is far from every passage: no answer
  const far = unit([-1, -1, -1]);
  expect(search.hybrid("price of a light bulb", far, vectors, 3, "en")).toEqual([]);
  // The member's language wins for the same video part
  expect(search.hybrid("blue light", unit([1, 0, 0]), vectors, 3, "hi").map((p) => p.id)).toEqual(["4"]);
  // Vectors that don't fit the passages fall back to keyword search
  expect(search.hybrid("blue light meditation", unit([1, 0, 0]), { dim: 3, data: new Float32Array(3) }, 3, "en")[0].id).toBe("1");
});
