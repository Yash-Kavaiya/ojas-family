import type { Firestore } from "firebase-admin/firestore";

export interface Threshold {
  days: number;
  points: number;
}

export interface LevelConfig {
  l2: Threshold;
  l3: Threshold;
  l4: Threshold;
  dailyCaps: { like: number; comment: number; question: number };
}

// Spec defaults; admins tune them in Firestore config/levels without an app release
export const DEFAULT_LEVELS: LevelConfig = {
  l2: { days: 7, points: 7 },
  l3: { days: 90, points: 200 }, // owner decision 2026-11-07 (was 500: unreachable for a Level 2 user)
  l4: { days: 365, points: 10000 }, // owner decision 2026-11-07 (1,00,000 exceeded the ~27,700/year maximum)
  dailyCaps: { like: 50, comment: 20, question: 5 },
};

export function computeUserLevel(input: { activeDays: number; points: number }, cfg: LevelConfig): 1 | 2 | 3 | 4 {
  const meets = (t: Threshold) => input.activeDays >= t.days && input.points >= t.points;
  if (meets(cfg.l4)) return 4;
  if (meets(cfg.l3)) return 3;
  if (meets(cfg.l2)) return 2;
  return 1;
}

export async function loadLevelConfig(db: Firestore): Promise<LevelConfig> {
  const snap = await db.doc("config/levels").get();
  const data = (snap.data() ?? {}) as Partial<LevelConfig>;
  return {
    l2: { ...DEFAULT_LEVELS.l2, ...data.l2 },
    l3: { ...DEFAULT_LEVELS.l3, ...data.l3 },
    l4: { ...DEFAULT_LEVELS.l4, ...data.l4 },
    dailyCaps: { ...DEFAULT_LEVELS.dailyCaps, ...data.dailyCaps },
  };
}
