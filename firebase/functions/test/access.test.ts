import { Timestamp } from "firebase-admin/firestore";
import { aiDailyLimit, allows, qaDailyLimit, RULES } from "../src/access";

const NOW = new Date("2026-11-20T06:00:00Z");
const ago = (days: number) => Timestamp.fromMillis(NOW.getTime() - days * 86_400_000);
const free = (days: number) => ({ createdAt: ago(days) });
const plan = (p: string, days: number, joinedDays = days) => ({ createdAt: ago(joinedDays), plan: p, planSince: ago(days), planUntil: ago(-30) });

describe("the plan table", () => {
  test("free members open things with time; Silver and Gold sooner", () => {
    expect(allows(free(6), "member", RULES.like, NOW)).toBe(false);
    expect(allows(free(7), "member", RULES.like, NOW)).toBe(true);
    expect(allows(plan("silver", 0), "member", RULES.like, NOW)).toBe(true);
    expect(allows(free(1000), "member", RULES.activityRead, NOW)).toBe(false);
    expect(allows(plan("silver", 181), "member", RULES.activityRead, NOW)).toBe(false);
    expect(allows(plan("silver", 182), "member", RULES.activityRead, NOW)).toBe(true);
    expect(allows(plan("gold", 7), "member", RULES.activityRead, NOW)).toBe(true);
    expect(allows(plan("gold", 364), "member", RULES.activityPost, NOW)).toBe(false);
    expect(allows(plan("gold", 365), "member", RULES.activityPost, NOW)).toBe(true);
  });

  test("a member gets the better of their own time and their plan", () => {
    // Joined 200 days ago, Silver since today: replies are open through their own time
    expect(allows(plan("silver", 0, 200), "member", RULES.replyThought, NOW)).toBe(true);
  });

  test("a plan that ran out counts for nothing", () => {
    const lapsed = { createdAt: ago(1), plan: "gold", planSince: ago(400), planUntil: ago(1) };
    expect(allows(lapsed, "member", RULES.like, NOW)).toBe(false);
  });

  test("Swamiji and admins have everything; moderators follow their plan", () => {
    expect(allows(free(0), "swamiji", RULES.activityPost, NOW)).toBe(true);
    expect(allows(free(0), "admin", RULES.hideProfile, NOW)).toBe(true);
    expect(allows(free(0), "moderator", RULES.like, NOW)).toBe(false);
  });

  test("Swamiji AI questions a day", () => {
    expect(aiDailyLimit(free(0), "member", NOW)).toBe(5);
    expect(aiDailyLimit(free(182), "member", NOW)).toBe(50);
    expect(aiDailyLimit(free(365), "member", NOW)).toBe(100);
    expect(aiDailyLimit(plan("silver", 0), "member", NOW)).toBe(50);
    expect(aiDailyLimit(plan("silver", 400), "member", NOW)).toBe(100);
    expect(aiDailyLimit(plan("gold", 365), "member", NOW)).toBe(Number.POSITIVE_INFINITY);
    expect(aiDailyLimit(free(0), "swamiji", NOW)).toBe(Number.POSITIVE_INFINITY);
  });

  test("Q&A questions a day", () => {
    expect(qaDailyLimit(free(100), "member", NOW)).toBe(0);
    expect(qaDailyLimit(free(182), "member", NOW)).toBe(1);
    expect(qaDailyLimit(plan("silver", 7), "member", NOW)).toBe(1);
    expect(qaDailyLimit(free(365), "member", NOW)).toBe(5);
    expect(qaDailyLimit(plan("gold", 182), "member", NOW)).toBe(5);
    expect(qaDailyLimit(plan("gold", 365), "member", NOW)).toBe(10);
  });
});
