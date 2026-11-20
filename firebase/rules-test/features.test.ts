import { readFileSync } from "fs";
import { resolve } from "path";
import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, deleteDoc, doc, getDoc, getDocs, limit, query, serverTimestamp, setDoc, Timestamp, updateDoc, where } from "firebase/firestore";

let env: RulesTestEnvironment;
const FUTURE = Timestamp.fromDate(new Date(Date.now() + 7 * 24 * 3600 * 1000));
const PAST = Timestamp.fromDate(new Date(Date.now() - 1000));
const daysAgo = (d: number) => Timestamp.fromDate(new Date(Date.now() - d * 24 * 3600 * 1000));
// The plan table (firestore.rules allowed(u, s, g)): time since joining, or on a paid plan
const joined = (d: number) => ({ createdAt: daysAgo(d) });
const onPlan = (plan: string, d: number) => ({ createdAt: daysAgo(d), plan, planSince: daysAgo(d), planUntil: FUTURE });

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-ojas-features",
    firestore: { rules: readFileSync(resolve(__dirname, "../firestore.rules"), "utf8") },
  });
});

afterAll(async () => env.cleanup());

const users: Record<string, Record<string, unknown>> = {
  // Free for 200 days: likes, replies and words-only questions, but no Activity, media questions or hiding
  free: { displayName: "Free", userLevel: 3, memberLevel: 0, role: "member", ...joined(200) },
  // Silver for 400 days: reads, likes and replies on Activity, but never posts there or hides a profile
  paid: { displayName: "Paid", userLevel: 2, memberLevel: 1, role: "member", ...onPlan("silver", 400) },
  // Gold for 400 days: everything a member can have, including posting on Activity and hiding a profile
  m3: { displayName: "M3", userLevel: 2, memberLevel: 3, role: "member", ...onPlan("gold", 400) },
  sw: { displayName: "Swamiji", role: "swamiji" },
  senior: { displayName: "Senior", role: "senior_member" },
  adm: { displayName: "Admin", role: "admin" },
  m1only: { displayName: "M1", userLevel: 1, memberLevel: 1, role: "member", ...onPlan("silver", 0) },
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

  test("only Gold after a year posts: a photo, words or both, never an empty post", async () => {
    await assertFails(setDoc(doc(as("paid"), "dailyPosts", "x"), post("paid", { imageUrl: "https://img" })));
    await assertFails(setDoc(doc(as("m3"), "dailyPosts", "x"), post("m3", { imageUrl: "", text: "" })));
    await assertSucceeds(setDoc(doc(as("m3"), "dailyPosts", "x"), post("m3", { imageUrl: "https://img" })));
    await assertSucceeds(setDoc(doc(as("m3"), "dailyPosts", "words"), post("m3", { imageUrl: "", text: "Cooked for the family today." })));
    await assertSucceeds(setDoc(doc(as("m3"), "dailyPosts", "photoOnly"), post("m3", { imageUrl: "https://img", text: "" })));
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

describe("audio and video in Activity and Q&A", () => {
  const av = (kind: string) => ({ mediaType: kind, mediaUrl: "https://media" });
  const question = (uid: string, extra: Record<string, unknown> = {}) => ({
    authorUid: uid, authorName: String(users[uid].displayName), authorRole: users[uid].role, title: "Q", body: "", status: "visible", ...extra,
  });

  test("an Activity post can be just an audio clip or a video", async () => {
    await assertSucceeds(setDoc(doc(as("m3"), "dailyPosts", "audio"), post("m3", { imageUrl: "", text: "", ...av("audio") })));
    await assertSucceeds(setDoc(doc(as("m3"), "dailyPosts", "video"), post("m3", { imageUrl: "", ...av("video") })));
    await assertFails(setDoc(doc(as("m3"), "dailyPosts", "pdf"), post("m3", { imageUrl: "", text: "", ...av("pdf") })));
    await assertFails(setDoc(doc(as("m3"), "dailyPosts", "empty"), post("m3", { imageUrl: "", text: "", mediaType: "audio", mediaUrl: "" })));
  });

  test("a question with audio or video waits for review, and only members who may add photos can add it", async () => {
    await assertSucceeds(setDoc(doc(as("m3"), "questions", "qa"), question("m3", { ...av("video"), status: "pending" })));
    await assertFails(setDoc(doc(as("m3"), "questions", "qb"), question("m3", { ...av("video") })));
    await assertFails(setDoc(doc(as("free"), "questions", "qc"), question("free", { ...av("audio"), status: "pending" })));
    await assertSucceeds(setDoc(doc(as("free"), "questions", "qd"), question("free")));
  });

  test("replies to an announcement are words only", async () => {
    await assertSucceeds(setDoc(doc(as("sw"), "announcements", "n1", "comments", "c1"), post("sw")));
    await assertFails(setDoc(doc(as("sw"), "announcements", "n1", "comments", "c2"), post("sw", { imageUrl: "https://img", status: "pending" })));
  });
});

describe("editing and deleting your own posts", () => {
  const edited = (fields: Record<string, unknown>) => ({ ...fields, editedAt: serverTimestamp() });

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, "questions", "mine"), { authorUid: "m3", authorRole: "member", title: "Old", body: "", status: "visible" });
      await setDoc(doc(db, "questions", "mine", "answers", "a1"), { authorUid: "m3", authorRole: "member", text: "Old words", status: "visible" });
      await setDoc(doc(db, "dailyPosts", "photo"), { authorUid: "m3", text: "Seva", imageUrl: "https://img", status: "visible" });
      await setDoc(doc(db, "thoughts", "t1", "comments", "c1"), { authorUid: "m3", text: "Nice", status: "visible" });
    });
  });

  test("the author changes the words; nobody else can, and nothing else changes", async () => {
    await assertSucceeds(updateDoc(doc(as("m3"), "questions", "mine", "answers", "a1"), edited({ text: "New words", status: "visible" })));
    await assertSucceeds(updateDoc(doc(as("m3"), "thoughts", "t1", "comments", "c1"), edited({ text: "Very nice", status: "visible" })));
    await assertFails(updateDoc(doc(as("paid"), "questions", "mine", "answers", "a1"), edited({ text: "Hacked", status: "visible" })));
    await assertFails(updateDoc(doc(as("m3"), "questions", "mine", "answers", "a1"), edited({ text: "x", status: "visible", likeCount: 99 })));
    await assertFails(updateDoc(doc(as("m3"), "questions", "mine", "answers", "a1"), edited({ text: "", status: "visible" })));
  });

  test("adding a link sends it back for approval", async () => {
    await assertFails(updateDoc(doc(as("m3"), "questions", "mine", "answers", "a1"), edited({ text: "see https://x.in", status: "visible" })));
    await assertFails(updateDoc(doc(as("m3"), "questions", "mine", "answers", "a1"), edited({ text: "see HTTPS://x.in", status: "visible" })));
    await assertSucceeds(updateDoc(doc(as("m3"), "questions", "mine", "answers", "a1"), edited({ text: "see https://x.in", status: "pending" })));
  });

  test("a question's title and details are edited the same way", async () => {
    await assertSucceeds(updateDoc(doc(as("m3"), "questions", "mine"), edited({ title: "New", body: "More", status: "visible" })));
    await assertFails(updateDoc(doc(as("m3"), "questions", "mine"), edited({ title: "", body: "More", status: "visible" })));
    await assertFails(updateDoc(doc(as("free"), "questions", "mine"), edited({ title: "Mine now", body: "", status: "visible" })));
  });

  test("an Activity post with a photo may lose its words", async () => {
    await assertSucceeds(updateDoc(doc(as("m3"), "dailyPosts", "photo"), edited({ text: "", status: "visible" })));
  });

  test("the author deletes their own post; others cannot", async () => {
    await assertFails(deleteDoc(doc(as("paid"), "questions", "mine", "answers", "a1")));
    await assertSucceeds(deleteDoc(doc(as("m3"), "questions", "mine", "answers", "a1")));
    await assertSucceeds(deleteDoc(doc(as("m3"), "dailyPosts", "photo")));
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

  test("the callee can list the calls ringing for them, and nobody else's", async () => {
    await assertSucceeds(setDoc(doc(as("sw"), "calls", "c4"), call("sw", "free", "free", "sw")));
    const ringingFor = (who: string, uid: string) =>
      getDocs(query(collection(as(who), "calls"), where("calleeUid", "==", uid), where("status", "==", "ringing"), limit(20)));
    await assertSucceeds(ringingFor("free", "free"));
    await assertFails(ringingFor("m3", "free"));
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
    // "free" is User Level 3, Member Level 0 (and may answer after 200 days)
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
  test("opens for Gold after a year; turning it off always works", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "users", "low"), { displayName: "Low", userLevel: 2, memberLevel: 3, role: "member", hideProfile: false, ...onPlan("gold", 300) });
      await setDoc(doc(ctx.firestore(), "users", "was"), { displayName: "Was", userLevel: 1, role: "member", hideProfile: true });
    });
    await assertFails(updateDoc(doc(as("low"), "users", "low"), { hideProfile: true }));
    await assertFails(updateDoc(doc(as("free"), "users", "free"), { hideProfile: true })); // free members never hide
    await assertFails(updateDoc(doc(as("paid"), "users", "paid"), { hideProfile: true })); // nor Silver
    await assertSucceeds(updateDoc(doc(as("m3"), "users", "m3"), { hideProfile: true })); // Gold for 400 days
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

describe("paid chat and calls (plan table): team from day 1, Swamiji after 6 months", () => {
  const call = (caller: string, callee: string, member: string, staff: string, extra: Record<string, unknown> = {}) =>
    ({ callerUid: caller, calleeUid: callee, memberUid: member, staffUid: staff, status: "ringing", offer: { sdp: "x", type: "offer" }, ...extra });
  // "newbie" joined 10 days ago, "elder" 200 days ago; both bought a chat pack and a call pack
  const pack = (credits?: number) => ({ expiresAt: FUTURE, ...(credits !== undefined ? { credits } : {}) });
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      for (const [uid, days] of [["newbie", 10], ["elder", 200]] as const) {
        await setDoc(doc(db, "users", uid), { displayName: uid, role: "member", ...joined(days) });
        await setDoc(doc(db, "users", uid, "entitlements", "chat"), pack());
        await setDoc(doc(db, "users", uid, "entitlements", "call"), pack(2));
      }
      await setDoc(doc(db, "users", "nopack"), { displayName: "nopack", role: "member", ...onPlan("gold", 400) });
    });
  });

  test("chat needs a chat pack: with the team at once, with Swamiji after 6 months", async () => {
    await assertSucceeds(setDoc(doc(as("newbie"), "adminChats", "newbie"), { memberUid: "newbie", memberName: "N" }));
    await assertFails(setDoc(doc(as("newbie"), "chats", "newbie"), { memberUid: "newbie", memberName: "N" }));
    await assertSucceeds(setDoc(doc(as("elder"), "chats", "elder"), { memberUid: "elder", memberName: "E" }));
    // Gold for a year, but no chat bought
    await assertFails(setDoc(doc(as("nopack"), "adminChats", "nopack"), { memberUid: "nopack", memberName: "G" }));
    await assertFails(setDoc(doc(as("nopack"), "chats", "nopack"), { memberUid: "nopack", memberName: "G" }));
  });

  test("files in chat: photo, PDF or audio only", async () => {
    const att = (mime: string) => ({ senderUid: "elder", text: "", attachment: { url: "https://x/y", name: "f", mime, size: 10 } });
    await assertSucceeds(setDoc(doc(as("elder"), "chats", "elder", "messages", "a"), att("application/pdf")));
    await assertSucceeds(setDoc(doc(as("elder"), "adminChats", "elder", "messages", "b"), att("image/jpeg")));
    await assertFails(setDoc(doc(as("elder"), "chats", "elder", "messages", "c"), att("application/zip")));
    await assertFails(setDoc(doc(as("elder"), "chats", "elder", "messages", "d"), { senderUid: "elder", text: "" }));
    await assertFails(setDoc(doc(as("newbie"), "chats", "newbie", "messages", "e"), { senderUid: "newbie", text: "hi" }));
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

  test("calls need a call pack with calls left; audio or video; Swamiji after 6 months", async () => {
    await assertSucceeds(setDoc(doc(as("newbie"), "calls", "a1"), call("newbie", "adm", "newbie", "adm", { channel: "admin", video: true })));
    await assertFails(setDoc(doc(as("newbie"), "calls", "a2"), call("newbie", "sw", "newbie", "sw", { channel: "swamiji" })));
    await assertSucceeds(setDoc(doc(as("elder"), "calls", "a3"), call("elder", "sw", "elder", "sw", { channel: "swamiji", video: true })));
    await assertFails(setDoc(doc(as("nopack"), "calls", "a4"), call("nopack", "adm", "nopack", "adm", { channel: "admin" })));
    await env.withSecurityRulesDisabled(async (ctx) => updateDoc(doc(ctx.firestore(), "users", "elder", "entitlements", "call"), { credits: 0 }));
    await assertFails(setDoc(doc(as("elder"), "calls", "a5"), call("elder", "sw", "elder", "sw", { channel: "swamiji" })));
  });

  test("members read their own call history; admins read all", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => setDoc(doc(ctx.firestore(), "calls", "h1"), call("sw", "m3", "m3", "sw")));
    await assertSucceeds(getDoc(doc(as("m3"), "calls", "h1")));
    await assertSucceeds(getDoc(doc(as("adm"), "calls", "h1")));
    await assertFails(getDoc(doc(as("paid"), "calls", "h1")));
  });

  test("scheduled calls: member requests, staff confirm, member cannot confirm", async () => {
    const req = { memberUid: "elder", memberName: "E", channel: "swamiji", video: true, at: FUTURE, note: "", status: "pending" };
    await assertSucceeds(setDoc(doc(as("elder"), "callRequests", "r1"), req));
    await assertFails(setDoc(doc(as("newbie"), "callRequests", "r2"), { ...req, memberUid: "newbie" })); // Swamiji: not 6 months yet
    await assertSucceeds(setDoc(doc(as("newbie"), "callRequests", "r4"), { ...req, memberUid: "newbie", channel: "admin" }));
    await assertFails(setDoc(doc(as("elder"), "callRequests", "r3"), { ...req, at: PAST }));
    await assertFails(updateDoc(doc(as("elder"), "callRequests", "r1"), { status: "confirmed" }));
    await assertSucceeds(updateDoc(doc(as("sw"), "callRequests", "r1"), { status: "proposed", proposedAt: FUTURE, staffUid: "sw", staffName: "Swamiji" }));
    await assertSucceeds(updateDoc(doc(as("elder"), "callRequests", "r1"), { status: "confirmed", at: FUTURE }));
    await assertFails(updateDoc(doc(as("adm"), "callRequests", "r1"), { reminded15: true }));
  });
});

