import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { notifyUser, type Messenger } from "./notify";

type Data = Record<string, unknown> | undefined;
const str = (v: unknown) => (typeof v === "string" ? v : "");
const STAFF = ["admin", "swamiji", "senior_member"];

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

/** Keeps the chat's preview and unread flags current and tells the other side. */
export async function onChatMessage(db: Firestore, m: Messenger, memberUid: string, data: Data): Promise<void> {
  const sender = str(data?.senderUid);
  const fromMember = sender === memberUid;
  const text = str(data?.text).slice(0, 200);
  await db.doc(`chats/${memberUid}`).set({
    lastMessage: text,
    lastAt: FieldValue.serverTimestamp(),
    lastSenderUid: sender,
    unreadByStaff: fromMember,
    unreadByMember: !fromMember,
  }, { merge: true });

  const senderName = str((await db.doc(`users/${sender}`).get()).get("displayName"));
  const msg = { type: "chat", title: senderName, body: text, target: `chats/${memberUid}`, fromUid: sender };
  const recipients = fromMember ? await staffFor(db, memberUid) : [memberUid];
  for (const uid of recipients) await notifyUser(db, m, uid, "chat", msg);
}

// ---- Calls (WebRTC; signalling through Firestore, design §11 Q4: free WebRTC) ----

/** Rings the callee's phone with a high-priority push. */
export async function onCallCreated(db: Firestore, m: Messenger, callId: string, data: Data): Promise<void> {
  if (data?.status !== "ringing") return;
  const caller = str(data?.callerUid);
  const name = str((await db.doc(`users/${caller}`).get()).get("displayName"));
  await notifyUser(db, m, str(data?.calleeUid), "call", { type: "call", title: name, target: `calls/${callId}`, fromUid: caller, callId });
}

/**
 * A weekly call pack is a number of calls: one is used the first time a call the member is in connects.
 * Monthly subscribers have no credits field and are never charged per call.
 */
export async function onCallUpdated(db: Firestore, callId: string, before: Data, after: Data): Promise<void> {
  if (after?.status !== "active" || before?.status === "active" || after?.creditUsed === true) return;
  const memberUid = str(after?.memberUid);
  const ent = db.doc(`users/${memberUid}/entitlements/call`);
  const role = str((await db.doc(`users/${memberUid}`).get()).get("role"));
  await db.runTransaction(async (tx) => {
    const call = await tx.get(db.doc(`calls/${callId}`));
    if (call.get("creditUsed") === true) return;
    const e = await tx.get(ent);
    const credits = e.get("credits");
    if (!STAFF.includes(role) && typeof credits === "number") tx.set(ent, { credits: Math.max(0, credits - 1) }, { merge: true });
    tx.set(call.ref, { creditUsed: true, startedAt: FieldValue.serverTimestamp() }, { merge: true });
  });
}
