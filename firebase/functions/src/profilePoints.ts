import type { Firestore } from "firebase-admin/firestore";
import { awardPoints } from "./points";
import { loadPointsConfig } from "./pointsConfig";

type Data = Record<string, unknown> | undefined;
const filled = (v: unknown) => typeof v === "string" && v.trim().length > 0;

/**
 * The items that make a profile complete. The app's domain/ProfileCompletion.kt uses the same
 * list and rules; a test keeps both in step. Each earns config/points.profileField once, and all
 * of them together earn config/points.profileComplete once.
 */
export const PROFILE_ITEMS: { id: string; done: (d: Record<string, unknown>) => boolean }[] = [
  { id: "photo", done: (d) => filled(d.photoUrl) },
  { id: "name", done: (d) => filled(d.displayName) },
  { id: "dob", done: (d) => filled(d.dob) },
  { id: "gender", done: (d) => filled(d.gender) },
  { id: "location", done: (d) => filled(d.country) && (filled(d.state) || filled(d.city)) },
  { id: "about", done: (d) => typeof d.about === "string" && d.about.trim().length >= 10 },
  { id: "language", done: (d) => filled(d.language) },
];

export function profileCompletion(d: Data): { done: string[]; percent: number } {
  const data = d ?? {};
  const done = PROFILE_ITEMS.filter((i) => i.done(data)).map((i) => i.id);
  return { done, percent: Math.round((done.length * 100) / PROFILE_ITEMS.length) };
}

const TRACKED = ["photoUrl", "displayName", "dob", "gender", "country", "state", "city", "about", "language"];

/**
 * Runs on every users/{uid} write. Only looks further when a profile field changed, so the points
 * this writes (which also touch users/{uid}) don't loop. Awards are once-only by event key.
 */
export async function onProfileWritten(db: Firestore, uid: string, before: Data, after: Data): Promise<number> {
  if (!after) return 0;
  if (before && TRACKED.every((k) => before[k] === after[k])) return 0;
  const { done } = profileCompletion(after);
  if (done.length === 0) return 0;
  const cfg = await loadPointsConfig(db);
  let given = 0;
  for (const id of done) {
    if (cfg.profileField > 0 && await awardPoints(db, uid, {
      eventKey: `profile:${id}`, amount: cfg.profileField, reason: "profile", milestone: false, note: id,
    })) given += cfg.profileField;
  }
  if (done.length === PROFILE_ITEMS.length && cfg.profileComplete > 0 && await awardPoints(db, uid, {
    eventKey: "profile:complete", amount: cfg.profileComplete, reason: "profile_complete", milestone: true,
  })) given += cfg.profileComplete;
  return given;
}
