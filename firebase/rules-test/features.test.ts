import { readFileSync } from "fs";
import { resolve } from "path";
import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from "firebase/firestore";

let env: RulesTestEnvironment;
const FUTURE = Timestamp.fromDate(new Date(Date.now() + 7 * 24 * 3600 * 1000));
const PAST = Timestamp.fromDate(new Date(Date.now() - 1000));

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-ojas-features",
    firestore: { rules: readFileSync(resolve(__dirname, "../firestore.rules"), "utf8") },
  });
});

afterAll(async () => env.cleanup());

const users: Record<string, Record<string, unknown>> = {
  free: { displayName: "Free", userLevel: 3, memberLevel: 0, role: "member" },
  paid: { displayName: "Paid", userLevel: 2, memberLevel: 1, role: "member" },
  m3: { displayName: "M3", userLevel: 2, memberLevel: 3, role: "member" },
  sw: { displayName: "Swamiji", role: "swamiji" },
  senior: { displayName: "Senior", role: "senior_member" },
  adm: { displayName: "Admin", role: "admin" },
  m1only: { displayName: "M1", userLevel: 1, memberLevel: 1, role: "member" },
};

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [uid, data] of Object.entries(users)) await setDoc(doc(db, "users", uid), data);
    await setDoc(doc(db, "dailyPosts", "p1"), { authorUid: "m3", status: "visible", text: "", imageUrl: "u" });
    await setDoc(doc(db, "users", "paid", "entitlements", "chat"), { expiresAt: FUTURE });
    await setDoc(doc(db, "users", "free", "entitlements", "chat"), { expiresAt: PAST });
  });
});

const as = (uid: string) => env.authenticatedContext(uid, { role: users[uid]?.role ?? "member" }).firestore();

const post = (uid: string, extra: Record<string, unknown> = {}) => ({
  authorUid: uid, authorName: String(users[uid].displayName), authorRole: users[uid].role, text: "Done today", status: "visible", ...extra,
});

describe("daily activities", () => {
  test("free users cannot see daily activities; paid members can", async () => {
    await assertFails(getDoc(doc(as("free"), "dailyPosts", "p1")));
    await assertSucceeds(getDoc(doc(as("paid"), "dailyPosts", "p1")));
  });

  test("only Member Level 3 posts, and a photo is required", async () => {
    await assertFails(setDoc(doc(as("paid"), "dailyPosts", "x"), post("paid", { imageUrl: "https://img" })));
    await assertFails(setDoc(doc(as("m3"), "dailyPosts", "x"), post("m3", { imageUrl: "" })));
    await assertSucceeds(setDoc(doc(as("m3"), "dailyPosts", "x"), post("m3", { imageUrl: "https://img" })));
  });

  test("paid members reply and like", async () => {
    await assertSucceeds(setDoc(doc(as("paid"), "dailyPosts", "p1", "replies", "r1"), post("paid")));
    await assertSucceeds(setDoc(doc(as("paid"), "dailyPosts", "p1", "likes", "paid"), {}));
    await assertFails(setDoc(doc(as("free"), "dailyPosts", "p1", "likes", "free"), {}));
  });
});

describe("images in posts", () => {
  test("a free Level 3 user cannot attach an image; a paid one can, and it waits for review", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "questions", "q1"), { status: "visible", authorUid: "sw" });
      await updateDoc(doc(ctx.firestore(), "users", "paid"), { memberLevel: 2 });
    });
    await assertFails(setDoc(doc(as("free"), "questions", "q1", "answers", "a1"), post("free", { imageUrl: "https://img", status: "pending" })));
    await assertFails(setDoc(doc(as("paid"), "questions", "q1", "answers", "a2"), post("paid", { imageUrl: "https://img" })));
    await assertSucceeds(setDoc(doc(as("paid"), "questions", "q1", "answers", "a3"), post("paid", { imageUrl: "https://img", status: "pending" })));
  });
});