describe("bell list", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, "users", "m3", "notifications", "n1"), { group: "qa", read: false, title: "A" });
      await setDoc(doc(db, "broadcasts", "b1"), { group: "qa" });
      await setDoc(doc(db, "paidBroadcasts", "p1"), { group: "activity" });
    });
  });

  test("a member reads and marks their own lines read, and nobody else's", async () => {
    await assertSucceeds(getDoc(doc(as("m3"), "users", "m3", "notifications", "n1")));
    await assertFails(getDoc(doc(as("paid"), "users", "m3", "notifications", "n1")));
    await assertSucceeds(updateDoc(doc(as("m3"), "users", "m3", "notifications", "n1"), { read: true }));
    await assertFails(updateDoc(doc(as("m3"), "users", "m3", "notifications", "n1"), { title: "changed" }));
    await assertFails(setDoc(doc(as("m3"), "users", "m3", "notifications", "fake"), { group: "critical", read: false }));
  });

  test("shared lines: everyone reads new posts; only paid members read new Activity posts", async () => {
    await assertSucceeds(getDoc(doc(as("free"), "broadcasts", "b1")));
    await assertFails(getDoc(doc(as("free"), "paidBroadcasts", "p1")));
    await assertSucceeds(getDoc(doc(as("paid"), "paidBroadcasts", "p1")));
    await assertFails(setDoc(doc(as("adm"), "broadcasts", "x"), { group: "qa" }));
  });

  test("a member keeps their own seen state", async () => {
    await assertSucceeds(setDoc(doc(as("m3"), "users", "m3", "notificationState", "main"), { seen: { qa: serverTimestamp() }, opened: ["b1"] }));
    await assertFails(setDoc(doc(as("m3"), "users", "m3", "notificationState", "main"), { seen: {}, extra: 1 }));
    await assertFails(getDoc(doc(as("paid"), "users", "m3", "notificationState", "main")));
  });
});

