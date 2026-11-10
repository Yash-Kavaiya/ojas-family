import { FieldValue, type Firestore } from "firebase-admin/firestore";

/**
 * Adds [delta] to a counter kept on a post. When the post itself is gone (deleting it also deletes its
 * likes and comments, each of which lands here) nothing is written: a merge would bring the post back
 * as an empty document holding only the counter.
 */
export async function bumpCount(db: Firestore, path: string, field: string, delta: number): Promise<boolean> {
  try {
    await db.doc(path).update({ [field]: FieldValue.increment(delta) });
    return true;
  } catch (e) {
    // NOT_FOUND (gRPC code 5): the post was deleted
    if ((e as { code?: number }).code === 5) return false;
    throw e;
  }
}
