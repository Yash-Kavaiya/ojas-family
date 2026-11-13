import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { awardPoints } from "./points";
import { canPractise, dayBefore, logPracticeFor, streak, type DayLog } from "./practice";
import { loadPointsConfig } from "./pointsConfig";
import { istDay } from "./time";

/**
 * The four guided practices in the Practice tab. Each has one video or audio, set by an admin in
 * config/practiceMedia: { blue_light: { url, kind: "video" | "audio", durationSec, title }, ... },
 * or a YouTube video: { kind: "youtube", videoId, durationSec, title }.
 * Listening or watching to the end earns config/points.practice once per practice per IST day.
 */
export const PRACTICE_SECTIONS = ["blue_light", "quick_protection", "morning", "night"] as const;
export type PracticeSection = (typeof PRACTICE_SECTIONS)[number];

/** Default points per finished practice; config/points.practice overrides it. */
export const PRACTICE_POINTS = 2;

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
  const source = media?.kind === "youtube" ? media?.videoId : media?.url;
  if (!source || !(secs > 0)) throw new HttpsError("failed-precondition", "This practice has no media yet");
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
 * to the end earns nothing. Gives the practice points once per practice per day, marks the day as
 * practised for the streak, and adds the bonuses: all four practices in a day, and every 7th day
 * in a row. Morning and night also count toward the 21-day goals.
 */
export async function completePracticeFor(
  db: Firestore, uid: string, role: unknown, rawSection: unknown, now: Date,
): Promise<{ awarded: boolean; points: number; streak: number; completedGoals: string[] }> {
  const section = sectionOf(rawSection);
  await checkAccess(db, uid, role, section);
  const secs = await mediaSeconds(db, section);
  const day = istDay(now);
  const ref = db.doc(`users/${uid}/practiceSessions/${section}_${day}`);
  const session = await ref.get();
  if (!session.exists) throw new HttpsError("failed-precondition", "Start the practice first");
  const elapsed = (now.getTime() - (session.get("startedAt") as Timestamp).toMillis()) / 1000;
  if (elapsed < secs * MIN_WATCHED_SHARE) throw new HttpsError("failed-precondition", "Finish the practice to earn the point");

  const cfg = await loadPointsConfig(db);
  let points = 0;
  const awarded = await awardPoints(db, uid, {
    eventKey: `practice:${section}:${day}`,
    amount: cfg.practice,
    reason: "practice_media",
    milestone: false,
    note: section,
  });
  if (awarded) points += cfg.practice;
  await ref.set({ completedAt: FieldValue.serverTimestamp() }, { merge: true });

  // Any finished practice keeps the streak going
  const userRef = db.doc(`users/${uid}`);
  await userRef.collection("practiceLog").doc(day).set({ any: true, [section]: true, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  const anyStreak = await currentStreak(db, uid, day);

  if (cfg.allPracticesBonus > 0) {
    const done = await Promise.all(PRACTICE_SECTIONS.map((s) => userRef.collection("pointEvents").doc(`practice:${s}:${day}`).get()));
    if (done.every((d) => d.exists) && await awardPoints(db, uid, {
      eventKey: `practice_all:${day}`, amount: cfg.allPracticesBonus, reason: "practice_bonus", milestone: true, note: "all",
    })) points += cfg.allPracticesBonus;
  }
  if (cfg.streakBonus > 0 && anyStreak > 0 && anyStreak % cfg.streakBonusEvery === 0) {
    if (await awardPoints(db, uid, {
      eventKey: `streak:${anyStreak}:${day}`, amount: cfg.streakBonus, reason: "streak_bonus", milestone: true, note: String(anyStreak),
    })) points += cfg.streakBonus;
  }

  let completedGoals: string[] = [];
  if (section === "morning" || section === "night") {
    completedGoals = (await logPracticeFor(db, uid, role, section, now)).completed;
  }
  return { awarded, points, streak: anyStreak, completedGoals };
}

/** Days in a row, ending today, with any finished practice (or a morning/night check-in). */
export async function currentStreak(db: Firestore, uid: string, today: string): Promise<number> {
  const col = db.collection(`users/${uid}/practiceLog`);
  const snap = await col.where("__name__", ">=", col.doc(dayBefore(today, 400))).get();
  const logs: Record<string, DayLog & { any?: boolean }> = {};
  snap.forEach((d) => { logs[d.id] = d.data() as DayLog; });
  return streak(logs, today, (l) => (l as { any?: boolean }).any === true || l.morning === true || l.night === true);
}
