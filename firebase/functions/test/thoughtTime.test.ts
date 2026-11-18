import { Timestamp } from "firebase-admin/firestore";
import type { Messenger } from "../src/notify";
import { onLikeChanged } from "../src/social";
import { onThoughtPushTick, thoughtPushesDue, thoughtTopic } from "../src/thoughtTime";
import { clearDb, testDb } from "./emulator";

const db = testDb();

function fakeMessenger() {
  const sent: Array<{ to: string; data: Record<string, string> }> = [];
  const m: Messenger = {
    async sendToTopic(topic, data) { sent.push({ to: `topic:${topic}`, data }); },
    async sendToTokens() { return []; },
  };
  return { m, sent };
}

beforeEach(async () => { await clearDb(db); });

describe("Thought of the Day at 4 am local time", () => {
  test("each time zone has its own topic", () => {
    expect(thoughtTopic(330)).toBe("thoughtOfDay_p330");
    expect(thoughtTopic(-300)).toBe("thoughtOfDay_m300");
    expect(thoughtTopic(0)).toBe("thoughtOfDay_p0");
  });

  test("at each quarter hour, the zones where it is now 4 am get their local day's thought", () => {
    // 09:00 UTC is 04:00 in New York (UTC-5)
    expect(thoughtPushesDue(new Date("2026-11-18T09:00:00Z"))).toEqual([{ offset: -300, day: "2026-11-18" }]);
    // 22:30 UTC is 04:00 the next day in India
    expect(thoughtPushesDue(new Date("2026-11-18T22:30:00Z"))).toEqual([{ offset: 330, day: "2026-11-19" }]);
    // A late run in the same quarter hour still counts; 14:00 UTC is 4 am for both UTC-10 and UTC+14
    expect(thoughtPushesDue(new Date("2026-11-18T14:07:00Z"))).toEqual([
      { offset: -600, day: "2026-11-18" },
      { offset: 840, day: "2026-11-19" },
    ]);
  });

  test("the push goes to the zone's topic; India also gets the bell line and the old topic", async () => {
    await db.doc("thoughts/2026-11-18").set({ title: "Be still", text: "Breathe" });
    await db.doc("thoughts/2026-11-19").set({ title: "Be kind", text: "Smile" });
    const ny = fakeMessenger();
    await onThoughtPushTick(db, ny.m, new Date("2026-11-18T09:00:00Z"));
    expect(ny.sent.map((s) => s.to)).toEqual(["topic:thoughtOfDay_m300"]);
    expect(ny.sent[0].data).toMatchObject({ title: "Be still", target: "thoughts/2026-11-18", category: "thoughtOfDay" });
    expect((await db.collection("broadcasts").get()).size).toBe(0);

    const india = fakeMessenger();
    await onThoughtPushTick(db, india.m, new Date("2026-11-18T22:30:00Z"));
    expect(india.sent.map((s) => s.to).sort()).toEqual(["topic:thoughtOfDay", "topic:thoughtOfDay_p330"]);
    expect((await db.collection("broadcasts").get()).size).toBe(1);
  });

  test("no thought that day, no push", async () => {
    const { m, sent } = fakeMessenger();
    await onThoughtPushTick(db, m, new Date("2026-11-18T09:00:00Z"));
    expect(sent).toEqual([]);
  });

  test("a like on the thought someone sees as today earns a point, even when India is a day ahead", async () => {
    // 20:00 in New York on 18 Nov is already 19 Nov in India
    const now = new Date("2026-11-19T01:00:00Z");
    await db.doc("users/u1").set({ mokshaPoints: 0 });
    await db.doc("thoughts/2026-11-18").set({ publishAt: Timestamp.fromDate(new Date("2026-11-18T00:00:00+05:30")) });
    await db.doc("thoughts/2026-11-15").set({ publishAt: Timestamp.fromDate(new Date("2026-11-15T00:00:00+05:30")) });
    await onLikeChanged(db, "thoughts/2026-11-18", "u1", 1, now);
    await onLikeChanged(db, "thoughts/2026-11-15", "u1", 1, now);
    const events = await db.collection("users/u1/pointEvents").get();
    expect(events.docs.map((d) => d.id)).toEqual(["like:thought:2026-11-18"]);
  });
});
