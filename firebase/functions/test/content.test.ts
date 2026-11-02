import { readFileSync } from "fs";
import { resolve } from "path";
import { addVideoFor, importEntries, parsePlaylistFeed } from "../src/content";
import { clearDb, testDb } from "./emulator";

const db = testDb();
const feed = readFileSync(resolve(__dirname, "fixtures/playlist.xml"), "utf8");
const ADMIN = { role: "admin" };

beforeEach(async () => clearDb(db));

test("parsePlaylistFeed returns entries in feed order", () => {
  const entries = parsePlaylistFeed(feed);
  expect(entries.map((e) => e.videoId)).toEqual(["PtYkxDusOTk", "qZ3_td76CEo", "4wM3yd7eQ70"]);
  expect(entries[0].title).toBe("Self-Healing Online Course on Zoom by Swami Anand Swaroop");
});

test("import creates yt_<id> docs with section and order by feed position", async () => {
  const result = await importEntries(db, "other", parsePlaylistFeed(feed));
  expect(result).toEqual({ created: 3, skipped: 0 });
  const second = (await db.doc("content/yt_qZ3_td76CEo").get()).data()!;
  expect(second).toMatchObject({ section: "other", videoId: "qZ3_td76CEo", order: 1, published: true, minUserLevel: 1, minMemberLevel: 0 });
});

test("import keeps existing docs untouched", async () => {
  await db.doc("content/yt_PtYkxDusOTk").set({ section: "yoga", videoId: "PtYkxDusOTk", title: "Edited", minUserLevel: 3 });
  const result = await importEntries(db, "other", parsePlaylistFeed(feed));
  expect(result).toEqual({ created: 2, skipped: 1 });
  expect((await db.doc("content/yt_PtYkxDusOTk").get()).data()).toMatchObject({ title: "Edited", section: "yoga", minUserLevel: 3 });
});

test("non-admin addVideo is permission-denied", async () => {
  await expect(addVideoFor(db, { role: "member" }, { url: "https://youtu.be/Pg54NjjSvzY", section: "yoga" }))
    .rejects.toMatchObject({ code: "permission-denied" });
  expect((await db.doc("content/yt_Pg54NjjSvzY").get()).exists).toBe(false);
});

test("addVideo rejects a non-YouTube url", async () => {
  await expect(addVideoFor(db, ADMIN, { url: "https://example.com/v", section: "yoga" }))
    .rejects.toMatchObject({ code: "invalid-argument" });
});

test("addVideo looks up the title and writes the doc", async () => {
  const fakeFetch = async () => ({ ok: true, json: async () => ({ title: "Chakra Basics" }) });
  const { id } = await addVideoFor(db, ADMIN, { url: "https://www.youtube.com/watch?v=Pg54NjjSvzY&t=3", section: "understanding" }, fakeFetch);
  expect(id).toBe("yt_Pg54NjjSvzY");
  expect((await db.doc(`content/${id}`).get()).data()).toMatchObject({ title: "Chakra Basics", section: "understanding" });
});
