import { randomUUID } from "crypto";
import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { computeUserLevel, loadLevelConfig } from "./levels";
import { earnedOf } from "./points";
import { cleanPointsConfig, loadPointsConfig } from "./pointsConfig";
import { istDay } from "./time";

type Claims = Record<string, unknown> | undefined;

/** Swamiji and admins have full control over Moksha Points. */
export function isPointsStaff(token: Claims): boolean {
  return token?.role === "admin" || token?.role === "swamiji";
}

function requirePointsStaff(token: Claims) {
  if (!isPointsStaff(token)) throw new HttpsError("permission-denied", "Only Swamiji and admins can change points");
}

const cleanNote = (n: unknown): string => (typeof n === "string" ? n.trim().slice(0, 200) : "");

/**
 * A member gives some of their Moksha Points to another member. The sender's balance goes down and
 * the receiver's goes up by the same amount, in one transaction. Received points are spendable but
 * never raise the receiver's Level (Level uses earnedPoints), so points can't be farmed through
 * friends. Limits come from config/points.
 */
export async function transferPointsFor(
  db: Firestore, fromUid: string, input: { toUid?: unknown; amount?: unknown; note?: unknown }, now: Date,
): Promise<{ transferId: string; balance: number }> {
  const toUid = typeof input.toUid === "string" ? input.toUid : "";
  const amount = Math.floor(Number(input.amount));
  const note = cleanNote(input.note);
  if (!toUid) throw new HttpsError("invalid-argument", "Choose who to send points to");
  if (toUid === fromUid) throw new HttpsError("invalid-argument", "You can't send points to yourself");
  const cfg = await loadPointsConfig(db);
  if (!cfg.transfersEnabled) throw new HttpsError("failed-precondition", "Sending points is switched off for now");
  if (!(amount >= 1 && amount <= cfg.maxTransfer)) throw new HttpsError("invalid-argument", `Send between 1 and ${cfg.maxTransfer} points`);

  const day = istDay(now);
  const fromRef = db.doc(`users/${fromUid}`);
  const toRef = db.doc(`users/${toUid}`);
  const counterRef = fromRef.collection("counters").doc(day);
  const transferId = `${Date.now()}_${randomUUID().slice(0, 8)}`;
  const transferRef = db.doc(`pointTransfers/${transferId}`);

  const balance = await db.runTransaction(async (tx) => {
    const [from, to, counter, block1, block2] = await Promise.all([
      tx.get(fromRef), tx.get(toRef), tx.get(counterRef),
      tx.get(db.doc(`users/${toUid}/blocked/${fromUid}`)), tx.get(db.doc(`users/${fromUid}/blocked/${toUid}`)),
    ]);
    if (!to.exists) throw new HttpsError("not-found", "That member was not found");
    const f = from.data() ?? {};
    if (f.transfersFrozen === true) throw new HttpsError("permission-denied", "Sending points is paused on your account");
    if (Number(f.userLevel ?? 1) < cfg.minLevelToSend) throw new HttpsError("failed-precondition", `Sending points opens at Level ${cfg.minLevelToSend}`);
    if (block1.exists || block2.exists) throw new HttpsError("permission-denied", "You can't send points to this member");
    const have = Number(f.mokshaPoints ?? 0);
    if (have < amount) throw new HttpsError("failed-precondition", "You don't have enough points");
    const sentToday = Number(counter.get("sent") ?? 0);
    if (sentToday + amount > cfg.dailySendCap) {
      throw new HttpsError("resource-exhausted", `You can send up to ${cfg.dailySendCap} points a day (${Math.max(0, cfg.dailySendCap - sentToday)} left today)`);
    }
    const t = to.data() ?? {};
    const fromName = String(f.displayName ?? "");
    const toName = String(t.displayName ?? "");
    const at = FieldValue.serverTimestamp();

    tx.set(counterRef, { sent: sentToday + amount }, { merge: true });
    // Only the balance moves: Level (earnedPoints) stays as it was on both sides
    tx.set(fromRef, { mokshaPoints: have - amount, earnedPoints: earnedOf(f) }, { merge: true });
    tx.set(toRef, { mokshaPoints: Number(t.mokshaPoints ?? 0) + amount, earnedPoints: earnedOf(t), lastGiftNote: note || FieldValue.delete() }, { merge: true });
    tx.set(fromRef.collection("pointEvents").doc(`transfer_out:${transferId}`), {
      amount: -amount, reason: "transfer_out", milestone: false, otherUid: toUid, otherName: toName, ...(note ? { note } : {}), createdAt: at,
    });
    tx.set(toRef.collection("pointEvents").doc(`transfer_in:${transferId}`), {
      amount, reason: "transfer_in", milestone: true, otherUid: fromUid, otherName: fromName, ...(note ? { note } : {}), createdAt: at,
    });
    tx.set(transferRef, { fromUid, toUid, fromName, toName, amount, note, day, status: "done", createdAt: at });
    return have - amount;
  });
  return { transferId, balance };
}

