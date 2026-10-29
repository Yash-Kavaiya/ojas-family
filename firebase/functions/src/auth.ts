import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

export const ROLES = ["member", "senior_member", "swamiji", "admin"] as const;
export type RoleId = (typeof ROLES)[number];

const SERVER_DEFAULTS = {
  mokshaPoints: 0,
  userLevel: 1,
  memberLevel: 0,
  paidMonths: 0,
  role: "member",
  activeDays: 0,
};

/**
 * Gives a new user their server-owned fields. Safe to run more than once and after the client has
 * already written its own profile fields: only missing server fields are filled in.
 */
export async function ensureUserDoc(db: Firestore, uid: string): Promise<void> {
  const ref = db.doc(`users/${uid}`);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const data = snap.data() ?? {};
    const missing = Object.fromEntries(Object.entries(SERVER_DEFAULTS).filter(([k]) => data[k] === undefined));
    if (!snap.exists) {
      tx.set(ref, { ...missing, language: "en", profileComplete: false, createdAt: FieldValue.serverTimestamp() });
    } else if (Object.keys(missing).length > 0 || data.createdAt === undefined) {
      tx.set(ref, { ...missing, createdAt: data.createdAt ?? FieldValue.serverTimestamp() }, { merge: true });
    }
  });
}

// Removes the profile and every subcollection (pointEvents, later entitlements, practiceLog, ...)
export async function deleteUserData(db: Firestore, uid: string): Promise<void> {
  await db.recursiveDelete(db.doc(`users/${uid}`));
}

export async function setRoleFor(
  db: Firestore,
  callerToken: Record<string, unknown> | undefined,
  uid: string,
  role: string,
  setClaims: (uid: string, claims: Record<string, unknown>) => Promise<void> = async () => {},
): Promise<void> {
  if (callerToken?.role !== "admin") throw new HttpsError("permission-denied", "Only admins can assign roles");
  if (!ROLES.includes(role as RoleId)) throw new HttpsError("invalid-argument", `Unknown role: ${role}`);
  await setClaims(uid, { role });
  await db.doc(`users/${uid}`).set({ role }, { merge: true });
}
