import type { Firestore } from "firebase-admin/firestore";
import { channelOf, staffForChannel } from "./chat";
import { notifyUser, type Messenger } from "./notify";

type Data = Record<string, unknown> | undefined;
const str = (v: unknown) => (typeof v === "string" ? v : "");
const millis = (v: unknown) => (v && typeof (v as { toMillis?: () => number }).toMillis === "function" ? (v as { toMillis: () => number }).toMillis() : 0);

/** "7 Nov, 4:30 pm" in India time, for push text (the app shows its own localized wording around it). */
export function istLabel(ms: number): string {
  return new Intl.DateTimeFormat("en-IN", {
    timeZone: "Asia/Kolkata", day: "numeric", month: "short", hour: "numeric", minute: "2-digit",
  }).format(new Date(ms));
}

/** Who should hear about a change to a call request. */
async function staffSide(db: Firestore, req: Record<string, unknown>): Promise<string[]> {
  const chosen = str(req.staffUid);
  return chosen ? [chosen] : staffForChannel(db, channelOf(req.channel), str(req.memberUid));
}

/** New request → staff; confirmed / proposed / declined → member; cancelled by the member → staff. */
export async function onCallRequestWritten(db: Firestore, m: Messenger, id: string, before: Data, after: Data): Promise<void> {
  if (!after) return;
  const status = str(after.status);
  if (before && str(before.status) === status) return;
  const target = `callRequests/${id}`;
  const when = istLabel(millis(status === "proposed" ? after.proposedAt : after.at));
  const video = after.video === true ? "1" : "0";
  if (!before && status === "pending") {
    for (const uid of await staffSide(db, after)) {
      await notifyUser(db, m, uid, "chat", { type: "callRequest", title: str(after.memberName), body: when, target, video, fromUid: str(after.memberUid) });
    }
    return;
  }
  if (status === "confirmed" || status === "proposed" || status === "declined") {
    await notifyUser(db, m, str(after.memberUid), "chat", { type: `callRequest_${status}`, title: str(after.staffName), body: when, target, video });
  } else if (status === "cancelled") {
    for (const uid of await staffSide(db, after)) {
      await notifyUser(db, m, uid, "chat", { type: "callRequest_cancelled", title: str(after.memberName), body: when, target, video });
    }
  }
}

export const REMIND_BEFORE_MS = 15 * 60 * 1000;

/** Which reminder a confirmed call is due for at [now]: "soon" (15 minutes before), "now" (at the start), or none. */
export function reminderDue(atMs: number, nowMs: number, reminded15: boolean, remindedStart: boolean): "soon" | "now" | null {
  const until = atMs - nowMs;
  if (!remindedStart && until <= 60 * 1000 && until >= -5 * 60 * 1000) return "now";
  if (!reminded15 && !remindedStart && until <= REMIND_BEFORE_MS && until > 60 * 1000) return "soon";
  return null;
}

/** Runs every 5 minutes: reminds both sides 15 minutes before a confirmed call and again when it starts. */
export async function sendCallReminders(db: Firestore, m: Messenger, now: Date): Promise<number> {
  const horizon = new Date(now.getTime() + REMIND_BEFORE_MS + 60 * 1000);
  const snap = await db.collection("callRequests").where("status", "==", "confirmed").where("at", "<=", horizon).get();
  let sent = 0;
  for (const d of snap.docs) {
    const r = d.data();
    const due = reminderDue(millis(r.at), now.getTime(), r.reminded15 === true, r.remindedStart === true);
    if (!due) continue;
    const when = istLabel(millis(r.at));
    const base = { type: due === "now" ? "callReminder_now" : "callReminder_soon", body: when, target: `callRequests/${d.id}`, video: r.video === true ? "1" : "0" };
    await notifyUser(db, m, str(r.memberUid), "call", { ...base, title: str(r.staffName) });
    for (const uid of await staffSide(db, r)) await notifyUser(db, m, uid, "call", { ...base, title: str(r.memberName) });
    await d.ref.set(due === "now" ? { remindedStart: true, reminded15: true } : { reminded15: true }, { merge: true });
    sent++;
  }
  return sent;
}
