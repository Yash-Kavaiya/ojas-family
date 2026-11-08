import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { awardPoints } from "./points";
import { canPractise, logPracticeFor } from "./practice";
import { istDay } from "./time";

/**
 * The four guided practices in the Practice tab. Each has one video or audio, set by an admin in
 * config/practiceMedia: { blue_light: { url, kind: "video" | "audio", durationSec, title }, ... }.
 * Listening or watching to the end earns one Moksha Point per practice per IST day.
 */
export const PRACTICE_SECTIONS = ["blue_light", "quick_protection", "morning", "night"] as const;
export type PracticeSection = (typeof PRACTICE_SECTIONS)[number];

export const PRACTICE_POINTS = 1;

/** Share of the media's length that must have passed since starting before it counts as finished. */
export const MIN_WATCHED_SHARE = 0.9;

function sectionOf(raw: unknown): PracticeSection {
  const s = String(raw ?? "");
  if (!(PRACTICE_SECTIONS as readonly string[]).includes(s)) throw new HttpsError("invalid-argument", "Unknown practice");
  return s as PracticeSection;
}

async function mediaSeconds(db: Firestore, section: PracticeSection): Promise<number> {
  const media = (await db.doc("config/practiceMedia").get()).get(section) as Record<string, unknown> | undefined;
  const secs = Number(media?.durationSec ?? 0);
  if (!media?.url || !(secs > 0)) throw new HttpsError("failed-precondition", "This practice has no media yet");
  return secs;
}

/** Morning and night are the daily practices: Level 4, membership or staff (same as logPractice). */
async function checkAccess(db: Firestore, uid: string, role: unknown, section: PracticeSection): Promise<void> {
  if (section !== "morning" && section !== "night") return;
  const user = (await db.doc(`users/${uid}`).get()).data() ?? {};
  if (!canPractise(user, role)) throw new HttpsError("permission-denied", "Daily practices unlock at Level 4 or with membership");
}

/**
 * Marks when the member pressed play today. Only the first start of the day counts, so pausing,
 * leaving and resuming later keeps the original start time.
 */
export async function startPracticeFor(db: Firestore, uid: string, role: unknown, rawSection: unknown, now: Date): Promise<{ startedAt: number }> {
  const section = sectionOf(rawSection);
  await checkAccess(db, uid, role, section);
  await mediaSeconds(db, section);
  const ref = db.doc(`users/${uid}/practiceSessions/${section}_${istDay(now)}`);
  const startedAt = await db.runTransaction(async (tx) => {
    const s = await tx.get(ref);
    if (s.exists) return (s.get("startedAt") as Timestamp).toMillis();
    tx.set(ref, { section, day: istDay(now), startedAt: Timestamp.fromDate(now) });
    return now.getTime();
  });
  return { startedAt };
}

/**
 * Finishing a practice: real time since today's start must cover most of the media, so skipping
 * to the end earns nothing. Gives the point once per practice per day; morning and night also
 * count as that day's practice toward the 21-day goals.
 */
export async function completePracticeFor(
  db: Firestore, uid: string, role: unknown, rawSection: unknown, now: Date,
): Promise<{ awarded: boolean; points: number; completedGoals: string[] }> {
  const section = sectionOf(rawSection);
  await checkAccess(db, uid, role, section);
  const secs = await mediaSeconds(db, section);
  const day = istDay(now);
  const ref = db.doc(`users/${uid}/practiceSessions/${section}_${day}`);
  const session = await ref.get();
  if (!session.exists) throw new HttpsError("failed-precondition", "Start the practice first");
  const elapsed = (now.getTime() - (session.get("startedAt") as Timestamp).toMillis()) / 1000;
  if (elapsed < secs * MIN_WATCHED_SHARE) throw new HttpsError("failed-precondition", "Finish the practice to earn the point");

  const awarded = await awardPoints(db, uid, {
    eventKey: `practice:${section}:${day}`,
    amount: PRACTICE_POINTS,
    reason: "practice_media",
    milestone: false,
    note: section,
  });
  await ref.set({ completedAt: FieldValue.serverTimestamp() }, { merge: true });

  let completedGoals: string[] = [];
  if (section === "morning" || section === "night") {
    completedGoals = (await logPracticeFor(db, uid, role, section, now)).completed;
  }
  return { awarded, points: awarded ? PRACTICE_POINTS : 0, completedGoals };
}
