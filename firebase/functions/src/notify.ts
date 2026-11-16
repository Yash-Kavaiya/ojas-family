import { FieldValue, type Firestore } from "firebase-admin/firestore";

/**
 * Push messages are data-only: the app builds the visible notification itself, so it is shown in the
 * user's language and the app can drop one the user has switched off.
 *
 * Every notification also has a line in the app's bell list, so it is not lost when the phone's tray is
 * cleared: personal ones in users/{uid}/notifications, posts everyone gets in broadcasts (and Activity
 * posts, which only paid members may see, in paidBroadcasts).
 *
 * Settings are grouped: Critical, Thought, Activity, Announcement and Q&A. Each group has a switch, and
 * every group but Critical has three more: Post, Like & Reply, and Mention.
 */
export const TOPICS = ["announcements", "thoughtOfDay", "communityPosts", "dailyPosts"] as const;
export type Topic = (typeof TOPICS)[number];

export const GROUPS = ["critical", "thought", "activity", "announcement", "qa"] as const;
export type Group = (typeof GROUPS)[number];
export type Action = "post" | "likeReply" | "mention";

/**
 * What a personal notification is about. "chat" is a chat message (counted on the Ask tab, not listed in
 * the bell); "ring" is an incoming call, which rings whatever the Critical switch says.
 */
export type Kind = { group: "critical" } | { group: Exclude<Group, "critical">; action: Action } | "chat" | "ring";

export interface PushMessage {
  type: string; // what happened, e.g. "announcement", "answer", "chat", "call"
  title?: string; // user content (post title, sender name); the app adds the localized wording
  body?: string;
  target?: string; // what to open, e.g. "questions/abc"
  fromUid?: string;
  [extra: string]: string | undefined;
}

export interface Messenger {
  sendToTopic(topic: string, data: Record<string, string>): Promise<void>;
  /** Returns the tokens FCM rejected as no longer registered. */
  sendToTokens(tokens: string[], data: Record<string, string>, urgent: boolean): Promise<string[]>;
}

function clean(msg: PushMessage): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(msg)) if (v !== undefined) out[k] = String(v).slice(0, 1000);
  return out;
}

/**
 * Settings saved before the groups existed, and what each now means. A member who had switched one off
 * keeps it off; the new key wins once it is set.
 */
const OLD_KEYS: Record<string, string> = {
  "announcement.post": "announcements",
  "thought.post": "thoughtOfDay",
  "qa.post": "communityPosts",
  "qa.likeReply": "communityInteractions",
  "qa.mention": "communityMentions",
  "thought.likeReply": "communityInteractions",
  "thought.mention": "communityMentions",
  "announcement.likeReply": "communityInteractions",
  "announcement.mention": "communityMentions",
  "activity.post": "dailyPosts",
  "activity.likeReply": "dailyReplies",
  "activity.mention": "dailyMentions",
};

/** Whether a switch is on. Everything is on until the member turns it off. */
export function switchOn(prefs: Record<string, unknown> | undefined, group: Group, action?: Action): boolean {
  if (prefs?.all === false || prefs?.[group] === false) return false;
  if (!action) return true;
  const key = `${group}.${action}`;
  if (key in (prefs ?? {})) return prefs?.[key] !== false;
  const old = OLD_KEYS[key];
  return !(old && prefs?.[old] === false);
}

export function wantsPersonal(prefs: Record<string, unknown> | undefined, kind: Kind): boolean {
  // Chat and calls are paid one-to-one services: only the master switch silences them
  if (kind === "chat" || kind === "ring") return prefs?.all !== false;
  return switchOn(prefs, kind.group, "action" in kind ? kind.action : undefined);
}

const groupOf = (kind: Kind): Group | null => (typeof kind === "string" ? null : kind.group);
const actionOf = (kind: Kind): Action | "" => (typeof kind === "string" || !("action" in kind) ? "" : kind.action);

/** The tray notification's category: the app uses it to pick the Android channel. */
const categoryOf = (kind: Kind): string => (typeof kind === "string" ? (kind === "ring" ? "call" : "chat") : kind.group);

/** A bell-list id from an event: letters, digits, "_" and "-" only, so it is a valid document id. */
export function inboxId(key: string): string {
  return key.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 300);
}

const INBOX_FIELDS = (msg: PushMessage, group: Group, action: Action | "") => ({
  group, action, type: msg.type,
  title: (msg.title ?? "").slice(0, 200), body: (msg.body ?? "").slice(0, 500),
  target: msg.target ?? "", fromUid: msg.fromUid ?? "",
});

