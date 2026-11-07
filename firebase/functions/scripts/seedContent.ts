// Fills content/ with the Ojas Healing videos chosen per section (scripts/content.json, built from
// youtube.com/@OjasHealing). Only adds videos that are missing, so admin edits are never undone.
// Run with Application Default Credentials:
//   gcloud auth application-default login && npx ts-node scripts/seedContent.ts
import { readFileSync } from "fs";
import { resolve } from "path";
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

interface Seed { videoId: string; section: string; title: string; order: number }

initializeApp({ projectId: "ojas-family-app" });
const db = getFirestore();
const seeds = JSON.parse(readFileSync(resolve(__dirname, "content.json"), "utf8")) as Seed[];

(async () => {
  let created = 0;
  let skipped = 0;
  for (const s of seeds) {
    try {
      await db.doc(`content/yt_${s.videoId}`).create({
        section: s.section,
        videoId: s.videoId,
        title: s.title.slice(0, 200),
        minUserLevel: 1,
        minMemberLevel: 0,
        order: s.order,
        published: true,
        createdAt: FieldValue.serverTimestamp(),
      });
      created++;
    } catch (e) {
      if ((e as { code?: number }).code !== 6) throw e; // 6 = ALREADY_EXISTS
      skipped++;
    }
  }
  console.log(`content: ${created} added, ${skipped} already there`);
})();
