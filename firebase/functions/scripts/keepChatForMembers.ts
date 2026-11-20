// One-off for the plan table, part 2 (2026-11-20). Chat and calls used to come with membership
// (chat from Member Level 1, audio calls from Level 2, video from Level 3); now they are bought
// separately. Members paying today keep what they had until their current membership ends.
// Run after deploying the functions and before deploying firestore.rules. Safe to run again.
//   npx ts-node scripts/keepChatForMembers.ts          (lists what it would do)
//   npx ts-node scripts/keepChatForMembers.ts --write  (does it)
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore, Timestamp } from "firebase-admin/firestore";

initializeApp({ projectId: "ojas-family-app" });

async function main() {
  const write = process.argv.includes("--write");
  const db = getFirestore();
  const now = Timestamp.now();
  const active = await db.collectionGroup("entitlements").where("expiresAt", ">", now).get();
  const memberships = active.docs.filter((d) => d.id === "membership");
  console.log(`${memberships.length} running membership(s)${write ? "" : " (dry run: add --write to apply)"}`);
  for (const m of memberships) {
    const userRef = m.ref.parent.parent!;
    const user = (await userRef.get()).data() ?? {};
    const level = Number(user.memberLevel ?? 0);
    const until = m.get("expiresAt") as Timestamp;
    const give: string[] = [];
    if (level >= 1) give.push("chat");
    if (level >= 2) give.push("call");
    for (const name of give) {
      const ref = userRef.collection("entitlements").doc(name);
      const cur = (await ref.get()).data();
      const curUntil = cur?.expiresAt as Timestamp | undefined;
      // Never shorten a pack they bought; an unlimited call subscription stays unlimited (credits null)
      if (curUntil && curUntil.toMillis() >= until.toMillis()) { console.log(`- ${userRef.id}: ${name} already runs to ${curUntil.toDate().toISOString().slice(0, 10)}`); continue; }
      console.log(`- ${userRef.id}: ${name} until ${until.toDate().toISOString().slice(0, 10)} (member level ${level})`);
      if (write) {
        await ref.set({
          expiresAt: until, source: "kept-from-membership", productId: null, tokenKey: null,
          ...(name === "call" ? { credits: null } : {}), updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      }
    }
    if (give.length === 0) console.log(`- ${userRef.id}: member level ${level}, nothing to keep`);
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
