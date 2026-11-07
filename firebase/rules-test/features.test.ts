import { readFileSync } from "fs";
import { resolve } from "path";
import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, Timestamp, updateDoc } from "firebase/firestore";

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
