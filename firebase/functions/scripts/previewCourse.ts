// Shows what the course book would add to Ask Swamiji AI's knowledge, without writing anything anywhere.
//   npx ts-node scripts/previewCourse.ts <course folder>
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { chapterPassages, SPEAKER_EN, SPEAKER_HI } from "./courseBook";

const dir = process.argv[2];
if (!dir) throw new Error("Give the course folder");
const firstVideos: string[] = JSON.parse(readFileSync(join(__dirname, "..", "knowledge", "first-playlist.json"), "utf8")).videoIds;

for (const [folder, speaker] of [["book_en", SPEAKER_EN], ["book", SPEAKER_HI]] as const) {
  const chapters = join(dir, folder, "chapters");
  const all = readdirSync(chapters).filter((n) => n.endsWith(".tex")).sort()
    .flatMap((f) => chapterPassages(readFileSync(join(chapters, f), "utf8"), speaker));
  const videos = new Set(all.map((p) => p.videoId));
  const leftover = all.filter((p) => /\\[a-zA-Z]|[{}]/.test(p.text)).length;
  console.log(`${folder}: ${all.length} sections from ${videos.size} videos, ${all.reduce((n, p) => n + p.text.length, 0)} characters`);
  console.log(`  videos of the playlist without a chapter: ${firstVideos.filter((v) => !videos.has(v)).join(", ") || "none"}`);
  console.log(`  chapters for videos outside the playlist: ${[...videos].filter((v) => !firstVideos.includes(v)).join(", ") || "none"}`);
  console.log(`  sections with LaTeX left in them: ${leftover}`);
  const sample = all[Math.min(40, all.length - 1)];
  console.log(`  sample (${sample.videoId} at ${sample.startSec}s):\n${sample.text.slice(0, 500).replace(/^/gm, "    ")}\n`);
}
