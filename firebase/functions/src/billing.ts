import { createHash } from "crypto";
import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { istDay } from "./time";

/**
 * Paid access is stored as users/{uid}/entitlements/{membership|chat|call} with an expiry. Play Billing
 * purchases and admin grants (for payments taken outside the app) both end up here; only Functions write.
 */
export type Entitlement = "membership" | "chat" | "call";

/** The paid plans (owner's plan table, 2026-11-20); membership_monthly has always been Silver. */
export type Tier = "silver" | "gold";

export interface Product {
  kind: "subs" | "inapp";
  entitlement: Entitlement;
  tier?: Tier; // membership only: which plan it gives (Silver when not set)
  days?: number; // one-time packs: how long the pack lasts
  credits?: number; // one-time call pack: number of calls
}

// Product ids must match the ones created in Play Console
export const PRODUCTS: Record<string, Product> = {
  membership_monthly: { kind: "subs", entitlement: "membership" },
  chat_monthly: { kind: "subs", entitlement: "chat" },
  call_monthly: { kind: "subs", entitlement: "call" },
  chat_week: { kind: "inapp", entitlement: "chat", days: 7 },
  call_week: { kind: "inapp", entitlement: "call", days: 7, credits: 4 },
};

const DAY_MS = 24 * 60 * 60 * 1000;

// Member Level from paid days: 3 months -> Level 2, 6 months -> Level 3. Days pause while unpaid (design §9).
export function memberLevelFor(paidDays: number, active: boolean): 0 | 1 | 2 | 3 {
  if (!active) return 0;
  if (paidDays >= 180) return 3;
  if (paidDays >= 90) return 2;
  return 1;
}

function isActive(data: FirebaseFirestore.DocumentData | undefined, now: Date): boolean {
  const exp = data?.expiresAt as Timestamp | undefined;
  return !!exp && exp.toMillis() > now.getTime();
}

/**
 * Re-derives memberLevel, and the plan the access table reads (plan, planSince, planUntil), from the
 * membership entitlement. planSince is when continuous paid membership began: moving from Silver to
 * Gold keeps it, a lapse starts it again. The first time it is set it counts the paid days so far.
 */
export async function refreshMemberLevel(db: Firestore, uid: string, now: Date): Promise<number> {
  const userRef = db.doc(`users/${uid}`);
  const [user, ent] = await Promise.all([userRef.get(), userRef.collection("entitlements").doc("membership").get()]);
  const data = ent.data();
  const active = isActive(data, now);
  const paidDays = Number(user.get("paidDays") ?? 0);
  const level = memberLevelFor(paidDays, active);
  const plan = active ? (data?.tier === "gold" ? "gold" : "silver") : "user";
  const update: Record<string, unknown> = {};
  if (user.get("memberLevel") !== level) update.memberLevel = level;
  if (user.get("plan") !== plan) update.plan = plan;
  if (active) {
    const until = data!.expiresAt as Timestamp;
    const prevUntil = user.get("planUntil") as Timestamp | undefined;
    if (!prevUntil || !prevUntil.isEqual(until)) update.planUntil = until;
    const since = user.get("planSince") as Timestamp | undefined;
    if (!since) update.planSince = Timestamp.fromMillis(now.getTime() - paidDays * DAY_MS);
    else if (!prevUntil || prevUntil.toMillis() < now.getTime() - DAY_MS) update.planSince = Timestamp.fromDate(now);
  }
  if (Object.keys(update).length > 0) await userRef.set(update, { merge: true });
  return level;
}

/**
 * Extends (never shortens) an entitlement. A one-time call pack replaces the remaining credits;
 * subscriptions set the expiry Google reports.
 */
