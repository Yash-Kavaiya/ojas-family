import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { mentionsOf, notifyTopic, notifyUser, type Messenger } from "./notify";

type Data = Record<string, unknown> | undefined;

// A post is announced once, when it first becomes visible (on create, or when an admin approves it)
const becameVisible = (before: Data, after: Data) => after?.status === "visible" && before?.status !== "visible";
const str = (v: unknown) => (typeof v === "string" ? v : "");

export async function onAnnouncementCreated(m: Messenger, id: string, data: Data): Promise<void> {
  await notifyTopic(m, "announcements", { type: "announcement", title: str(data?.title), body: str(data?.body), target: `announcements/${id}` });
}

export async function onThoughtOfDay(db: Firestore, m: Messenger, today: string): Promise<boolean> {
  const t = await db.doc(`thoughts/${today}`).get();
  if (!t.exists) return false;
  await notifyTopic(m, "thoughtOfDay", { type: "thought", title: str(t.get("title")), body: str(t.get("text")), target: `thoughts/${today}` });
  return true;
}

export async function onQuestionNotify(db: Firestore, m: Messenger, id: string, before: Data, after: Data): Promise<void> {
  if (!becameVisible(before, after)) return;
  const from = str(after?.authorUid);
  await notifyTopic(m, "communityPosts", { type: "question", title: str(after?.authorName), body: str(after?.title), target: `questions/${id}`, fromUid: from });
  for (const uid of mentionsOf(after)) {
    await notifyUser(db, m, uid, "communityMentions", { type: "mention", title: str(after?.authorName), body: str(after?.title), target: `questions/${id}`, fromUid: from });
  }
}

/** A visible answer tells the person who asked, and anyone mentioned in it. */
export async function onAnswerNotify(db: Firestore, m: Messenger, questionId: string, before: Data, after: Data): Promise<void> {
  if (!becameVisible(before, after)) return;
  const from = str(after?.authorUid);
  const msg = { title: str(after?.authorName), body: str(after?.text), target: `questions/${questionId}`, fromUid: from };
  const asker = str((await db.doc(`questions/${questionId}`).get()).get("authorUid"));
  const mentioned = mentionsOf(after);
  if (asker && !mentioned.includes(asker)) await notifyUser(db, m, asker, "communityInteractions", { type: "answer", ...msg });
  for (const uid of mentioned) await notifyUser(db, m, uid, "communityMentions", { type: "mention", ...msg });
}

export async function onThoughtCommentNotify(db: Firestore, m: Messenger, thoughtId: string, before: Data, after: Data): Promise<void> {
  if (!becameVisible(before, after)) return;
  for (const uid of mentionsOf(after)) {
    await notifyUser(db, m, uid, "communityMentions", {
      type: "mention", title: str(after?.authorName), body: str(after?.text), target: `thoughts/${thoughtId}`, fromUid: str(after?.authorUid),
    });
  }
}

// ---- Daily Activities (paid members) ----

export async function onDailyPostWritten(db: Firestore, m: Messenger, id: string, before: Data, after: Data): Promise<void> {
  if (!becameVisible(before, after)) return;
  const from = str(after?.authorUid);
  await notifyTopic(m, "dailyPosts", { type: "daily", title: str(after?.authorName), body: str(after?.text), target: `dailyPosts/${id}`, fromUid: from });
  for (const uid of mentionsOf(after)) {
    await notifyUser(db, m, uid, "dailyMentions", { type: "mention", title: str(after?.authorName), body: str(after?.text), target: `dailyPosts/${id}`, fromUid: from });
  }
}

export async function onDailyReplyWritten(db: Firestore, m: Messenger, postId: string, before: Data, after: Data): Promise<void> {
  const post = db.doc(`dailyPosts/${postId}`);
  if (!before && after) await post.set({ replyCount: FieldValue.increment(1) }, { merge: true });
  if (before && !after) await post.set({ replyCount: FieldValue.increment(-1) }, { merge: true });
  if (!becameVisible(before, after)) return;
  const from = str(after?.authorUid);
  const msg = { title: str(after?.authorName), body: str(after?.text), target: `dailyPosts/${postId}`, fromUid: from };
  const owner = str((await post.get()).get("authorUid"));
  const mentioned = mentionsOf(after);
  if (owner && !mentioned.includes(owner)) await notifyUser(db, m, owner, "dailyReplies", { type: "dailyReply", ...msg });
  for (const uid of mentioned) await notifyUser(db, m, uid, "dailyMentions", { type: "mention", ...msg });
}

export async function onDailyLikeChanged(db: Firestore, postId: string, delta: 1 | -1): Promise<void> {
  await db.doc(`dailyPosts/${postId}`).set({ likeCount: FieldValue.increment(delta) }, { merge: true });
}
