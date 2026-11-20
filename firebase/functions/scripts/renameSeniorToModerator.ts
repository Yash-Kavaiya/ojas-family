// One-off (owner decision 2026-11-20): the Senior member role is now Moderator. Moves every
// "senior_member" account to "moderator" in users/{uid}.role and in the role claim. Safe to run again.
// Run with Application Default Credentials (an admin of the project):
//   npx ts-node scripts/renameSeniorToModerator.ts
// The member sees the new role after their sign-in token refreshes (within an hour, or on reopening).
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

initializeApp({ projectId: "ojas-family-app" });

async function main() {
  const db = getFirestore();
  const snap = await db.collection("users").where("role", "==", "senior_member").get();
  console.log(`${snap.size} senior member account(s)`);
  for (const d of snap.docs) {
    const user = await getAuth().getUser(d.id).catch(() => null);
    if (user) await getAuth().setCustomUserClaims(d.id, { ...(user.customClaims ?? {}), role: "moderator" });
    await d.ref.set({ role: "moderator" }, { merge: true });
    console.log(`- ${d.id} (${d.get("displayName") ?? ""}) is now a moderator`);
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
