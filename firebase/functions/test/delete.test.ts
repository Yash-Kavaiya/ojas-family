import { deleteAccountData } from "../src/auth";
import { ensureUserDoc } from "../src/auth";
import { clearDb, testDb } from "./emulator";

const db = testDb();
beforeEach(async () => clearDb(db));

test("a failing Storage cleanup does not stop data and auth deletion", async () => {
  await ensureUserDoc(db, "u1");
  const deletedAuth: string[] = [];
  await deleteAccountData(db, "u1", {
    deleteFiles: async () => { throw new Error("bucket missing"); },
    deleteAuthUser: async (uid: string) => { deletedAuth.push(uid); },
  });
  expect((await db.doc("users/u1").get()).exists).toBe(false);
  expect(deletedAuth).toEqual(["u1"]);
});