/**
 * Swamiji or an admin changes a member's points. "add" and "deduct" move the balance by [amount];
 * "set" makes the balance exactly [amount]. With target "earned" the change also applies to the
 * points that count for Level (and the Level is recomputed). Every change is logged with who made it.
 */
export async function staffAdjustPointsFor(
  db: Firestore, token: Claims, staffUid: string,
  input: { uid?: unknown; mode?: unknown; amount?: unknown; target?: unknown; note?: unknown },
): Promise<{ balance: number; earned: number; userLevel: number }> {
  requirePointsStaff(token);
  const uid = typeof input.uid === "string" ? input.uid : "";
  const mode = String(input.mode ?? "");
  const target = input.target === "earned" ? "earned" : "balance";
  const amount = Math.floor(Number(input.amount));
  const note = cleanNote(input.note);
  if (!uid) throw new HttpsError("invalid-argument", "Choose a member");
  if (!["add", "deduct", "set"].includes(mode)) throw new HttpsError("invalid-argument", "mode must be add, deduct or set");
  if (!(amount >= 0 && amount <= 10_000_000) || (mode !== "set" && amount < 1)) throw new HttpsError("invalid-argument", "Enter a valid number of points");
  if (!note) throw new HttpsError("invalid-argument", "Add a short reason");

  const levels = await loadLevelConfig(db);
  const ref = db.doc(`users/${uid}`);
  const id = `${Date.now()}_${randomUUID().slice(0, 8)}`;
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "That member was not found");
    const d = snap.data() ?? {};
    const was = Number(d.mokshaPoints ?? 0);
    const wasEarned = earnedOf(d);
    const balance = Math.max(0, mode === "add" ? was + amount : mode === "deduct" ? was - amount : amount);
    const delta = balance - was;
    const earned = target === "earned" ? Math.max(0, wasEarned + delta) : wasEarned;
    const userLevel = computeUserLevel({ activeDays: Number(d.activeDays ?? 0), points: earned }, levels);
    const at = FieldValue.serverTimestamp();
    tx.set(ref, { mokshaPoints: balance, earnedPoints: earned, userLevel }, { merge: true });
    if (delta !== 0) {
      tx.set(ref.collection("pointEvents").doc(`adjust:${id}`), {
        amount: delta, reason: delta > 0 ? "staff_add" : "staff_deduct", milestone: delta > 0, note, createdAt: at,
      });
    }
    tx.set(db.doc(`pointAdjustments/${id}`), {
      uid, byUid: staffUid, byRole: token?.role ?? "", mode, target, amount, before: was, after: balance,
      earnedBefore: wasEarned, earnedAfter: earned, note, createdAt: at,
    });
    return { balance, earned, userLevel };
  });
}

/**
 * Swamiji or an admin undoes a member-to-member transfer: the receiver gives the points back (as
 * far as they still have them) and the sender gets them back. Each transfer can be undone once.
 */