describe("personal chat", () => {
  test("a member with an active chat pack opens a chat and writes", async () => {
    await assertSucceeds(setDoc(doc(as("paid"), "chats", "paid"), { memberUid: "paid", memberName: "Paid" }));
    await assertSucceeds(setDoc(doc(as("paid"), "chats", "paid", "messages", "m1"), { senderUid: "paid", text: "Pranam" }));
  });

  test("an expired pack cannot write", async () => {
    await assertFails(setDoc(doc(as("free"), "chats", "free"), { memberUid: "free", memberName: "Free" }));
  });

  test("other members cannot read a chat; Swamiji can; a senior member only when assigned", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "chats", "paid"), { memberUid: "paid" });
      await setDoc(doc(ctx.firestore(), "chats", "paid", "messages", "m1"), { senderUid: "paid", text: "x" });
    });
    await assertFails(getDoc(doc(as("m3"), "chats", "paid", "messages", "m1")));
    await assertSucceeds(getDoc(doc(as("sw"), "chats", "paid", "messages", "m1")));
    await assertFails(getDoc(doc(as("senior"), "chats", "paid", "messages", "m1")));
    await assertSucceeds(updateDoc(doc(as("sw"), "chats", "paid"), { assigneeUid: "senior", assigneeName: "Senior" }));
    await assertSucceeds(getDoc(doc(as("senior"), "chats", "paid", "messages", "m1")));
    await assertSucceeds(setDoc(doc(as("senior"), "chats", "paid", "messages", "m2"), { senderUid: "senior", text: "Hari Om" }));
  });

  test("a member cannot assign their own chat", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => setDoc(doc(ctx.firestore(), "chats", "paid"), { memberUid: "paid" }));
    await assertFails(updateDoc(doc(as("paid"), "chats", "paid"), { assigneeUid: "paid" }));
  });
});

describe("calls", () => {
  const call = (caller: string, callee: string, member: string, staff: string) =>
    ({ callerUid: caller, calleeUid: callee, memberUid: member, staffUid: staff, status: "ringing", offer: { sdp: "x", type: "offer" } });

  test("a member needs a call pack with calls left", async () => {
    await assertFails(setDoc(doc(as("paid"), "calls", "c1"), call("paid", "sw", "paid", "sw")));
    await env.withSecurityRulesDisabled(async (ctx) =>
      setDoc(doc(ctx.firestore(), "users", "paid", "entitlements", "call"), { expiresAt: FUTURE, credits: 0 }));
    await assertFails(setDoc(doc(as("paid"), "calls", "c1"), call("paid", "sw", "paid", "sw")));
    await env.withSecurityRulesDisabled(async (ctx) =>
      setDoc(doc(ctx.firestore(), "users", "paid", "entitlements", "call"), { expiresAt: FUTURE, credits: 2 }));
    await assertSucceeds(setDoc(doc(as("paid"), "calls", "c1"), call("paid", "sw", "paid", "sw")));
  });

  test("a member can only call staff", async () => {
    await env.withSecurityRulesDisabled(async (ctx) =>
      setDoc(doc(ctx.firestore(), "users", "paid", "entitlements", "call"), { expiresAt: FUTURE }));
    await assertFails(setDoc(doc(as("paid"), "calls", "c1"), call("paid", "m3", "paid", "m3")));
  });

  test("staff can call any member; outsiders cannot read the call", async () => {
    await assertSucceeds(setDoc(doc(as("sw"), "calls", "c2"), call("sw", "free", "free", "sw")));
    await assertSucceeds(getDoc(doc(as("free"), "calls", "c2")));
    await assertFails(getDoc(doc(as("m3"), "calls", "c2")));
    await assertSucceeds(updateDoc(doc(as("free"), "calls", "c2"), { status: "active", answer: { sdp: "y", type: "answer" } }));
    await assertFails(updateDoc(doc(as("free"), "calls", "c2"), { calleeUid: "m3" }));
  });

  test("only the callee's phone marks a call as ringing; both may send a reconnect offer", async () => {
    await assertSucceeds(setDoc(doc(as("sw"), "calls", "c3"), call("sw", "free", "free", "sw")));
    await assertSucceeds(updateDoc(doc(as("free"), "calls", "c3"), { deliveredAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as("sw"), "calls", "c3"), { deliveredAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as("sw"), "calls", "c3"), { restartOffer: { n: 1, sdp: "x" } }));
    await assertSucceeds(updateDoc(doc(as("free"), "calls", "c3"), { restartAnswer: { n: 1, sdp: "y" } }));
  });

  test("a call that is over cannot be answered late or have its outcome rewritten", async () => {
    await assertSucceeds(setDoc(doc(as("sw"), "calls", "c4"), call("sw", "free", "free", "sw")));
    await assertSucceeds(updateDoc(doc(as("sw"), "calls", "c4"), { status: "missed", endedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as("free"), "calls", "c4"), { status: "active", answer: { sdp: "y", type: "answer" } }));
    await assertFails(updateDoc(doc(as("sw"), "calls", "c4"), { status: "ended" }));
    // The phone that rang late may still say so
    await assertSucceeds(updateDoc(doc(as("free"), "calls", "c4"), { deliveredAt: serverTimestamp() }));
  });
});

