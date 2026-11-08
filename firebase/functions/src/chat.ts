import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { notifyUser, type Messenger } from "./notify";

type Data = Record<string, unknown> | undefined;
const str = (v: unknown) => (typeof v === "string" ? v : "");
const STAFF = ["admin", "swamiji", "senior_member"];

/** The member's two conversations: with Swamiji (chats/) and with the Admin team (adminChats/). */
export type Channel = "swamiji" | "admin";
export const chatCollection = (channel: Channel) => (channel === "admin" ? "adminChats" : "chats");
export const channelOf = (v: unknown): Channel => (v === "admin" ? "admin" : "swamiji");

/**
 * Staff who answer a member: the assigned senior member if Swamiji assigned one, otherwise everyone
 * with the swamiji role (config/staff.swamijiUid first, kept by setRole).
 */
export async function staffFor(db: Firestore, memberUid: string): Promise<string[]> {
  const assignee = str((await db.doc(`chats/${memberUid}`).get()).get("assigneeUid"));
  if (assignee) return [assignee];
  const swamiji = await db.collection("users").where("role", "==", "swamiji").limit(5).get();
  return swamiji.docs.map((d) => d.id);
}

/** Every admin answers the Admin-team conversation. */
export async function adminsFor(db: Firestore): Promise<string[]> {
  const admins = await db.collection("users").where("role", "==", "admin").limit(20).get();
  return admins.docs.map((d) => d.id);
}

export const staffForChannel = (db: Firestore, channel: Channel, memberUid: string) =>
  channel === "admin" ? adminsFor(db) : staffFor(db, memberUid);

/** What a message says in the chat list and the push: its text, or the kind of file it carries. */
export function previewOf(data: Data): string {
  const text = str(data?.text).trim();
  if (text) return text.slice(0, 200);
  const att = data?.attachment as Record<string, unknown> | undefined;
  const mime = str(att?.mime);
  if (mime.startsWith("image/")) return "📷 Photo";
  if (mime === "application/pdf") return `📄 ${str(att?.name) || "PDF"}`;
  if (mime.startsWith("audio/")) return "🎵 Audio";
  if (data?.kind === "call") return data?.video === true ? "📹 Video call" : "📞 Call";
  return "";
}

/** Keeps the chat's preview and unread flags current and tells the other side. */
export async function onChatMessage(db: Firestore, m: Messenger, memberUid: string, data: Data, channel: Channel = "swamiji"): Promise<void> {
  const sender = str(data?.senderUid);
  const fromMember = sender === memberUid;
  const isLog = data?.kind === "call"; // written by the server when a call ends
  await db.doc(`${chatCollection(channel)}/${memberUid}`).set({
    lastMessage: previewOf(data),
    lastAt: FieldValue.serverTimestamp(),
    lastSenderUid: sender,
    ...(isLog ? {} : {
      unreadByStaff: fromMember, unreadByMember: !fromMember,
      // The other side's unread count goes up; the sender has obviously read up to here
      ...(fromMember
        ? { unreadCountStaff: FieldValue.increment(1), unreadCountMember: 0 }
        : { unreadCountMember: FieldValue.increment(1), unreadCountStaff: 0 }),
    }),
  }, { merge: true });
  if (isLog) return;

  const senderName = str((await db.doc(`users/${sender}`).get()).get("displayName"));
  const msg = { type: "chat", title: senderName, body: previewOf(data), target: `${chatCollection(channel)}/${memberUid}`, fromUid: sender };
  const recipients = fromMember ? await staffForChannel(db, channel, memberUid) : [memberUid];
  for (const uid of recipients) {
    if (await chatMuted(db, uid, channel, memberUid)) continue;
    await notifyUser(db, m, uid, "chat", msg);
  }
}

