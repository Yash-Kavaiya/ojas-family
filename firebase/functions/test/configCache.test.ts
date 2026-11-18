import { cached, clearConfigCache, setConfigCacheEnabled } from "../src/configCache";

beforeEach(() => setConfigCacheEnabled(true));
afterAll(() => setConfigCacheEnabled(false));

test("serves a value until the TTL passes, then reloads", async () => {
  let t = 0, loads = 0;
  const load = async () => ++loads;
  expect(await cached("k", load, 1000, () => t)).toBe(1);
  t = 999;
  expect(await cached("k", load, 1000, () => t)).toBe(1);
  t = 1000;
  expect(await cached("k", load, 1000, () => t)).toBe(2);
});

test("calls that arrive together share one load", async () => {
  let loads = 0;
  const load = () => new Promise<number>((r) => setTimeout(() => r(++loads), 10));
  const [a, b] = await Promise.all([cached("p", load), cached("p", load)]);
  expect([a, b, loads]).toEqual([1, 1, 1]);
});

test("a failed load is not kept", async () => {
  await expect(cached("f", () => Promise.reject(new Error("x")))).rejects.toThrow("x");
  expect(await cached("f", async () => 5)).toBe(5);
});

test("clearing one key reloads only that key", async () => {
  await cached("a", async () => 1);
  await cached("b", async () => 1);
  clearConfigCache("a");
  expect(await cached("a", async () => 2)).toBe(2);
  expect(await cached("b", async () => 2)).toBe(1);
});

test("off under Jest by default: every call loads", async () => {
  setConfigCacheEnabled(false);
  let loads = 0;
  await cached("z", async () => ++loads);
  await cached("z", async () => ++loads);
  expect(loads).toBe(2);
});
