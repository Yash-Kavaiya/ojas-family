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
import { completePracticeFor, startPracticeFor } from "./practiceMedia";
import { turnCredentialsFor } from "./turn";
import { adminGrantFor, countPaidDay, onSubscriptionNotice, verifyPurchaseFor } from "./billing";
import { playApi } from "./playApi";
import { refreshAuthorSnapshots, syncPublicProfile } from "./profiles";
import { onCallCreated, onCallUpdated, onChatMessage, sweepRingingCalls } from "./chat";
import { onCallRequestWritten, sendCallReminders } from "./schedule";
import {
  onAnnouncementCommentNotify, onAnnouncementCountChanged, onCommentLikeChanged, onAnnouncementCreated, onAnswerNotify, onDailyLikeChanged, onDailyPostWritten, onDailyReplyWritten,
  onQuestionNotify, onThoughtCommentNotify, onThoughtOfDay,
} from "./community";
import type { Messenger } from "./notify";
import { istDay } from "./time";
import { applyAdminList, applyAdminListNow, givePointsFor, statsFor } from "./admin";
import { reverseTransferFor, setTransfersFrozenFor, staffAdjustPointsFor, transferPointsFor, updatePointsConfigFor } from "./transfer";
import { onProfileWritten } from "./profilePoints";
import { askSwamijiFor, deleteAiChatFor, rateAiAnswerFor, remainingToday } from "./ai";
import { geminiDeps } from "./aiGemini";

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
  // Emails on the admin list become admins the first time they sign in; the app then refreshes its token
  const roleChanged = await applyAdminList(db, uid, req.auth?.token.email, req.auth?.token.email_verified === true,
    req.auth?.token.role, (target, claims) => getAuth().setCustomUserClaims(target, claims));
  return { ...(await claimDailyOpenFor(db, uid, new Date())), roleChanged };
});

// ---- Admin website ----

export const syncRole = onCall(async (req) => {
  const uid = requireUid(req);
  await ensureUserDoc(db, uid);
  const roleChanged = await applyAdminList(db, uid, req.auth?.token.email, req.auth?.token.email_verified === true,
    req.auth?.token.role, (target, claims) => getAuth().setCustomUserClaims(target, claims));
  return { roleChanged };
});

// Up to 500 members in one go, one transaction each
export const adminGivePoints = onCall({ timeoutSeconds: 300 }, async (req) => {
  requireUid(req);
  return givePointsFor(db, req.auth?.token, (req.data ?? {}) as Record<string, unknown>);
});

export const adminStats = onCall({ timeoutSeconds: 120 }, async (req) => {
  requireUid(req);
  return statsFor(db, req.auth?.token, new Date());
});

export const adminApplyAdminList = onCall(async (req) => {
  requireUid(req);
  return applyAdminListNow(db, req.auth?.token, async (email) => {
    try {
      const u = await getAuth().getUserByEmail(email);
      return { uid: u.uid, emailVerified: u.emailVerified };
    } catch {
      return null;
    }
  }, (target, claims) => getAuth().setCustomUserClaims(target, claims));
});

