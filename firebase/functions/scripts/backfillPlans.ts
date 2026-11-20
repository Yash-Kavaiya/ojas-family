// One-off for the membership plans (owner's plan table, 2026-11-20). Run it after deploying the
// functions and before deploying firestore.rules, so paying members have plan, planSince and
// planUntil before the rules start reading them. Existing memberships become Silver, counting the
// paid days they already have. Safe to run again.
// Run with Application Default Credentials (an admin of the project):
//   npx ts-node scripts/backfillPlans.ts
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { inChunks, refreshMemberLevel } from "../src/billing";

initializeApp({ projectId: "ojas-family-app" });

async function main() {
  const db = getFirestore();
  const now = new Date();
  const active = await db.collectionGroup("entitlements").where("expiresAt", ">", Timestamp.fromDate(now)).get();
  const members = active.docs.filter((d) => d.id === "membership").map((d) => d.ref.parent.parent!.id);
  console.log(`${members.length} member(s) with a running membership`);
  await inChunks(members, 10, async (uid) => {
    await refreshMemberLevel(db, uid, now);
    const u = (await db.doc(`users/${uid}`).get()).data() ?? {};
    console.log(`- ${uid}: ${u.plan} since ${(u.planSince as Timestamp | undefined)?.toDate().toISOString().slice(0, 10)} until ${(u.planUntil as Timestamp | undefined)?.toDate().toISOString().slice(0, 10)}`);
  });
  // Every account needs createdAt for a free member's time: take it from when they signed up
  const users = await db.collection("users").select("createdAt").get();
  const missing = users.docs.filter((d) => d.get("createdAt") === undefined);
  console.log(`${missing.length} account(s) without createdAt`);
  await inChunks(missing, 10, async (d) => {
    const signedUp = await getAuth().getUser(d.id).then((u) => new Date(u.metadata.creationTime)).catch(() => now);
    await d.ref.set({ createdAt: Timestamp.fromDate(signedUp) }, { merge: true });
  });
}

main().catch((e) => { console.error(e); process.exit(1); });
