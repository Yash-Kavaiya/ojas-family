import { Timestamp } from "firebase-admin/firestore";

/**
 * Membership plans and what each opens, and when (owner's plan table, 2026-11-20). The same table
 * lives in the app (domain/PlanAccess.kt) and in firestore.rules (allowed(u, s, g)); keep all three
 * in step. A free member's time counts from users/{uid}.createdAt; a Silver or Gold member's from
 * planSince while planUntil is ahead. A member gets the better of the two. Swamiji and admins have
 * everything; moderators follow their own plan.
 */
export type Plan = "user" | "silver" | "gold";

/** Days on each plan before something opens; null means that plan never gets it. */
export interface AccessRule { user: number | null; silver: number | null; gold: number | null }

const WEEK = 7;
export const HALF_YEAR = 182;
export const YEAR = 365;

export const RULES = {
  like: { user: WEEK, silver: 0, gold: 0 },
  replyThought: { user: HALF_YEAR, silver: WEEK, gold: WEEK },
  quickProtection: { user: HALF_YEAR, silver: 0, gold: 0 },
  replyAnnouncement: { user: HALF_YEAR, silver: WEEK, gold: 0 },
  replyQa: { user: HALF_YEAR, silver: WEEK, gold: WEEK },
  askText: { user: HALF_YEAR, silver: WEEK, gold: WEEK },
  askMedia: { user: YEAR, silver: YEAR, gold: HALF_YEAR },
  activityRead: { user: null, silver: HALF_YEAR, gold: WEEK },
  activityReply: { user: null, silver: YEAR, gold: HALF_YEAR },
  activityPost: { user: null, silver: null, gold: YEAR },
  dailyPractice: { user: null, silver: WEEK, gold: 0 },
  healingPhoto: { user: null, silver: YEAR, gold: HALF_YEAR },
  hideProfile: { user: null, silver: null, gold: YEAR },
  // Paid chat and calls (bought separately): with the team from day 1, with Swamiji after 6 months
  withSwamiji: { user: HALF_YEAR, silver: HALF_YEAR, gold: HALF_YEAR },
} satisfies Record<string, AccessRule>;

const DAY_MS = 86_400_000;

export const normalizeRole = (role: unknown): string => (role === "senior_member" ? "moderator" : String(role ?? "member"));

/** Swamiji and admins: every feature, no limits. */
export function hasFullAccess(role: unknown): boolean {
  return role === "admin" || role === "swamiji";
}

function millis(v: unknown): number | null {
  if (v instanceof Timestamp) return v.toMillis();
  if (v instanceof Date) return v.getTime();
  return null;
}

function daysSince(v: unknown, now: Date): number {
  const ms = millis(v);
  return ms === null || now.getTime() < ms ? 0 : Math.floor((now.getTime() - ms) / DAY_MS);
}

/** The paid plan running now, or "user". */
export function activePlan(user: Record<string, unknown>, now: Date): Plan {
  const plan = user.plan;
  const until = millis(user.planUntil);
  return (plan === "silver" || plan === "gold") && until !== null && until > now.getTime() ? plan : "user";
}

export function allows(user: Record<string, unknown>, role: unknown, rule: AccessRule, now: Date): boolean {
  if (hasFullAccess(role)) return true;
  if (rule.user !== null && daysSince(user.createdAt, now) >= rule.user) return true;
  const plan = activePlan(user, now);
  if (plan === "user") return false;
  const need = rule[plan];
  return need !== null && daysSince(user.planSince, now) >= need;
}

type Steps = Record<Plan, Array<[days: number, perDay: number]>>;

const AI_STEPS: Steps = {
  user: [[0, 5], [HALF_YEAR, 50], [YEAR, 100]],
  silver: [[0, 50], [HALF_YEAR, 100]],
  gold: [[0, 50], [HALF_YEAR, 100], [YEAR, Number.POSITIVE_INFINITY]],
};

function bestStep(user: Record<string, unknown>, steps: Steps, now: Date): number {
  const pick = (list: Array<[number, number]>, days: number) => list.filter(([d]) => days >= d).pop()?.[1] ?? 0;
  const free = pick(steps.user, daysSince(user.createdAt, now));
  const plan = activePlan(user, now);
  const paid = plan === "user" ? 0 : pick(steps[plan], daysSince(user.planSince, now));
  return Math.max(free, paid);
}

/** Questions to Swamiji AI a day; Infinity for no limit. */
export function aiDailyLimit(user: Record<string, unknown>, role: unknown, now: Date): number {
  return hasFullAccess(role) ? Number.POSITIVE_INFINITY : bestStep(user, AI_STEPS, now);
}

/** Questions a member may post in Q&A a day: 1 with words only, 5 once media opens, 10 for Gold after a year. */
export function qaDailyLimit(user: Record<string, unknown>, role: unknown, now: Date): number {
  if (hasFullAccess(role)) return Number.POSITIVE_INFINITY;
  if (activePlan(user, now) === "gold" && daysSince(user.planSince, now) >= YEAR) return 10;
  if (allows(user, role, RULES.askMedia, now)) return 5;
  if (allows(user, role, RULES.askText, now)) return 1;
  return 0;
}
