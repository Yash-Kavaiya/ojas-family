// Writes the default config/levels document. Run with Application Default Credentials:
//   gcloud auth application-default login && npm run seed:config
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { DEFAULT_LEVELS } from "../src/levels";

initializeApp({ projectId: "ojas-family-app" });
getFirestore()
  .doc("config/levels")
  .set(DEFAULT_LEVELS, { merge: true })
  .then(() => console.log("config/levels written"));
