// Sets the admin email list (config/admins) and promotes listed accounts that already exist.
// Listed people who have not signed up yet become admins on their first sign-in.
//   npx ts-node scripts/setAdmins.ts a@x.com b@y.com
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

const emails = process.argv.slice(2).map((e) => e.trim().toLowerCase()).filter(Boolean);
if (emails.length === 0) throw new Error("Usage: npx ts-node scripts/setAdmins.ts <email>...");

initializeApp({ projectId: "ojas-family-app" });
const db = getFirestore();
(async () => {
  await db.doc("config/admins").set({ emails: FieldValue.arrayUnion(...emails) }, { merge: true });
  for (const email of emails) {
    try {
      const u = await getAuth().getUserByEmail(email);
      await getAuth().setCustomUserClaims(u.uid, { ...(u.customClaims ?? {}), role: "admin" });
      await db.doc(`users/${u.uid}`).set({ role: "admin" }, { merge: true });
      console.log(`${email}: admin now`);
    } catch {
      console.log(`${email}: not signed up yet, becomes admin on first sign-in`);
    }
  }
})();
