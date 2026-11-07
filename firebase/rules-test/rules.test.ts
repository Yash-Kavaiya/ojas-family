import { readFileSync } from "fs";
import { resolve } from "path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";
import { ref, uploadBytes } from "firebase/storage";

let env: RulesTestEnvironment;

const ALICE = "alice";
const BOB = "bob";

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-ojas",
    firestore: { rules: readFileSync(resolve(__dirname, "../firestore.rules"), "utf8") },
    storage: { rules: readFileSync(resolve(__dirname, "../storage.rules"), "utf8") },
  });
});

afterAll(async () => env.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  // Server-created profiles, written with rules disabled like onUserCreate would
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "users", ALICE), { displayName: "Alice", mokshaPoints: 5, userLevel: 1, memberLevel: 0, role: "member", hideProfile: false });
    await setDoc(doc(db, "users", BOB), { displayName: "Bob", mokshaPoints: 9, userLevel: 2, memberLevel: 0, role: "member", hideProfile: false });
    await setDoc(doc(db, "users", ALICE, "pointEvents", "daily:2026-10-29"), { amount: 1 });
    await setDoc(doc(db, "config", "levels"), { l2: { days: 7, points: 7 } });
  });
});

const asUser = (uid: string, claims: Record<string, unknown> = {}) => env.authenticatedContext(uid, claims).firestore();

describe("users", () => {
  test("owner can read and update own displayName", async () => {
    const db = asUser(ALICE);
    await assertSucceeds(getDoc(doc(db, "users", ALICE)));
    await assertSucceeds(updateDoc(doc(db, "users", ALICE), { displayName: "Alice A" }));
  });

  test("user cannot write mokshaPoints on create or update", async () => {
    for (const field of ["mokshaPoints", "userLevel", "memberLevel", "role", "lastDailyOpen", "activeDays", "paidMonths"]) {
      await assertFails(updateDoc(doc(asUser(ALICE), "users", ALICE), { [field]: 999 }));
    }
    const carol = asUser("carol");
    await assertFails(setDoc(doc(carol, "users", "carol"), { displayName: "Carol", mokshaPoints: 100000 }));
  });

  test("user can create own profile with editable fields only", async () => {
    const carol = asUser("carol");
    await assertSucceeds(setDoc(doc(carol, "users", "carol"), { displayName: "Carol", language: "gu", profileComplete: true }));
  });

  test("user cannot create a profile for someone else", async () => {
    await assertFails(setDoc(doc(asUser(ALICE), "users", "carol"), { displayName: "Not Carol" }));
  });

  test("L2 user cannot read other users at all", async () => {
    await assertFails(getDoc(doc(asUser(BOB), "users", ALICE)));
  });

  test("M3 member cannot read the private profile (date of birth) but can read the public one", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), "users", BOB), { memberLevel: 3 });
      await setDoc(doc(ctx.firestore(), "publicProfiles", ALICE), { displayName: "Alice", hidden: false });
    });
    await assertFails(getDoc(doc(asUser(BOB), "users", ALICE)));
    await assertSucceeds(getDoc(doc(asUser(BOB), "publicProfiles", ALICE)));
  });

  test("hidden public profile: M3 cannot read it, staff can, lower levels never", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), "users", BOB), { memberLevel: 3 });
      await setDoc(doc(ctx.firestore(), "publicProfiles", ALICE), { displayName: "Alice", hidden: true });
      await setDoc(doc(ctx.firestore(), "publicProfiles", BOB), { displayName: "Bob", hidden: false });
    });
    await assertFails(getDoc(doc(asUser(BOB), "publicProfiles", ALICE)));
    await assertSucceeds(getDoc(doc(asUser("sw", { role: "swamiji" }), "publicProfiles", ALICE)));
    await assertFails(getDoc(doc(asUser(ALICE), "publicProfiles", BOB)));
    await assertFails(setDoc(doc(asUser(ALICE), "publicProfiles", ALICE), { displayName: "x" }));
  });

  test("admin can read any profile, even hidden", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), "users", ALICE), { hideProfile: true });
    });
    await assertSucceeds(getDoc(doc(asUser("root", { role: "admin" }), "users", ALICE)));
  });
});

describe("pointEvents", () => {
  test("user cannot write pointEvents", async () => {
    await assertFails(setDoc(doc(asUser(ALICE), "users", ALICE, "pointEvents", "fake"), { amount: 1000 }));
  });

  test("owner can read own pointEvents; others cannot", async () => {
    await assertSucceeds(getDoc(doc(asUser(ALICE), "users", ALICE, "pointEvents", "daily:2026-10-29")));
    await assertFails(getDoc(doc(asUser(BOB), "users", ALICE, "pointEvents", "daily:2026-10-29")));
  });
});

