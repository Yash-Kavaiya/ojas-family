import { GoogleGenAI } from "@google/genai";
import type { Firestore } from "firebase-admin/firestore";
import type { AiDeps, Passage, Turn } from "./ai";

/**
 * Gemini for Ask Swamiji AI. A key, if used, stays on the server as a secret, never in the app or
 * the code.
 */
export const EMBED_DIMENSIONS = 768; // Firestore vector fields allow up to 2048
// Gemini runs on Vertex AI in the genaiguruyoutube project (it has billing); the Ojas Functions
// service account has roles/aiplatform.user there. "global" is where the Gemini 3 models are served.
export const VERTEX_PROJECT = process.env.VERTEX_PROJECT || "genaiguruyoutube";
export const VERTEX_LOCATION = process.env.VERTEX_LOCATION || "global";

let client: GoogleGenAI | null = null;
/**
 * With GEMINI_API_KEY set (a Gemini API key from AI Studio, kept as the Firebase secret of that name)
 * the Gemini Developer API is used; without it (the default), the Functions service account signs in to
 * Vertex AI in [VERTEX_PROJECT].
 * The same model names work on both.
 */
export function genai(): GoogleGenAI {
  if (!client) {
    const apiKey = process.env.GEMINI_API_KEY?.trim();
    if (apiKey) {
      client = new GoogleGenAI({ apiKey });
    } else {
      client = new GoogleGenAI({ vertexai: true, project: VERTEX_PROJECT, location: VERTEX_LOCATION });
    }
  }
  return client;
}

export async function embedText(model: string, text: string, taskType: "RETRIEVAL_QUERY" | "RETRIEVAL_DOCUMENT", title?: string): Promise<number[]> {
  const res = await genai().models.embedContent({
    model,
    contents: text,
    config: { taskType, outputDimensionality: EMBED_DIMENSIONS, ...(title ? { title } : {}) },
  });
  const values = res.embeddings?.[0]?.values;
  if (!values?.length) throw new Error("No embedding returned");
  return values;
}

export function geminiDeps(db: Firestore): AiDeps {
  return {
    embedQuery: (model, text) => embedText(model, text, "RETRIEVAL_QUERY"),

    async retrieve(vector: number[], topK: number): Promise<Passage[]> {
      const snap = await db.collection("knowledge").findNearest({
        vectorField: "embedding",
        queryVector: vector,
        limit: topK,
        distanceMeasure: "COSINE",
        distanceResultField: "_distance",
      }).get();
      return snap.docs.map((d) => ({
        id: d.id,
        text: String(d.get("text") ?? ""),
        title: String(d.get("title") ?? ""),
        url: String(d.get("url") ?? ""),
        videoId: d.get("videoId") || undefined,
        startSec: Number(d.get("startSec") ?? 0) || undefined,
        sourceType: String(d.get("sourceType") ?? ""),
        distance: Number(d.get("_distance")),
      }));
    },

    async generate(model: string, system: string, history: Turn[], prompt: string, onText: (piece: string) => void): Promise<string> {
      const stream = await genai().models.generateContentStream({
        model,
        contents: [
          ...history.map((t) => ({ role: t.role, parts: [{ text: t.text }] })),
          { role: "user", parts: [{ text: prompt }] },
        ],
        config: {
          systemInstruction: system,
          temperature: 0.4,
          maxOutputTokens: 1400,
          // Answers should read straight away; no long hidden thinking for a chat reply
          thinkingConfig: { thinkingBudget: 0 },
        },
      });
      let full = "";
      let shown = 0;
      for await (const chunk of stream) {
        const piece = chunk.text ?? "";
        if (!piece) continue;
        full += piece;
        // Don't stream the FOLLOWUPS line to the member; it becomes chips under the answer
        const cut = full.indexOf("FOLLOWUPS:");
        const visible = cut >= 0 ? full.slice(0, cut) : full.slice(0, Math.max(shown, full.length - "FOLLOWUPS:".length));
        if (visible.length > shown) {
          onText(visible.slice(shown));
          shown = visible.length;
        }
      }
      return full;
    },
  };
}
