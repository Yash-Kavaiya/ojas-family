// Fills the LOCAL Firebase emulators (project demo-ojas) with sample data for screenshots and demos.
// It refuses to run unless FIRESTORE_EMULATOR_HOST is set, so it can never write to production.
//   (cd firebase && firebase emulators:start --project demo-ojas)
//   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npx ts-node scripts/seedEmulator.ts [uidToMakeAdmin]
// Demo sign-in (Auth emulator only): admin@ojas.test / ojas-demo-2026 (create it in the app)
import { readFileSync } from "fs";
import { resolve } from "path";
import { initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  console.error("FIRESTORE_EMULATOR_HOST is not set: refusing to write (this script is for the emulators only)");
  process.exit(1);
}
initializeApp({ projectId: "demo-ojas" });
const db = getFirestore();
const DAY = 86_400_000;
const IST = 5.5 * 3_600_000;
const istDay = (ms: number) => new Date(ms + IST).toISOString().slice(0, 10);
const istMidnight = (ms: number) => { const d = istDay(ms); return Date.parse(`${d}T00:00:00Z`) - IST; };
const ago = (days: number) => Timestamp.fromMillis(Date.now() - days * DAY);

async function main() {
  const now = Date.now();
  const content = JSON.parse(readFileSync(resolve(__dirname, "content.json"), "utf8")) as { videoId: string; section: string; title: string; order: number }[];
  for (const s of content.slice(0, 60)) {
    await db.doc(`content/yt_${s.videoId}`).set({ section: s.section, videoId: s.videoId, title: s.title.slice(0, 200), minUserLevel: 1, minMemberLevel: 0, order: s.order, published: true, createdAt: ago(10) });
  }
  const goals = JSON.parse(readFileSync(resolve(__dirname, "goals.json"), "utf8")) as { id: string }[];
  for (const { id, ...f } of goals) await db.doc(`goals/${id}`).set(f, { merge: true });

  const thoughts = [
    ["Breath is the bridge", "Every breath you take with awareness brings you home to yourself. Sit for one minute today and simply watch the breath come and go."],
    ["Gratitude heals", "Name three things you are thankful for before you sleep. Gratitude softens the mind and opens the heart."],
    ["Light within", "The light you seek outside has always been within you. Close your eyes and rest in that light for a few moments."],
  ];
  for (let i = 0; i < thoughts.length; i++) {
    const t = istMidnight(now - i * DAY) + 60_000;
    await db.doc(`thoughts/${istDay(now - i * DAY)}`).set({ title: thoughts[i][0], text: thoughts[i][1], publishAt: Timestamp.fromMillis(t), likeCount: 128 - i * 20, commentCount: 14 - i * 3, viewCount: 940 - i * 120 });
  }

  const sw = { authorUid: "swamiji", authorName: "Swamiji", authorPhoto: "", authorRole: "swamiji" };
  const ann = [
    ["Full moon group meditation", "Join us this Sunday at 7 pm IST for a live full moon meditation. The link will appear here an hour before."],
    ["New practice: Night healing", "A new guided Night practice is now in the app for Silver and Gold members. Try it tonight before sleep."],
  ];
  for (let i = 0; i < ann.length; i++) {
    await db.doc(`announcements/a${i}`).set({ ...sw, title: ann[i][0], body: ann[i][1], createdAt: ago(i + 1), likeCount: 64 - i * 10, commentCount: 9 - i * 2, viewCount: 610 - i * 90 });
  }

  const people = [["u_asha", "Asha Patel"], ["u_ravi", "Ravi Sharma"], ["u_meera", "Meera Joshi"]];
  for (const [uid, name] of people) {
    await db.doc(`users/${uid}`).set({ displayName: name, role: "member", createdAt: ago(300), plan: "silver", planSince: ago(200), planUntil: Timestamp.fromMillis(now + 30 * DAY), userLevel: 3, memberLevel: 1, language: "en" }, { merge: true });
    await db.doc(`publicProfiles/${uid}`).set({ displayName: name, photoUrl: "", userLevel: 3, memberLevel: 1, role: "member" }, { merge: true });
  }
  const qs = [
    ["How long should I meditate each day?", "I am new to the practice. Is ten minutes enough to begin with?"],
    ["Feeling heavy after the night practice", "Sometimes I feel tired after the night practice. Is this normal?"],
    ["Can children join the morning practice?", "My daughter is 10. Can she do the morning practice with me?"],
  ];
  for (let i = 0; i < qs.length; i++) {
    const [uid, name] = people[i];
    await db.doc(`questions/q${i}`).set({ authorUid: uid, authorName: name, authorPhoto: "", authorRole: "member", authorLevel: 3, title: qs[i][0], body: qs[i][1], status: "visible", createdAt: ago(i * 0.3 + 0.1), likeCount: 12 - i * 3, answerCount: 1, viewCount: 85 - i * 20 });
    await db.doc(`answers/q${i}_a`).set({ questionId: `q${i}`, ...sw, text: "Begin gently. Ten minutes with full attention is better than an hour with a wandering mind. Let it grow naturally.", status: "visible", createdAt: ago(i * 0.3), likeCount: 20 - i * 4 });
  }

  const admin = process.argv[2];
  if (admin) {
    await db.doc(`users/${admin}`).set({ role: "admin", plan: "gold", planSince: ago(400), planUntil: Timestamp.fromMillis(now + 60 * DAY), createdAt: ago(400), userLevel: 4, memberLevel: 3 }, { merge: true });
    console.log(`made ${admin} an admin on Gold`);
  }
  console.log("seeded the emulators");
}

main().catch((e) => { console.error(e); process.exit(1); });
