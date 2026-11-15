// Turns one chapter of the course book (LaTeX, one chapter per video of the playlist) into passages
// for Ask Swamiji AI: one passage per section, with the video and the moment in it the section is about.
//
// The book itself is not in this repository. buildKnowledge.ts reads it from the folder given with --course.

export interface BookPassage {
  title: string;
  text: string;
  videoId: string;
  startSec: number;
}

/** The text inside the braces that open at [from] (the index of "{"), and the index after the closing brace. */
function braced(src: string, from: number): [string, number] {
  let depth = 0;
  for (let i = from; i < src.length; i++) {
    if (src[i] === "\\") { i++; continue; }
    if (src[i] === "{") depth++;
    else if (src[i] === "}" && --depth === 0) return [src.slice(from + 1, i), i + 1];
  }
  return [src.slice(from + 1), src.length];
}

/** The [count] brace arguments that follow a command ending at [from]. */
function args(src: string, from: number, count: number): [string[], number] {
  const out: string[] = [];
  let at = from;
  for (let n = 0; n < count; n++) {
    while (at < src.length && /\s/.test(src[at])) at++;
    if (src[at] !== "{") break;
    const [inner, next] = braced(src, at);
    out.push(inner);
    at = next;
  }
  return [out, at];
}

/** "05:24" or "1:02:03" as seconds. */
export function clockSeconds(clock: string): number {
  const parts = clock.trim().split(":").map((p) => Number(p));
  if (!parts.length || parts.some((p) => !Number.isFinite(p))) return 0;
  return parts.reduce((total, p) => total * 60 + p, 0);
}

// Pictures and drawings say nothing a search can use
const DROPPED = ["figure", "tikzpicture", "axis", "table", "tabular", "longtable", "center"];

/** Plain words from LaTeX: commands gone, their text kept, lists as lines. */
export function plainText(tex: string): string {
  let s = tex.replace(/(^|[^\\])%.*$/gm, "$1");
  for (const env of DROPPED) s = s.replace(new RegExp(`\\\\begin\\{${env}\\*?\\}[\\s\\S]*?\\\\end\\{${env}\\*?\\}`, "g"), "\n");
  // A coloured box keeps its heading: \begin{insight}[Prior Knowledge Needed] -> "Prior Knowledge Needed:"
  s = s.replace(/\\begin\{[a-zA-Z*]+\}\[([^\]]*)\]/g, "\n$1:\n").replace(/\\(begin|end)\{[a-zA-Z*]+\}/g, "\n");
  s = s.replace(/\\item\s*/g, "\n- ").replace(/\\(sub)*section\*?\{/g, "\n{").replace(/\\\\/g, "\n");
  // Maths arrows and the like, then every other command: its name goes, what it wraps stays
  s = s.replace(/\$\\?(left|right)?arrow\$|\$\\(rightarrow|leftarrow|to)\$/g, " ").replace(/\$([^$]*)\$/g, "$1");
  s = s.replace(/\\[a-zA-Z]+\*?(\[[^\]]*\])?/g, "").replace(/\\(.)/g, "$1").replace(/[{}]/g, "");
  return s.replace(/~/g, " ").replace(/[ \t]+/g, " ").replace(/ *\n */g, "\n").replace(/\n{2,}/g, "\n").trim();
}

/**
 * One chapter as passages. The chapter opens with \videochapter{n}{title}{videoId}{length}; each \section
 * (with its subsections) is a passage titled "<chapter>: <section>", and \videoframe{..}{..}{mm:ss} lines
 * say where in the video the text has reached. [speaker] replaces the book's "the speaker".
 */
export function chapterPassages(tex: string, speaker: { from: RegExp; to: string }): BookPassage[] {
  const head = /\\videochapter/.exec(tex);
  if (!head) return [];
  const [[, chapterTitleTex, videoId], bodyFrom] = args(tex, head.index + "\\videochapter".length, 4);
  if (!videoId) return [];
  const chapterTitle = plainText(chapterTitleTex);
  const body = tex.slice(bodyFrom);

  // Cut at \section and \videoframe, in the order they come
  const marks: { at: number; end: number; kind: "section" | "frame"; value: string }[] = [];
  const finder = /\\(section\*?|videoframes?)(?=\s*\{)/g;
  for (let m = finder.exec(body); m; m = finder.exec(body)) {
    const kind = m[1].startsWith("section") ? "section" : "frame";
    const [found, end] = args(body, m.index + m[0].length, kind === "section" ? 1 : m[1] === "videoframes" ? 6 : 3);
    // A frame's last argument is its time in the video
    marks.push({ at: m.index, end, kind, value: found[found.length - 1] ?? "" });
    finder.lastIndex = end;
  }

  const out: BookPassage[] = [];
  let title = chapterTitle;
  let startSec = 0;
  let sectionStart = 0;
  let textFrom = 0;
  let text = "";
  const close = (upTo: number) => {
    text += body.slice(textFrom, upTo);
    const words = plainText(text).replace(speaker.from, speaker.to);
    if (words.length > 40) out.push({ title, text: `${title}\n${words}`, videoId, startSec: sectionStart });
    text = "";
  };
  for (const mark of marks) {
    if (mark.kind === "frame") {
      // Leave the frame's own caption out of the text, and remember the time
      text += body.slice(textFrom, mark.at);
      textFrom = mark.end;
      startSec = clockSeconds(mark.value);
      continue;
    }
    close(mark.at);
    textFrom = mark.end;
    title = `${chapterTitle}: ${plainText(mark.value)}`;
    sectionStart = startSec;
  }
  close(body.length);
  return out;
}

export const SPEAKER_EN = { from: /\b[Tt]he speaker\b/g, to: "Swamiji" };
export const SPEAKER_HI = { from: /वक्ता/g, to: "स्वामीजी" };