export async function reverseTransferFor(
  db: Firestore, token: Claims, staffUid: string, input: { transferId?: unknown; note?: unknown },
): Promise<{ returned: number }> {
  requirePointsStaff(token);
  const transferId = typeof input.transferId === "string" ? input.transferId : "";
  if (!transferId) throw new HttpsError("invalid-argument", "transferId is required");
  const note = cleanNote(input.note) || "Transfer reversed";
  const tRef = db.doc(`pointTransfers/${transferId}`);
  return db.runTransaction(async (tx) => {
    const t = await tx.get(tRef);
    if (!t.exists) throw new HttpsError("not-found", "Transfer not found");
    if (t.get("status") === "reversed") throw new HttpsError("failed-precondition", "Already reversed");
    const fromRef = db.doc(`users/${t.get("fromUid")}`);
    const toRef = db.doc(`users/${t.get("toUid")}`);
    const [from, to] = await Promise.all([tx.get(fromRef), tx.get(toRef)]);
    const amount = Number(t.get("amount") ?? 0);
    const toHas = Number(to.get("mokshaPoints") ?? 0);
    const returned = Math.min(amount, toHas);
    const at = FieldValue.serverTimestamp();
    tx.set(toRef, { mokshaPoints: toHas - returned, earnedPoints: earnedOf(to.data()) }, { merge: true });
    tx.set(fromRef, { mokshaPoints: Number(from.get("mokshaPoints") ?? 0) + returned, earnedPoints: earnedOf(from.data()) }, { merge: true });
    if (returned > 0) {
      tx.set(toRef.collection("pointEvents").doc(`reverse_out:${transferId}`), { amount: -returned, reason: "transfer_reversed", milestone: false, note, createdAt: at });
      tx.set(fromRef.collection("pointEvents").doc(`reverse_in:${transferId}`), { amount: returned, reason: "transfer_reversed", milestone: false, note, createdAt: at });
    }
    tx.set(tRef, { status: "reversed", reversedBy: staffUid, reversedAmount: returned, reversedAt: at }, { merge: true });
    return { returned };
  });
}

/** Swamiji or an admin pauses (or resumes) one member's ability to send points. */
export async function setTransfersFrozenFor(
  db: Firestore, token: Claims, staffUid: string, input: { uid?: unknown; frozen?: unknown; note?: unknown },
): Promise<{ frozen: boolean }> {
  requirePointsStaff(token);
  const uid = typeof input.uid === "string" ? input.uid : "";
  if (!uid) throw new HttpsError("invalid-argument", "Choose a member");
  const frozen = input.frozen === true;
  const ref = db.doc(`users/${uid}`);
  if (!(await ref.get()).exists) throw new HttpsError("not-found", "That member was not found");
  await ref.set({ transfersFrozen: frozen }, { merge: true });
  await db.collection("pointAdjustments").add({
    uid, byUid: staffUid, byRole: token?.role ?? "", mode: frozen ? "freeze" : "unfreeze", note: cleanNote(input.note), createdAt: FieldValue.serverTimestamp(),
  });
  return { frozen };
}

/** Swamiji or an admin updates config/points (activity amounts and transfer rules). */
export async function updatePointsConfigFor(
  db: Firestore, token: Claims, staffUid: string, input: Record<string, unknown>,
): Promise<{ ok: true }> {
  requirePointsStaff(token);
  const current = (await db.doc("config/points").get()).data() ?? {};
  const next = cleanPointsConfig({ ...current, ...input });
  await db.doc("config/points").set({ ...next, updatedBy: staffUid, updatedAt: FieldValue.serverTimestamp() });
  return { ok: true };
}

export interface GiftRecipient { uid: string; name: string; photoUrl: string; userLevel: number; role: string }

/**
 * Finds members to send points to by the start of their name (2+ letters). Returns only what the
 * community already shows next to posts (name, photo, Level), and never members who hid their profile.
 */
export async function findGiftRecipientsFor(db: Firestore, uid: string, input: { prefix?: unknown }): Promise<{ members: GiftRecipient[] }> {
  const raw = typeof input.prefix === "string" ? input.prefix.trim().slice(0, 40) : "";
  if (raw.length < 2) return { members: [] };
  // Names are stored as typed: also try a capital first letter ("ra" finds "Ravi")
  const variants = [...new Set([raw, raw.charAt(0).toUpperCase() + raw.slice(1)])];
  const found = new Map<string, GiftRecipient>();
  for (const p of variants) {
    const snap = await db.collection("publicProfiles").orderBy("displayName").startAt(p).endAt(p + "").limit(15).get();
    snap.forEach((d) => {
      if (d.id === uid || d.get("hidden") === true || found.has(d.id)) return;
      found.set(d.id, {
        uid: d.id, name: String(d.get("displayName") ?? ""), photoUrl: String(d.get("photoUrl") ?? ""),
        userLevel: Number(d.get("userLevel") ?? 1), role: String(d.get("role") ?? "member"),
      });
    });
  }
  return { members: [...found.values()].slice(0, 20) };
}
