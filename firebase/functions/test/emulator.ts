import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore, Firestore } from "firebase-admin/firestore";

// Tests run inside `firebase emulators:exec`, which sets FIRESTORE_EMULATOR_HOST for the admin SDK
export function testDb(): Firestore {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error("Run via firebase emulators:exec");
  if (getApps().length === 0) initializeApp({ projectId: "demo-ojas", storageBucket: "demo-ojas.appspot.com" });
  return getFirestore();
}

export async function clearDb(db: Firestore) {
  for (const col of await db.listCollections()) await db.recursiveDelete(col);
}
