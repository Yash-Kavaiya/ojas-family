import type { Firestore } from "firebase-admin/firestore";
import { onThoughtOfDay } from "./community";
import type { Messenger } from "./notify";

/** The Thought of the Day comes at this hour, in each member's own time. */
export const THOUGHT_HOUR = 4;
const INDIA = 330;
const QUARTER = 15 * 60 * 1000;
// Every time zone on Earth is a multiple of 15 minutes from UTC, from UTC-12 to UTC+14
const OFFSETS = Array.from({ length: (840 + 720) / 15 + 1 }, (_, i) => -720 + i * 15);

/** The topic a phone in a zone [offset] minutes from UTC listens on (same names as the app's thoughtTopic). */
export function thoughtTopic(offset: number): string {
  return `thoughtOfDay_${offset < 0 ? "m" : "p"}${Math.abs(offset)}`;
}

/** The zones where it is 4 am in this quarter hour, each with its local date. */
export function thoughtPushesDue(now: Date): Array<{ offset: number; day: string }> {
  const tick = Math.floor(now.getTime() / QUARTER) * QUARTER;
  return OFFSETS.flatMap((offset) => {
    const local = new Date(tick + offset * 60000);
    if (local.getUTCHours() !== THOUGHT_HOUR || local.getUTCMinutes() !== 0) return [];
    return [{ offset, day: local.toISOString().slice(0, 10) }];
  });
}

/**
 * Runs every 15 minutes: tells the zones where it is now 4 am about their day's thought.
 * India's turn also writes the bell line and uses the old shared topic, for phones with an older app.
 */
export async function onThoughtPushTick(db: Firestore, m: Messenger, now: Date): Promise<void> {
  for (const { offset, day } of thoughtPushesDue(now)) {
    const t = await db.doc(`thoughts/${day}`).get();
    if (!t.exists) continue;
    const str = (v: unknown) => (typeof v === "string" ? v : "");
    try {
      await m.sendToTopic(thoughtTopic(offset), {
        type: "thought", title: str(t.get("title")).slice(0, 1000), body: str(t.get("text")).slice(0, 1000),
        target: `thoughts/${day}`, category: "thoughtOfDay",
      });
    } catch (e) {
      console.warn(`thought push to ${offset} failed`, e);
    }
    if (offset === INDIA) await onThoughtOfDay(db, m, day);
  }
}
