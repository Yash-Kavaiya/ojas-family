import { istDay } from "../src/time";

test("istDay uses Asia/Kolkata calendar day", () => {
  expect(istDay(new Date("2026-10-29T18:29:00Z"))).toBe("2026-10-29");
  expect(istDay(new Date("2026-10-29T18:31:00Z"))).toBe("2026-10-30");
});

test("localDay and offsetMinutes follow the phone's UTC offset", () => {
  const { localDay, offsetMinutes } = require("../src/time");
  expect(localDay(new Date("2026-11-09T03:00:00Z"), -300)).toBe("2026-11-08");
  expect(localDay(new Date("2026-11-09T03:00:00Z"), 330)).toBe("2026-11-09");
  expect(localDay(new Date("2026-11-08T11:00:00Z"), 780)).toBe("2026-11-09"); // New Zealand summer, +13
  expect(offsetMinutes(-300)).toBe(-300);
  expect(offsetMinutes(345)).toBe(345); // Nepal
  for (const bad of [undefined, null, "", "x", 7, 900, -800, 330.5]) expect(offsetMinutes(bad)).toBe(330);
});
