import { ensureUserDoc } from "../src/auth";
import { DEFAULT_POINTS } from "../src/pointsConfig";
import { PROFILE_ITEMS, onProfileWritten, profileCompletion } from "../src/profilePoints";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const points = async () => Number((await db.doc("users/u1").get()).get("mokshaPoints") ?? 0);

const full = {
  photoUrl: "https://x/p.jpg", displayName: "Meera", dob: "1990-01-01", gender: "female",
  country: "India", state: "Gujarat", city: "Rajkot", about: "Meditating every morning", language: "gu",
};

beforeEach(async () => {
  await clearDb(db);
  await ensureUserDoc(db, "u1");
});

test("completion counts each item and rounds the percent", () => {
  expect(profileCompletion({}).percent).toBe(0);
  expect(profileCompletion(full).percent).toBe(100);
  expect(profileCompletion({ displayName: "A", language: "en" }).done).toEqual(["name", "language"]);
  expect(profileCompletion({ ...full, about: "short" }).done).not.toContain("about");
  expect(profileCompletion({ ...full, state: "", city: "" }).done).not.toContain("location");
});

test("the item list matches the app's ProfileItem enum", () => {
  // Kept in step with app/.../domain/ProfileCompletion.kt
  expect(PROFILE_ITEMS.map((i) => i.id)).toEqual(["photo", "name", "dob", "gender", "location", "about", "language"]);
});

test("each item pays once, and 100% adds the bonus once", async () => {
  const before = await points();
  const half = { displayName: "Meera", language: "gu" };
  expect(await onProfileWritten(db, "u1", undefined, half)).toBe(2 * DEFAULT_POINTS.profileField);
  // Saving the same again pays nothing
  expect(await onProfileWritten(db, "u1", { language: "en" }, half)).toBe(0);
  const rest = 5 * DEFAULT_POINTS.profileField + DEFAULT_POINTS.profileComplete;
  expect(await onProfileWritten(db, "u1", half, full)).toBe(rest);
  expect(await points()).toBe(before + 7 * DEFAULT_POINTS.profileField + DEFAULT_POINTS.profileComplete);
  // Clearing and filling again earns nothing more
  expect(await onProfileWritten(db, "u1", { ...full, about: "" }, full)).toBe(0);
});

test("writes that don't touch profile fields are ignored", async () => {
  expect(await onProfileWritten(db, "u1", { ...full, mokshaPoints: 1 }, { ...full, mokshaPoints: 2 })).toBe(0);
});
