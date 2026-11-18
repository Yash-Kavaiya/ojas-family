import { isPrivateAddress, linkPreviewFor, parsePreview, previewTarget, youtubeOembedUrl, type PageFetcher } from "../src/linkPreview";
import { clearDb, testDb } from "./emulator";

const db = testDb();
beforeEach(async () => { await clearDb(db); });

describe("parsePreview", () => {
  it("reads the Open Graph title, description, picture and site", () => {
    const html = `<html><head><title>Plain</title>
      <meta property="og:title" content="Ojas &amp; Healing">
      <meta content='A calm place' property='og:description'>
      <meta property="og:image" content="/img/cover.jpg">
      <meta property="og:site_name" content="Ojas"></head></html>`;
    expect(parsePreview(html, "https://ojashealing.com/book")).toEqual({
      title: "Ojas & Healing", description: "A calm place", image: "https://ojashealing.com/img/cover.jpg", site: "Ojas",
    });
  });

  it("falls back to the page title, the description tag and the address's name", () => {
    const html = `<head><TITLE> Breath &#8211; work </TITLE><meta name="description" content="Daily practice"></head>`;
    expect(parsePreview(html, "https://www.example.org/a")).toEqual({
      title: "Breath – work", description: "Daily practice", image: "", site: "example.org",
    });
  });

  it("uses the Twitter tags when Open Graph is missing, and drops a picture that is not a web address", () => {
    const html = `<meta name="twitter:title" content="T"><meta name="twitter:image" content="javascript:alert(1)">`;
    expect(parsePreview(html, "https://x.com/p")).toEqual({ title: "T", description: "", image: "", site: "x.com" });
  });

  it("keeps long texts short", () => {
    const html = `<meta property="og:title" content="${"a".repeat(500)}"><meta property="og:description" content="${"b".repeat(900)}">`;
    const p = parsePreview(html, "https://e.com");
    expect(p.title.length).toBeLessThanOrEqual(200);
    expect(p.description.length).toBeLessThanOrEqual(300);
  });
});

describe("previewTarget", () => {
  it("accepts web addresses and drops the part after #", () => {
    expect(previewTarget("https://e.com/a?b=1#top")).toBe("https://e.com/a?b=1");
    expect(previewTarget("http://e.com")).toBe("http://e.com/");
  });
  it("refuses other kinds of address, odd ports and plain numbers for a host", () => {
    for (const bad of ["ftp://e.com", "file:///etc/passwd", "https://e.com:8080/", "http://127.0.0.1/", "http://[::1]/", "http://localhost/", "nope", "", 5]) {
      expect(previewTarget(bad)).toBeNull();
    }
  });
});

describe("isPrivateAddress", () => {
  it("knows inside-network addresses", () => {
    for (const ip of ["127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "0.0.0.0", "100.64.0.1", "::1", "fc00::1", "fe80::1", "::ffff:10.0.0.1"]) {
      expect(isPrivateAddress(ip)).toBe(true);
    }
  });
  it("lets public addresses through", () => {
    for (const ip of ["8.8.8.8", "142.250.183.14", "2607:f8b0:4004:c07::64"]) expect(isPrivateAddress(ip)).toBe(false);
  });
});

describe("youtubeOembedUrl", () => {
  it("asks YouTube itself for its videos", () => {
    expect(youtubeOembedUrl("https://youtu.be/abc")).toBe("https://www.youtube.com/oembed?format=json&url=https%3A%2F%2Fyoutu.be%2Fabc");
    expect(youtubeOembedUrl("https://m.youtube.com/watch?v=abc")).not.toBeNull();
    expect(youtubeOembedUrl("https://e.com/youtube.com")).toBeNull();
  });
});

describe("linkPreviewFor", () => {
  const NOW = new Date("2026-11-18T06:00:00Z");
  const page = (html: string): PageFetcher => { const f = jest.fn(async () => ({ body: html, finalUrl: "https://e.com/a" })); return f; };

  it("fetches a page once and serves the saved preview after that", async () => {
    const fetchPage = page(`<meta property="og:title" content="Hello">`);
    const first = await linkPreviewFor(db, "https://e.com/a", { fetchPage, now: NOW });
    const again = await linkPreviewFor(db, "https://e.com/a#x", { fetchPage, now: NOW });
    expect(first).toEqual({ title: "Hello", description: "", image: "", site: "e.com" });
    expect(again).toEqual(first);
    expect(fetchPage).toHaveBeenCalledTimes(1);
  });

  it("fetches again after a week", async () => {
    const fetchPage = page(`<meta property="og:title" content="Hello">`);
    await linkPreviewFor(db, "https://e.com/a", { fetchPage, now: NOW });
    await linkPreviewFor(db, "https://e.com/a", { fetchPage, now: new Date(NOW.getTime() + 8 * 86400_000) });
    expect(fetchPage).toHaveBeenCalledTimes(2);
  });

  it("returns null for a page without a title, and does not keep asking for a day", async () => {
    const fetchPage = page(`<p>nothing</p>`);
    expect(await linkPreviewFor(db, "https://e.com/a", { fetchPage, now: NOW })).toBeNull();
    expect(await linkPreviewFor(db, "https://e.com/a", { fetchPage, now: new Date(NOW.getTime() + 3600_000) })).toBeNull();
    expect(fetchPage).toHaveBeenCalledTimes(1);
  });

  it("returns null when the page can't be reached", async () => {
    const fetchPage: PageFetcher = jest.fn(async () => { throw new Error("timeout"); });
    expect(await linkPreviewFor(db, "https://e.com/a", { fetchPage, now: NOW })).toBeNull();
  });

  it("refuses a bad address without fetching", async () => {
    const fetchPage = page("");
    expect(await linkPreviewFor(db, "http://169.254.169.254/latest", { fetchPage, now: NOW })).toBeNull();
    expect(fetchPage).not.toHaveBeenCalled();
  });

  it("reads YouTube's answer for a video", async () => {
    const fetchPage: PageFetcher = jest.fn(async (url: string) => {
      expect(url).toContain("youtube.com/oembed");
      return { body: JSON.stringify({ title: "Morning meditation", author_name: "Ojas", thumbnail_url: "https://i.ytimg.com/vi/abc/hqdefault.jpg" }), finalUrl: url };
    });
    expect(await linkPreviewFor(db, "https://youtu.be/abc", { fetchPage, now: NOW })).toEqual({
      title: "Morning meditation", description: "Ojas", image: "https://i.ytimg.com/vi/abc/hqdefault.jpg", site: "YouTube",
    });
  });
});
