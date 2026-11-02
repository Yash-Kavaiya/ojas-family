import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { deleteAccountData, ensureUserDoc, setRoleFor } from "./auth";
import { claimDailyOpenFor } from "./daily";
import { addVideoFor, importPlaylistFor } from "./content";
import { onGoalProgressWritten } from "./goals";
import { onAnswerWritten, onCommentWritten, onLikeChanged, onQuestionWritten } from "./social";
import { onDocumentCreated, onDocumentDeleted, onDocumentWritten } from "firebase-functions/v2/firestore";

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

// No auth "user created" trigger: that needs a 1st-gen function. Server fields are filled by ensureUserDoc
// from claimDailyOpen, which every signed-in session calls, and clients may create their own profile fields.

export const claimDailyOpen = onCall({ enforceAppCheck: enforceAppCheck }, async (req) => {
  const uid = requireUid(req);
  // The profile may not exist yet if this runs before onUserCreate finishes
  await ensureUserDoc(db, uid);
  return claimDailyOpenFor(db, uid, new Date());
});

export const deleteAccount = onCall({ enforceAppCheck: enforceAppCheck }, async (req) => {
  const uid = requireUid(req);
  await deleteAccountData(db, uid, {
    deleteFiles: (prefix) => getStorage().bucket().deleteFiles({ prefix }),
    deleteAuthUser: (target) => getAuth().deleteUser(target),
  });
  return { deleted: true };
});

export const setRole = onCall(async (req) => {
  requireUid(req);
  const { uid, role } = (req.data ?? {}) as { uid?: string; role?: string };
  if (!uid || !role) throw new HttpsError("invalid-argument", "uid and role are required");
  await setRoleFor(db, req.auth?.token, uid, role, (target, claims) => getAuth().setCustomUserClaims(target, claims));
  return { ok: true };
});

export const addVideo = onCall(async (req) => {
  requireUid(req);
  return addVideoFor(db, req.auth?.token, (req.data ?? {}) as { url: string; section: string; title?: string });
});

export const importPlaylist = onCall({ timeoutSeconds: 120 }, async (req) => {
  requireUid(req);
  return importPlaylistFor(db, req.auth?.token, (req.data ?? {}) as { playlistId: string; section: string });
});

// ---- Social triggers: counters and points stay server-side ----

export const thoughtLikeAdded = onDocumentCreated("thoughts/{thoughtId}/likes/{uid}", (e) =>
  onLikeChanged(db, `thoughts/${e.params.thoughtId}`, e.params.uid, 1, new Date()));

export const thoughtLikeRemoved = onDocumentDeleted("thoughts/{thoughtId}/likes/{uid}", (e) =>
  onLikeChanged(db, `thoughts/${e.params.thoughtId}`, e.params.uid, -1, new Date()));

export const questionLikeAdded = onDocumentCreated("questions/{questionId}/likes/{uid}", (e) =>
  onLikeChanged(db, `questions/${e.params.questionId}`, e.params.uid, 1, new Date()));

export const questionLikeRemoved = onDocumentDeleted("questions/{questionId}/likes/{uid}", (e) =>
  onLikeChanged(db, `questions/${e.params.questionId}`, e.params.uid, -1, new Date()));

export const thoughtCommentWritten = onDocumentWritten("thoughts/{thoughtId}/comments/{commentId}", (e) =>
  onCommentWritten(db, `thoughts/${e.params.thoughtId}`, e.params.commentId,
    e.data?.before.data(), e.data?.after.data(), new Date()));

export const questionWritten = onDocumentWritten("questions/{questionId}", (e) =>
  onQuestionWritten(db, e.params.questionId, e.data?.before.data(), e.data?.after.data(), new Date()));

export const answerWritten = onDocumentWritten("questions/{questionId}/answers/{answerId}", (e) =>
  onAnswerWritten(db, e.params.questionId, e.params.answerId, e.data?.before.data(), e.data?.after.data(), new Date()));

export const goalProgressWritten = onDocumentWritten("users/{uid}/goalProgress/{goalId}", (e) =>
  onGoalProgressWritten(db, e.params.uid, e.params.goalId, e.data?.before.data(), e.data?.after.data()));