describe("usage dashboard", () => {
  test("only admins read the daily numbers, and nobody writes them", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => { await setDoc(doc(ctx.firestore(), "stats", "2026-11-06"), { day: "2026-11-06" }); });
    await assertSucceeds(getDoc(doc(as("adm"), "stats", "2026-11-06")));
    await assertFails(getDoc(doc(as("paid"), "stats", "2026-11-06")));
    await assertFails(setDoc(doc(as("adm"), "stats", "2026-11-07"), { day: "2026-11-07" }));
  });
});

describe("views", () => {
  const paths = ["thoughts/t1", "questions/q1", "announcements/n1", "dailyPosts/p1", "content/yt_x", "books/b1"];

  test("a member counts their own view once, with nothing but the time", async () => {
    for (const p of paths) {
      await assertSucceeds(setDoc(doc(as("paid"), `${p}/views/paid`), { createdAt: serverTimestamp() }));
      // A second write would be an update: refused, so a view is never counted twice
      await assertFails(setDoc(doc(as("paid"), `${p}/views/paid`), { createdAt: serverTimestamp() }));
    }
  });

  test("nobody can count a view for someone else, add fields, or set the count", async () => {
    await assertFails(setDoc(doc(as("paid"), "announcements/n1/views/free"), { createdAt: serverTimestamp() }));
    await assertFails(setDoc(doc(as("paid"), "books/b1/views/paid"), { createdAt: serverTimestamp(), n: 5 }));
    await assertFails(deleteDoc(doc(as("paid"), "books/b1/views/paid")));
    await assertFails(setDoc(doc(env.unauthenticatedContext().firestore(), "books/b1/views/x"), { createdAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as("paid"), "dailyPosts/p1"), { viewCount: 99 }));
  });
});

