import { Timestamp } from "firebase-admin/firestore";
import { ensureUserDoc } from "../src/auth";
import { adminGrantFor, countPaidDay, memberLevelFor, verifyPurchaseFor, type PlayApi } from "../src/billing";
import { onCallUpdated, onChatMessage } from "../src/chat";
import { onAnswerNotify, onCommentLikeChanged, onDailyReplyWritten, onQuestionNotify, onThoughtCommentNotify } from "../src/community";
import { onGoalProgressWritten } from "../src/goals";
import { wantsPersonal, type Messenger } from "../src/notify";
import { dayBefore, logPracticeFor, streak } from "../src/practice";
import { publicProfileOf, refreshAuthorSnapshots, syncPublicProfile } from "../src/profiles";
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

describe("author snapshots", () => {
  test("a new photo or name reaches every post, comment, answer and reply", async () => {
    await db.doc("questions/q9").set({ authorUid: "pic", authorName: "Old", authorPhoto: "old" });
    await db.doc("questions/q9/answers/a1").set({ authorUid: "pic", authorName: "Old", authorPhoto: "old" });
    await db.doc("thoughts/2026-11-07/comments/c1").set({ authorUid: "pic", authorName: "Old", authorPhoto: "old" });
    await db.doc("dailyPosts/d9/replies/r1").set({ authorUid: "pic", authorName: "Old", authorPhoto: "old" });
    await db.doc("questions/q8").set({ authorUid: "someoneElse", authorName: "X", authorPhoto: "x" });
    const n = await refreshAuthorSnapshots(db, "pic", { displayName: "Old", photoUrl: "old" }, { displayName: "New", photoUrl: "new" });
    expect(n).toBe(4);
    expect((await db.doc("thoughts/2026-11-07/comments/c1").get()).get("authorPhoto")).toBe("new");
    expect((await db.doc("dailyPosts/d9/replies/r1").get()).get("authorName")).toBe("New");
    expect((await db.doc("questions/q8").get()).get("authorPhoto")).toBe("x");
    expect(await refreshAuthorSnapshots(db, "pic", { displayName: "New", photoUrl: "new" }, { displayName: "New", photoUrl: "new", mokshaPoints: 5 })).toBe(0);
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

  test("a reply to a comment tells that comment's author, not the replier", async () => {
    await db.doc("thoughts/t1/comments/c1").set({ authorUid: "m1", text: "Jai" });
    await db.doc("users/m1").set({ fcmTokens: ["tok-m1"] }, { merge: true });
    await db.doc("users/m2").set({ fcmTokens: ["tok-m2"] }, { merge: true });
    const { m, sent } = fakeMessenger();
    await onThoughtCommentNotify(db, m, "t1", undefined, { status: "visible", authorUid: "m2", authorName: "Amit", text: "So true", parentId: "c1" });
    expect(sent.map((s) => s.to)).toEqual(["tok-m1"]);
    expect(sent[0].data.type).toBe("commentReply");

    const self = fakeMessenger();
    await onThoughtCommentNotify(db, self.m, "t1", undefined, { status: "visible", authorUid: "m1", text: "Thanks", parentId: "c1" });
    expect(self.sent).toHaveLength(0);
  });

  test("replying to an answer tells its author once, and the asker separately", async () => {
    await db.doc("questions/q2").set({ authorUid: "asker" });
    await db.doc("questions/q2/answers/a1").set({ authorUid: "m1", text: "Sit daily" });
    for (const u of ["asker", "m1"]) await db.doc(`users/${u}`).set({ fcmTokens: [`tok-${u}`] }, { merge: true });
    const { m, sent } = fakeMessenger();
    await onAnswerNotify(db, m, "q2", undefined, { status: "visible", authorUid: "m2", text: "Agreed", parentId: "a1" });
    expect(sent.map((s) => [s.to, s.data.type])).toEqual([["tok-m1", "commentReply"], ["tok-asker", "answer"]]);
  });
});

describe("comment likes", () => {
  test("likes on a comment count up and down, never below zero", async () => {
    await db.doc("thoughts/t9/comments/c1").set({ authorUid: "m1", text: "Jai" });
    await onCommentLikeChanged(db, "thoughts/t9/comments/c1", 1);
    await onCommentLikeChanged(db, "thoughts/t9/comments/c1", 1);
    expect((await db.doc("thoughts/t9/comments/c1").get()).get("likeCount")).toBe(2);
    await onCommentLikeChanged(db, "thoughts/t9/comments/c1", -1);
    await onCommentLikeChanged(db, "thoughts/t9/comments/c1", -1);
    await onCommentLikeChanged(db, "thoughts/t9/comments/c1", -1);
    expect((await db.doc("thoughts/t9/comments/c1").get()).get("likeCount")).toBe(0);
  });

  test("a like on a removed comment does not bring it back", async () => {
    await onCommentLikeChanged(db, "questions/q9/answers/gone", 1);
    expect((await db.doc("questions/q9/answers/gone").get()).exists).toBe(false);
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

describe("admin website helpers", () => {
  const { applyAdminList, givePointsFor } = require("../src/admin");

  test("a verified email on the admin list becomes admin once", async () => {
    await db.doc("config/admins").set({ emails: ["Boss@Example.com"] });
    const claims: Record<string, unknown>[] = [];
    const set = async (_: string, c: Record<string, unknown>) => { claims.push(c); };
    expect(await applyAdminList(db, "m1", "boss@example.com", false, "member", set)).toBe(false);
    expect(await applyAdminList(db, "m1", "boss@example.com", true, "member", set)).toBe(true);
    expect(await applyAdminList(db, "m1", "boss@example.com", true, "admin", set)).toBe(false);
    expect(claims).toEqual([{ role: "admin" }]);
    expect((await db.doc("users/m1").get()).get("role")).toBe("admin");
    expect(await applyAdminList(db, "m2", "other@example.com", true, "member", set)).toBe(false);
  });

  test("admins gift points to several members; others cannot", async () => {
    const r = await givePointsFor(db, ADMIN, { uids: ["m1", "m2", "nobody"], amount: 25 });
    expect(r.given).toBe(2);
    expect((await db.doc("users/m2").get()).get("mokshaPoints")).toBe(25);
    await expect(givePointsFor(db, { role: "member" }, { uids: ["m1"], amount: 5 })).rejects.toThrow(/Admins/);
  });
});

describe("admin team chat, call log and scheduled calls", () => {
  test("an Admin-team message notifies every admin and shows a file preview", async () => {
    const { m, sent } = fakeMessenger();
    await db.doc("users/a1").set({ role: "admin", fcmTokens: ["tA1"] });
    await db.doc("users/a2").set({ role: "admin", fcmTokens: ["tA2"] });
    await db.doc("users/sw").set({ role: "swamiji", fcmTokens: ["tSw"] });
    await onChatMessage(db, m, "m1", { senderUid: "m1", text: "", attachment: { mime: "application/pdf", name: "report.pdf" } }, "admin");
    expect(sent.map((s) => s.to).sort()).toEqual(["tA1", "tA2"]);
    expect((await db.doc("adminChats/m1").get()).get("lastMessage")).toBe("📄 report.pdf");
  });

  test("Member Level 2 calls without using a pack; the end writes a call log", async () => {
    await db.doc("users/m1").set({ memberLevel: 2 }, { merge: true });
    await db.doc("users/m1/entitlements/call").set({ expiresAt: Timestamp.fromMillis(NOW.getTime() + DAY), credits: 3 });
    await db.doc("calls/c9").set({ memberUid: "m1", callerUid: "m1", channel: "admin", video: true, status: "active" });
    await onCallUpdated(db, "c9", { status: "ringing" }, { status: "active", memberUid: "m1" });
    expect((await db.doc("users/m1/entitlements/call").get()).get("credits")).toBe(3);
    await db.doc("calls/c9").set({ status: "ended", startedAt: Timestamp.fromMillis(1000), endedAt: Timestamp.fromMillis(61_000) }, { merge: true });
    await onCallUpdated(db, "c9", { status: "active" }, { status: "ended", memberUid: "m1" });
    const logs = await db.collection("adminChats/m1/messages").get();
    expect(logs.size).toBe(1);
    expect(logs.docs[0].get("seconds")).toBe(60);
    expect(logs.docs[0].get("video")).toBe(true);
    await onCallUpdated(db, "c9", { status: "active" }, { status: "ended", memberUid: "m1", logged: true });
    expect((await db.collection("adminChats/m1/messages").get()).size).toBe(1);
  });

  test("a request pushes staff; confirming pushes the member", async () => {
    const { onCallRequestWritten } = await import("../src/schedule");
    const { m, sent } = fakeMessenger();
    await db.doc("users/sw").set({ role: "swamiji", fcmTokens: ["tSw"] });
    await db.doc("users/m1").set({ fcmTokens: ["tM1"] }, { merge: true });
    const req = { memberUid: "m1", memberName: "M", channel: "swamiji", video: false, at: Timestamp.fromMillis(NOW.getTime() + DAY), status: "pending" };
    await onCallRequestWritten(db, m, "r1", undefined, req);
    expect(sent.map((s) => s.to)).toEqual(["tSw"]);
    await onCallRequestWritten(db, m, "r1", req, { ...req, status: "confirmed", staffName: "Swamiji" });
    expect(sent[1]).toMatchObject({ to: "tM1", data: { type: "callRequest_confirmed" } });
  });

  test("reminders go once 15 minutes before and once at the start", async () => {
    const { reminderDue, sendCallReminders } = await import("../src/schedule");
    expect(reminderDue(20 * 60_000, 0, false, false)).toBeNull();
    expect(reminderDue(14 * 60_000, 0, false, false)).toBe("soon");
    expect(reminderDue(14 * 60_000, 0, true, false)).toBeNull();
    expect(reminderDue(30_000, 0, true, false)).toBe("now");
    expect(reminderDue(-10 * 60_000, 0, true, false)).toBeNull();

    const { m, sent } = fakeMessenger();
    await db.doc("users/m1").set({ fcmTokens: ["tM1"] }, { merge: true });
    await db.doc("users/sw").set({ role: "swamiji", fcmTokens: ["tSw"] });
    await db.doc("callRequests/r2").set({ memberUid: "m1", channel: "swamiji", status: "confirmed", staffUid: "sw", at: Timestamp.fromMillis(NOW.getTime() + 10 * 60_000) });
    expect(await sendCallReminders(db, m, NOW)).toBe(1);
    expect(await sendCallReminders(db, m, NOW)).toBe(0);
    expect(sent.map((s) => s.to).sort()).toEqual(["tM1", "tSw"]);
    expect(await sendCallReminders(db, m, new Date(NOW.getTime() + 10 * 60_000))).toBe(1);
    expect((await db.doc("callRequests/r2").get()).get("remindedStart")).toBe(true);
  });
});
