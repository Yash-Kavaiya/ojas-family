// Builds the Ask Swamiji (AI) knowledge base and publishes it as ai/knowledge.json in Storage,
// the file the askSwamiji function searches.
//
// Passages live in Firestore `knowledge/{chunkId}`:
//   1. knowledge/*.md (hand-written facts about Ojas Healing and the app), split at "## " headings
//   2. config/about (the About Ojas Family / About Swamiji texts admins edit in the app)
//   3. Notes on the Ojas Healing YouTube videos, in English, Hindi and Gujarati. These were written
//      once (November 2026) and are kept as they are.
// Every passage is embedded with NVIDIA's embedding model (nvidia/nemotron-3.5-embed-8b) and the
// vectors are published beside the passages as ai/knowledge.vectors.bin. No Google AI service is used.
//      To add a new video, add passages for it to a knowledge/*.md file.
//
// Run (an owner of the project):
//   gcloud auth application-default login
//   NVIDIA_API_KEY=<key> GOOGLE_CLOUD_QUOTA_PROJECT=ojas-family-app npx ts-node scripts/buildKnowledge.ts [--publish-only]
// (the quota project matters when gcloud's default one has no billing: Storage then refuses the upload)
import { createHash } from "node:crypto";
import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { initializeApp } from "firebase-admin/app";
import { getStorage } from "firebase-admin/storage";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { DEFAULT_AI } from "../src/ai";
import { EMBED_DIM, EMBED_MODEL, embed, vectorsPath } from "../src/aiNvidia";

const EMBED_BATCH = 32;

// GCLOUD_PROJECT=demo-ojas with FIRESTORE_EMULATOR_HOST set builds into the local emulator instead
const PROJECT = process.env.GCLOUD_PROJECT || "ojas-family-app";
initializeApp({ projectId: PROJECT, storageBucket: process.env.STORAGE_BUCKET || `${PROJECT}.firebasestorage.app` });
const db = getFirestore();

const args = process.argv.slice(2);
const PUBLISH_ONLY = args.includes("--publish-only");
const OUT_DIR = args.includes("--out") ? args[args.indexOf("--out") + 1] : "";
// The phone model reads only a few hundred words per passage well
const MAX_PASSAGE_CHARS = 900;

interface Chunk {
  text: string;
  title: string;
  url: string;
  sourceType: "doc" | "about" | "video";
  videoId?: string;
  startSec?: number;
  lang?: string;
}

const idFor = (s: string) => createHash("sha1").update(s).digest("hex").slice(0, 24);

async function writeChunks(sourceId: string, chunks: Chunk[]) {
  // Replace this source's old passages so edits don't leave stale ones behind
  const old = await db.collection("knowledge").where("sourceId", "==", sourceId).get();
  const batch = db.batch();
  old.forEach((d) => batch.delete(d.ref));
  await batch.commit();
  const write = db.batch();
  for (const c of chunks.flatMap(splitLong)) {
    // Firestore refuses undefined values: leave missing fields out
    const clean = Object.fromEntries(Object.entries(c).filter(([, v]) => v !== undefined));
    write.set(db.collection("knowledge").doc(idFor(`${sourceId}|${c.lang ?? ""}|${c.startSec ?? 0}|${c.text.slice(0, 60)}`)), {
      ...clean, sourceId, updatedAt: FieldValue.serverTimestamp(),
    });
  }
  await write.commit();
  console.log(`  ${chunks.length} passages from ${sourceId}`);
}

/** Long passages become several, cut at sentence ends, each starting with the passage title. */
function splitLong(c: Chunk): Chunk[] {
  if (c.text.length <= MAX_PASSAGE_CHARS) return [c];
  const [head, ...rest] = c.text.split("\n");
  const sentences = rest.join("\n").split(/(?<=[.!?।])\s+/);
  const out: Chunk[] = [];
  let cur = "";
  for (const s of sentences) {
    if (cur && head.length + cur.length + s.length + 2 > MAX_PASSAGE_CHARS) {
      out.push({ ...c, text: `${head}\n${cur.trim()}` });
      cur = "";
    }
    cur += `${s} `;
  }
  if (cur.trim()) out.push({ ...c, text: `${head}\n${cur.trim()}`.slice(0, MAX_PASSAGE_CHARS * 2) });
  return out;
}

