// Bootstraps the first admin (setRole itself requires an existing admin):
//   npm run grant-admin -- someone@example.com
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

const email = process.argv[2];
if (!email) throw new Error("Usage: npm run grant-admin -- <email>");

initializeApp({ projectId: "ojas-family-app" });
(async () => {
  const user = await getAuth().getUserByEmail(email);
  await getAuth().setCustomUserClaims(user.uid, { role: "admin" });
  await getFirestore().doc(`users/${user.uid}`).set({ role: "admin" }, { merge: true });
  console.log(`${email} is now an admin (sign out and in again to refresh the claim)`);
})();
