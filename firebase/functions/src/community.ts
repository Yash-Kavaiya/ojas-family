import type { Firestore } from "firebase-admin/firestore";
import { bumpCount } from "./counters";
import { announcePost, mentionsOf, notifyUser, type Group, type Messenger } from "./notify";

type Data = Record<string, unknown> | undefined;

// A post is announced once, when it first becomes visible (on create, or when an admin approves it)
const becameVisible = (before: Data, after: Data) => after?.status === "visible" && before?.status !== "visible";
const str = (v: unknown) => (typeof v === "string" ? v : "");

export async function onAnnouncementCreated(db: Firestore, m: Messenger, id: string, data: Data): Promise<void> {
  await announcePost(db, m, "announcements", `announcement:${id}`, { type: "announcement", title: str(data?.title), body: str(data?.body), target: `announcements/${id}` });
}

export async function onThoughtOfDay(db: Firestore, m: Messenger, today: string): Promise<boolean> {
  const t = await db.doc(`thoughts/${today}`).get();
  if (!t.exists) return false;
  await announcePost(db, m, "thoughtOfDay", `thought:${today}`, { type: "thought", title: str(t.get("title")), body: str(t.get("text")), target: `thoughts/${today}` });
  return true;
}

export async function onQuestionNotify(db: Firestore, m: Messenger, id: string, before: Data, after: Data): Promise<void> {
  if (!becameVisible(before, after)) return;
  const from = str(after?.authorUid);
  await announcePost(db, m, "communityPosts", `question:${id}`, { type: "question", title: str(after?.authorName), body: str(after?.title), target: `questions/${id}`, fromUid: from });
  for (const uid of mentionsOf(after)) {
    await notifyUser(db, m, uid, { group: "qa", action: "mention" }, { type: "mention", title: str(after?.authorName), body: str(after?.title), target: `questions/${id}`, fromUid: from }, `mention:questions/${id}`);
  }
}

/**
 * A reply (a comment with parentId) tells the author of the comment it hangs under, unless they wrote
 * the reply or are mentioned in it (they get the mention instead). Returns who was told, or "".
 */
async function notifyCommentReply(
  db: Firestore, m: Messenger, collection: string, after: Data, target: string, group: Exclude<Group, "critical">
): Promise<string> {
  const parentId = str(after?.parentId);
  if (!parentId) return "";
  const parentAuthor = str((await db.doc(`${collection}/${parentId}`).get()).get("authorUid"));
  const from = str(after?.authorUid);
  if (!parentAuthor || parentAuthor === from || mentionsOf(after).includes(parentAuthor)) return "";
  await notifyUser(db, m, parentAuthor, { group, action: "likeReply" }, {
    type: "commentReply", title: str(after?.authorName), body: str(after?.text), target, fromUid: from,
  }, `reply:${collection}/${parentId}`);
  return parentAuthor;
}

/** A visible announcement comment tells the people mentioned in it; a reply tells the comment's author. */
export async function onAnnouncementCommentNotify(db: Firestore, m: Messenger, id: string, before: Data, after: Data): Promise<void> {
  if (!becameVisible(before, after)) return;
  await notifyCommentReply(db, m, `announcements/${id}/comments`, after, `announcements/${id}`, "announcement");
  // A mentioned person gets the mention (notifyCommentReply leaves them out, so they are told once)
  for (const uid of mentionsOf(after)) {
    if (uid === str(after?.authorUid)) continue;
    await notifyUser(db, m, uid, { group: "announcement", action: "mention" }, {
      type: "mention", title: str(after?.authorName), body: str(after?.text), target: `announcements/${id}`, fromUid: str(after?.authorUid),
    }, `mention:announcements/${id}`);
  }
}

/** A visible answer tells the person who asked, and anyone mentioned in it. */
export async function onAnswerNotify(db: Firestore, m: Messenger, questionId: string, before: Data, after: Data): Promise<void> {
  if (!becameVisible(before, after)) return;
  const from = str(after?.authorUid);
  const msg = { title: str(after?.authorName), body: str(after?.text), target: `questions/${questionId}`, fromUid: from };
  const asker = str((await db.doc(`questions/${questionId}`).get()).get("authorUid"));
  const mentioned = mentionsOf(after);
  const replied = await notifyCommentReply(db, m, `questions/${questionId}/answers`, after, `questions/${questionId}`, "qa");
  if (asker && asker !== replied && !mentioned.includes(asker)) {
    await notifyUser(db, m, asker, { group: "qa", action: "likeReply" }, { type: "answer", ...msg }, `answer:questions/${questionId}`);
  }
  for (const uid of mentioned) await notifyUser(db, m, uid, { group: "qa", action: "mention" }, { type: "mention", ...msg }, `mention:questions/${questionId}`);
}

