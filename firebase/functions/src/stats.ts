import { HttpsError } from "firebase-functions/v2/https";
import { AggregateField, Timestamp, type Firestore, type Query } from "firebase-admin/firestore";
import { istDay } from "./time";

type Claims = Record<string, unknown> | undefined;

/** The four practices of the Practice tab, as the app and the server name them. */
export const PRACTICE_SECTIONS = ["blue_light", "quick_protection", "morning", "night"] as const;

/** Languages the app offers. */
const LANGUAGES = ["en", "hi", "gu"] as const;

const DAY_MS = 86_400_000;

/** One day of app use, stored at stats/{day} (an IST date) for the admin dashboard. */
export interface DayStats {
  day: string;
  people: { active: number; new: number; total: number };
  practice: { people: number; sections: Record<string, { started: number; completed: number }> };
  ai: { questions: number; up: number; down: number };
  community: { questions: number; answers: number; posts: number; replies: number; comments: number; likes: number; announcements: number };
  calls: { total: number; connected: number; minutes: number; requests: number };
  points: { transfers: number; transferred: number };
  /** Only on days computed at the time (not when history is rebuilt): who is paid, languages, places, levels. */
  snapshot?: Snapshot;
}

export interface Snapshot {
  paid: Record<string, number>;
  languages: Record<string, number>;
  countries: Record<string, number>;
  states: Record<string, number>;
  userLevels: number[];
  memberLevels: number[];
}

function requireAdmin(token: Claims) {
  if (token?.role !== "admin") throw new HttpsError("permission-denied", "Admins only");
}

/** Midnight in India at the start of [day], and the next midnight. */
export function dayRange(day: string): [Timestamp, Timestamp] {
  const start = new Date(`${day}T00:00:00+05:30`).getTime();
  return [Timestamp.fromMillis(start), Timestamp.fromMillis(start + DAY_MS)];
}

/** The IST day [n] days before [day]. */
export function daysBefore(day: string, n: number): string {
  return istDay(new Date(new Date(`${day}T12:00:00+05:30`).getTime() - n * DAY_MS));
}

const count = async (q: Query) => (await q.count().get()).data().count;

/**
 * Counts one day of use with aggregate queries, so it costs about one read per thousand records,
 * not one per record. Everything here carries a date, so past days can be counted again later.
 */
export async function computeDay(db: Firestore, day: string): Promise<DayStats> {
  const [start, end] = dayRange(day);
  const inDay = (q: Query, field = "createdAt") => q.where(field, ">=", start).where(field, "<", end);
  const group = (name: string) => db.collectionGroup(name);

  const [active, fresh, total, practising] = await Promise.all([
    // Every member who opens the app gets the daily point once; that event marks them active
    count(inDay(group("pointEvents").where("reason", "==", "daily"))),
    count(inDay(db.collection("users"))),
    count(db.collection("users").where("createdAt", "<", end)),
    count(inDay(group("practiceLog"), "updatedAt")),
  ]);

  const sections: Record<string, { started: number; completed: number }> = {};
  await Promise.all(PRACTICE_SECTIONS.map(async (s) => {
    const q = group("practiceSessions").where("day", "==", day).where("section", "==", s);
    const [started, completed] = await Promise.all([count(q), count(q.where("completedAt", ">", Timestamp.fromMillis(0)))]);
    sections[s] = { started, completed };
  }));

  const aiSum = await group("counters").where("day", "==", day).aggregate({ n: AggregateField.sum("ai") }).get();
  const [up, down] = await Promise.all([1, -1].map((r) => count(inDay(db.collection("aiFeedback").where("rating", "==", r)))));

  const [questions, answers, posts, replies, comments, likes, announcements] = await Promise.all([
    count(inDay(db.collection("questions"))), count(inDay(group("answers"))), count(inDay(db.collection("dailyPosts"))),
    count(inDay(group("replies"))), count(inDay(group("comments"))), count(inDay(group("likes"))), count(inDay(db.collection("announcements"))),
  ]);

  // Calls and transfers are few a day, so their documents are read for minutes and amounts
  const [callDocs, requests, transferDocs] = await Promise.all([
    inDay(db.collection("calls")).select("seconds").get(),
    count(inDay(db.collection("callRequests"))),
    inDay(db.collection("pointTransfers")).select("amount").get(),
  ]);
  const seconds = callDocs.docs.map((d) => Number(d.get("seconds") ?? 0));

  return {
    day,
    people: { active, new: fresh, total },
    practice: { people: practising, sections },
    ai: { questions: Number(aiSum.data().n ?? 0), up, down },
    community: { questions, answers, posts, replies, comments, likes, announcements },
    calls: { total: callDocs.size, connected: seconds.filter((s) => s > 0).length, minutes: Math.round(seconds.reduce((a, b) => a + b, 0) / 60), requests },
    points: { transfers: transferDocs.size, transferred: transferDocs.docs.reduce((a, d) => a + Number(d.get("amount") ?? 0), 0) },
  };
}

