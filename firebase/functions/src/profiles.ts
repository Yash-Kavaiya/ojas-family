import type { Firestore } from "firebase-admin/firestore";

/**
 * publicProfiles/{uid} is what other people may see of a member (design §7.1, App Settings): the age is
 * kept as a birth month so the exact date of birth never leaves the owner's own document. Members can
 * hide the whole profile or only their achieved goals.
 */
export function publicProfileOf(user: Record<string, unknown>): Record<string, unknown> {
  const dob = typeof user.dob === "string" ? user.dob : "";
  return {
    displayName: user.displayName ?? "",
    photoUrl: user.photoUrl ?? "",
    birthMonth: /^\d{4}-\d{2}/.test(dob) ? dob.slice(0, 7) : "",
    gender: user.gender ?? "",
    country: user.country ?? "",
    state: user.state ?? "",
    city: user.city ?? "",
    mokshaPoints: user.mokshaPoints ?? 0,
    userLevel: user.userLevel ?? 1,
    memberLevel: user.memberLevel ?? 0,
    role: user.role ?? "member",
    hidden: user.hideProfile === true,
    achievedGoals: user.hideGoals === true ? [] : (user.achievedGoalIds ?? []),
  };
}

// Firestore may return map keys in a different order than they were written
const stable = (o: Record<string, unknown>) => JSON.stringify(Object.keys(o).sort().map((k) => [k, o[k]]));

export async function syncPublicProfile(db: Firestore, uid: string, after: Record<string, unknown> | undefined): Promise<void> {
  const ref = db.doc(`publicProfiles/${uid}`);
  if (!after) {
    await ref.delete();
    return;
  }
  const next = publicProfileOf(after);
  const cur = (await ref.get()).data();
  // Skip no-op writes: most user updates (points ledger, tokens) change nothing public
  if (cur && stable(cur) === stable(next)) return;
  await ref.set(next);
}
