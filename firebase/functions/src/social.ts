import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { awardPoints } from "./points";
import { istDay } from "./time";

interface PostData {
  authorUid?: string;
  status?: string;
}

// A post earns its point when it first becomes visible: on create, or later when an admin approves it
function becameVisible(before: PostData | undefined, after: PostData | undefined): boolean {
  return after?.status === "visible" && before?.status !== "visible";
}

function publishedOn(data: FirebaseFirestore.DocumentData | undefined): string | null {
  const at = (data?.publishAt ?? data?.createdAt) as Timestamp | undefined;
  return at ? istDay(at.toDate()) : null;
}

/**
 * A like was added (+1) or removed (-1) on a thought or question at [parentPath].
 * Only a like on something published today (IST) earns a point, and the event key makes
 * unlike + like again worthless.
 */
export async function onLikeChanged(db: Firestore, parentPath: string, uid: string, delta: 1 | -1, now: Date): Promise<void> {
  const parent = db.doc(parentPath);
  await parent.set({ likeCount: FieldValue.increment(delta) }, { merge: true });
  if (delta < 0) return;

  const today = istDay(now);
  if (publishedOn((await parent.get()).data()) !== today) return;
  const [kind, id] = parentPath.split("/");
  await awardPoints(db, uid, {
    eventKey: `like:${kind === "thoughts" ? "thought" : "question"}:${id}`,
    amount: 1,
    reason: "like",
    milestone: false,
    cap: { action: "like", day: today },
  });
}

export async function onCommentWritten(
  db: Firestore, thoughtPath: string, commentId: string, before: PostData | undefined, after: PostData | undefined, now: Date,
): Promise<void> {
  if (!before && after) await db.doc(thoughtPath).set({ commentCount: FieldValue.increment(1) }, { merge: true });
  if (before && !after) await db.doc(thoughtPath).set({ commentCount: FieldValue.increment(-1) }, { merge: true });
  if (after?.authorUid && becameVisible(before, after)) {
    await awardPoints(db, after.authorUid, {
      eventKey: `comment:${thoughtPath.split("/")[1]}:${commentId}`,
      amount: 1,
      reason: "comment",
      milestone: false,
      cap: { action: "comment", day: istDay(now) },
    });
  }
}

export async function onQuestionWritten(
  db: Firestore, questionId: string, before: PostData | undefined, after: PostData | undefined, now: Date,
): Promise<void> {
  if (after?.authorUid && becameVisible(before, after)) {
    await awardPoints(db, after.authorUid, {
      eventKey: `question:${questionId}`,
      amount: 1,
      reason: "question",
      milestone: false,
      cap: { action: "question", day: istDay(now) },
    });
  }
}

export async function onAnswerWritten(
  db: Firestore, questionId: string, answerId: string, before: PostData | undefined, after: PostData | undefined, now: Date,
): Promise<void> {
  const question = db.doc(`questions/${questionId}`);
  if (!before && after) await question.set({ answerCount: FieldValue.increment(1) }, { merge: true });
  if (before && !after) await question.set({ answerCount: FieldValue.increment(-1) }, { merge: true });
  if (after?.authorUid && becameVisible(before, after)) {
    await awardPoints(db, after.authorUid, {
      eventKey: `answer:${questionId}:${answerId}`,
      amount: 1,
      reason: "answer",
      milestone: false,
      cap: { action: "comment", day: istDay(now) },
    });
  }
}