/** Who is paid now, and members by language, place and level. Reads the user list once, so it runs once a day. */
export async function computeSnapshot(db: Firestore, now: Date): Promise<Snapshot> {
  const ents = await db.collectionGroup("entitlements").where("expiresAt", ">", Timestamp.fromDate(now)).select().get();
  const paid: Record<string, number> = { membership: 0, chat: 0, call: 0 };
  ents.forEach((d) => { if (d.id in paid) paid[d.id]++; });

  const users = await db.collection("users").select("language", "country", "state", "userLevel", "memberLevel").get();
  const languages: Record<string, number> = Object.fromEntries(LANGUAGES.map((l) => [l, 0]));
  const countries: Record<string, number> = {};
  const states: Record<string, number> = {};
  const userLevels = [0, 0, 0, 0];
  const memberLevels = [0, 0, 0];
  const add = (m: Record<string, number>, k: unknown) => {
    const key = typeof k === "string" && k.trim() ? k.trim().slice(0, 60) : "Not set";
    m[key] = (m[key] ?? 0) + 1;
  };
  users.forEach((d) => {
    add(languages, d.get("language"));
    add(countries, d.get("country"));
    add(states, d.get("state"));
    const ul = Number(d.get("userLevel") ?? 1);
    if (ul >= 1 && ul <= 4) userLevels[ul - 1]++;
    const ml = Number(d.get("memberLevel") ?? 0);
    if (ml >= 1 && ml <= 3) memberLevels[ml - 1]++;
  });
  return { paid, languages, countries: top(countries, 15), states: top(states, 15), userLevels, memberLevels };
}

/** The [n] largest entries; the rest are added up under "Other". */
function top(m: Record<string, number>, n: number): Record<string, number> {
  const sorted = Object.entries(m).sort((a, b) => b[1] - a[1]);
  const out = Object.fromEntries(sorted.slice(0, n));
  const rest = sorted.slice(n).reduce((a, [, v]) => a + v, 0);
  if (rest > 0) out.Other = (out.Other ?? 0) + rest;
  return out;
}

/** Scheduled just after midnight: yesterday's numbers, with who is paid and where members are from. */
export async function writeDailyStats(db: Firestore, now: Date): Promise<string> {
  const day = daysBefore(istDay(now), 1);
  const stats: DayStats = { ...(await computeDay(db, day)), snapshot: await computeSnapshot(db, now) };
  await db.doc(`stats/${day}`).set({ ...stats, computedAt: Timestamp.fromDate(now) });
  return day;
}

/** Admin: today's numbers so far (not stored), with the latest member breakdown. */
export async function todayStatsFor(db: Firestore, token: Claims, now: Date): Promise<DayStats> {
  requireAdmin(token);
  return { ...(await computeDay(db, istDay(now))), snapshot: await computeSnapshot(db, now) };
}

/**
 * Admin: counts the past [days] days again and stores them, for days the dashboard has no numbers for yet
 * (it never overwrites a stored day). AI questions are only known from when the counting started.
 */
export async function backfillStatsFor(db: Firestore, token: Claims, input: { days?: unknown }, now: Date): Promise<{ filled: string[] }> {
  requireAdmin(token);
  const days = Math.min(90, Math.max(1, Math.floor(Number(input?.days ?? 30)) || 30));
  const today = istDay(now);
  const filled: string[] = [];
  for (let i = 1; i <= days; i++) {
    const day = daysBefore(today, i);
    const ref = db.doc(`stats/${day}`);
    if ((await ref.get()).exists) continue;
    await ref.set({ ...(await computeDay(db, day)), computedAt: Timestamp.fromDate(now), rebuilt: true });
    filled.push(day);
  }
  return { filled };
}

/** How many people use the app and how many are members (a membership level), for Swamiji and admins on Home. */
export async function userCountsFor(db: Firestore, token: Claims): Promise<{ users: number; members: number }> {
  if (token?.role !== "admin" && token?.role !== "swamiji") throw new HttpsError("permission-denied", "Swamiji and admins only");
  const users = db.collection("users");
  const [all, members] = await Promise.all([count(users), count(users.where("memberLevel", ">=", 1))]);
  return { users: all, members };
}
