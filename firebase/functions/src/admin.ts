import { randomUUID } from "crypto";
import { Timestamp, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { awardPoints } from "./points";
import { istDay } from "./time";

type Claims = Record<string, unknown> | undefined;
type SetClaims = (uid: string, claims: Record<string, unknown>) => Promise<void>;

function requireAdmin(token: Claims) {
  if (token?.role !== "admin") throw new HttpsError("permission-denied", "Admins only");
}

const normal = (e: unknown) => (typeof e === "string" ? e.trim().toLowerCase() : "");

/** config/admins.emails: people who become admins as soon as they sign in with that (verified) email. */
export async function adminEmails(db: Firestore): Promise<string[]> {
  const list = (await db.doc("config/admins").get()).get("emails");
  return Array.isArray(list) ? list.map(normal).filter(Boolean) : [];
}

/** Makes [uid] an admin if its verified email is on the list. Returns true when the role changed now. */
export async function applyAdminList(
  db: Firestore, uid: string, email: unknown, verified: boolean, currentRole: unknown, setClaims: SetClaims,
): Promise<boolean> {
  if (!verified || currentRole === "admin") return false;
  if (!(await adminEmails(db)).includes(normal(email))) return false;
  await setClaims(uid, { role: "admin" });
  await db.doc(`users/${uid}`).set({ role: "admin" }, { merge: true });
  return true;
}

/** Admin: give Moksha Points (a gift; shown to the member with the big coin animation). */
export async function givePointsFor(
  db: Firestore, token: Claims, input: { uids?: unknown; amount?: unknown; note?: unknown },
): Promise<{ given: number }> {
  requireAdmin(token);
  const uids = (Array.isArray(input.uids) ? input.uids : []).filter((u): u is string => typeof u === "string").slice(0, 500);
  const amount = Math.floor(Number(input.amount));
  if (uids.length === 0 || !(amount >= 1 && amount <= 100000)) throw new HttpsError("invalid-argument", "uids and an amount from 1 to 100000 are required");
  let given = 0;
  for (const uid of uids) {
    if (!(await db.doc(`users/${uid}`).get()).exists) continue;
    const ok = await awardPoints(db, uid, {
      eventKey: `gift:${Date.now()}:${randomUUID().slice(0, 8)}`,
      amount,
      reason: "gift",
      milestone: true,
      note: typeof input.note === "string" && input.note ? input.note.slice(0, 200) : undefined,
      extra: typeof input.note === "string" && input.note ? { lastGiftNote: input.note.slice(0, 200) } : undefined,
    });
    if (ok) given++;
  }
  return { given };
}

/** Numbers for the admin dashboard, counted on the server. */
export async function statsFor(db: Firestore, token: Claims, now: Date): Promise<Record<string, unknown>> {
  requireAdmin(token);
  const count = async (q: FirebaseFirestore.Query) => (await q.count().get()).data().count;
  const users = db.collection("users");
  const today = istDay(now);
  const dayStart = Timestamp.fromDate(new Date(`${today}T00:00:00+05:30`));
  const nowTs = Timestamp.fromDate(now);

  const [total, activeToday, newToday, l1, l2, l3, l4, m1, m2, m3, admins, swamiji, seniors] = await Promise.all([
    count(users), count(users.where("lastDailyOpen", "==", today)), count(users.where("createdAt", ">=", dayStart)),
    ...[1, 2, 3, 4].map((l) => count(users.where("userLevel", "==", l))),
    ...[1, 2, 3].map((l) => count(users.where("memberLevel", "==", l))),
    ...["admin", "swamiji", "senior_member"].map((r) => count(users.where("role", "==", r))),
  ]);

  const ents = await db.collectionGroup("entitlements").where("expiresAt", ">", nowTs).get();
  const paid = { membership: 0, chat: 0, call: 0 } as Record<string, number>;
  ents.forEach((d) => { if (d.id in paid) paid[d.id]++; });

  const pending = async (group: string) => count(db.collectionGroup(group).where("status", "==", "pending"));
  const [pComments, pAnswers, pReplies, pQuestions, pDaily, goalPhotos, reports] = await Promise.all([
    pending("comments"), pending("answers"), pending("replies"),
    count(db.collection("questions").where("status", "==", "pending")),
    count(db.collection("dailyPosts").where("status", "==", "pending")),
    count(db.collectionGroup("goalProgress").where("status", "==", "submitted")),
    count(db.collection("reports")),
  ]);

  const [questionsToday, dailyToday, chatsWaiting, contentTotal] = await Promise.all([
    count(db.collection("questions").where("createdAt", ">=", dayStart)),
    count(db.collection("dailyPosts").where("createdAt", ">=", dayStart)),
    count(db.collection("chats").where("unreadByStaff", "==", true)),
    count(db.collection("content")),
  ]);

  // The next 30 days without a scheduled Thought of the Day
  const ahead = await db.collection("thoughts").where("publishAt", ">=", dayStart).get();
  const have = new Set(ahead.docs.map((d) => d.id));
  const missingThoughts: string[] = [];
  for (let i = 0; i < 30; i++) {
    const d = new Date(new Date(`${today}T12:00:00+05:30`).getTime() + i * 86400000).toISOString().slice(0, 10);
    if (!have.has(d)) missingThoughts.push(d);
  }

  return {
    today,
    users: { total, activeToday, newToday, byLevel: [l1, l2, l3, l4], byMemberLevel: [m1, m2, m3], admins, swamiji, seniors },
    paid,
    moderation: { comments: pComments, answers: pAnswers, replies: pReplies, questions: pQuestions, dailyPosts: pDaily, goalPhotos, reports },
    activity: { questionsToday, dailyToday, chatsWaiting },
    content: { total: contentTotal },
    thoughts: { scheduledAhead: have.size, missingNext30: missingThoughts },
  };
}

/** Admin: apply the admin email list now to accounts that already exist. */
export async function applyAdminListNow(
  db: Firestore, token: Claims, lookup: (email: string) => Promise<{ uid: string; emailVerified: boolean } | null>, setClaims: SetClaims,
): Promise<{ promoted: string[]; notSignedUpYet: string[] }> {
  requireAdmin(token);
  const promoted: string[] = [];
  const notSignedUpYet: string[] = [];
  for (const email of await adminEmails(db)) {
    const u = await lookup(email);
    if (!u) { notSignedUpYet.push(email); continue; }
    const role = (await db.doc(`users/${u.uid}`).get()).get("role");
    if (await applyAdminList(db, u.uid, email, u.emailVerified, role, setClaims)) promoted.push(email);
  }
  return { promoted, notSignedUpYet };
}