export async function grantEntitlement(
  db: Firestore, uid: string, entitlement: Entitlement,
  opts: { until?: Date; days?: number; credits?: number; source: string; productId?: string; tier?: Tier }, now: Date,
): Promise<Date> {
  const ref = db.doc(`users/${uid}/entitlements/${entitlement}`);
  const until = await db.runTransaction(async (tx) => {
    const cur = (await tx.get(ref)).data();
    const curExp = isActive(cur, now) ? (cur!.expiresAt as Timestamp).toDate() : now;
    const target = opts.until ?? new Date(Math.max(curExp.getTime(), now.getTime()) + (opts.days ?? 0) * DAY_MS);
    const expiresAt = target.getTime() > curExp.getTime() ? target : curExp;
    const fields: Record<string, unknown> = {
      expiresAt: Timestamp.fromDate(expiresAt),
      source: opts.source,
      productId: opts.productId ?? null,
      updatedAt: FieldValue.serverTimestamp(),
    };
    // A subscription means unlimited calls; a pack gives a fixed number
    if (entitlement === "call") fields.credits = opts.credits ?? null;
    // Which plan a membership is; Gold is never lowered by a Silver renewal while Gold is still running
    if (entitlement === "membership") {
      const keepGold = cur?.tier === "gold" && isActive(cur, now) && opts.tier !== "gold" && opts.source !== "admin";
      fields.tier = keepGold ? "gold" : (opts.tier ?? "silver");
    }
    tx.set(ref, fields, { merge: true });
    return expiresAt;
  });
  if (entitlement === "membership") await refreshMemberLevel(db, uid, now);
  return until;
}

