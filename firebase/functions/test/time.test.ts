import { istDay } from "../src/time";

test("istDay uses Asia/Kolkata calendar day", () => {
  expect(istDay(new Date("2026-10-29T18:29:00Z"))).toBe("2026-10-29");
  expect(istDay(new Date("2026-10-29T18:31:00Z"))).toBe("2026-10-30");
});
