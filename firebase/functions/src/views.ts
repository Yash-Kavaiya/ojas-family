import type { Firestore } from "firebase-admin/firestore";
import { bumpCount } from "./counters";

/** Where members' views are counted: each post or item keeps one views/{uid} doc per member. */
export const VIEWED = ["thoughts", "questions", "announcements", "dailyPosts", "content", "books"] as const;
export type Viewed = (typeof VIEWED)[number];

/**
 * A member saw [collection]/[id] for the first time (the rules allow one views doc each, never
 * changed): the item's viewCount goes up by one. A view of a post deleted meanwhile is dropped.
 */
export async function onViewAdded(db: Firestore, collection: Viewed, id: string): Promise<void> {
  await bumpCount(db, `${collection}/${id}`, "viewCount", 1);
}