describe("config", () => {
  test("signed-in user can read config/levels; non-admin cannot write it", async () => {
    await assertSucceeds(getDoc(doc(asUser(ALICE), "config", "levels")));
    await assertFails(setDoc(doc(asUser(ALICE), "config", "levels"), { l2: { days: 0, points: 0 } }));
    await assertSucceeds(setDoc(doc(asUser("root", { role: "admin" }), "config", "levels"), { l2: { days: 7, points: 7 } }));
  });
});

describe("unauthenticated", () => {
  test("unauthenticated user can read nothing", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "users", ALICE)));
    await assertFails(getDoc(doc(db, "config", "levels")));
  });
});

describe("storage", () => {
  const image = (bytes: number) => new Uint8Array(bytes);

  test("owner can upload users/{uid}/avatar.jpg under 5 MB image/*", async () => {
    const storage = env.authenticatedContext(ALICE).storage();
    await assertSucceeds(uploadBytes(ref(storage, `users/${ALICE}/avatar.jpg`), image(1024), { contentType: "image/jpeg" }));
  });

  test("cannot upload to another uid", async () => {
    const storage = env.authenticatedContext(BOB).storage();
    await assertFails(uploadBytes(ref(storage, `users/${ALICE}/avatar.jpg`), image(1024), { contentType: "image/jpeg" }));
  });

  test("cannot upload 6 MB", async () => {
    const storage = env.authenticatedContext(ALICE).storage();
    await assertFails(uploadBytes(ref(storage, `users/${ALICE}/avatar.jpg`), image(6 * 1024 * 1024), { contentType: "image/jpeg" }));
  });

  test("cannot upload a non-image", async () => {
    const storage = env.authenticatedContext(ALICE).storage();
    await assertFails(uploadBytes(ref(storage, `users/${ALICE}/avatar.jpg`), image(10), { contentType: "application/pdf" }));
  });

  test("only admins upload announcement picture, audio or video, within each size limit", async () => {
    const admin = env.authenticatedContext("admin1", { role: "admin" }).storage();
    const member = env.authenticatedContext(ALICE).storage();
    await assertSucceeds(uploadBytes(ref(admin, "announcements/a.mp3"), image(1024), { contentType: "audio/mpeg" }));
    await assertSucceeds(uploadBytes(ref(admin, "announcements/v.mp4"), image(10 * 1024 * 1024), { contentType: "video/mp4" }));
    await assertFails(uploadBytes(ref(admin, "announcements/big.jpg"), image(6 * 1024 * 1024), { contentType: "image/jpeg" }));
    await assertFails(uploadBytes(ref(admin, "announcements/doc.pdf"), image(10), { contentType: "application/pdf" }));
    await assertFails(uploadBytes(ref(member, "announcements/a.mp3"), image(1024), { contentType: "audio/mpeg" }));
  });
});

describe("content", () => {
  const video = { section: "quick_protection", videoId: "Pg54NjjSvzY", title: "Protection", minUserLevel: 1, minMemberLevel: 0, order: 0, published: true };

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "content", "yt_Pg54NjjSvzY"), video);
    });
  });

  test("signed-in user can read content", async () => {
    await assertSucceeds(getDoc(doc(asUser(ALICE), "content", "yt_Pg54NjjSvzY")));
  });

  test("unauthenticated cannot read content", async () => {
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "content", "yt_Pg54NjjSvzY")));
  });

  test("non-admin cannot write content", async () => {
    await assertFails(setDoc(doc(asUser(ALICE), "content", "yt_abcdefghijk"), { ...video, videoId: "abcdefghijk" }));
    await assertFails(updateDoc(doc(asUser(ALICE), "content", "yt_Pg54NjjSvzY"), { title: "Hacked" }));
  });

  test("admin can create content with valid fields", async () => {
    await assertSucceeds(setDoc(doc(asUser("root", { role: "admin" }), "content", "yt_abcdefghijk"), { ...video, videoId: "abcdefghijk" }));
  });

  test("admin cannot create content with unknown section", async () => {
    await assertFails(setDoc(doc(asUser("root", { role: "admin" }), "content", "yt_abcdefghijk"), { ...video, videoId: "abcdefghijk", section: "random" }));
  });
});

