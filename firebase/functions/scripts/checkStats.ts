// Read-only: prints what the admin dashboard will show.  npx ts-node scripts/checkStats.ts
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { statsFor } from "../src/admin";

initializeApp({ projectId: "ojas-family-app" });
statsFor(getFirestore(), { role: "admin" }, new Date()).then((s) => console.log(JSON.stringify(s)));
