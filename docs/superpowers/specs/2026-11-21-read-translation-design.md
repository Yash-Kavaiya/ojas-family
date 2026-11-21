# Read section: Hindi in, three languages out

## Intent
Admins and Swamiji write each Practice > Read question and answer once, in Hindi. Members read it in
their app language (Hindi, English or Gujarati) without anyone translating by hand.

## Goals
- Saving Hindi text produces English and Gujarati automatically.
- Each member sees their language; Hindi members see the original.
- Admins and Swamiji can correct the English or Gujarati in the edit screen.
- Existing questions are translated once.

## Non-goals and anti-goals
- No approval step before translations appear (the owner chose publish first, fix later).
- No Vertex AI, Gemini or other paid Google AI. Use the existing NVIDIA API and `NVIDIA_API_KEY`.
- A failed translation must never hide a question or lose the Hindi.

## Approaches
1. **Publish automatically, fix later (picked by the owner).** Translations show as soon as they are ready.
2. Approve first. Safer, but adds a step in two languages for every upload.

## Design (builder's calls unless noted above)
- **Data:** `readQa` documents gain `question_en`, `answer_en`, `question_gu`, `answer_gu`, following the
  `field_language` pattern in `About.kt`, plus `translatedHash`, a hash of the Hindi the translations came from.
- **Server:** a Firestore trigger on `readQa/{id}` translates when the Hindi's hash differs from `translatedHash`.
  Writing the result does not trigger another translation, because the hash then matches.
- **Editing Hindi** translates again and replaces earlier English and Gujarati, including corrections.
  Editing only English or Gujarati leaves the hash alone, so corrections stay.
- **App:** shows `field_language` when filled, else the Hindi. The list updates live when translations arrive.
- **Editor:** admins and Swamiji see Hindi, English and Gujarati fields once translations exist.
- **Rules:** allow the new text fields with the same length limits. Only the server may change `translatedHash`.
- **Existing questions:** a one-time script run by the owner fills in missing translations.
- **Deploy:** the owner runs the Firebase deploy. Rules and functions go out before the new app.
