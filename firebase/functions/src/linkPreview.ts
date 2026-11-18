import { createHash } from "node:crypto";
import { lookup } from "node:dns/promises";
import { isIP } from "node:net";
import { Timestamp, type Firestore } from "firebase-admin/firestore";

/** What a link card shows: the page's title, a line about it, its picture and the site's name. */
export interface LinkPreview { title: string; description: string; image: string; site: string }

/** Downloads a page (or YouTube's answer) and says where it ended up after redirects. */
export type PageFetcher = (url: string) => Promise<{ body: string; finalUrl: string }>;

const WEEK_MS = 7 * 86400_000;
const DAY_MS = 86400_000;
const MAX_BYTES = 400_000;

/** The address to look up, without the part after "#", or null if it is not a public web page address. */
export function previewTarget(raw: unknown): string | null {
  if (typeof raw !== "string" || raw.length === 0 || raw.length > 2000) return null;
  let u: URL;
  try { u = new URL(raw); } catch { return null; }
  if (u.protocol !== "https:" && u.protocol !== "http:") return null;
  if (u.port !== "" || u.username !== "" || u.password !== "") return null;
  const host = u.hostname.replace(/^\[|\]$/g, "").toLowerCase();
  // Numbers for a host and names inside the network are never looked up
  if (isIP(host) !== 0 || host === "localhost" || host.endsWith(".localhost") || host.endsWith(".internal") || !host.includes(".")) return null;
  u.hash = "";
  return u.toString();
}

/** True for addresses inside a network (the server's own, Google's metadata, home routers). */
export function isPrivateAddress(ip: string): boolean {
  const mapped = ip.toLowerCase().match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/);
  if (mapped) return isPrivateAddress(mapped[1]);
  if (isIP(ip) === 4) {
    const [a, b] = ip.split(".").map(Number);
    return a === 0 || a === 10 || a === 127 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31)
      || (a === 192 && b === 168) || (a === 100 && b >= 64 && b <= 127) || a >= 224;
  }
  const v6 = ip.toLowerCase();
  return v6 === "::" || v6 === "::1" || v6.startsWith("fc") || v6.startsWith("fd") || v6.startsWith("fe8")
    || v6.startsWith("fe9") || v6.startsWith("fea") || v6.startsWith("feb") || v6.startsWith("ff");
}

/** YouTube's own description of a video link, which is more reliable than its page. */
export function youtubeOembedUrl(url: string): string | null {
  let host: string;
  try { host = new URL(url).hostname.toLowerCase(); } catch { return null; }
  const yt = host === "youtu.be" || host === "youtube.com" || host.endsWith(".youtube.com");
  return yt ? `https://www.youtube.com/oembed?format=json&url=${encodeURIComponent(url)}` : null;
}

function decodeEntities(s: string): string {
  const named: Record<string, string> = { amp: "&", lt: "<", gt: ">", quot: "\"", apos: "'", nbsp: " " };
  return s.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (all, code: string) => {
    if (code[0] === "#") {
      const n = code[1] === "x" || code[1] === "X" ? parseInt(code.slice(2), 16) : parseInt(code.slice(1), 10);
      return Number.isFinite(n) && n > 0 && n < 0x110000 ? String.fromCodePoint(n) : all;
    }
    return named[code.toLowerCase()] ?? all;
  });
}

const clean = (s: string | undefined, max: number) => {
  const t = decodeEntities(s ?? "").replace(/\s+/g, " ").trim();
  return t.length > max ? t.slice(0, max - 1).trimEnd() + "…" : t;
};

/** The preview a page's head describes, with a relative picture address made whole. */
export function parsePreview(html: string, pageUrl: string): LinkPreview {
  const meta = new Map<string, string>();
  for (const tag of html.match(/<meta\b[^>]*>/gi) ?? []) {
    const attrs = new Map<string, string>();
    for (const m of tag.matchAll(/([a-z:_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))/gi)) {
      attrs.set(m[1].toLowerCase(), m[2] ?? m[3] ?? m[4] ?? "");
    }
    const key = (attrs.get("property") ?? attrs.get("name") ?? "").toLowerCase();
    const content = attrs.get("content");
    if (key && content !== undefined && !meta.has(key)) meta.set(key, content);
  }
  const titleTag = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1];
  const pick = (...keys: string[]) => keys.map((k) => meta.get(k)).find((v) => v !== undefined && v.trim() !== "");

  let image = "";
  const rawImage = pick("og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src");
  if (rawImage) {
    try {
      const u = new URL(decodeEntities(rawImage.trim()), pageUrl);
      if (u.protocol === "https:" || u.protocol === "http:") image = u.toString();
    } catch { /* not an address */ }
  }
  let host = "";
  try { host = new URL(pageUrl).hostname.replace(/^www\./, ""); } catch { /* keep empty */ }
  return {
    title: clean(pick("og:title", "twitter:title") ?? titleTag, 200),
    description: clean(pick("og:description", "twitter:description", "description"), 300),
    image,
    site: clean(pick("og:site_name"), 80) || host,
  };
}

