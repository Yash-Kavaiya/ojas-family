import { FieldValue, type Firestore } from "firebase-admin/firestore";

/**
 * Push messages are data-only: the app builds the visible notification itself, so it is shown in the
 * user's language and the app can drop one the user has switched off.
 *
 * Broadcast categories go to FCM topics the app subscribes to from Settings; personal categories go to
 * the user's own tokens after checking users/{uid}.notificationPrefs.
 */
export const TOPICS = ["announcements", "thoughtOfDay", "communityPosts", "dailyPosts"] as const;
export type PersonalCategory =
  | "communityInteractions" | "communityMentions" | "dailyReplies" | "dailyMentions" | "chat" | "call";

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

export function wantsPersonal(prefs: Record<string, unknown> | undefined, category: PersonalCategory): boolean {
  if (prefs?.all === false) return false;
  // Chat and calls are paid one-to-one services: only the master switch silences them
  if (category === "chat" || category === "call") return true;
  return prefs?.[category] !== false;
}

export async function notifyTopic(m: Messenger, topic: (typeof TOPICS)[number], msg: PushMessage): Promise<void> {
  try {
    await m.sendToTopic(topic, { ...clean(msg), category: topic });
  } catch (e) {
    console.warn(`push to topic ${topic} failed`, e);
  }
}

export async function notifyUser(db: Firestore, m: Messenger, uid: string, category: PersonalCategory, msg: PushMessage): Promise<void> {
  if (!uid || uid === msg.fromUid) return;
  const ref = db.doc(`users/${uid}`);
  const user = (await ref.get()).data();
  const tokens = ((user?.fcmTokens ?? []) as unknown[]).filter((t): t is string => typeof t === "string").slice(-10);
  if (!user || tokens.length === 0 || !wantsPersonal(user.notificationPrefs, category)) return;
  try {
    const dead = await m.sendToTokens(tokens, { ...clean(msg), category }, category === "call");
    if (dead.length > 0) await ref.update({ fcmTokens: FieldValue.arrayRemove(...dead) });
  } catch (e) {
    console.warn(`push to ${uid} failed`, e);
  }
}

/** Mentioned uids written by the app (picked from people in the thread), capped and de-duplicated. */
export function mentionsOf(data: Record<string, unknown> | undefined): string[] {
  const raw = Array.isArray(data?.mentions) ? data?.mentions : [];
  return [...new Set((raw as unknown[]).filter((u): u is string => typeof u === "string"))].slice(0, 10);
}
