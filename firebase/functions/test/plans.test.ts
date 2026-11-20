import { Timestamp } from "firebase-admin/firestore";
import { adminGrantFor, grantEntitlement, revokeEntitlement } from "../src/billing";
import { enforceQuestionLimit } from "../src/questionLimit";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const NOW = new Date("2026-11-20T06:00:00Z");
const DAY = 86_400_000;
const user = async () => (await db.doc("users/u1").get()).data() ?? {};
const ADMIN = { role: "admin" };

beforeEach(async () => {
  await clearDb(db);
  await db.doc("users/u1").set({ displayName: "U", role: "member", createdAt: Timestamp.fromDate(new Date("2026-11-01T00:00:00Z")) });
});

describe("plans from membership", () => {
  test("an admin sets Gold; the plan, its start and its end are recorded", async () => {
    await adminGrantFor(db, ADMIN, { uid: "u1", entitlement: "membership", days: 30, tier: "gold" }, NOW);
    const u = await user();
    expect(u.plan).toBe("gold");
    expect((u.planSince as Timestamp).toMillis()).toBe(NOW.getTime());
    expect((u.planUntil as Timestamp).toMillis()).toBe(NOW.getTime() + 30 * DAY);
  });

  test("a membership without a tier is Silver; members already paying keep their paid time", async () => {
    await db.doc("users/u1").set({ paidDays: 100 }, { merge: true });
    await grantEntitlement(db, "u1", "membership", { days: 30, source: "play" }, NOW);
    const u = await user();
    expect(u.plan).toBe("silver");
    expect((u.planSince as Timestamp).toMillis()).toBe(NOW.getTime() - 100 * DAY);
  });

  test("moving to Gold keeps the plan's start; a lapse starts it again", async () => {
    await adminGrantFor(db, ADMIN, { uid: "u1", entitlement: "membership", days: 30, tier: "silver" }, NOW);
    const later = new Date(NOW.getTime() + 10 * DAY);
    await adminGrantFor(db, ADMIN, { uid: "u1", entitlement: "membership", days: 30, tier: "gold" }, later);
    expect((await user()).plan).toBe("gold");
    expect(((await user()).planSince as Timestamp).toMillis()).toBe(NOW.getTime());

    await revokeEntitlement(db, "u1", "membership", later);
    expect((await user()).plan).toBe("user");
    const back = new Date(later.getTime() + 60 * DAY);
    await adminGrantFor(db, ADMIN, { uid: "u1", entitlement: "membership", days: 30, tier: "gold" }, back);
    expect(((await user()).planSince as Timestamp).toMillis()).toBe(back.getTime());
  });

  test("only silver or gold", async () => {
    await expect(adminGrantFor(db, ADMIN, { uid: "u1", entitlement: "membership", days: 30, tier: "platinum" }, NOW)).rejects.toThrow(/tier/);
  });
});

describe("Q&A questions a day", () => {
  const ask = async (id: string, extra: Record<string, unknown> = {}) => {
    const data = { authorUid: "u1", authorRole: "member", title: "Q", body: "", status: "visible", ...extra };
    await db.doc(`questions/${id}`).set(data);
    return enforceQuestionLimit(db, id, data, NOW);
  };

  test("Silver after 7 days posts one words-only question a day; the next is removed", async () => {
    await db.doc("users/u1").set({ plan: "silver", planSince: Timestamp.fromMillis(NOW.getTime() - 10 * DAY), planUntil: Timestamp.fromMillis(NOW.getTime() + 20 * DAY) }, { merge: true });
    expect(await ask("q1")).toBe(true);
    expect(await ask("q2")).toBe(false);
    expect((await db.doc("questions/q2").get()).exists).toBe(false);
    expect((await db.doc("questions/q1").get()).exists).toBe(true);
  });

  test("a question with media before media opens is removed", async () => {
    await db.doc("users/u1").set({ plan: "silver", planSince: Timestamp.fromMillis(NOW.getTime() - 10 * DAY), planUntil: Timestamp.fromMillis(NOW.getTime() + 20 * DAY) }, { merge: true });
    expect(await ask("q1", { mediaUrl: "https://a", mediaType: "audio", status: "pending" })).toBe(false);
  });

  test("Gold after a year posts ten a day", async () => {
    await db.doc("users/u1").set({ plan: "gold", planSince: Timestamp.fromMillis(NOW.getTime() - 400 * DAY), planUntil: Timestamp.fromMillis(NOW.getTime() + 20 * DAY) }, { merge: true });
    for (let i = 0; i < 10; i++) expect(await ask(`q${i}`, { imageUrl: "https://img", status: "pending" })).toBe(true);
    expect(await ask("q10")).toBe(false);
  });
});
