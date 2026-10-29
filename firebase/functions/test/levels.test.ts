import { computeUserLevel, DEFAULT_LEVELS } from "../src/levels";

test.each([
  [6, 1000, 1],
  [7, 7, 2],
  [90, 499, 2],
  [90, 500, 3],
  [365, 99999, 3],
  [365, 100000, 4],
])("activeDays %i, points %i -> level %i", (activeDays, points, level) => {
  expect(computeUserLevel({ activeDays, points }, DEFAULT_LEVELS)).toBe(level);
});