export async function onThoughtCommentNotify(db: Firestore, m: Messenger, thoughtId: string, before: Data, after: Data): Promise<void> {
  if (!becameVisible(before, after)) return;
  await notifyCommentReply(db, m, `thoughts/${thoughtId}/comments`, after, `thoughts/${thoughtId}`, "thought");
  for (const uid of mentionsOf(after)) {
    await notifyUser(db, m, uid, { group: "thought", action: "mention" }, {
      type: "mention", title: str(after?.authorName), body: str(after?.text), target: `thoughts/${thoughtId}`, fromUid: str(after?.authorUid),
    }, `mention:thoughts/${thoughtId}`);
  }
}

// ---- Daily Activities (paid members) ----

export async function onDailyPostWritten(db: Firestore, m: Messenger, id: string, before: Data, after: Data): Promise<void> {
  if (!becameVisible(before, after)) return;
  const from = str(after?.authorUid);
  await announcePost(db, m, "dailyPosts", `daily:${id}`, { type: "daily", title: str(after?.authorName), body: str(after?.text), target: `dailyPosts/${id}`, fromUid: from });
  for (const uid of mentionsOf(after)) {
    await notifyUser(db, m, uid, { group: "activity", action: "mention" }, { type: "mention", title: str(after?.authorName), body: str(after?.text), target: `dailyPosts/${id}`, fromUid: from }, `mention:dailyPosts/${id}`);
  }
}

export async function onDailyReplyWritten(db: Firestore, m: Messenger, postId: string, before: Data, after: Data): Promise<void> {
  const post = db.doc(`dailyPosts/${postId}`);
  if (!before && after) await bumpCount(db, post.path, "replyCount", 1);
  if (before && !after) await bumpCount(db, post.path, "replyCount", -1);
  if (!becameVisible(before, after)) return;
  const from = str(after?.authorUid);
  const msg = { title: str(after?.authorName), body: str(after?.text), target: `dailyPosts/${postId}`, fromUid: from };
  const owner = str((await post.get()).get("authorUid"));
  const mentioned = mentionsOf(after);
  const replied = await notifyCommentReply(db, m, `dailyPosts/${postId}/replies`, after, `dailyPosts/${postId}`, "activity");
  if (owner && owner !== replied && !mentioned.includes(owner)) {
    await notifyUser(db, m, owner, { group: "activity", action: "likeReply" }, { type: "dailyReply", ...msg }, `reply:dailyPosts/${postId}`);
  }
  for (const uid of mentioned) await notifyUser(db, m, uid, { group: "activity", action: "mention" }, { type: "mention", ...msg }, `mention:dailyPosts/${postId}`);
}

export async function onDailyLikeChanged(db: Firestore, postId: string, delta: 1 | -1): Promise<void> {
  await bumpCount(db, `dailyPosts/${postId}`, "likeCount", delta);
}

/** Announcement likes and comments only keep counts (no points, no pushes). */
export async function onAnnouncementCountChanged(db: Firestore, id: string, field: "likeCount" | "commentCount", delta: 1 | -1): Promise<void> {
  await bumpCount(db, `announcements/${id}`, field, delta);
}

/**
 * A like added to or taken off a comment, answer or reply keeps that comment's likeCount.
 * Nothing happens if the comment itself is gone. Never below zero.
 */
export async function onCommentLikeChanged(db: Firestore, commentPath: string, delta: 1 | -1): Promise<void> {
  const ref = db.doc(commentPath);
  await db.runTransaction(async (tx) => {
    const c = await tx.get(ref);
    if (!c.exists) return;
    const now = Number(c.get("likeCount") ?? 0);
    tx.update(ref, { likeCount: Math.max(0, now + delta) });
  });
}

// ---- Likes: the author hears about them (Like & Reply), one counted line per post or comment ----

/** Which notification group a post or comment path belongs to. */
export function groupOfPath(path: string): Exclude<Group, "critical"> | null {
  const top = path.split("/")[0];
  return top === "thoughts" ? "thought" : top === "announcements" ? "announcement" : top === "questions" ? "qa" : top === "dailyPosts" ? "activity" : null;
}

/**
 * A like on [likedPath] (a question, an Activity post, or a comment, answer or reply) tells whoever wrote it.
 * Likes keep one line per thing liked: "Meera and 2 others liked your post". Taking a like back says nothing.
 */
export async function onLikeNotify(db: Firestore, m: Messenger, likedPath: string, likerUid: string): Promise<void> {
  const group = groupOfPath(likedPath);
  if (!group) return;
  const liked = await db.doc(likedPath).get();
  const author = str(liked.get("authorUid"));
  if (!author || author === likerUid) return;
  const parts = likedPath.split("/");
  const name = str((await db.doc(`users/${likerUid}`).get()).get("displayName"));
  await notifyUser(db, m, author, { group, action: "likeReply" }, {
    type: parts.length > 2 ? "commentLike" : "like",
    title: name,
    body: str(liked.get("title")) || str(liked.get("text")),
    target: `${parts[0]}/${parts[1]}`,
    fromUid: likerUid,
  }, `like:${likedPath}`);
}
