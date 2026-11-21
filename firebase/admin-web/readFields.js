// What to save for a Read question. Kept apart from the page so it can be tested without a browser.
const QUESTION_MAX = 500;
const ANSWER_MAX = 10000;
export const READ_TRANSLATIONS = ["question_en", "answer_en", "question_gu", "answer_gu"];
export const readMax = (field) => (field.startsWith("question") ? QUESTION_MAX : ANSWER_MAX);

// Text boxes report line ends differently from what is stored
export const readClean = (v) => String(v ?? "").replace(/\r\n?/g, "\n").trim();

/** A message when the form can't be saved, else null. Same limits as firestore.rules. */
export function readFormError(form) {
  if (!readClean(form.question)) return "Write the question in Hindi";
  if (!readClean(form.answer)) return "Write the answer in Hindi";
  for (const field of ["question", "answer", ...READ_TRANSLATIONS]) {
    if (readClean(form[field]).length > readMax(field)) {
      return `${field.startsWith("question") ? "A question" : "An answer"} can be at most ${readMax(field).toLocaleString("en-US")} characters`;
    }
  }
  return null;
}

/**
 * Only what really changed since the page loaded, so a save never overwrites a newer translation.
 * Changed Hindi removes the translations ([remove] is deleteField()): the server makes new ones.
 */
export function readEditFields(opened, form, remove) {
  const out = {};
  for (const field of ["question", "answer"]) {
    if (readClean(form[field]) !== readClean(opened[field])) out[field] = readClean(form[field]);
  }
  if (Object.keys(out).length) {
    for (const field of READ_TRANSLATIONS) out[field] = remove;
    return out;
  }
  for (const field of READ_TRANSLATIONS) {
    if (readClean(form[field]) !== readClean(opened[field])) out[field] = readClean(form[field]);
  }
  return out;
}
