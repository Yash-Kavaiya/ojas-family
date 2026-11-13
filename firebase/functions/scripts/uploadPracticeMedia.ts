// Uploads the four guided practice videos/audios and points config/practiceMedia at them.
// Run with Application Default Credentials (an admin of the project):
//   gcloud auth application-default login
//   npx ts-node scripts/uploadPracticeMedia.ts <folder with blue_light.mp4, quick_protection.mp4, morning.mp4, night.mp4>
// A file may also be .m4a or .mp3 for an audio practice. Durations are read with ffprobe.
// A practice can stream a YouTube video instead of a file (no upload; the file in the folder is ignored):
//   ... --youtube quick_protection=KPRvRrJgmVk:<length in seconds>
// Quick Protection is Protection Vidhi 1 from YouTube (owner decision 2026-11-12):
//   npx ts-node scripts/uploadPracticeMedia.ts ../../videos/streaming --youtube quick_protection=KPRvRrJgmVk:<seconds>
// Running it again replaces a practice's media; members' points for today are not affected.
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { PRACTICE_SECTIONS } from "../src/practiceMedia";

const BUCKET = "ojas-family-app.firebasestorage.app";
initializeApp({ projectId: "ojas-family-app", storageBucket: BUCKET });

/** Titles shown under each practice's name; edit them in config/practiceMedia later if needed. */
const TITLES: Record<string, string> = {
  blue_light: "Blue Light Meditation",
  quick_protection: "Protection Vidhi 1 · Spiritual Protection Shield",
  morning: "Higher Self (Hindi, with music)",
  night: "Night Meditation",
};

const TYPES: Record<string, { kind: "video" | "audio"; mime: string }> = {
  ".mp4": { kind: "video", mime: "video/mp4" },
  ".m4a": { kind: "audio", mime: "audio/mp4" },
  ".mp3": { kind: "audio", mime: "audio/mpeg" },
};

function seconds(file: string): number {
  const out = execFileSync("ffprobe", ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file]).toString();
  return Math.round(Number(out.trim()));
}

async function main() {
  const dir = process.argv[2];
  if (!dir) throw new Error("Pass the folder that holds the practice files");
  const bucket = getStorage().bucket();
  const config: Record<string, unknown> = {};

  // --youtube section=videoId:seconds (may be repeated)
  const youtube = new Map<string, { videoId: string; seconds: number }>();
  process.argv.forEach((a, i) => {
    if (a !== "--youtube") return;
    const m = /^([a-z_]+)=([A-Za-z0-9_-]{6,20}):(\d+)$/.exec(process.argv[i + 1] ?? "");
    if (!m || !(PRACTICE_SECTIONS as readonly string[]).includes(m[1])) throw new Error("Use --youtube <section>=<videoId>:<seconds>");
    youtube.set(m[1], { videoId: m[2], seconds: Number(m[3]) });
  });
  for (const [section, y] of youtube) {
    config[section] = { kind: "youtube", videoId: y.videoId, durationSec: y.seconds, title: TITLES[section], url: FieldValue.delete() };
    console.log(`- ${section}: YouTube ${y.videoId} (${y.seconds}s)`);
  }

  for (const section of PRACTICE_SECTIONS) {
    if (youtube.has(section)) continue;
    const ext = Object.keys(TYPES).find((e) => existsSync(join(dir, section + e)));
    if (!ext) { console.log(`- ${section}: no file, skipped`); continue; }
    const file = join(dir, section + ext);
    const { kind, mime } = TYPES[ext];
    const dest = `practice/${section}${ext}`;
    const token = randomUUID();
    console.log(`- ${section}: uploading ${file} …`);
    await bucket.upload(file, {
      destination: dest,
      resumable: true,
      metadata: { contentType: mime, cacheControl: "public, max-age=86400", metadata: { firebaseStorageDownloadTokens: token } },
    });
    const url = `https://firebasestorage.googleapis.com/v0/b/${BUCKET}/o/${encodeURIComponent(dest)}?alt=media&token=${token}`;
    config[section] = { url, kind, durationSec: seconds(file), title: TITLES[section] };
    console.log(`  done (${(config[section] as { durationSec: number }).durationSec}s)`);
  }

  // mergeFields-style update: only the practices named here change
  await getFirestore().doc("config/practiceMedia").set(config, { merge: true });
  console.log("config/practiceMedia written");
}

main().catch((e) => { console.error(e); process.exit(1); });
