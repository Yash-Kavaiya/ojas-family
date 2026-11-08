import { turnCredentialsFor } from "../src/turn";
import { clearDb, testDb } from "./emulator";

const db = testDb();
beforeEach(async () => clearDb(db));

const reply = (status: number, body: unknown) => async () => ({ ok: status < 300, status, json: async () => body });

test("without a Cloudflare key, no relay (calls use STUN as before)", async () => {
  let called = false;
  const r = await turnCredentialsFor(db, async () => { called = true; return { ok: true, status: 200, json: async () => ({}) }; });
  expect(r.iceServers).toEqual([]);
  expect(called).toBe(false);
});

test("Cloudflare's servers come back as a list with string urls", async () => {
  await db.doc("serverConfig/turn").set({ keyId: "k1", apiToken: "t1" });
  let seen = "";
  const r = await turnCredentialsFor(db, async (url, init) => {
    seen = `${url} ${init.headers.Authorization}`;
    return { ok: true, status: 201, json: async () => ({ iceServers: { urls: ["turn:turn.cloudflare.com:3478?transport=udp", 5], username: "u", credential: "c" } }) };
  });
  expect(seen).toContain("/turn/keys/k1/credentials/generate-ice-servers Bearer t1");
  expect(r.iceServers).toEqual([{ urls: ["turn:turn.cloudflare.com:3478?transport=udp"], username: "u", credential: "c" }]);
});

test("a Cloudflare error falls back to no relay", async () => {
  await db.doc("serverConfig/turn").set({ keyId: "k1", apiToken: "t1" });
  expect((await turnCredentialsFor(db, reply(401, { error: "bad token" }))).iceServers).toEqual([]);
});
