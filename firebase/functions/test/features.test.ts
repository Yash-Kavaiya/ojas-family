import { Timestamp } from "firebase-admin/firestore";
import { ensureUserDoc } from "../src/auth";
import { adminGrantFor, countPaidDay, memberLevelFor, verifyPurchaseFor, type PlayApi } from "../src/billing";
import { onCallUpdated, onChatMessage } from "../src/chat";
import { onAnswerNotify, onDailyReplyWritten, onQuestionNotify } from "../src/community";
import { onGoalProgressWritten } from "../src/goals";
import { wantsPersonal, type Messenger } from "../src/notify";
import { dayBefore, logPracticeFor, streak } from "../src/practice";
import { publicProfileOf, syncPublicProfile } from "../src/profiles";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const NOW = new Date("2026-11-07T06:00:00Z"); // 11:30 IST
const DAY = 24 * 3600 * 1000;
const ADMIN = { role: "admin" };

function fakeMessenger() {
  const sent: Array<{ to: string; data: Record<string, string> }> = [];
  const m: Messenger = {
    async sendToTopic(topic, data) { sent.push({ to: `topic:${topic}`, data }); },
    async sendToTokens(tokens, data) { tokens.forEach((t) => sent.push({ to: t, data })); return []; },
  };
  return { m, sent };
}

beforeEach(async () => {
  await clearDb(db);
  for (const uid of ["m1", "m2", "staff"]) await ensureUserDoc(db, uid);
});

describe("practice streaks", () => {
  test("dayBefore crosses months", () => {
    expect(dayBefore("2026-03-01", 1)).toBe("2026-02-28");
  });

  test("streak counts back from today and stops at a gap", () => {
    const logs = { "2026-11-07": { morning: true }, "2026-11-06": { morning: true }, "2026-11-04": { morning: true } };
    expect(streak(logs, "2026-11-07", (l) => l.morning === true)).toBe(2);
  });

  test("free Level 1 user cannot log a practice", async () => {
    await expect(logPracticeFor(db, "m1", "member", "morning", NOW)).rejects.toThrow(/Level 4/);
  });

  test("21 mornings in a row complete the morning goal and award it once", async () => {
    await db.doc("users/m1").set({ memberLevel: 1 }, { merge: true });
    await db.doc("goals/morning_21").set({ kind: "practice_morning_21", reward: 50 });
    for (let i = 20; i >= 1; i--) {
      await db.doc(`users/m1/practiceLog/${dayBefore("2026-11-07", i)}`).set({ morning: true });
    }
    const r = await logPracticeFor(db, "m1", "member", "morning", NOW);
    expect(r.morningStreak).toBe(21);
    expect(r.completed).toEqual(["morning_21"]);
    const progress = await db.doc("users/m1/goalProgress/morning_21").get();
    expect(progress.get("status")).toBe("approved");
    // The trigger would run next; run it by hand and check the reward
    await onGoalProgressWritten(db, "m1", "morning_21", undefined, { status: "approved" });
    expect((await db.doc("users/m1").get()).get("mokshaPoints")).toBe(50);
    expect((await db.doc("users/m1").get()).get("achievedGoalIds")).toEqual(["morning_21"]);
    const again = await logPracticeFor(db, "m1", "member", "morning", NOW);
    expect(again.completed).toEqual([]);
  });
});