// Removes posts, conversations and files too, which takes a while for an active member
export const deleteAccount = onCall({ enforceAppCheck: enforceAppCheck, timeoutSeconds: 300 }, async (req) => {
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

// Guided practice media: one Moksha Point for finishing each practice once a day
export const startPractice = onCall({ enforceAppCheck: enforceAppCheck }, async (req) => {
  const uid = requireUid(req);
  return startPracticeFor(db, uid, req.auth?.token.role, (req.data ?? {}).section, new Date());
});

export const completePractice = onCall({ enforceAppCheck: enforceAppCheck }, async (req) => {
  const uid = requireUid(req);
  return completePracticeFor(db, uid, req.auth?.token.role, (req.data ?? {}).section, new Date());
});

// ---- Profiles visible to others ----

export const userWritten = onDocumentWritten("users/{uid}", async (e) => {
  await syncPublicProfile(db, e.params.uid, e.data?.after.data());
  await refreshAuthorSnapshots(db, e.params.uid, e.data?.before.data(), e.data?.after.data());
  // Points for each profile item filled in, and a bonus at 100%; once each
  await onProfileWritten(db, e.params.uid, e.data?.before.data(), e.data?.after.data());
});

// ---- Moksha Points: member-to-member gifts and Swamiji/admin control ----

export const transferPoints = onCall({ enforceAppCheck: enforceAppCheck }, async (req) => {
  const uid = requireUid(req);
  return transferPointsFor(db, uid, (req.data ?? {}) as Record<string, unknown>, new Date());
});

export const staffAdjustPoints = onCall(async (req) => {
  const uid = requireUid(req);
  return staffAdjustPointsFor(db, req.auth?.token, uid, (req.data ?? {}) as Record<string, unknown>);
});

export const reverseTransfer = onCall(async (req) => {
  const uid = requireUid(req);
  return reverseTransferFor(db, req.auth?.token, uid, (req.data ?? {}) as Record<string, unknown>);
});

export const setTransfersFrozen = onCall(async (req) => {
  const uid = requireUid(req);
  return setTransfersFrozenFor(db, req.auth?.token, uid, (req.data ?? {}) as Record<string, unknown>);
});

export const updatePointsConfig = onCall(async (req) => {
  const uid = requireUid(req);
  return updatePointsConfigFor(db, req.auth?.token, uid, (req.data ?? {}) as Record<string, unknown>);
});

// ---- Ask Swamiji (AI): Gemini answers grounded in Ojas Healing's teachings ----

// Streams the answer as it is written when the app asks for a stream; the final result has it all
export const askSwamiji = onCall({ enforceAppCheck: enforceAppCheck, timeoutSeconds: 120, memory: "512MiB" }, async (req, res) => {
  const uid = requireUid(req);
  return askSwamijiFor(db, geminiDeps(db), uid, req.auth?.token.role, (req.data ?? {}) as Record<string, unknown>, new Date(),
    (piece) => { void res?.sendChunk({ text: piece }); });
});

export const aiQuota = onCall(async (req) => {
  const uid = requireUid(req);
  return remainingToday(db, uid, req.auth?.token.role, new Date());
});

export const rateAiAnswer = onCall(async (req) => {
  const uid = requireUid(req);
  return rateAiAnswerFor(db, uid, (req.data ?? {}) as Record<string, unknown>);
});

export const deleteAiChat = onCall(async (req) => {
  const uid = requireUid(req);
  return deleteAiChatFor(db, uid, (req.data ?? {}) as Record<string, unknown>);
});

// ---- Notifications ----

export const announcementCreated = onDocumentCreated("announcements/{id}", (e) =>
  onAnnouncementCreated(messenger, e.params.id, e.data?.data()));

export const thoughtOfDayPush = onSchedule({ schedule: "0 6 * * *", timeZone: "Asia/Kolkata" }, async () => {
  await onThoughtOfDay(db, messenger, istDay(new Date()));
});

// ---- Daily Activities (paid members) ----

export const announcementLikeAdded = onDocumentCreated("announcements/{id}/likes/{uid}", (e) =>
  onAnnouncementCountChanged(db, e.params.id, "likeCount", 1));
export const announcementLikeRemoved = onDocumentDeleted("announcements/{id}/likes/{uid}", (e) =>
  onAnnouncementCountChanged(db, e.params.id, "likeCount", -1));
export const announcementCommentWritten = onDocumentWritten("announcements/{id}/comments/{commentId}", async (e) => {
  const before = e.data?.before.exists, after = e.data?.after.exists;
  if (!before && after) await onAnnouncementCountChanged(db, e.params.id, "commentCount", 1);
  if (before && !after) await onAnnouncementCountChanged(db, e.params.id, "commentCount", -1);
  await onAnnouncementCommentNotify(db, messenger, e.params.id, e.data?.before.data(), e.data?.after.data());
});

export const dailyPostWritten = onDocumentWritten("dailyPosts/{id}", (e) =>
  onDailyPostWritten(db, messenger, e.params.id, e.data?.before.data(), e.data?.after.data()));

export const dailyReplyWritten = onDocumentWritten("dailyPosts/{id}/replies/{replyId}", (e) =>
  onDailyReplyWritten(db, messenger, e.params.id, e.data?.before.data(), e.data?.after.data()));

// Likes on comments, answers and replies keep the comment's own likeCount
const commentLikePaths = [
  "thoughts/{p}/comments/{c}",
  "announcements/{p}/comments/{c}",
  "questions/{p}/answers/{c}",
  "dailyPosts/{p}/replies/{c}",
];
const commentPathOf = (pattern: string, params: Record<string, string>) =>
  pattern.replace("{p}", params.p).replace("{c}", params.c);
export const thoughtCommentLikeAdded = onDocumentCreated(`${commentLikePaths[0]}/likes/{uid}`, (e) => onCommentLikeChanged(db, commentPathOf(commentLikePaths[0], e.params), 1));
export const thoughtCommentLikeRemoved = onDocumentDeleted(`${commentLikePaths[0]}/likes/{uid}`, (e) => onCommentLikeChanged(db, commentPathOf(commentLikePaths[0], e.params), -1));
export const announcementCommentLikeAdded = onDocumentCreated(`${commentLikePaths[1]}/likes/{uid}`, (e) => onCommentLikeChanged(db, commentPathOf(commentLikePaths[1], e.params), 1));
export const announcementCommentLikeRemoved = onDocumentDeleted(`${commentLikePaths[1]}/likes/{uid}`, (e) => onCommentLikeChanged(db, commentPathOf(commentLikePaths[1], e.params), -1));
export const answerLikeAdded = onDocumentCreated(`${commentLikePaths[2]}/likes/{uid}`, (e) => onCommentLikeChanged(db, commentPathOf(commentLikePaths[2], e.params), 1));
export const answerLikeRemoved = onDocumentDeleted(`${commentLikePaths[2]}/likes/{uid}`, (e) => onCommentLikeChanged(db, commentPathOf(commentLikePaths[2], e.params), -1));
export const dailyReplyLikeAdded = onDocumentCreated(`${commentLikePaths[3]}/likes/{uid}`, (e) => onCommentLikeChanged(db, commentPathOf(commentLikePaths[3], e.params), 1));
export const dailyReplyLikeRemoved = onDocumentDeleted(`${commentLikePaths[3]}/likes/{uid}`, (e) => onCommentLikeChanged(db, commentPathOf(commentLikePaths[3], e.params), -1));

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

export const adminChatMessageCreated = onDocumentCreated("adminChats/{memberUid}/messages/{id}", (e) =>
  onChatMessage(db, messenger, e.params.memberUid, e.data?.data(), "admin"));

export const callCreated = onDocumentCreated("calls/{id}", (e) => onCallCreated(db, messenger, e.params.id, e.data?.data()));

// ---- Scheduled calls: request, confirm, reminders ----

export const callRequestWritten = onDocumentWritten("callRequests/{id}", (e) =>
  onCallRequestWritten(db, messenger, e.params.id, e.data?.before.data(), e.data?.after.data()));

export const callReminders = onSchedule({ schedule: "every 5 minutes", timeZone: "Asia/Kolkata" }, async () => {
  await sendCallReminders(db, messenger, new Date());
});

export const callUpdated = onDocumentUpdated("calls/{id}", (e) =>
  onCallUpdated(db, e.params.id, e.data?.before.data(), e.data?.after.data(), messenger));

// Short-lived relay credentials so calls get through networks that block direct connections
export const turnCredentials = onCall({ enforceAppCheck: enforceAppCheck }, async (req) => {
  requireUid(req);
  return turnCredentialsFor(db, (url, init) => fetch(url, init));
});

// A ring nobody answered within a minute becomes a missed call
export const sweepCalls = onSchedule({ schedule: "every 1 minutes", timeZone: "Asia/Kolkata" }, async () => {
  await sweepRingingCalls(db, new Date());
});
