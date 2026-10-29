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

  test("M3 member can read when hideProfile is false", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), "users", BOB), { memberLevel: 3 });
    });
    await assertSucceeds(getDoc(doc(asUser(BOB), "users", ALICE)));
  });

  test("user cannot read another user's doc when target hideProfile is true", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), "users", BOB), { memberLevel: 3 });
      await updateDoc(doc(ctx.firestore(), "users", ALICE), { hideProfile: true });
    });
    await assertFails(getDoc(doc(asUser(BOB), "users", ALICE)));
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
});