describe("membership", () => {
  test.each([[0, true, 1], [89, true, 1], [90, true, 2], [180, true, 3], [400, false, 0]])(
    "paidDays %i active %s -> member level %i", (days, active, level) => {
      expect(memberLevelFor(days as number, active as boolean)).toBe(level);
    });

  test("admin grant makes a member Level 1 and the daily tick counts paid days", async () => {
    await adminGrantFor(db, ADMIN, { uid: "m1", entitlement: "membership", days: 30 }, NOW);
    expect((await db.doc("users/m1").get()).get("memberLevel")).toBe(1);
    await db.doc("users/m1").set({ paidDays: 89 }, { merge: true });
    await countPaidDay(db, NOW);
    expect((await db.doc("users/m1").get()).get("memberLevel")).toBe(2);
  });

  test("lapsed membership drops the member level but keeps paid days", async () => {
    await adminGrantFor(db, ADMIN, { uid: "m1", entitlement: "membership", days: 1 }, NOW);
    await db.doc("users/m1").set({ paidDays: 100 }, { merge: true });
    await countPaidDay(db, new Date(NOW.getTime() + 2 * DAY));
    const u = (await db.doc("users/m1").get()).data()!;
    expect(u.memberLevel).toBe(0);
    expect(u.paidDays).toBe(100);
  });

  test("only admins grant", async () => {
    await expect(adminGrantFor(db, { role: "member" }, { uid: "m1", entitlement: "chat", days: 7 }, NOW)).rejects.toThrow(/admins/);
  });

  test("a weekly pack extends from the current expiry", async () => {
    await adminGrantFor(db, ADMIN, { uid: "m1", entitlement: "chat", days: 7 }, NOW);
    const r = await adminGrantFor(db, ADMIN, { uid: "m1", entitlement: "chat", days: 7 }, NOW);
    expect(new Date(r.expiresAt).getTime()).toBe(NOW.getTime() + 14 * DAY);
  });
});

describe("Play purchases", () => {
  const api = (over: Partial<PlayApi> = {}): PlayApi & { consumed: number } => {
    const a = {
      consumed: 0,
      async getSubscription() { return { productId: "membership_monthly", expiry: new Date(NOW.getTime() + 30 * DAY), active: true, acknowledged: false }; },
      async getProduct() { return { purchased: true, consumed: false }; },
      async acknowledgeSubscription() {},
      async consumeProduct() { a.consumed++; },
      ...over,
    };
    return a;
  };

  test("subscription grants membership until Google's expiry", async () => {
    const r = await verifyPurchaseFor(db, api(), "m1", { productId: "membership_monthly", purchaseToken: "token-abcdef-1" }, NOW);
    expect(r.entitlement).toBe("membership");
    expect((await db.doc("users/m1").get()).get("memberLevel")).toBe(1);
  });

  test("a token claimed by one account cannot unlock another", async () => {
    await verifyPurchaseFor(db, api(), "m1", { productId: "chat_week", purchaseToken: "token-abcdef-2" }, NOW);
    await expect(verifyPurchaseFor(db, api(), "m2", { productId: "chat_week", purchaseToken: "token-abcdef-2" }, NOW))
      .rejects.toThrow(/another account/);
  });

  test("retrying a weekly pack grants it once and consumes it once", async () => {
    const a = api();
    const first = await verifyPurchaseFor(db, a, "m1", { productId: "call_week", purchaseToken: "token-abcdef-3" }, NOW);
    const second = await verifyPurchaseFor(db, a, "m1", { productId: "call_week", purchaseToken: "token-abcdef-3" }, NOW);
    expect(second.expiresAt).toBe(first.expiresAt);
    expect(a.consumed).toBe(1);
    expect((await db.doc("users/m1/entitlements/call").get()).get("credits")).toBe(4);
  });
});

describe("public profiles", () => {
  test("only the birth month is shared, and hidden goals are not listed", () => {
    const p = publicProfileOf({ displayName: "A", dob: "1990-05-17", hideGoals: true, achievedGoalIds: ["tree"] });
    expect(p.birthMonth).toBe("1990-05");
    expect(p.achievedGoals).toEqual([]);
    expect(JSON.stringify(p)).not.toContain("1990-05-17");
  });

  test("sync writes and deletes the mirror", async () => {
    await syncPublicProfile(db, "m1", { displayName: "A", hideProfile: true });
    expect((await db.doc("publicProfiles/m1").get()).get("hidden")).toBe(true);
    await syncPublicProfile(db, "m1", undefined);
    expect((await db.doc("publicProfiles/m1").get()).exists).toBe(false);
  });
});