describe("server-only data", () => {
  test("members cannot write entitlements or practice logs", async () => {
    await assertFails(setDoc(doc(as("free"), "users", "free", "entitlements", "chat"), { expiresAt: FUTURE }));
    await assertFails(setDoc(doc(as("free"), "users", "free", "practiceLog", "2026-11-07"), { morning: true }));
    await assertSucceeds(getDoc(doc(as("paid"), "users", "paid", "entitlements", "chat")));
  });
});

describe("level badges on posts", () => {
  test("a badge may show the author's real level or lower, never higher", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "questions", "q1"), { status: "visible", authorUid: "sw" });
    });
    // "free" is User Level 3, Member Level 0
    await assertSucceeds(setDoc(doc(as("free"), "questions", "q1", "answers", "a1"), post("free", { authorLevel: 3 })));
    await assertFails(setDoc(doc(as("free"), "questions", "q1", "answers", "a2"), post("free", { authorLevel: 4 })));
    await assertFails(setDoc(doc(as("free"), "questions", "q1", "answers", "a3"), post("free", { authorMember: 1 })));
    // "m3" is Member Level 3
    await assertSucceeds(setDoc(doc(as("m3"), "dailyPosts", "x"), post("m3", { imageUrl: "https://img", authorLevel: 2, authorMember: 3 })));
    await assertFails(setDoc(doc(as("m3"), "dailyPosts", "y"), post("m3", { imageUrl: "https://img", authorLevel: "4" })));
  });
});

describe("announcement likes and comments", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "announcements", "n1"), { title: "Hi", body: "" });
    });
  });

  test("members who may like and comment elsewhere may here too", async () => {
    await assertSucceeds(setDoc(doc(as("free"), "announcements", "n1", "likes", "free"), {}));
    await assertFails(setDoc(doc(as("free"), "announcements", "n1", "likes", "paid"), {}));
    await assertSucceeds(setDoc(doc(as("free"), "announcements", "n1", "comments", "c1"), post("free")));
    await assertFails(setDoc(doc(as("free"), "announcements", "n1", "comments", "c2"), post("paid")));
  });
});

describe("hiding a profile", () => {
  test("opens at User Level 3; turning it off always works", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "users", "low"), { displayName: "Low", userLevel: 2, memberLevel: 3, role: "member", hideProfile: false });
      await setDoc(doc(ctx.firestore(), "users", "was"), { displayName: "Was", userLevel: 1, role: "member", hideProfile: true });
    });
    await assertFails(updateDoc(doc(as("low"), "users", "low"), { hideProfile: true }));
    await assertSucceeds(updateDoc(doc(as("free"), "users", "free"), { hideProfile: true })); // "free" is Level 3
    await assertSucceeds(updateDoc(doc(as("was"), "users", "was"), { displayName: "Was 2" })); // already hidden stays allowed
    await assertSucceeds(updateDoc(doc(as("was"), "users", "was"), { hideProfile: false }));
  });
});

describe("profile text fields", () => {
  test("about, city and village are typed text with length limits", async () => {
    await assertSucceeds(updateDoc(doc(as("free"), "users", "free"), { about: "Seeker", city: "Rajkot", village: "Gondal" }));
    await assertFails(updateDoc(doc(as("free"), "users", "free"), { about: "x".repeat(501) }));
    await assertFails(updateDoc(doc(as("free"), "users", "free"), { village: 5 }));
  });
});

