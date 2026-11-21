// Run from firebase/: node --test admin-web-test/readFields.test.mjs
import assert from "node:assert/strict";
import test from "node:test";
import { readEditFields, readFormError } from "../admin-web/readFields.js";

const REMOVE = Symbol("remove");
const opened = {
  question: "प्रश्न", answer: "उत्तर",
  question_en: "Question", answer_en: "Answer", question_gu: "પ્રશ્ન", answer_gu: "જવાબ",
};

test("nothing changed writes nothing", () => {
  assert.deepEqual(readEditFields(opened, { ...opened }, REMOVE), {});
});

test("a corrected translation writes only that field", () => {
  assert.deepEqual(readEditFields(opened, { ...opened, answer_gu: " સુધારેલું " }, REMOVE), { answer_gu: "સુધારેલું" });
});

test("empty fields opened before translation arrived are not written", () => {
  const before = { question: "प्रश्न", answer: "उत्तर" };
  const form = { ...before, question_en: "", answer_en: "", question_gu: "", answer_gu: "" };
  assert.deepEqual(readEditFields(before, form, REMOVE), {});
});

test("changed Hindi clears all translations, even edited ones", () => {
  assert.deepEqual(readEditFields(opened, { ...opened, answer: "नया", answer_en: "Edited" }, REMOVE), {
    answer: "नया", question_en: REMOVE, answer_en: REMOVE, question_gu: REMOVE, answer_gu: REMOVE,
  });
});

test("line endings from a text box are not a change", () => {
  const multi = { ...opened, answer: "एक\nदो" };
  assert.deepEqual(readEditFields(multi, { ...multi, answer: "एक\r\nदो" }, REMOVE), {});
});

test("form limits match the server rules", () => {
  assert.equal(readFormError({ question: "प्र", answer: "उ" }), null);
  assert.match(readFormError({ question: " ", answer: "उ" }), /question/i);
  assert.match(readFormError({ question: "प्र", answer: "" }), /answer/i);
  assert.match(readFormError({ question: "x".repeat(501), answer: "उ" }), /500/);
  assert.match(readFormError({ question: "प्र", answer: "उ", answer_gu: "x".repeat(10001) }), /10,000/);
});