export async function notifyTopic(m: Messenger, topic: Topic, msg: PushMessage): Promise<void> {
  try {
    await m.sendToTopic(topic, { ...clean(msg), category: topic });
  } catch (e) {
    console.warn(`push to topic ${topic} failed`, e);
  }
}

/**
 * A new post everyone may get (a thought, an announcement, a question, an Activity post): pushed to its
 * topic and kept as one line for the bell list, written once however many members there are.
 */
export async function announcePost(db: Firestore, m: Messenger, topic: Topic, key: string, msg: PushMessage): Promise<void> {
  const group: Group = topic === "announcements" ? "announcement" : topic === "thoughtOfDay" ? "thought" : topic === "communityPosts" ? "qa" : "activity";
  // Activity is for paid members: its lines live where only they can read them
  const col = group === "activity" ? "paidBroadcasts" : "broadcasts";
  try {
    await db.doc(`${col}/${inboxId(key)}`).set({ ...INBOX_FIELDS(msg, group, "post"), createdAt: FieldValue.serverTimestamp() });
  } catch (e) {
    console.warn(`bell line for ${key} failed`, e);
  }
  await notifyTopic(m, topic, msg);
}

/**
 * Tells one member. [key] names the event for the bell list; when the same key comes again while its line
 * is still unread (a second like on the same post), the line is updated and counted rather than repeated.
 */
export async function notifyUser(db: Firestore, m: Messenger, uid: string, kind: Kind, msg: PushMessage, key?: string): Promise<void> {
  if (!uid || uid === msg.fromUid) return;
  const ref = db.doc(`users/${uid}`);
  const user = (await ref.get()).data();
  if (!user || !wantsPersonal(user.notificationPrefs, kind)) return;
  const group = groupOf(kind);
  let count = 1;
  if (group) {
    try {
      count = await keepInBell(db, uid, group, actionOf(kind), msg, key);
    } catch (e) {
      console.warn(`bell line for ${uid} failed`, e);
    }
  }
  const tokens = ((user.fcmTokens ?? []) as unknown[]).filter((t): t is string => typeof t === "string").slice(-10);
  if (tokens.length === 0) return;
  try {
    const data = { ...clean(msg), category: categoryOf(kind), ...(count > 1 ? { count: String(count) } : {}) };
    const dead = await m.sendToTokens(tokens, data, kind === "ring");
    if (dead.length > 0) await ref.update({ fcmTokens: FieldValue.arrayRemove(...dead) });
  } catch (e) {
    console.warn(`push to ${uid} failed`, e);
  }
}

/** Writes or updates the member's bell line; returns how many events it now stands for. */
async function keepInBell(db: Firestore, uid: string, group: Group, action: Action | "", msg: PushMessage, key?: string): Promise<number> {
  const id = inboxId(key ?? `${msg.type}:${msg.target ?? ""}:${msg.fromUid ?? ""}:${Date.now()}`);
  const ref = db.doc(`users/${uid}/notifications/${id}`);
  return db.runTransaction(async (tx) => {
    const old = await tx.get(ref);
    // Still unread: one line, counted ("Meera and 2 others liked your post"); read: a fresh line
    const count = old.exists && old.get("read") === false ? Number(old.get("count") ?? 1) + 1 : 1;
    tx.set(ref, { ...INBOX_FIELDS(msg, group, action), count, read: false, createdAt: FieldValue.serverTimestamp() });
    return count;
  });
}

/** Mentioned uids written by the app (picked from people in the thread), capped and de-duplicated. */
export function mentionsOf(data: Record<string, unknown> | undefined): string[] {
  const raw = Array.isArray(data?.mentions) ? data?.mentions : [];
  return [...new Set((raw as unknown[]).filter((u): u is string => typeof u === "string"))].slice(0, 10);
}

/** Bell lines are kept for 30 days. */
export const BELL_DAYS = 30;

/** Removes bell lines older than [BELL_DAYS]; runs once a day. Returns how many went. */
export async function sweepBell(db: Firestore, now: Date): Promise<number> {
  const cutoff = new Date(now.getTime() - BELL_DAYS * 24 * 3600 * 1000);
  let removed = 0;
  const queries = [
    db.collectionGroup("notifications").where("createdAt", "<", cutoff),
    db.collection("broadcasts").where("createdAt", "<", cutoff),
    db.collection("paidBroadcasts").where("createdAt", "<", cutoff),
  ];
  for (const q of queries) {
    for (;;) {
      const snap = await q.limit(400).get();
      if (snap.empty) break;
      const batch = db.batch();
      snap.docs.forEach((d) => batch.delete(d.ref));
      await batch.commit();
      removed += snap.size;
      if (snap.size < 400) break;
    }
  }
  return removed;
}
