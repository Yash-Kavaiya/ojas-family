import { ensureUserDoc } from "../src/auth";
import { onGoalProgressWritten } from "../src/goals";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const points = async () => (await db.doc("users/u1").get()).get("mokshaPoints") ?? 0;

beforeEach(async () => {
  await clearDb(db);
  await ensureUserDoc(db, "u1");
  await db.doc("goals/tree").set({ title: "Plant a tree", kind: "photo", reward: 25, order: 1, active: true });
});

test("approval awards goal reward as milestone", async () => {
  await onGoalProgressWritten(db, "u1", "tree", { status: "submitted" }, { status: "approved" });
  expect(await points()).toBe(25);
  expect((await db.doc("users/u1/pointEvents/goal:tree").get()).get("milestone")).toBe(true);
});

test("second approval awards nothing", async () => {
  await onGoalProgressWritten(db, "u1", "tree", { status: "submitted" }, { status: "approved" });
  await onGoalProgressWritten(db, "u1", "tree", { status: "rejected" }, { status: "approved" });
  expect(await points()).toBe(25);
});

test("rejection awards nothing", async () => {
  await onGoalProgressWritten(db, "u1", "tree", { status: "submitted" }, { status: "rejected" });
  expect(await points()).toBe(0);
});