describe("book covers", () => {
  const book = { title: "Book", url: "https://x/b.pdf", minUserLevel: 1, minMemberLevel: 0, order: 0, published: true };

  test("an admin can give a book an https cover picture, or none", async () => {
    await assertSucceeds(setDoc(doc(as("adm"), "books/b2"), { ...book, coverUrl: "https://x/c.jpg" }));
    await assertSucceeds(setDoc(doc(as("adm"), "books/b3"), { ...book, coverUrl: "" }));
    await assertFails(setDoc(doc(as("adm"), "books/b4"), { ...book, coverUrl: "javascript:alert(1)" }));
    await assertFails(setDoc(doc(as("paid"), "books/b5"), { ...book, coverUrl: "https://x/c.jpg" }));
  });
});

describe("membership plans (owner's table, 2026-11-20)", () => {
  const setUser = (uid: string, data: Record<string, unknown>) =>
    env.withSecurityRulesDisabled(async (ctx) => { await setDoc(doc(ctx.firestore(), "users", uid), { displayName: uid, role: "member", ...data }); });
  const like = (uid: string) => setDoc(doc(as(uid), "thoughts", "t1", "likes", uid), { createdAt: serverTimestamp() });

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "thoughts", "t1"), { title: "T", text: "x", publishAt: Timestamp.now() });
    });
  });

  test("free members like after 7 days; Silver and Gold from the first day", async () => {
    await setUser("new", joined(3));
    await setUser("week", joined(8));
    await setUser("silver0", onPlan("silver", 0));
    await assertFails(like("new"));
    await assertSucceeds(like("week"));
    await assertSucceeds(like("silver0"));
  });

  test("a plan that ran out counts for nothing; the member keeps what their own time gives", async () => {
    await setUser("lapsed", { ...joined(3), plan: "gold", planSince: daysAgo(400), planUntil: PAST });
    await assertFails(like("lapsed"));
    await assertFails(getDoc(doc(as("lapsed"), "dailyPosts", "p1")));
  });

  test("Activity: Gold reads after 7 days, Silver after 6 months, free members never", async () => {
    await setUser("gold8", onPlan("gold", 8));
    await setUser("silver100", onPlan("silver", 100));
    await setUser("free900", joined(900));
    await assertSucceeds(getDoc(doc(as("gold8"), "dailyPosts", "p1")));
    await assertFails(getDoc(doc(as("silver100"), "dailyPosts", "p1")));
    await assertFails(getDoc(doc(as("free900"), "dailyPosts", "p1")));
  });

  test("announcement replies: Gold from the first day, Silver after 7 days", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => { await setDoc(doc(ctx.firestore(), "announcements", "n1"), { title: "Hi", body: "" }); });
    await setUser("gold0", onPlan("gold", 0));
    await setUser("silver0", onPlan("silver", 0));
    const reply = (uid: string) => ({ authorUid: uid, authorName: uid, authorRole: "member", text: "Hari Om", status: "visible" });
    await assertSucceeds(setDoc(doc(as("gold0"), "announcements", "n1", "comments", "c1"), reply("gold0")));
    await assertFails(setDoc(doc(as("silver0"), "announcements", "n1", "comments", "c2"), reply("silver0")));
  });

  test("moderators follow their own plan; Swamiji and admins have everything", async () => {
    const mod = (uid: string) => env.authenticatedContext(uid, { role: "moderator" }).firestore();
    const oldName = (uid: string) => env.authenticatedContext(uid, { role: "senior_member" }).firestore();
    await setUser("mod", { ...joined(1), role: "moderator" });
    await assertFails(setDoc(doc(mod("mod"), "thoughts", "t1", "likes", "mod"), { createdAt: serverTimestamp() }));
    await assertFails(setDoc(doc(oldName("mod"), "thoughts", "t1", "likes", "mod"), { createdAt: serverTimestamp() }));
    await assertSucceeds(getDoc(doc(as("sw"), "dailyPosts", "p1")));
    await assertSucceeds(setDoc(doc(as("adm"), "thoughts", "t1", "likes", "adm"), { createdAt: serverTimestamp() }));
  });
});
