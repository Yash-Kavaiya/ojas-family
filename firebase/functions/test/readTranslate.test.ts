import { FieldValue } from "firebase-admin/firestore";
import { nvidiaTranslator } from "../src/aiNvidia";
import { readSourceHash, splitForTranslation, translateReadQa, type Translator } from "../src/readTranslate";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const ref = db.doc("readQa/q1");
const Q = "सुरक्षा क्या है?";
const A = "प्रकाश का कवच।";

let calls = 0;
const fake: Translator = async (text, lang) => {
  calls++;
  return `${lang}:${text}`;
};

beforeEach(async () => {
  await clearDb(db);
  calls = 0;
  await ref.set({ question: Q, answer: A });
});

const data = async () => (await ref.get()).data()!;

test("translates a new question", async () => {
  expect(await translateReadQa(db, "q1", fake)).toBe("translated");
  expect(await data()).toMatchObject({
    question_en: `en:${Q}`, answer_en: `en:${A}`,
    question_gu: `gu:${Q}`, answer_gu: `gu:${A}`,
    translatedHash: readSourceHash(Q, A),
  });
});

test("skips when the Hindi is unchanged", async () => {
  await translateReadQa(db, "q1", fake);
  expect(await translateReadQa(db, "q1", fake)).toBe("skipped");
  expect(calls).toBe(4);
});

test("keeps a staff correction", async () => {
  await translateReadQa(db, "q1", fake);
  await ref.update({ answer_en: "A shield of light." });
  await translateReadQa(db, "q1", fake);
  expect((await data()).answer_en).toBe("A shield of light.");
});

test("translates again when the Hindi changes", async () => {
  await translateReadQa(db, "q1", fake);
  await ref.update({ answer: "नया उत्तर" });
  expect(await translateReadQa(db, "q1", fake)).toBe("translated");
  expect((await data()).answer_gu).toBe("gu:नया उत्तर");
});

test("one failed language writes nothing", async () => {
  const failing: Translator = async (text, lang) => {
    if (lang === "gu") throw new Error("busy");
    return `en:${text}`;
  };
  await expect(translateReadQa(db, "q1", failing)).rejects.toThrow("busy");
  const d = await data();
  for (const k of ["question_en", "answer_en", "question_gu", "answer_gu", "translatedHash"]) expect(d[k]).toBeUndefined();
});

test("discards a result when the Hindi changed meanwhile", async () => {
  let edited = false;
  const editing: Translator = async (text, lang) => {
    if (!edited) {
      edited = true;
      await ref.update({ answer: "बदला हुआ उत्तर" });
    }
    return `${lang}:${text}`;
  };
  expect(await translateReadQa(db, "q1", editing)).toBe("skipped");
  const d = await data();
  expect(d.answer_en).toBeUndefined();
  expect(d.translatedHash).toBeUndefined();
});

test("translates again when Hindi is restored after the app cleared the translations", async () => {
  await translateReadQa(db, "q1", fake);
  await ref.update({
    question_en: FieldValue.delete(), answer_en: FieldValue.delete(),
    question_gu: FieldValue.delete(), answer_gu: FieldValue.delete(),
  });
  expect(await translateReadQa(db, "q1", fake)).toBe("translated");
  expect((await data()).answer_gu).toBe(`gu:${A}`);
});

test("translates again when every translation is blank", async () => {
  await translateReadQa(db, "q1", fake);
  await ref.update({ question_en: "", answer_en: "", question_gu: "", answer_gu: "" });
  expect(await translateReadQa(db, "q1", fake)).toBe("translated");
  expect((await data()).answer_en).toBe(`en:${A}`);
});

test("a failed new translation removes the outdated one so members see the Hindi", async () => {
  await translateReadQa(db, "q1", fake);
  await ref.update({ answer: "नया उत्तर" });
  await expect(translateReadQa(db, "q1", async () => { throw new Error("busy"); })).rejects.toThrow("busy");
  const d = await data();
  for (const k of ["question_en", "answer_en", "question_gu", "answer_gu", "translatedHash"]) expect(d[k]).toBeUndefined();
  expect(d.answer).toBe("नया उत्तर");
});

test("a long answer is translated in pieces, in order, keeping line breaks", async () => {
  const one = "अ".repeat(1500) + "।";
  const two = "ब".repeat(1500) + "।";
  await ref.update({ answer: `${one}\n\n${two}` });
  await translateReadQa(db, "q1", fake);
  expect((await data()).answer_en).toBe(`en:${one}\n\nen:${two}`);
});

test("pieces stay within the size and lose no text", () => {
  const text = Array.from({ length: 40 }, (_, i) => `वाक्य ${i} `.repeat(30) + "।").join("\n");
  const parts = splitForTranslation(text, 2000);
  expect(parts.length).toBeGreaterThan(1);
  for (const p of parts) expect(p.text.length).toBeLessThanOrEqual(2000);
  expect(parts.map((p) => p.text + p.sep).join("")).toBe(text);
});

describe("nvidiaTranslator", () => {
  const realFetch = global.fetch;
  const reply = (finish_reason: string) => new Response(JSON.stringify({ choices: [{ finish_reason, message: { content: "partial" } }] }));
  beforeEach(() => { process.env.NVIDIA_API_KEY = "test"; });
  afterEach(() => { global.fetch = realFetch; delete process.env.NVIDIA_API_KEY; });

  test("a reply cut off by length is refused without trying again", async () => {
    const f = jest.fn(async () => reply("length"));
    global.fetch = f as unknown as typeof fetch;
    await expect(nvidiaTranslator()("पाठ", "en")).rejects.toThrow("did not finish");
    expect(f).toHaveBeenCalledTimes(1);
  });

  test("a reply stopped for any other reason is refused", async () => {
    global.fetch = jest.fn(async () => reply("content_filter")) as unknown as typeof fetch;
    await expect(nvidiaTranslator()("पाठ", "gu")).rejects.toThrow("did not finish");
  });
});

test("skips a deleted document", async () => {
  await ref.delete();
  expect(await translateReadQa(db, "q1", fake)).toBe("skipped");
  expect(calls).toBe(0);
});
