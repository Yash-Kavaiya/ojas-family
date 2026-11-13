import { getStorage } from "firebase-admin/storage";
import type { AiDeps, Turn } from "./ai";
import { KnowledgeSearch, type Passage, unit, type Vectors } from "./knowledgeSearch";

/**
 * The model behind Ask Swamiji AI: NVIDIA's hosted API (OpenAI-compatible chat completions).
 * The key is the Firebase secret NVIDIA_API_KEY; it is never in the app or the code.
 */
const BASE = (process.env.NVIDIA_BASE_URL || "https://integrate.api.nvidia.com/v1").replace(/\/$/, "");
const ENDPOINT = `${BASE}/chat/completions`;
const EMBED_ENDPOINT = `${BASE}/embeddings`;
const FOLLOWUPS = "FOLLOWUPS:";

export const EMBED_MODEL = "nvidia/nemotron-3.5-embed-8b";
/** The model can give up to 4096 numbers per text; 1024 keeps the file small and searches just as well here. */
export const EMBED_DIM = 1024;
/** One unit-length vector per passage, in passage order, as raw 32-bit floats (written by scripts/buildKnowledge.ts). */
export const vectorsPath = (knowledgePath: string) => `${knowledgePath.replace(/\.json$/, "")}.vectors.bin`;

function apiKey(): string {
  const key = process.env.NVIDIA_API_KEY?.trim();
  if (!key) throw new Error("NVIDIA_API_KEY is not set");
  return key;
}

/** Embeds texts with NVIDIA's embedding model: "query" for a question, "passage" for teachings. */
export async function embed(texts: string[], inputType: "query" | "passage", timeoutMs = 20_000): Promise<Float32Array[]> {
  const res = await fetch(EMBED_ENDPOINT, {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey()}`, "Content-Type": "application/json" },
    body: JSON.stringify({ input: texts, model: EMBED_MODEL, encoding_format: "float", input_type: inputType, truncate: "END", dimensions: EMBED_DIM }),
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new Error(`NVIDIA embeddings ${res.status}: ${(await res.text().catch(() => "")).slice(0, 300)}`);
  const data = ((await res.json()) as { data?: { index: number; embedding: number[] }[] }).data ?? [];
  if (data.length !== texts.length) throw new Error("NVIDIA embeddings: wrong count");
  return data.sort((x, y) => x.index - y.index).map((d) => unit(d.embedding));
}

// The passages and their vectors are two small files; keep them in memory between questions and look for new ones now and then
const KNOWLEDGE_TTL_MS = 10 * 60 * 1000;
let cached: { path: string; at: number; search: KnowledgeSearch; vectors: Vectors | null } | null = null;

async function knowledge(path: string): Promise<{ search: KnowledgeSearch; vectors: Vectors | null }> {
  if (cached && cached.path === path && Date.now() - cached.at < KNOWLEDGE_TTL_MS) return cached;
  try {
    const bucket = getStorage().bucket();
    const [bytes] = await bucket.file(path).download();
    const passages = (JSON.parse(bytes.toString("utf8")) as { passages?: Passage[] }).passages ?? [];
    // Without the vectors file (or with one from another build) the keyword search still answers
    let vectors: Vectors | null = null;
    try {
      const [raw] = await bucket.file(vectorsPath(path)).download();
      const data = new Float32Array(new Uint8Array(raw).buffer);
      if (passages.length && data.length === passages.length * EMBED_DIM) vectors = { dim: EMBED_DIM, data };
      else console.warn(`vectors don't match the passages (${data.length} numbers for ${passages.length} passages)`);
    } catch (e) {
      console.warn("no vectors file; using keyword search only", (e as Error).message);
    }
    cached = { path, at: Date.now(), search: new KnowledgeSearch(passages), vectors };
  } catch (e) {
    // Keep answering from the last good copy if Storage hiccups
    if (!cached || cached.path !== path) throw e;
    console.warn("knowledge reload failed, using the cached copy", e);
  }
  return cached!;
}

/** Reads an OpenAI-style event stream, calling [onDelta] with each piece of answer text. */
async function readStream(body: ReadableStream<Uint8Array>, onDelta: (text: string) => void): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let nl: number;
    while ((nl = buffer.indexOf("\n")) >= 0) {
      const line = buffer.slice(0, nl).trim();
      buffer = buffer.slice(nl + 1);
      if (!line.startsWith("data:")) continue;
      const data = line.slice(5).trim();
      if (!data || data === "[DONE]") continue;
      try {
        const piece = (JSON.parse(data) as { choices?: { delta?: { content?: string | null } }[] }).choices?.[0]?.delta?.content;
        if (piece) onDelta(piece);
      } catch {
        // a partial or keep-alive line: skip it
      }
    }
  }
}

export function nvidiaDeps(): AiDeps {
  return {
    async retrieve(path, query, topK, lang) {
      const { search, vectors } = await knowledge(path);
      if (!vectors) return search.search(query, topK, lang);
      try {
        const [vector] = await embed([query.slice(0, 2000)], "query", 8_000);
        return search.hybrid(query, vector, vectors, topK, lang);
      } catch (e) {
        // The embedding service is slow or down: the keyword search alone still finds the teaching
        console.warn("query embedding failed; keyword search only", (e as Error).message);
        return search.search(query, topK, lang);
      }
    },

    async generate(model: string, system: string, history: Turn[], prompt: string, onText: (piece: string) => void): Promise<string> {
      const key = apiKey();
      const attempt = async (): Promise<string> => {
        const res = await fetch(ENDPOINT, {
          method: "POST",
          headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json", Accept: "text/event-stream" },
          body: JSON.stringify({
            model,
            messages: [
              { role: "system", content: system },
              ...history.map((t) => ({ role: t.role, content: t.text })),
              { role: "user", content: prompt },
            ],
            temperature: 0.3,
            top_p: 0.9,
            max_tokens: 1200,
            // Answers should start right away; no long hidden thinking for a chat reply
            chat_template_kwargs: { enable_thinking: false },
            stream: true,
          }),
          signal: AbortSignal.timeout(90_000),
        });
        if (!res.ok || !res.body) throw new Error(`NVIDIA API ${res.status}: ${(await res.text().catch(() => "")).slice(0, 300)}`);
        let full = "";
        let shown = 0;
        await readStream(res.body, (piece) => {
          full += piece;
          // Don't stream the FOLLOWUPS line to the member; it becomes chips under the answer
          const cut = full.indexOf(FOLLOWUPS);
          const visible = cut >= 0 ? full.slice(0, cut) : full.slice(0, Math.max(shown, full.length - FOLLOWUPS.length));
          if (visible.length > shown) {
            onText(visible.slice(shown));
            shown = visible.length;
          }
        });
        return full;
      };
      // The hosted model now and then closes a stream without a word, or is briefly busy: try once more
      let full = "";
      try {
        full = await attempt();
      } catch (e) {
        console.warn("NVIDIA first attempt failed", e);
      }
      return full.trim() ? full : attempt();
    },
  };
}
