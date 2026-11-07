import { computeUserLevel, DEFAULT_LEVELS } from "../src/levels";

test.each([
  [6, 1000, 1],
  [7, 7, 2],
  [90, 199, 2],
  [90, 200, 3],
  [89, 5000, 2],
  [365, 9999, 3],
  [365, 10000, 4],
  [364, 50000, 3],
])("activeDays %i, points %i -> level %i", (activeDays, points, level) => {
  expect(computeUserLevel({ activeDays, points }, DEFAULT_LEVELS)).toBe(level);
});