function youtubePreview(body: string): LinkPreview {
  const j = JSON.parse(body) as Record<string, unknown>;
  const str = (v: unknown) => (typeof v === "string" ? v : "");
  const thumb = str(j.thumbnail_url);
  return {
    title: clean(str(j.title), 200),
    description: clean(str(j.author_name), 300),
    image: thumb.startsWith("https://") ? thumb : "",
    site: "YouTube",
  };
}

/**
 * The preview of [rawUrl], looked up at most once a week for everyone (once a day when the page
 * has nothing to show or can't be reached). Null when there is nothing worth showing.
 */
export async function linkPreviewFor(
  db: Firestore, rawUrl: unknown, deps: { fetchPage: PageFetcher; now: Date },
): Promise<LinkPreview | null> {
  const url = previewTarget(rawUrl);
  if (!url) return null;
  const ref = db.collection("linkPreviews").doc(createHash("sha256").update(url).digest("hex").slice(0, 40));
  const now = deps.now.getTime();
  const saved = (await ref.get()).data();
  if (saved && typeof saved.fetchedAt === "number") {
    const age = now - saved.fetchedAt;
    if (saved.ok === true && age < WEEK_MS) {
      return { title: saved.title, description: saved.description, image: saved.image, site: saved.site };
    }
    if (saved.ok !== true && age < DAY_MS) return null;
  }

  let preview: LinkPreview | null = null;
  try {
    const yt = youtubeOembedUrl(url);
    const page = await deps.fetchPage(yt ?? url);
    preview = yt ? youtubePreview(page.body) : parsePreview(page.body, page.finalUrl);
    if (!preview.title) preview = null;
  } catch {
    preview = null;
  }
  // Unused previews are removed by the TTL policy on expireAt 30 days after the last look-up
  const expireAt = Timestamp.fromMillis(now + 30 * DAY_MS);
  await ref.set(preview ? { url, ok: true, fetchedAt: now, expireAt, ...preview } : { url, ok: false, fetchedAt: now, expireAt });
  return preview;
}

/**
 * Fetches a public page: only http(s) on the usual ports, never an address inside a network
 * (checked again on every redirect), at most 5 seconds and the first 400 KB.
 */
export const fetchPublicPage: PageFetcher = async (start) => {
  let url = start;
  for (let hop = 0; hop < 5; hop++) {
    const target = previewTarget(url);
    if (!target) throw new Error("Not a public address");
    const host = new URL(target).hostname;
    const addresses = await lookup(host, { all: true });
    if (addresses.length === 0 || addresses.some((a) => isPrivateAddress(a.address))) throw new Error("Private address");
    const res = await fetch(target, {
      redirect: "manual",
      signal: AbortSignal.timeout(5_000),
      headers: {
        // Many sites only give their preview tags to link-preview robots
        "user-agent": "Mozilla/5.0 (compatible; OjasLinkPreview/1.0; +https://ojas-family-app.web.app)",
        "accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.5",
        "accept-language": "en-IN,en;q=0.8",
      },
    });
    if (res.status >= 300 && res.status < 400) {
      const next = res.headers.get("location");
      if (!next) throw new Error("Redirect without an address");
      url = new URL(next, target).toString();
      continue;
    }
    if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`);
    const type = res.headers.get("content-type") ?? "";
    if (!/html|json|xml/i.test(type)) throw new Error("Not a page");
    const reader = res.body.getReader();
    const chunks: Uint8Array[] = [];
    let size = 0;
    while (size < MAX_BYTES) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(value);
      size += value.length;
    }
    await reader.cancel().catch(() => undefined);
    return { body: Buffer.concat(chunks).toString("utf8"), finalUrl: target };
  }
  throw new Error("Too many redirects");
};