describe("rich chat", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) =>
      setDoc(doc(ctx.firestore(), "chats", "paid"), { memberUid: "paid", unreadCountMember: 3, unreadCountStaff: 2 }));
  });

  test("each side marks its own reading and typing, and clears only its own count", async () => {
    await assertSucceeds(updateDoc(doc(as("paid"), "chats", "paid"), { memberReadAt: serverTimestamp(), unreadCountMember: 0, unreadByMember: false }));
    await assertSucceeds(updateDoc(doc(as("paid"), "chats", "paid"), { typingMember: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as("sw"), "chats", "paid"), { staffReadAt: serverTimestamp(), unreadCountStaff: 0, typingStaff: serverTimestamp() }));
    // Not the other side's fields
    await assertFails(updateDoc(doc(as("paid"), "chats", "paid"), { staffReadAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as("sw"), "chats", "paid"), { memberReadAt: serverTimestamp() }));
  });

  test("counts can only be cleared and times must be the server's", async () => {
    await assertFails(updateDoc(doc(as("paid"), "chats", "paid"), { unreadCountMember: 9 }));
    await assertFails(updateDoc(doc(as("paid"), "chats", "paid"), { memberReadAt: Timestamp.fromDate(new Date(2030, 1, 1)) }));
  });

  test("a voice note carries its length and waveform", async () => {
    const voice = (extra: Record<string, unknown>) => ({ senderUid: "paid", text: "", attachment: { url: "https://x/v.m4a", name: "voice.m4a", mime: "audio/mp4", size: 10, ...extra } });
    await assertSucceeds(setDoc(doc(as("paid"), "chats", "paid", "messages", "v1"), voice({ durationMs: 4200, waveform: [1, 5, 9] })));
    await assertFails(setDoc(doc(as("paid"), "chats", "paid", "messages", "v2"), voice({ durationMs: 999999 })));
    await assertFails(setDoc(doc(as("paid"), "chats", "paid", "messages", "v3"), voice({ waveform: new Array(65).fill(1) })));
  });

  test("presence: own heartbeat only; members see staff, staff see members", async () => {
    await assertSucceeds(setDoc(doc(as("paid"), "presence", "paid"), { lastActiveAt: serverTimestamp() }));
    await assertFails(setDoc(doc(as("paid"), "presence", "sw"), { lastActiveAt: serverTimestamp() }));
    await assertSucceeds(setDoc(doc(as("sw"), "presence", "sw"), { lastActiveAt: serverTimestamp() }));
    await assertSucceeds(getDoc(doc(as("paid"), "presence", "sw")));
    await assertSucceeds(getDoc(doc(as("sw"), "presence", "paid")));
    await assertFails(getDoc(doc(as("free"), "presence", "paid")));
  });

  test("the TURN key is server-only", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => setDoc(doc(ctx.firestore(), "serverConfig", "turn"), { keyId: "k", apiToken: "t" }));
    await assertFails(getDoc(doc(as("adm"), "serverConfig", "turn")));
    await assertFails(setDoc(doc(as("adm"), "serverConfig", "turn"), { keyId: "x" }));
  });

  test("chat preferences are private", async () => {
    await assertSucceeds(setDoc(doc(as("sw"), "users", "sw", "chatPrefs", "swamiji_paid"), { pinned: true, muted: false }));
    await assertFails(getDoc(doc(as("paid"), "users", "sw", "chatPrefs", "swamiji_paid")));
  });
});

