// Shows which passages Ask Swamiji AI would answer from, using the live knowledge and the real search.
//   NVIDIA_API_KEY=<key> GOOGLE_CLOUD_QUOTA_PROJECT=ojas-family-app npx ts-node scripts/trySearch.ts "<lang>|<question>" ...
import { initializeApp } from "firebase-admin/app";
import { DEFAULT_AI } from "../src/ai";
import { nvidiaDeps } from "../src/aiNvidia";

const PROJECT = process.env.GCLOUD_PROJECT || "ojas-family-app";
initializeApp({ projectId: PROJECT, storageBucket: process.env.STORAGE_BUCKET || `${PROJECT}.firebasestorage.app` });

async function main() {
  const deps = nvidiaDeps();
  for (const arg of process.argv.slice(2)) {
    const [lang, question] = arg.split("|");
    const found = await deps.retrieve(DEFAULT_AI.knowledgePath, question, DEFAULT_AI.topK, lang);
    console.log(`\n[${lang}] ${question}`);
    if (!found.length) console.log("   (nothing: the AI says the teachings do not cover this)");
    found.forEach((p, i) => console.log(`   ${i + 1}. ${p.first ? "PLAYLIST" : "other   "} ${p.lang} ${p.videoId ?? "-"}@${p.startSec ?? 0}s  ${p.title.slice(0, 70)}`));
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
