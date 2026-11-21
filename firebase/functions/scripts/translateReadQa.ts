/**
 * One-time: translates Read questions that have no translation yet. Safe to run again.
 * From firebase/functions, with Google credentials and NVIDIA_API_KEY set:
 *   npx ts-node scripts/translateReadQa.ts
 */
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { nvidiaTranslator } from "../src/aiNvidia";
import { translateReadQa } from "../src/readTranslate";

async function main() {
  initializeApp({ projectId: "ojas-family-app" });
  const db = getFirestore();
  const translate = nvidiaTranslator();
  const counts = { translated: 0, skipped: 0, failed: 0 };
  for (const ref of await db.collection("readQa").listDocuments()) {
    try {
      counts[await translateReadQa(db, ref.id, translate)]++;
    } catch (e) {
      counts.failed++;
      console.error(`failed: ${ref.id}`, (e as Error).message);
    }
  }
  console.log(counts);
  if (counts.failed) process.exitCode = 1;
}

void main();