describe("chat and calls by member level", () => {
  const call = (caller: string, callee: string, member: string, staff: string, extra: Record<string, unknown> = {}) =>
    ({ callerUid: caller, calleeUid: callee, memberUid: member, staffUid: staff, status: "ringing", offer: { sdp: "x", type: "offer" }, ...extra });

  test("Member Level 1 chats without a pack; a free user without a pack cannot", async () => {
    await assertSucceeds(setDoc(doc(as("m1only"), "chats", "m1only"), { memberUid: "m1only", memberName: "M1" }));
    await assertSucceeds(setDoc(doc(as("m1only"), "adminChats", "m1only"), { memberUid: "m1only", memberName: "M1" }));
    await assertFails(setDoc(doc(as("free"), "adminChats", "free"), { memberUid: "free", memberName: "Free" }));
  });

  test("files in chat: photo, PDF or audio only", async () => {
    const att = (mime: string) => ({ senderUid: "m1only", text: "", attachment: { url: "https://x/y", name: "f", mime, size: 10 } });
    await assertSucceeds(setDoc(doc(as("m1only"), "chats", "m1only", "messages", "a"), att("application/pdf")));
    await assertSucceeds(setDoc(doc(as("m1only"), "adminChats", "m1only", "messages", "b"), att("image/jpeg")));
    await assertFails(setDoc(doc(as("m1only"), "chats", "m1only", "messages", "c"), att("application/zip")));
    await assertFails(setDoc(doc(as("m1only"), "chats", "m1only", "messages", "d"), { senderUid: "m1only", text: "" }));
  });

  test("the Admin-team thread is for admins, not Swamiji", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "adminChats", "m1only"), { memberUid: "m1only" });
      await setDoc(doc(ctx.firestore(), "adminChats", "m1only", "messages", "x"), { senderUid: "m1only", text: "hi" });
    });
    await assertSucceeds(getDoc(doc(as("adm"), "adminChats", "m1only", "messages", "x")));
    await assertFails(getDoc(doc(as("sw"), "adminChats", "m1only", "messages", "x")));
    await assertSucceeds(setDoc(doc(as("adm"), "adminChats", "m1only", "messages", "y"), { senderUid: "adm", text: "Namaste" }));
  });

  test("audio calls open at Member Level 2, video at Level 3", async () => {
    await assertFails(setDoc(doc(as("m1only"), "calls", "a1"), call("m1only", "sw", "m1only", "sw")));
    await env.withSecurityRulesDisabled(async (ctx) => updateDoc(doc(ctx.firestore(), "users", "m1only"), { memberLevel: 2 }));
    await assertSucceeds(setDoc(doc(as("m1only"), "calls", "a2"), call("m1only", "sw", "m1only", "sw")));
    await assertFails(setDoc(doc(as("m1only"), "calls", "a3"), call("m1only", "sw", "m1only", "sw", { video: true })));
    await assertSucceeds(setDoc(doc(as("m3"), "calls", "v1"), call("m3", "sw", "m3", "sw", { video: true, channel: "swamiji" })));
    await assertSucceeds(setDoc(doc(as("m3"), "calls", "v2"), call("m3", "adm", "m3", "adm", { video: true, channel: "admin" })));
  });

  test("members read their own call history; admins read all", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => setDoc(doc(ctx.firestore(), "calls", "h1"), call("sw", "m3", "m3", "sw")));
    await assertSucceeds(getDoc(doc(as("m3"), "calls", "h1")));
    await assertSucceeds(getDoc(doc(as("adm"), "calls", "h1")));
    await assertFails(getDoc(doc(as("paid"), "calls", "h1")));
  });

  test("scheduled calls: member requests, staff confirm, member cannot confirm", async () => {
    const req = { memberUid: "m3", memberName: "M3", channel: "swamiji", video: true, at: FUTURE, note: "", status: "pending" };
    await assertSucceeds(setDoc(doc(as("m3"), "callRequests", "r1"), req));
    await assertFails(setDoc(doc(as("m1only"), "callRequests", "r2"), { ...req, memberUid: "m1only", video: false })); // Level 1: no calls
    await assertFails(setDoc(doc(as("m3"), "callRequests", "r3"), { ...req, at: PAST }));
    await assertFails(updateDoc(doc(as("m3"), "callRequests", "r1"), { status: "confirmed" }));
    await assertSucceeds(updateDoc(doc(as("sw"), "callRequests", "r1"), { status: "proposed", proposedAt: FUTURE, staffUid: "sw", staffName: "Swamiji" }));
    await assertSucceeds(updateDoc(doc(as("m3"), "callRequests", "r1"), { status: "confirmed", at: FUTURE }));
    await assertFails(updateDoc(doc(as("adm"), "callRequests", "r1"), { reminded15: true }));
  });
});