/** Splits markdown at "## " headings; each heading and its text is one passage. */
function markdownChunks(file: string, body: string): Chunk[] {
  const clean = body.replace(/<!--[\s\S]*?-->/g, "");
  return clean.split(/\n(?=## )/).map((part) => part.trim()).filter((p) => p.startsWith("## ")).map((part) => {
    const [head, ...rest] = part.split("\n");
    const title = head.replace(/^##\s+/, "").trim();
    return { text: `${title}\n${rest.join("\n").trim()}`, title, url: "https://www.youtube.com/@OjasHealing", sourceType: "doc" as const, lang: "en" };
  });
}

async function docs() {
  const dir = join(__dirname, "..", "knowledge");
  for (const f of readdirSync(dir).filter((n) => n.endsWith(".md"))) {
    await writeChunks(`doc:${f}`, markdownChunks(f, readFileSync(join(dir, f), "utf8")));
  }
  const about = (await db.doc("config/about").get()).data() ?? {};
  const chunks: Chunk[] = [];
  for (const [key, label] of [["ojasFamily", "About Ojas Family"], ["swamiji", "About Swamiji"]] as const) {
    for (const [suffix, lang] of [["", "en"], ["_hi", "hi"], ["_gu", "gu"]] as const) {
      const text = about[`${key}${suffix}`];
      if (typeof text === "string" && text.trim().length > 20) {
        chunks.push({ text: `${label}\n${text.trim()}`, title: label, url: "https://www.youtube.com/@OjasHealing", sourceType: "about", lang });
      }
    }
  }
  if (chunks.length) await writeChunks("about", chunks);
}

/** Publishes every passage as ai/knowledge.json, the file the app searches on the phone. */
async function publish() {
  const snap = await db.collection("knowledge").get();
  const passages = snap.docs.map((d) => {
    const x = d.data();
    return {
      id: d.id, title: String(x.title ?? ""), text: String(x.text ?? ""), url: String(x.url ?? ""),
      ...(x.videoId ? { videoId: String(x.videoId) } : {}), ...(x.startSec ? { startSec: Number(x.startSec) } : {}),
      sourceType: String(x.sourceType ?? ""), lang: String(x.lang ?? "en"),
    };
  });
  // Stable order, so the vectors file lines up with the passages
  passages.sort((x, y) => x.id.localeCompare(y.id));
  // One embedding per passage from NVIDIA's embedding model, for meaning search next to keyword search
  const vectors = new Float32Array(passages.length * EMBED_DIM);
  for (let i = 0; i < passages.length; i += EMBED_BATCH) {
    const batch = passages.slice(i, i + EMBED_BATCH);
    let rows: Float32Array[] | null = null;
    for (let attempt = 1; !rows; attempt++) {
      try {
        rows = await embed(batch.map((p) => p.text.slice(0, 2000)), "passage", 60_000);
      } catch (e) {
        if (attempt >= 5) throw e;
        console.warn(`  embedding batch at ${i} failed (${(e as Error).message.slice(0, 80)}); trying again`);
        await new Promise((r) => setTimeout(r, 3000 * attempt));
      }
    }
    rows.forEach((row, j) => vectors.set(row, (i + j) * EMBED_DIM));
    if ((i / EMBED_BATCH) % 10 === 0) console.log(`  embedded ${Math.min(i + EMBED_BATCH, passages.length)}/${passages.length}`);
  }
  const body = JSON.stringify({ version: Date.now(), embedModel: EMBED_MODEL, embedDim: EMBED_DIM, passages });
  if (OUT_DIR) {
    // --out <folder>: write the two files locally instead of publishing (for trying the search first)
    writeFileSync(join(OUT_DIR, "knowledge.json"), body);
    writeFileSync(join(OUT_DIR, "knowledge.vectors.bin"), Buffer.from(vectors.buffer));
    console.log(`Wrote ${passages.length} passages and vectors to ${OUT_DIR}`);
    return;
  }
  const bucket = getStorage().bucket();
  // Vectors first: for the moment between the two uploads the server sees a mismatch and uses keyword search only
  await bucket.file(vectorsPath(DEFAULT_AI.knowledgePath)).save(Buffer.from(vectors.buffer), {
    contentType: "application/octet-stream", resumable: false, metadata: { cacheControl: "no-cache" },
  });
  await bucket.file(DEFAULT_AI.knowledgePath).save(body, {
    contentType: "application/json", resumable: false, metadata: { cacheControl: "no-cache" },
  });
  console.log(`Published ${passages.length} passages (${Math.round(body.length / 1024)} KB) and their vectors (${Math.round(vectors.byteLength / 1024)} KB) to ${DEFAULT_AI.knowledgePath}`);
}

async function main() {
  if (!PUBLISH_ONLY) await docs();
  await publish();
  console.log("Knowledge base ready.");
}

main().catch((e) => { console.error(e); process.exit(1); });
