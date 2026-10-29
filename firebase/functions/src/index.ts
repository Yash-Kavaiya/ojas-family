import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import * as functionsV1 from "firebase-functions/v1";
import { deleteUserData, ensureUserDoc, setRoleFor } from "./auth";
import { claimDailyOpenFor } from "./daily";

initializeApp();
const db = getFirestore();

const REGION = "asia-south1";
setGlobalOptions({ region: REGION, maxInstances: 10 });

// Turned on (functions/.env: ENFORCE_APP_CHECK=true) once the app ships App Check with Play Integrity
// and debug tokens are registered; until then callables only require a signed-in user
const enforceAppCheck = process.env.ENFORCE_APP_CHECK === "true";

function requireUid(req: CallableRequest): string {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return req.auth.uid;
}

// v1 trigger: v2 has no plain "user created" event without Identity Platform blocking functions
export const onUserCreate = functionsV1
  .region(REGION)
  .auth.user()
  .onCreate((user) => ensureUserDoc(db, user.uid));

export const claimDailyOpen = onCall({ enforceAppCheck: enforceAppCheck }, async (req) => {
  const uid = requireUid(req);
  // The profile may not exist yet if this runs before onUserCreate finishes
  await ensureUserDoc(db, uid);
  return claimDailyOpenFor(db, uid, new Date());
});

export const deleteAccount = onCall({ enforceAppCheck: enforceAppCheck }, async (req) => {
  const uid = requireUid(req);
  await deleteUserData(db, uid);
  await getStorage().bucket().deleteFiles({ prefix: `users/${uid}/` });
  await getAuth().deleteUser(uid);
  return { deleted: true };
});

export const setRole = onCall(async (req) => {
  requireUid(req);
  const { uid, role } = (req.data ?? {}) as { uid?: string; role?: string };
  if (!uid || !role) throw new HttpsError("invalid-argument", "uid and role are required");
  await setRoleFor(db, req.auth?.token, uid, role, (target, claims) => getAuth().setCustomUserClaims(target, claims));
  return { ok: true };
});
