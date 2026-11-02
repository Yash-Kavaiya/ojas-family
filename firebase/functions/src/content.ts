import { FieldValue, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { XMLParser } from "fast-xml-parser";

export const SECTIONS = ["blue_light", "quick_protection", "morning", "night", "yoga", "other", "understanding"] as const;

export interface FeedEntry {
  videoId: string;
  title: string;
  published: string;
}

type FetchLike = (url: string) => Promise<{ ok: boolean; json: () => Promise<unknown> }>;

const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;
const YOUTUBE_HOSTS = new Set(["youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"]);

// Same rules as the app's youtubeIdFrom so a link accepted on the phone is accepted here
export function youtubeIdFrom(input: string): string | null {
  const text = input.trim();
  if (VIDEO_ID.test(text)) return text;
  let url: URL;
  try {
    url = new URL(text);
  } catch {
    return null;
  }
  if (!YOUTUBE_HOSTS.has(url.hostname.toLowerCase())) return null;
  const path = url.pathname.replace(/^\/+|\/+$/g, "");
  let candidate: string | null = null;
  if (url.hostname.toLowerCase() === "youtu.be") candidate = path;
  else if (path === "watch") candidate = url.searchParams.get("v");
  else if (path.startsWith("shorts/") || path.startsWith("embed/")) candidate = path.split("/")[1];
  return candidate && VIDEO_ID.test(candidate) ? candidate : null;
}

// YouTube's public playlist RSS: no API key, newest 15 videos per playlist
export function parsePlaylistFeed(xml: string): FeedEntry[] {
  const parsed = new XMLParser({ ignoreAttributes: true }).parse(xml);
  const raw = parsed?.feed?.entry ?? [];
  const entries = Array.isArray(raw) ? raw : [raw];
  return entries
    .map((e: Record<string, unknown>) => ({
      videoId: String(e["yt:videoId"] ?? ""),
      title: String(e.title ?? ""),
      published: String(e.published ?? ""),
    }))
    .filter((e) => VIDEO_ID.test(e.videoId));
}

/** Creates missing content docs only, so re-importing never undoes an admin's edits. */
export async function importEntries(db: Firestore, section: string, entries: FeedEntry[]): Promise<{ created: number; skipped: number }> {
  let created = 0;
  let skipped = 0;
  for (const [index, entry] of entries.entries()) {
    const ref = db.doc(`content/yt_${entry.videoId}`);
    try {
      await ref.create({
        section,
        videoId: entry.videoId,
        title: entry.title.slice(0, 200),
        minUserLevel: 1,
        minMemberLevel: 0,
        order: index,
        published: true,
        createdAt: FieldValue.serverTimestamp(),
      });
      created++;
    } catch (e) {
      // ALREADY_EXISTS (gRPC code 6) means an admin already has this video; anything else is real
      if ((e as { code?: number }).code !== 6) throw e;
      skipped++;
    }
  }
  return { created, skipped };
}

function requireAdmin(callerToken: Record<string, unknown> | undefined) {
  if (callerToken?.role !== "admin") throw new HttpsError("permission-denied", "Only admins can manage content");
}

function requireSection(section: string) {
  if (!(SECTIONS as readonly string[]).includes(section)) throw new HttpsError("invalid-argument", `Unknown section: ${section}`);
}

export async function addVideoFor(
  db: Firestore,
  callerToken: Record<string, unknown> | undefined,
  input: { url: string; section: string; title?: string },
  fetchFn: FetchLike = fetch,
): Promise<{ id: string }> {
  requireAdmin(callerToken);
  requireSection(input.section);
  const videoId = youtubeIdFrom(input.url ?? "");
  if (!videoId) throw new HttpsError("invalid-argument", "Not a YouTube video link");

  let title = input.title?.trim();
  if (!title) {
    const res = await fetchFn(`https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=${videoId}&format=json`);
    if (!res.ok) throw new HttpsError("not-found", "Video is private, removed or not embeddable");
    title = String(((await res.json()) as { title?: string }).title ?? videoId);
  }

  const id = `yt_${videoId}`;
  await db.doc(`content/${id}`).set(
    { section: input.section, videoId, title: title.slice(0, 200), minUserLevel: 1, minMemberLevel: 0, order: Date.now(), published: true },
    { merge: true },
  );
  return { id };
}

export async function importPlaylistFor(
  db: Firestore,
  callerToken: Record<string, unknown> | undefined,
  input: { playlistId: string; section: string },
  fetchText: (url: string) => Promise<string> = async (url) => (await fetch(url)).text(),
): Promise<{ created: number; skipped: number }> {
  requireAdmin(callerToken);
  requireSection(input.section);
  if (!/^[A-Za-z0-9_-]{10,64}$/.test(input.playlistId ?? "")) throw new HttpsError("invalid-argument", "Invalid playlist id");
  const xml = await fetchText(`https://www.youtube.com/feeds/videos.xml?playlist_id=${input.playlistId}`);
  return importEntries(db, input.section, parsePlaylistFeed(xml));
}
