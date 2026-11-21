# Read Translation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hindi Read questions and answers are translated to English and Gujarati on the server, and each member reads them in their app language.

**Architecture:** A Firestore trigger on `readQa/{id}` calls the NVIDIA chat API and stores `question_en/gu` and `answer_en/gu` with a hash of the Hindi. The app picks the field for its language and falls back to Hindi. Staff can correct translations in the existing editor.

**Tech Stack:** Firebase Functions v2 (TypeScript, Jest), Firestore rules, Kotlin, Jetpack Compose.

**Spec:** `docs/superpowers/specs/2026-11-21-read-translation-design.md`

## Global Constraints

- No Vertex AI, Gemini or paid Google AI. Use `NVIDIA_API_KEY` and model `nvidia/nemotron-3-ultra-550b-a55b`, thinking off.
- Limits: question 500 characters, answer 10,000, for every language. Truncate nothing silently.
- Never run `tools/gen_strings.py`. Edit the three `strings.xml` files by hand.
- The owner runs every `firebase deploy` and the backfill script.
- No git commits unless the owner asks.
- Server tests: from `firebase/`, `firebase emulators:exec --only firestore,storage --project demo-ojas "npm test --prefix rules-test && npm test --prefix functions -- --silent"`. Do not run during a Gradle build.
- App tests: Git Bash, `JAVA_HOME=/c/Users/yashk/.jdks/jdk-21.0.13+9`, Gradle 9.3.1 from `~/.gradle/wrapper/dists`, `testDebugUnitTest < /dev/null > file`.

## Review Focus

- A long answer cut off by the model's token limit: nothing is saved and the Hindi stays (Task 1 test).
- Hindi edited while a translation is running: the older result is discarded (Task 1 test).
- One language fails: neither language is written, so a question is never half translated (Task 1 test).
- Staff correct only the English: the trigger does not translate again (Task 1 test).
- Phone language outside Hindi, English and Gujarati: shows English, matching the app's default text (Task 3 test).

---

### Task 1: Server translation

**Files:**
- Create: `firebase/functions/src/readTranslate.ts`
- Create: `firebase/functions/test/readTranslate.test.ts`
- Modify: `firebase/functions/src/aiNvidia.ts`, `firebase/functions/src/index.ts` (after `rateAiAnswer`)

**Interfaces:**
- Produces: `type Translator = (text: string, lang: "en" | "gu") => Promise<string>`
- Produces: `readSourceHash(question: string, answer: string): string` (SHA-256 hex of `question + "\n\0" + answer`)
- Produces: `translateReadQa(db: Firestore, id: string, translate: Translator): Promise<"translated" | "skipped">`
- Produces: `nvidiaTranslator(): Translator` in `aiNvidia.ts`

- [ ] **Step 1: Write failing tests** in `readTranslate.test.ts` using `testDb`/`clearDb` and a fake translator returning `` `${lang}:${text}` ``:
  - `translates a new question`: all four fields and `translatedHash === readSourceHash(q, a)`.
  - `skips when the Hindi is unchanged`: second call returns `"skipped"`, translator called 4 times in total.
  - `keeps a staff correction`: change `answer_en` only, call again, value stays.
  - `translates again when the Hindi changes`.
  - `one failed language writes nothing`: Gujarati rejects, call rejects, no `_en`, `_gu` or hash fields.
  - `discards a result when the Hindi changed meanwhile`: the fake updates `answer` during translation, result is `"skipped"`, no stale fields written.
  - `skips a deleted document`.
- [ ] **Step 2: Run the server tests.** Expected: FAIL, module not found.
- [ ] **Step 3: Implement `translateReadQa`.** Translate all four texts first, then write in a transaction only if the current hash still equals the hash that was translated.
- [ ] **Step 4: Implement `nvidiaTranslator()`.** Non-streaming `ENDPOINT` call, `temperature: 0.1`, `max_tokens: 8000`, thinking off, 120-second timeout. System prompt: faithful translation of a spiritual teaching from Hindi to the target language, keep meaning, line breaks and names, keep Sanskrit terms and practice names transliterated, output only the translation. Throw on `finish_reason === "length"`, empty text, or output over the field limit.
- [ ] **Step 5: Export the trigger** in `index.ts`: `readQaWritten = onDocumentWritten({ document: "readQa/{id}", secrets: [nvidiaKey], timeoutSeconds: 300, memory: "256MiB" }, ...)`. Return early when `e.data?.after.exists` is false. Log failures and do not rethrow; the Hindi keeps showing and the backfill script can fill it in.
- [ ] **Step 6: Run the server tests.** Expected: PASS.

### Task 2: Security rules

