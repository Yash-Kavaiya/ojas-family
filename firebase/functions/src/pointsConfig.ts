import type { Firestore } from "firebase-admin/firestore";

/**
 * How many Moksha Points each activity earns, and the rules for sending points to another member.
 * Admins and Swamiji change these in Firestore config/points without an app release; anything not
 * set there falls back to these defaults. The app's "How Moksha Points work" screen reads the same
 * document, so what members are told always matches what the server gives.
 */
export interface PointsConfig {
  dailyOpen: number;
  practice: number; // finishing one guided practice (each of the four, once a day)
  allPracticesBonus: number; // all four guided practices on the same IST day
  streakBonus: number; // every streakBonusEvery days in a row with a practice
  streakBonusEvery: number;
  like: number;
  comment: number;
  question: number;
  answer: number;
  profileField: number; // each profile item filled in, once
  profileComplete: number; // bonus at 100%, once
  aiDaily: number; // first Ask Swamiji AI question of the day
  transfersEnabled: boolean;
  maxTransfer: number; // largest single gift a member may send
  dailySendCap: number; // most a member may send in one IST day
  minLevelToSend: number; // members below this Level can receive but not send
}

export const DEFAULT_POINTS: PointsConfig = {
  dailyOpen: 1,
  practice: 2,
  allPracticesBonus: 3,
  streakBonus: 10,
  streakBonusEvery: 7,
  like: 1,
  comment: 1,
  question: 1,
  answer: 1,
  profileField: 5,
  profileComplete: 25,
  aiDaily: 1,
  transfersEnabled: true,
  maxTransfer: 100,
  dailySendCap: 100,
  minLevelToSend: 2,
};

const NUMBER_KEYS = (Object.keys(DEFAULT_POINTS) as (keyof PointsConfig)[]).filter((k) => typeof DEFAULT_POINTS[k] === "number");

/** Keeps admin-entered values sane: whole numbers from 0 to 100000; booleans stay booleans. */
export function cleanPointsConfig(raw: Record<string, unknown> | undefined): PointsConfig {
  const out: PointsConfig = { ...DEFAULT_POINTS };
  const data = raw ?? {};
  for (const k of NUMBER_KEYS) {
    const v = Number(data[k]);
    if (data[k] !== undefined && Number.isFinite(v)) (out as unknown as Record<string, number>)[k] = Math.max(0, Math.min(100000, Math.floor(v)));
  }
  if (typeof data.transfersEnabled === "boolean") out.transfersEnabled = data.transfersEnabled;
  if (out.streakBonusEvery < 1) out.streakBonusEvery = DEFAULT_POINTS.streakBonusEvery;
  return out;
}

export async function loadPointsConfig(db: Firestore): Promise<PointsConfig> {
  return cleanPointsConfig((await db.doc("config/points").get()).data());
}
