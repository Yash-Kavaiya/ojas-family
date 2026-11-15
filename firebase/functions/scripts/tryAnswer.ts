// Writes a whole answer the way Ask Swamiji AI does (live knowledge, real search, real model), without
// touching the database: no question is counted and no point is given.
//   NVIDIA_API_KEY=<key> GOOGLE_CLOUD_QUOTA_PROJECT=ojas-family-app npx ts-node scripts/tryAnswer.ts "<lang>|<question>" ...
import { initializeApp } from "firebase-admin/app";
import { buildPrompt, citedSources, DEFAULT_AI, splitFollowUps, systemPrompt } from "../src/ai";
import { nvidiaDeps } from "../src/aiNvidia";

const PROJECT = process.env.GCLOUD_PROJECT || "ojas-family-app";
initializeApp({ projectId: PROJECT, storageBucket: process.env.STORAGE_BUCKET || `${PROJECT}.firebasestorage.app` });

async function main() {
  const deps = nvidiaDeps();
  for (const arg of process.argv.slice(2)) {
    const [lang, question] = arg.split("|");
    const passages = await deps.retrieve(DEFAULT_AI.knowledgePath, question, DEFAULT_AI.topK, lang);
    console.log(`\n=== [${lang}] ${question}`);
    if (!passages.length) { console.log("(no passage: the app says the teachings do not cover this)"); continue; }
    const started = Date.now();
    const raw = await deps.generate(DEFAULT_AI.model, systemPrompt(lang), [], buildPrompt(question, passages), () => {});
    const { answer, followUps } = splitFollowUps(raw);
    console.log(answer);
    console.log(`--- ${Math.round((Date.now() - started) / 1000)}s; sources:`);
    for (const s of citedSources(answer, passages)) {
      const p = passages[s.n - 1];
      console.log(`  [${s.n}] ${p.first ? "PLAYLIST" : "other"} ${s.videoId ?? "-"}@${s.startSec ?? 0}s ${s.title.slice(0, 70)}`);
    }
    console.log(`--- follow-ups: ${followUps.join(" | ")}`);
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
