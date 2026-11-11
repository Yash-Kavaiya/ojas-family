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

/**
 * Removes everything a member left behind: the profile and its subcollections (pointEvents, entitlements,
 * practiceLog, ...), both conversations, bookings and call records, presence, and what they posted
 * (questions, Daily Activities posts, comments, answers and replies). Counters on other people's
 * posts are kept right by the usual delete triggers.
 */
export async function deleteUserData(db: Firestore, uid: string): Promise<void> {
  const own = async (q: FirebaseFirestore.Query) => {
    for (const d of (await q.get()).docs) await db.recursiveDelete(d.ref);
  };
  await own(db.collection("questions").where("authorUid", "==", uid));
  await own(db.collection("dailyPosts").where("authorUid", "==", uid));
  for (const group of ["comments", "answers", "replies"]) await own(db.collectionGroup(group).where("authorUid", "==", uid));
  await own(db.collection("callRequests").where("memberUid", "==", uid));
  await own(db.collection("calls").where("memberUid", "==", uid));

  await db.recursiveDelete(db.doc(`users/${uid}`));
  await db.recursiveDelete(db.doc(`chats/${uid}`));
  await db.recursiveDelete(db.doc(`adminChats/${uid}`));
  await db.doc(`presence/${uid}`).delete();
  await db.doc(`publicProfiles/${uid}`).delete();
}

export interface AccountCleanup {
  deleteFiles: (prefix: string) => Promise<unknown>;
  deleteAuthUser: (uid: string) => Promise<void>;
}

/**
 * Deletes everything we hold for a user. Storage cleanup is best-effort: a missing bucket or a file
 * error must not leave a signed-in account with no profile behind.
 */
export async function deleteAccountData(db: Firestore, uid: string, cleanup: AccountCleanup): Promise<void> {
  // Photos and posts, then the files sent in either conversation
  for (const prefix of [`users/${uid}/`, `chats/${uid}/`, `adminChats/${uid}/`]) {
    try {
      await cleanup.deleteFiles(prefix);
    } catch (e) {
      console.warn(`Storage cleanup failed for ${prefix}`, e);
    }
  }
  await deleteUserData(db, uid);
  // Members would otherwise keep calling and messaging an account that is gone
  const staff = db.doc("config/staff");
  if ((await staff.get()).get("swamijiUid") === uid) await staff.set({ swamijiUid: FieldValue.delete() }, { merge: true });
  await cleanup.deleteAuthUser(uid);
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
  // Members call and chat with this account by default
  if (role === "swamiji") await db.doc("config/staff").set({ swamijiUid: uid }, { merge: true });
}