describe("social", () => {
  const HOUR = 3600 * 1000;
  const now = Date.now();
  const ts = (ms: number) => new Date(ms);
  const thought = (publishAtMs: number) => ({ title: "Thought", text: "Be present", publishAt: ts(publishAtMs), likeCount: 0, commentCount: 0 });
  const comment = (uid: string, text: string, status: string, authorRole = "member") =>
    ({ authorUid: uid, authorName: uid, authorPhoto: "", authorRole, text, status, createdAt: ts(now) });
  const L3 = "carol";

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, "users", L3), { displayName: "Carol", userLevel: 3, memberLevel: 0, role: "member" });
      await setDoc(doc(db, "thoughts", "today"), thought(now - HOUR));
      await setDoc(doc(db, "thoughts", "old"), thought(now - 8 * 24 * HOUR));
      await setDoc(doc(db, "thoughts", "future"), thought(now + 24 * HOUR));
      await setDoc(doc(db, "thoughts", "today", "comments", "pending1"), comment(L3, "see https://x.com", "pending"));
      await setDoc(doc(db, "questions", "q1"), { authorUid: L3, authorName: "Carol", authorPhoto: "", authorRole: "member", title: "Q", body: "B", status: "visible", createdAt: ts(now), likeCount: 0, answerCount: 0 });
    });
  });

  test("member reads today's thought", async () => {
    await assertSucceeds(getDoc(doc(asUser(ALICE), "thoughts", "today")));
  });
  test("member cannot read a thought older than 7 days", async () => {
    await assertFails(getDoc(doc(asUser(ALICE), "thoughts", "old")));
  });
  test("member cannot read a scheduled future thought", async () => {
    await assertFails(getDoc(doc(asUser(ALICE), "thoughts", "future")));
  });
  test("admin reads future thought and can schedule one", async () => {
    const admin = asUser("root", { role: "admin" });
    await assertSucceeds(getDoc(doc(admin, "thoughts", "future")));
    await assertSucceeds(setDoc(doc(admin, "thoughts", "2099-01-01"), thought(now + 48 * HOUR)));
    await assertFails(setDoc(doc(asUser(L3), "thoughts", "2099-01-02"), thought(now + 48 * HOUR)));
  });
  test("L1 cannot like", async () => {
    await assertFails(setDoc(doc(asUser(ALICE), "thoughts", "today", "likes", ALICE), { createdAt: ts(now) }));
  });
  test("L2 can like with own uid only", async () => {
    await assertSucceeds(setDoc(doc(asUser(BOB), "thoughts", "today", "likes", BOB), { createdAt: ts(now) }));
    await assertFails(setDoc(doc(asUser(BOB), "thoughts", "today", "likes", ALICE), { createdAt: ts(now) }));
  });
  test("L2 cannot comment", async () => {
    await assertFails(setDoc(doc(asUser(BOB), "thoughts", "today", "comments", "c1"), comment(BOB, "Jai", "visible")));
  });
  test("L3 can comment visible text", async () => {
    await assertSucceeds(setDoc(doc(asUser(L3), "thoughts", "today", "comments", "c1"), comment(L3, "Jai Gurudev", "visible")));
  });
  test("comment containing a link must be pending", async () => {
    await assertFails(setDoc(doc(asUser(L3), "thoughts", "today", "comments", "c2"), comment(L3, "go www.spam.com", "visible")));
    await assertSucceeds(setDoc(doc(asUser(L3), "thoughts", "today", "comments", "c3"), comment(L3, "go www.spam.com", "pending")));
  });
  test("pending comment unreadable by other members, readable by author and admin", async () => {
    await assertFails(getDoc(doc(asUser(BOB), "thoughts", "today", "comments", "pending1")));
    await assertSucceeds(getDoc(doc(asUser(L3), "thoughts", "today", "comments", "pending1")));
    await assertSucceeds(getDoc(doc(asUser("root", { role: "admin" }), "thoughts", "today", "comments", "pending1")));
  });
  test("L3 can ask a question; L2 cannot", async () => {
    const q = { authorUid: L3, authorName: "Carol", authorPhoto: "", authorRole: "member", title: "How?", body: "...", status: "visible", createdAt: ts(now) };
    await assertSucceeds(setDoc(doc(asUser(L3), "questions", "q2"), q));
    await assertFails(setDoc(doc(asUser(BOB), "questions", "q3"), { ...q, authorUid: BOB }));
  });
  test("answer by member claiming authorRole swamiji is rejected", async () => {
    await assertFails(setDoc(doc(asUser(L3), "questions", "q1", "answers", "a1"), comment(L3, "I am Swamiji", "visible", "swamiji")));
    await assertSucceeds(setDoc(doc(asUser("sw", { role: "swamiji" }), "questions", "q1", "answers", "a2"), comment("sw", "Truth", "visible", "swamiji")));
  });
  test("non-admin cannot post announcement", async () => {
    const a = { title: "Satsang", body: "Sunday", createdAt: ts(now) };
    await assertFails(setDoc(doc(asUser(L3), "announcements", "a1"), a));
    await assertSucceeds(setDoc(doc(asUser("root", { role: "admin" }), "announcements", "a1"), a));
    await assertSucceeds(getDoc(doc(asUser(ALICE), "announcements", "a1")));
  });
  test("anyone signed in can report; only admin reads reports", async () => {
    await assertSucceeds(setDoc(doc(asUser(ALICE), "reports", "r1"), { targetPath: "questions/q1", reason: "spam", reporterUid: ALICE, createdAt: ts(now) }));
    await assertFails(getDoc(doc(asUser(ALICE), "reports", "r1")));
    await assertSucceeds(getDoc(doc(asUser("root", { role: "admin" }), "reports", "r1")));
  });
  test("blocked list is owner-only", async () => {
    await assertSucceeds(setDoc(doc(asUser(ALICE), "users", ALICE, "blocked", BOB), { at: ts(now) }));
    await assertFails(setDoc(doc(asUser(BOB), "users", ALICE, "blocked", L3), { at: ts(now) }));
  });
});

