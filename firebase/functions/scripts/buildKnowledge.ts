// Builds the Ask Swamiji (AI) knowledge base in Firestore `knowledge/{chunkId}`.
//
// Sources:
//   1. knowledge/*.md (hand-written facts about Ojas Healing and the app), split at "## " headings
//   2. config/about (the About Ojas Family / About Swamiji texts admins edit in the app)
//   3. Ojas Healing YouTube videos: every video in scripts/content.json, plus the newest videos of
//      each playlist in tools/channel_playlists.txt (YouTube's public RSS feed). Gemini watches each
//      video and writes teaching notes with timestamps; the notes become passages.
//
// Every passage is embedded with gemini-embedding-001 (768 dimensions) for vector search.
// Videos already done are skipped, so running it again only adds new videos (--force redoes all).
//
// Run (an owner of the project, Vertex AI API enabled):
//   gcloud auth application-default login
//   npx ts-node scripts/buildKnowledge.ts [--force] [--limit 20] [--only-docs]
//
// Then create the vector index once (also in firestore.indexes.json):
//   gcloud firestore indexes composite create --collection-group=knowledge --query-scope=COLLECTION \
//     --field-config=vector-config='{"dimension":"768","flat":"{}"}',field-path=embedding
import { createHash } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { XMLParser } from "fast-xml-parser";
import { Type } from "@google/genai";
import { embedText, genai } from "../src/aiGemini";
import { DEFAULT_AI } from "../src/ai";

initializeApp({ projectId: "ojas-family-app" });
const db = getFirestore();

const args = process.argv.slice(2);
const FORCE = args.includes("--force");
const ONLY_DOCS = args.includes("--only-docs");
const LIMIT = Number(args[args.indexOf("--limit") + 1]) || Infinity;
const NOTES_MODEL = process.env.NOTES_MODEL || "gemini-2.5-flash";

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
  for (const c of chunks) {
    const embedding = await embedText(DEFAULT_AI.embedModel, c.text, "RETRIEVAL_DOCUMENT", c.title);
    await db.collection("knowledge").doc(idFor(`${sourceId}|${c.startSec ?? 0}|${c.text.slice(0, 60)}`)).set({
      ...c, sourceId, embedding: FieldValue.vector(embedding), updatedAt: FieldValue.serverTimestamp(),
    });
  }
  console.log(`  ${chunks.length} passages from ${sourceId}`);
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

interface Video { videoId: string; title: string }

async function playlistVideos(): Promise<Video[]> {
  const path = join(__dirname, "..", "..", "..", "tools", "channel_playlists.txt");
  const ids = readFileSync(path, "utf8").split("\n").map((l) => l.split("|")[0].trim()).filter(Boolean);
  const parser = new XMLParser({ ignoreAttributes: false });
  const out: Video[] = [];
  for (const id of ids) {
    try {
      const xml = await (await fetch(`https://www.youtube.com/feeds/videos.xml?playlist_id=${id}`)).text();
      const entries = parser.parse(xml)?.feed?.entry;
      for (const e of Array.isArray(entries) ? entries : entries ? [entries] : []) {
        if (e["yt:videoId"]) out.push({ videoId: String(e["yt:videoId"]), title: String(e.title ?? "") });
      }
    } catch (err) {
      console.warn(`  playlist ${id}: ${(err as Error).message}`);
    }
  }
  return out;
}

const NOTES_SCHEMA = {
  type: Type.OBJECT,
  properties: {
    language: { type: Type.STRING, description: "Main spoken language: en, hi or gu" },
    summary: { type: Type.STRING, description: "4-8 sentences: what Swamiji teaches in this video" },
    sections: {
      type: Type.ARRAY,
      items: {
        type: Type.OBJECT,
        properties: {
          startSec: { type: Type.INTEGER, description: "Where this part starts, in seconds" },
          heading: { type: Type.STRING },
          text: { type: Type.STRING, description: "What is taught here, faithfully, including any practice steps in order" },
        },
        required: ["startSec", "heading", "text"],
      },
    },
  },
  required: ["language", "summary", "sections"],
};

const NOTES_PROMPT = [
  "This is a video by Swamiji (Swami Anand Swaroop) of Ojas Healing.",
  "Write faithful teaching notes so members can later ask questions about it.",
  "Keep Swamiji's meaning exactly; don't add anything he didn't say. Keep Sanskrit and Hindi terms, with a short meaning in brackets.",
  "Write the notes in the video's own language (Hindi in Devanagari, Gujarati in Gujarati script, else English), and the summary in that language too.",
  "Split the video into 3 to 12 parts at natural topic changes. For a guided meditation, list its steps in order.",
].join("\n");

async function videoNotes(v: Video): Promise<Chunk[]> {
  const url = `https://www.youtube.com/watch?v=${v.videoId}`;
  const res = await genai().models.generateContent({
    model: NOTES_MODEL,
    contents: [{ role: "user", parts: [{ fileData: { fileUri: url, mimeType: "video/*" } }, { text: NOTES_PROMPT }] }],
    config: { responseMimeType: "application/json", responseSchema: NOTES_SCHEMA, temperature: 0.2, mediaResolution: "MEDIA_RESOLUTION_LOW" as never },
  });
  const notes = JSON.parse(res.text ?? "{}") as { language?: string; summary?: string; sections?: { startSec: number; heading: string; text: string }[] };
  const lang = notes.language ?? "hi";
  const chunks: Chunk[] = [];
  if (notes.summary) chunks.push({ text: `${v.title}\n${notes.summary}`, title: v.title, url, videoId: v.videoId, sourceType: "video", lang });
  for (const s of notes.sections ?? []) {
    const start = Math.max(0, Math.floor(s.startSec || 0));
    chunks.push({
      text: `${v.title} · ${s.heading}\n${s.text}`, title: v.title, url: start ? `${url}&t=${start}s` : url,
      videoId: v.videoId, startSec: start || undefined, sourceType: "video", lang,
    });
  }
  return chunks;
}

async function videos() {
  const content = JSON.parse(readFileSync(join(__dirname, "content.json"), "utf8")) as Video[];
  const all = new Map<string, Video>();
  for (const v of [...content, ...(await playlistVideos())]) if (v.videoId && !all.has(v.videoId)) all.set(v.videoId, v);
  console.log(`${all.size} videos found`);
  let done = 0;
  for (const v of all.values()) {
    if (done >= LIMIT) break;
    const mark = db.doc(`knowledgeSources/${v.videoId}`);
    if (!FORCE && (await mark.get()).get("status") === "done") continue;
    try {
      console.log(`- ${v.title}`);
      await writeChunks(`video:${v.videoId}`, await videoNotes(v));
      await mark.set({ status: "done", title: v.title, at: FieldValue.serverTimestamp() });
    } catch (e) {
      console.warn(`  skipped: ${(e as Error).message}`);
      await mark.set({ status: "failed", title: v.title, error: (e as Error).message.slice(0, 300), at: FieldValue.serverTimestamp() });
    }
    done++;
  }
}

async function main() {
  await docs();
  if (!ONLY_DOCS) await videos();
  console.log("Knowledge base ready.");
}

main().catch((e) => { console.error(e); process.exit(1); });