/** Whether [uid] muted this conversation (users/{uid}/chatPrefs/{channel}_{memberUid}). Calls still ring. */
export async function chatMuted(db: Firestore, uid: string, channel: Channel, memberUid: string): Promise<boolean> {
  return (await db.doc(`users/${uid}/chatPrefs/${channel}_${memberUid}`).get()).get("muted") === true;
}

// ---- Calls (WebRTC; signalling through Firestore, design §11 Q4: free WebRTC) ----

/** Rings the callee's phone with a high-priority push. */
export async function onCallCreated(db: Firestore, m: Messenger, callId: string, data: Data): Promise<void> {
  if (data?.status !== "ringing") return;
  const caller = str(data?.callerUid);
  const name = str((await db.doc(`users/${caller}`).get()).get("displayName"));
  await notifyUser(db, m, str(data?.calleeUid), "call", {
    type: "call", title: name, target: `calls/${callId}`, fromUid: caller, callId, video: data?.video === true ? "1" : "0",
  });
}

const millis = (v: unknown) => (v && typeof (v as { toMillis?: () => number }).toMillis === "function" ? (v as { toMillis: () => number }).toMillis() : 0);

/** How long a call lasted, in whole seconds (0 when it never connected). */
export function callSeconds(startedAt: unknown, endedAt: unknown): number {
  const s = millis(startedAt), e = millis(endedAt);
  return s > 0 && e > s ? Math.round((e - s) / 1000) : 0;
}

/**
 * When a call connects, a weekly call pack loses one call; members at Member Level 2 or higher
 * call as part of membership and never use a pack. When the call ends, a call-log line is added
 * to that conversation and a scheduled request becomes "done".
 */
export async function onCallUpdated(db: Firestore, callId: string, before: Data, after: Data): Promise<void> {
  const memberUid = str(after?.memberUid);
  if (after?.status === "active" && before?.status !== "active" && after?.creditUsed !== true) {
    const ent = db.doc(`users/${memberUid}/entitlements/call`);
    const member = (await db.doc(`users/${memberUid}`).get()).data() ?? {};
    const role = str(member.role);
    const level = Number(member.memberLevel ?? 0);
    await db.runTransaction(async (tx) => {
      const call = await tx.get(db.doc(`calls/${callId}`));
      if (call.get("creditUsed") === true) return;
      const e = await tx.get(ent);
      const credits = e.get("credits");
      if (!STAFF.includes(role) && level < 2 && typeof credits === "number") tx.set(ent, { credits: Math.max(0, credits - 1) }, { merge: true });
      tx.set(call.ref, { creditUsed: true, startedAt: FieldValue.serverTimestamp() }, { merge: true });
    });
    return;
  }

  const over = ["ended", "declined", "missed"];
  if (!over.includes(str(after?.status)) || over.includes(str(before?.status)) || after?.logged === true) return;
  const ref = db.doc(`calls/${callId}`);
  const fresh = (await ref.get()).data() ?? after ?? {};
  const seconds = callSeconds(fresh.startedAt, fresh.endedAt);
  const channel = channelOf(fresh.channel);
  // Make sure the conversation exists (a call can come before any message) so the log has a home
  const member = (await db.doc(`users/${memberUid}`).get()).data() ?? {};
  await db.doc(`${chatCollection(channel)}/${memberUid}`).set(
    { memberUid, memberName: str(member.displayName), memberPhoto: str(member.photoUrl) }, { merge: true });
  await db.collection(`${chatCollection(channel)}/${memberUid}/messages`).add({
    kind: "call",
    senderUid: str(fresh.callerUid),
    text: "",
    video: fresh.video === true,
    status: seconds > 0 ? "ended" : str(fresh.status) === "ended" ? "missed" : str(fresh.status),
    seconds,
    callId,
    createdAt: FieldValue.serverTimestamp(),
  });
  await ref.set({ logged: true, seconds }, { merge: true });
  const requestId = str(fresh.requestId);
  if (requestId && seconds > 0) await db.doc(`callRequests/${requestId}`).set({ status: "done" }, { merge: true });
}