export async function revokeEntitlement(db: Firestore, uid: string, entitlement: Entitlement, now: Date): Promise<void> {
  await db.doc(`users/${uid}/entitlements/${entitlement}`).set({ expiresAt: Timestamp.fromDate(now), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  if (entitlement === "membership") await refreshMemberLevel(db, uid, now);
}

/** Runs [fn] over [items], [size] at a time. */
export async function inChunks<T>(items: T[], size: number, fn: (item: T) => Promise<unknown>): Promise<void> {
  for (let i = 0; i < items.length; i += size) await Promise.all(items.slice(i, i + size).map(fn));
}

/** Daily: every active member earns one paid day; expired members drop to memberLevel 0. */
export async function countPaidDay(db: Firestore, now: Date): Promise<number> {
  const snap = await db.collectionGroup("entitlements").where("expiresAt", ">", Timestamp.fromDate(now)).get();
  const day = istDay(now);
  const refreshed = new Set<string>();
  const members = snap.docs.filter((d) => d.id === "membership").map((d) => d.ref.parent.parent!);
  let counted = 0;
  await inChunks(members, 20, async (userRef) => {
    // Scheduled functions can run twice for one tick: a day is counted once per member
    const added = await db.runTransaction(async (tx) => {
      const user = await tx.get(userRef);
      if (!user.exists || user.get("lastPaidDay") === day) return false;
      tx.set(userRef, { paidDays: Number(user.get("paidDays") ?? 0) + 1, lastPaidDay: day }, { merge: true });
      return true;
    });
    await refreshMemberLevel(db, userRef.id, now);
    refreshed.add(userRef.id);
    if (added) counted++;
  });
  // Members whose membership lapsed since yesterday lose their level until they renew
  // (active members were refreshed just above)
  const lapsed = await db.collection("users").where("memberLevel", ">", 0).select().get();
  await inChunks(lapsed.docs.filter((u) => !refreshed.has(u.id)), 20, (u) => refreshMemberLevel(db, u.id, now));
  return counted;
}

// ---- Play Billing verification ----

export interface PlayApi {
  /** subscriptionsv2.get: returns expiry and whether the subscription is in good standing. */
  getSubscription(token: string): Promise<{ productId: string; expiry: Date; active: boolean; acknowledged: boolean }>;
  /** products.get: purchaseState 0 = purchased. */
  getProduct(productId: string, token: string): Promise<{ purchased: boolean; consumed: boolean }>;
  acknowledgeSubscription(productId: string, token: string): Promise<void>;
  consumeProduct(productId: string, token: string): Promise<void>;
}

export const tokenKey = (token: string) => createHash("sha256").update(token).digest("hex");

/**
 * Verifies a purchase with Google and grants it. A purchase token is bound to the first account that
 * claims it, so one payment can't unlock several accounts.
 */
export async function verifyPurchaseFor(
  db: Firestore, api: PlayApi, uid: string, input: { productId?: string; purchaseToken?: string }, now: Date,
): Promise<{ entitlement: Entitlement; expiresAt: string }> {
  const product = PRODUCTS[input.productId ?? ""];
  const token = input.purchaseToken ?? "";
  if (!product || token.length < 10) throw new HttpsError("invalid-argument", "Unknown product or token");

  const claimRef = db.doc(`purchases/${tokenKey(token)}`);
  await db.runTransaction(async (tx) => {
    const claim = await tx.get(claimRef);
    if (claim.exists && claim.get("uid") !== uid) throw new HttpsError("already-exists", "This purchase belongs to another account");
    if (!claim.exists) tx.set(claimRef, { uid, productId: input.productId, purchaseToken: token, createdAt: FieldValue.serverTimestamp() });
  });

  let until: Date;
  if (product.kind === "subs") {
    const sub = await api.getSubscription(token);
    // The token says what was bought: a cheaper subscription must not be claimed as a dearer one
    if (sub.productId !== input.productId) throw new HttpsError("invalid-argument", "This purchase is for a different product");
    if (!sub.active) throw new HttpsError("failed-precondition", "Subscription is not active");
    until = await grantEntitlement(db, uid, product.entitlement, { until: sub.expiry, source: "play", productId: input.productId, tier: product.tier }, now);
    if (!sub.acknowledged) await api.acknowledgeSubscription(input.productId!, token);
  } else {
    const claim = await claimRef.get();
    if (claim.get("granted") === true) {
      // Already granted (e.g. the app retried after a network error): report the current expiry
      const ent = await db.doc(`users/${uid}/entitlements/${product.entitlement}`).get();
      return { entitlement: product.entitlement, expiresAt: (ent.get("expiresAt") as Timestamp).toDate().toISOString() };
    }
    const p = await api.getProduct(input.productId!, token);
    if (!p.purchased) throw new HttpsError("failed-precondition", "Purchase is not complete");
    until = await grantEntitlement(db, uid, product.entitlement, { days: product.days, credits: product.credits, source: "play", productId: input.productId }, now);
    await claimRef.set({ granted: true }, { merge: true });
    // Consumed so the weekly pack can be bought again next week
    if (!p.consumed) await api.consumeProduct(input.productId!, token);
  }
  return { entitlement: product.entitlement, expiresAt: until.toISOString() };
}

/** Real-time developer notification for a subscription: re-read it from Google and update the expiry. */
export async function onSubscriptionNotice(db: Firestore, api: PlayApi, purchaseToken: string, now: Date): Promise<void> {
  const claim = await db.doc(`purchases/${tokenKey(purchaseToken)}`).get();
  if (!claim.exists) return; // not claimed yet; verifyPurchase will handle it when the app reports it
  const uid = claim.get("uid") as string;
  const product = PRODUCTS[claim.get("productId") as string];
  if (!product || product.kind !== "subs") return;
  const sub = await api.getSubscription(purchaseToken);
  const ref = db.doc(`users/${uid}/entitlements/${product.entitlement}`);
  await ref.set({ expiresAt: Timestamp.fromDate(sub.active ? sub.expiry : now), source: "play", updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  if (product.entitlement === "membership") await refreshMemberLevel(db, uid, now);
}

/** Admin grant for payments taken outside the app (cash, UPI) or for testing before Play Console exists. */
export async function adminGrantFor(
  db: Firestore, callerToken: Record<string, unknown> | undefined,
  input: { uid?: string; entitlement?: string; days?: number; credits?: number; revoke?: boolean; tier?: string }, now: Date,
): Promise<{ expiresAt: string }> {
  if (callerToken?.role !== "admin") throw new HttpsError("permission-denied", "Only admins can grant access");
  const ent = input.entitlement as Entitlement;
  if (!input.uid || !["membership", "chat", "call"].includes(ent)) throw new HttpsError("invalid-argument", "uid and entitlement are required");
  if (!(await db.doc(`users/${input.uid}`).get()).exists) throw new HttpsError("not-found", "No such member");
  if (input.revoke) {
    await revokeEntitlement(db, input.uid, ent, now);
    return { expiresAt: now.toISOString() };
  }
  const days = Math.floor(Number(input.days ?? 0));
  if (days < 1 || days > 366) throw new HttpsError("invalid-argument", "days must be 1 to 366");
  const credits = ent === "call" && input.credits ? Math.floor(Number(input.credits)) : undefined;
  if (input.tier !== undefined && input.tier !== "silver" && input.tier !== "gold") throw new HttpsError("invalid-argument", "tier must be silver or gold");
  const tier = ent === "membership" ? ((input.tier as Tier | undefined) ?? "silver") : undefined;
  const until = await grantEntitlement(db, input.uid, ent, { days, credits, source: "admin", tier }, now);
  return { expiresAt: until.toISOString() };
}