**Files:**
- Modify: `firebase/firestore.rules:186-201`
- Test: `firebase/rules-test/rules.test.ts` (`describe("readQa")`)

**Interfaces:**
- Consumes: the field names from Task 1.

- [ ] **Step 1: Write failing tests:**
  - `staff can save translations` with all four fields.
  - `editing a translated question succeeds` (seed with `translatedHash`, update `answer_gu`).
  - `translation over the limit is refused` (`answer_en` of 10,001 characters; `question_gu` of 501).
  - `the app cannot set or change translatedHash` on create and update.
  - Members still cannot write translation fields.
- [ ] **Step 2: Run the server tests.** Expected: the new tests FAIL.
- [ ] **Step 3: Update `validReadQa`.** Allow the five new keys; optional text fields are strings within the limits. Create refuses `translatedHash`; update requires `get('translatedHash', '')` to be unchanged.
- [ ] **Step 4: Run the server tests.** Expected: PASS.

### Task 3: App shows the member's language

**Files:**
- Modify: `app/src/main/java/com/ojashealing/family/data/read/ReadQa.kt`
- Modify: `app/src/main/java/com/ojashealing/family/ui/content/ReadSection.kt`
- Test: `app/src/test/java/com/ojashealing/family/data/read/ReadQaTest.kt`

**Interfaces:**
- Produces: `ReadQa` gains `questionEn`, `answerEn`, `questionGu`, `answerGu: String = ""`.
- Produces: `fun ReadQa.questionIn(language: String): String` and `fun ReadQa.answerIn(language: String): String`

- [ ] **Step 1: Write failing tests:** `hi` returns Hindi; `en` and `gu` return their text; a blank translation returns Hindi; `fr` returns English; `fr` with no English returns Hindi.
- [ ] **Step 2: Run the app tests.** Expected: FAIL, unresolved `questionIn`.
- [ ] **Step 3: Implement** the fields in `observe()` and the two functions.
- [ ] **Step 4: Use them** in `ReadRow`, `ReadAnswerSheet` (title, body, read aloud) and the delete dialog, with the language from `LocalConfiguration.current.locales[0].language`.
- [ ] **Step 5: Run the app tests.** Expected: PASS.

### Task 4: Staff correct translations

**Files:**
- Modify: `ReadQa.kt`, `ReadSection.kt`, `app/src/main/res/values/strings.xml`, `values-hi/strings.xml`, `values-gu/strings.xml`
- Test: `ReadQaTest.kt`

**Interfaces:**
- Consumes: `ReadQa` fields from Task 3.
- Produces: `data class ReadTranslations(val questionEn: String, val answerEn: String, val questionGu: String, val answerGu: String)`
- Produces: `fun readUpdateFields(old: ReadQa, question: String, answer: String, t: ReadTranslations?): Map<String, Any?>`, where a null value means delete the field in the repository.
- Changes: `ReadQaRepository.update(id, question, answer, translations)` and `ReadUi.onSave`/`ReadQaViewModel.save` gain `translations: ReadTranslations?`.

- [ ] **Step 1: Write failing tests** for `readUpdateFields`: Hindi unchanged keeps edited translations; Hindi changed clears all four, so members see Hindi until the new translation arrives; trimmed values are stored.
- [ ] **Step 2: Run the app tests.** Expected: FAIL.
- [ ] **Step 3: Implement** `readUpdateFields`, the repository change and the view model change.
- [ ] **Step 4: Update `ReadEditor`.** Show English and Gujarati fields only when editing a question that has translations. Label the first two fields Hindi. Add strings `read_question_hi`, `read_answer_hi`, `read_question_en`, `read_answer_en`, `read_question_gu`, `read_answer_gu` and `read_translate_note` ("Changing the Hindi translates it again and replaces these.") in all three languages.
- [ ] **Step 5: Run the app tests and `assembleDebug`.** Expected: PASS.

### Task 5: Existing questions and release

**Files:**
- Create: `firebase/functions/scripts/translateReadQa.ts`

**Interfaces:**
- Consumes: `translateReadQa`, `nvidiaTranslator` from Task 1.

- [ ] **Step 1: Implement the script.** Go through `readQa` one document at a time, call `translateReadQa`, print translated, skipped and failed counts, and continue past a failure. Rerunning skips finished ones.
- [ ] **Step 2: Run all server tests and `npm run build --prefix functions`.** Expected: PASS.
- [ ] **Step 3: Give the owner the release order:** deploy `firestore:rules` and `functions:readQaWritten`, run the script with `NVIDIA_API_KEY` set, then ship the app. Rules go first so older apps can still edit translated questions.