describe("moderation queries", () => {
  test("admin can query pending comments across threads; members cannot", async () => {
    const { collectionGroup, getDocs, query, where } = await import("firebase/firestore");
    const pending = (db: ReturnType<typeof asUser>) => getDocs(query(collectionGroup(db, "comments"), where("status", "==", "pending")));
    await assertSucceeds(pending(asUser("root", { role: "admin" })));
    await assertFails(pending(asUser(ALICE)));
  });
});

describe("goals", () => {
  const ADMIN = () => asUser("root", { role: "admin" });
  const goal = { title: "Plant a tree", description: "Plant and share a photo", kind: "photo", reward: 25, order: 5, active: true };
  const submitted = { status: "submitted", photoUrl: "https://x/p.jpg", note: "", submittedAt: new Date() };

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "goals", "tree"), goal);
      await setDoc(doc(ctx.firestore(), "users", BOB, "goalProgress", "rejected1"), { ...submitted, status: "rejected" });
    });
  });

  test("members read goals; only admin writes", async () => {
    await assertSucceeds(getDoc(doc(asUser(ALICE), "goals", "tree")));
    await assertFails(setDoc(doc(asUser(ALICE), "goals", "g2"), goal));
    await assertSucceeds(setDoc(doc(ADMIN(), "goals", "g2"), goal));
  });
  test("member submits own progress as submitted", async () => {
    await assertSucceeds(setDoc(doc(asUser(ALICE), "users", ALICE, "goalProgress", "tree"), submitted));
    await assertFails(setDoc(doc(asUser(ALICE), "users", BOB, "goalProgress", "tree"), submitted));
  });
  test("member cannot set approved", async () => {
    await assertFails(setDoc(doc(asUser(ALICE), "users", ALICE, "goalProgress", "tree"), { ...submitted, status: "approved" }));
  });
  test("member can resubmit after rejection", async () => {
    await assertSucceeds(setDoc(doc(asUser(BOB), "users", BOB, "goalProgress", "rejected1"), submitted));
  });
  test("other members cannot read progress", async () => {
    await assertFails(getDoc(doc(asUser(ALICE), "users", BOB, "goalProgress", "rejected1")));
    await assertSucceeds(getDoc(doc(asUser(BOB), "users", BOB, "goalProgress", "rejected1")));
  });
  test("admin approves", async () => {
    await assertSucceeds(updateDoc(doc(ADMIN(), "users", BOB, "goalProgress", "rejected1"), { status: "approved", reviewedAt: new Date() }));
  });
  test("admin lists submitted progress via collection group", async () => {
    const { collectionGroup, getDocs, query, where } = await import("firebase/firestore");
    const q = (db: ReturnType<typeof asUser>) => getDocs(query(collectionGroup(db, "goalProgress"), where("status", "==", "submitted")));
    await assertSucceeds(q(ADMIN()));
    await assertFails(q(asUser(ALICE)));
  });
});
