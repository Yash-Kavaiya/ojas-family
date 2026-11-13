import { GoogleGenAI } from "@google/genai";

/**
 * Gemini, used only by scripts/buildKnowledge.ts on an owner's computer to write notes on each
 * Ojas Healing video once. The app never calls it: members' questions are answered on the phone.
 * Runs on Vertex AI in the genaiguruyoutube project (it has billing) with the signed-in gcloud
 * account (gcloud auth application-default login).
 */
export const VERTEX_PROJECT = process.env.VERTEX_PROJECT || "genaiguruyoutube";
export const VERTEX_LOCATION = process.env.VERTEX_LOCATION || "global";

let client: GoogleGenAI | null = null;
export function genai(): GoogleGenAI {
  if (!client) client = new GoogleGenAI({ vertexai: true, project: VERTEX_PROJECT, location: VERTEX_LOCATION });
  return client;
}
