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
import { onDocumentCreated, onDocumentDeleted, onDocumentUpdated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { getMessaging } from "firebase-admin/messaging";
import { logPracticeFor } from "./practice";
import { adminGrantFor, countPaidDay, onSubscriptionNotice, verifyPurchaseFor } from "./billing";
import { playApi } from "./playApi";
import { syncPublicProfile } from "./profiles";
import { onCallCreated, onCallUpdated, onChatMessage } from "./chat";
import {
  onAnnouncementCreated, onAnswerNotify, onDailyLikeChanged, onDailyPostWritten, onDailyReplyWritten,
  onQuestionNotify, onThoughtCommentNotify, onThoughtOfDay,
} from "./community";
import type { Messenger } from "./notify";
import { istDay } from "./time";

initializeApp();
const db = getFirestore();

const REGION = "asia-south1";

// Data-only, high priority: the app shows the notification itself, in the user's language
const messenger: Messenger = {
  async sendToTopic(topic, data) {
    await getMessaging().send({ topic, data, android: { priority: "high" } });
  },
  async sendToTokens(tokens, data, urgent) {
    const res = await getMessaging().sendEachForMulticast({
      tokens, data, android: { priority: "high", ttl: urgent ? 45_000 : 24 * 3600_000 },
    });
    return res.responses.flatMap((r, i) =>
      r.error?.code === "messaging/registration-token-not-registered" || r.error?.code === "messaging/invalid-argument" ? [tokens[i]] : []);
  },
};
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

export const thoughtCommentWritten = onDocumentWritten("thoughts/{thoughtId}/comments/{commentId}", async (e) => {
  const before = e.data?.before.data();
  const after = e.data?.after.data();
  await onCommentWritten(db, `thoughts/${e.params.thoughtId}`, e.params.commentId, before, after, new Date());
  await onThoughtCommentNotify(db, messenger, e.params.thoughtId, before, after);
});

export const questionWritten = onDocumentWritten("questions/{questionId}", async (e) => {
  const before = e.data?.before.data();
  const after = e.data?.after.data();
  await onQuestionWritten(db, e.params.questionId, before, after, new Date());
  await onQuestionNotify(db, messenger, e.params.questionId, before, after);
});

export const answerWritten = onDocumentWritten("questions/{questionId}/answers/{answerId}", async (e) => {
  const before = e.data?.before.data();
  const after = e.data?.after.data();
  await onAnswerWritten(db, e.params.questionId, e.params.answerId, before, after, new Date());
  await onAnswerNotify(db, messenger, e.params.questionId, before, after);
});

export const goalProgressWritten = onDocumentWritten("users/{uid}/goalProgress/{goalId}", (e) =>
  onGoalProgressWritten(db, e.params.uid, e.params.goalId, e.data?.before.data(), e.data?.after.data()));

// ---- Daily practices (design §7.4) ----

export const logPractice = onCall({ enforceAppCheck: enforceAppCheck }, async (req) => {
  const uid = requireUid(req);
  return logPracticeFor(db, uid, req.auth?.token.role, String((req.data ?? {}).kind ?? ""), new Date());
});

// ---- Profiles visible to others ----

export const userWritten = onDocumentWritten("users/{uid}", (e) =>
  syncPublicProfile(db, e.params.uid, e.data?.after.data()));

// ---- Notifications ----

export const announcementCreated = onDocumentCreated("announcements/{id}", (e) =>
  onAnnouncementCreated(messenger, e.params.id, e.data?.data()));

export const thoughtOfDayPush = onSchedule({ schedule: "0 6 * * *", timeZone: "Asia/Kolkata" }, async () => {
  await onThoughtOfDay(db, messenger, istDay(new Date()));
});

// ---- Daily Activities (paid members) ----

export const dailyPostWritten = onDocumentWritten("dailyPosts/{id}", (e) =>
  onDailyPostWritten(db, messenger, e.params.id, e.data?.before.data(), e.data?.after.data()));

export const dailyReplyWritten = onDocumentWritten("dailyPosts/{id}/replies/{replyId}", (e) =>
  onDailyReplyWritten(db, messenger, e.params.id, e.data?.before.data(), e.data?.after.data()));

export const dailyLikeAdded = onDocumentCreated("dailyPosts/{id}/likes/{uid}", (e) => onDailyLikeChanged(db, e.params.id, 1));
export const dailyLikeRemoved = onDocumentDeleted("dailyPosts/{id}/likes/{uid}", (e) => onDailyLikeChanged(db, e.params.id, -1));

// ---- Payments (design §7.9) ----

export const verifyPurchase = onCall({ enforceAppCheck: enforceAppCheck }, async (req) => {
  const uid = requireUid(req);
  return verifyPurchaseFor(db, playApi(), uid, (req.data ?? {}) as { productId?: string; purchaseToken?: string }, new Date());
});

export const adminGrant = onCall(async (req) => {
  requireUid(req);
  return adminGrantFor(db, req.auth?.token, (req.data ?? {}) as Record<string, never>, new Date());
});

// Play Console → Monetization setup → Real-time developer notifications → topic "play-rtdn"
export const playNotification = onMessagePublished("play-rtdn", async (e) => {
  const body = e.data.message.json as { subscriptionNotification?: { purchaseToken?: string } } | undefined;
  const token = body?.subscriptionNotification?.purchaseToken;
  if (token) await onSubscriptionNotice(db, playApi(), token, new Date());
});

export const paidDayTick = onSchedule({ schedule: "5 0 * * *", timeZone: "Asia/Kolkata" }, async () => {
  await countPaidDay(db, new Date());
});

// ---- Personal chat and calls (design §7.7) ----

export const chatMessageCreated = onDocumentCreated("chats/{memberUid}/messages/{id}", (e) =>
  onChatMessage(db, messenger, e.params.memberUid, e.data?.data()));

export const callCreated = onDocumentCreated("calls/{id}", (e) => onCallCreated(db, messenger, e.params.id, e.data?.data()));

export const callUpdated = onDocumentUpdated("calls/{id}", (e) =>
  onCallUpdated(db, e.params.id, e.data?.before.data(), e.data?.after.data()));
