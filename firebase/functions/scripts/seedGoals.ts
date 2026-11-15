// Writes goals/ from scripts/goals.json: the photo activities and the daily sadhana goals shown in
// Practice > Freedom Practice, each with its level (easy, medium, hard). Goals already there are
// updated to match the file; a goal's "active" switch is left as it is.
// Run with Application Default Credentials:
//   gcloud auth application-default login && npx ts-node scripts/seedGoals.ts
import { readFileSync } from "fs";
import { resolve } from "path";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

interface Seed { id: string; [field: string]: unknown }

initializeApp({ projectId: "ojas-family-app" });
const db = getFirestore();
const seeds = JSON.parse(readFileSync(resolve(__dirname, "goals.json"), "utf8")) as Seed[];

(async () => {
  for (const { id, ...fields } of seeds) await db.doc(`goals/${id}`).set(fields, { merge: true });
  console.log(`goals: ${seeds.length} written`);
})();