describe("notifications", () => {
  test("master switch silences everything; categories default on", () => {
    expect(wantsPersonal({ all: false }, "chat")).toBe(false);
    expect(wantsPersonal({ communityMentions: false }, "communityMentions")).toBe(false);
    expect(wantsPersonal({ communityMentions: false }, "chat")).toBe(true);
    expect(wantsPersonal(undefined, "dailyReplies")).toBe(true);
  });

  test("a new visible question goes to the community topic and to mentioned people", async () => {
    await db.doc("users/m2").set({ fcmTokens: ["tok-m2"] }, { merge: true });
    const { m, sent } = fakeMessenger();
    await onQuestionNotify(db, m, "q1", undefined, { status: "visible", authorUid: "m1", authorName: "A", title: "Why?", mentions: ["m2"] });
    expect(sent.map((s) => s.to)).toEqual(["topic:communityPosts", "tok-m2"]);
    expect(sent[1].data.category).toBe("communityMentions");
  });

  test("a pending question is announced only when approved", async () => {
    const { m, sent } = fakeMessenger();
    await onQuestionNotify(db, m, "q1", undefined, { status: "pending", authorUid: "m1", title: "x" });
    expect(sent).toHaveLength(0);
  });

  test("an answer tells the asker unless they turned interactions off", async () => {
    await db.doc("questions/q1").set({ authorUid: "m1" });
    await db.doc("users/m1").set({ fcmTokens: ["tok-m1"], notificationPrefs: { communityInteractions: false } }, { merge: true });
    const { m, sent } = fakeMessenger();
    await onAnswerNotify(db, m, "q1", undefined, { status: "visible", authorUid: "m2", text: "Because" });
    expect(sent).toHaveLength(0);
  });

  test("a daily reply counts and tells the post's author", async () => {
    await db.doc("dailyPosts/p1").set({ authorUid: "m1", replyCount: 0 });
    await db.doc("users/m1").set({ fcmTokens: ["tok-m1"] }, { merge: true });
    const { m, sent } = fakeMessenger();
    await onDailyReplyWritten(db, m, "p1", undefined, { status: "visible", authorUid: "m2", text: "Lovely" });
    expect((await db.doc("dailyPosts/p1").get()).get("replyCount")).toBe(1);
    expect(sent[0].data.category).toBe("dailyReplies");
  });
});

describe("chat and calls", () => {
  test("a member's message reaches Swamiji and marks the chat unread for staff", async () => {
    await db.doc("users/staff").set({ role: "swamiji", fcmTokens: ["tok-staff"] }, { merge: true });
    const { m, sent } = fakeMessenger();
    await onChatMessage(db, m, "m1", { senderUid: "m1", text: "Pranam" });
    const chat = (await db.doc("chats/m1").get()).data()!;
    expect(chat.unreadByStaff).toBe(true);
    expect(chat.lastMessage).toBe("Pranam");
    expect(sent.map((s) => s.to)).toEqual(["tok-staff"]);
  });

  test("an assigned senior member gets the message instead of Swamiji", async () => {
    await db.doc("users/staff").set({ role: "swamiji", fcmTokens: ["tok-staff"] }, { merge: true });
    await db.doc("users/m2").set({ role: "senior_member", fcmTokens: ["tok-senior"] }, { merge: true });
    await db.doc("chats/m1").set({ memberUid: "m1", assigneeUid: "m2" });
    const { m, sent } = fakeMessenger();
    await onChatMessage(db, m, "m1", { senderUid: "m1", text: "Hi" });
    expect(sent.map((s) => s.to)).toEqual(["tok-senior"]);
  });

  test("a connected call uses one credit from a weekly pack, once", async () => {
    await db.doc("users/m1/entitlements/call").set({ expiresAt: Timestamp.fromDate(new Date(NOW.getTime() + DAY)), credits: 4 });
    await db.doc("calls/c1").set({ memberUid: "m1", status: "active" });
    await onCallUpdated(db, "c1", { status: "ringing" }, { status: "active", memberUid: "m1" });
    await onCallUpdated(db, "c1", { status: "ringing" }, { status: "active", memberUid: "m1" });
    expect((await db.doc("users/m1/entitlements/call").get()).get("credits")).toBe(3);
  });
});
