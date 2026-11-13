/** One passage of Ojas Healing's teachings, from ai/knowledge.json (see scripts/buildKnowledge.ts). */
export interface Passage {
  id: string;
  title: string;
  text: string;
  url?: string;
  videoId?: string;
  startSec?: number;
  sourceType?: string;
  lang?: string;
}

/** Words too common to tell passages apart, in English, Hindi and Gujarati. */
const STOP_WORDS = new Set([
  "a", "an", "the", "is", "are", "am", "was", "were", "be", "to", "of", "in", "on", "for", "and", "or", "it", "this", "that",
  "i", "me", "my", "we", "you", "your", "he", "she", "they", "what", "how", "why", "when", "which", "who", "do", "does", "did",
  "can", "should", "will", "with", "about", "from", "at", "by", "as", "so", "if", "please", "tell", "explain", "swamiji", "ji",
  "है", "हैं", "का", "की", "के", "को", "में", "से", "और", "या", "यह", "वह", "क्या", "कैसे", "क्यों", "कब", "मैं", "मुझे", "मेरा", "हम", "आप",
  "पर", "भी", "तो", "ही", "था", "थी", "हो", "कर", "करें", "करना", "बताइए", "बताओ", "स्वामीजी",
  "છે", "છો", "નો", "ની", "નું", "ને", "માં", "થી", "અને", "કે", "આ", "તે", "શું", "કેવી", "રીતે", "કેમ", "ક્યારે", "હું", "મને", "મારું", "તમે", "પણ", "સ્વામીજી",
]);

/** A light English stem so "meditations", "meditating" and "meditation" meet. */
function stem(w: string): string {
  if (/[^\u0000-\u007f]/.test(w)) return w;
  for (const suffix of ["ations", "ation", "ings", "ing", "ies", "es", "s", "ed"]) {
    if (w.length > suffix.length + 3 && w.endsWith(suffix)) return w.slice(0, -suffix.length);
  }
  return w;
}

/** Splits text into search words: letters and digits of any script, lower case, common words dropped. */
export function searchWords(text: string): string[] {
  return (text.toLowerCase().match(/[\p{L}\p{M}\p{N}]+/gu) ?? []).filter((w) => w.length > 1 && !STOP_WORDS.has(w)).map(stem);
}

/** The language a question is written in, from its script; [fallback] (the app language) for Latin letters. */
export function questionLanguage(text: string, fallback: string): string {
  if (/[઀-૿]/.test(text)) return "gu";
  if (/[ऀ-ॿ]/.test(text)) return "hi";
  return fallback;
}

const K1 = 1.4;
const B = 0.75;
// Passages far weaker than the best one are noise
const MIN_SHARE_OF_BEST = 0.35;

/**
 * Ranks passages for a question with BM25, the classic keyword search. Words in a passage's title
 * count extra, since titles name the practice ("Blue Light Meditation").
 */
export class KnowledgeSearch {
  private readonly docs: string[][];
  private readonly termFreq: Map<string, number>[];
  private readonly docFreq = new Map<string, number>();
  private readonly avgLength: number;
  private readonly counterpart = new Map<string, Passage>();

  constructor(private readonly passages: Passage[]) {
    this.docs = passages.map((p) => { const t = searchWords(p.title); return [...t, ...t, ...searchWords(p.text)]; });
    this.termFreq = this.docs.map((d) => { const m = new Map<string, number>(); for (const w of d) m.set(w, (m.get(w) ?? 0) + 1); return m; });
    for (const tf of this.termFreq) for (const w of tf.keys()) this.docFreq.set(w, (this.docFreq.get(w) ?? 0) + 1);
    this.avgLength = this.docs.reduce((n, d) => n + d.length, 0) / Math.max(1, this.docs.length) || 1;
    for (const p of passages) if (p.videoId) this.counterpart.set(`${p.videoId}|${p.startSec ?? 0}|${p.lang ?? "en"}`, p);
  }

  get size(): number {
    return this.passages.length;
  }

  /** BM25 score of every passage that shares a word with [question]. */
  private keywordScores(question: string, lang?: string): Map<number, number> {
    const words = [...new Set(searchWords(question))];
    const out = new Map<number, number>();
    if (!words.length) return out;
    const n = this.passages.length;
    this.termFreq.forEach((tf, i) => {
      let score = 0;
      for (const w of words) {
        const f = tf.get(w);
        const df = this.docFreq.get(w);
        if (!f || !df) continue;
        const idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
        score += (idf * (f * (K1 + 1))) / (f + K1 * (1 - B + (B * this.docs[i].length) / this.avgLength));
      }
      if (score > 0 && lang && this.passages[i].lang === lang) score *= 1.15;
      if (score > 0) out.set(i, score);
    });
    return out;
  }

  /** Passages by index, best first: the member's language where the same video part has it, no repeats, at most [k]. */
  private pick(order: number[], k: number, lang?: string): Passage[] {
    const out: Passage[] = [];
    const seen = new Set<string>();
    for (const i of order) {
      let p = this.passages[i];
      // The same part of a video is written in English, Hindi and Gujarati: give the member's own
      if (lang && p.lang !== lang && p.videoId) p = this.counterpart.get(`${p.videoId}|${p.startSec ?? 0}|${lang}`) ?? p;
      const key = `${p.videoId ?? p.title}|${p.startSec ?? 0}`;
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(p);
      if (out.length >= k) break;
    }
    return out;
  }

  /** Keyword search alone: the best [k] passages, best first; none when no word of the question matches. */
  search(question: string, k: number, lang?: string): Passage[] {
    const scores = this.keywordScores(question, lang);
    if (!scores.size) return [];
    const best = Math.max(...scores.values());
    const order = [...scores].filter(([, s]) => s >= best * MIN_SHARE_OF_BEST).sort((a, b) => b[1] - a[1]).map(([i]) => i);
    return this.pick(order, k, lang);
  }

  /**
   * Meaning search and keyword search together. [vectors] holds one unit-length embedding per
   * passage, in passage order; [query] is the question's embedding. Meaning decides whether the
   * teachings cover the question at all ([minCosine]) and leads the ranking; shared words add to it
   * (reciprocal rank fusion). So a question in other words than the teaching still finds it, and a
   * question about something else finds nothing.
   */
  hybrid(question: string, query: Float32Array, vectors: Vectors, k: number, lang?: string, minCosine = MIN_COSINE): Passage[] {
    const n = this.passages.length;
    if (!n || vectors.data.length !== n * vectors.dim || query.length !== vectors.dim) return this.search(question, k, lang);
    // How close each passage's meaning is to the question
    const likeness = new Float32Array(n);
    let closest = 0;
    for (let i = 0; i < n; i++) {
      let dot = 0;
      const off = i * vectors.dim;
      for (let d = 0; d < vectors.dim; d++) dot += query[d] * vectors.data[off + d];
      likeness[i] = dot;
      if (dot > closest) closest = dot;
    }
    // Nothing in the teachings is about this (a cricket score, a coding question): say so, whatever words happen to match
    if (closest < minCosine) return [];
    const byMeaning: number[] = [];
    for (let i = 0; i < n; i++) if (likeness[i] >= minCosine) byMeaning.push(i);
    const bonus = (i: number) => (lang && this.passages[i].lang === lang ? SAME_LANGUAGE_BONUS : 0);
    const meaningOrder = byMeaning.sort((x, y) => likeness[y] + bonus(y) - (likeness[x] + bonus(x))).slice(0, POOL);
    // Shared words help only where the meaning is at least roughly near
    const keyword = this.keywordScores(question, lang);
    const bestKeyword = keyword.size ? Math.max(...keyword.values()) : 0;
    const keywordOrder = [...keyword].filter(([i, sc]) => sc >= bestKeyword * MIN_SHARE_OF_BEST && likeness[i] >= minCosine * KEYWORD_COSINE_SHARE)
      .sort((x, y) => y[1] - x[1]).slice(0, POOL).map(([i]) => i);
    const fused = new Map<number, number>();
    meaningOrder.forEach((i, rank) => fused.set(i, (fused.get(i) ?? 0) + MEANING_WEIGHT / (RRF_K + rank)));
    keywordOrder.forEach((i, rank) => fused.set(i, (fused.get(i) ?? 0) + 1 / (RRF_K + rank)));
    return this.pick([...fused].sort((x, y) => y[1] - x[1]).map(([i]) => i), k, lang);
  }
}

/** Unit-length embeddings, one row per passage. */
export interface Vectors {
  dim: number;
  data: Float32Array;
}

const POOL = 30;
const RRF_K = 60;
const SAME_LANGUAGE_BONUS = 0.02;
const MEANING_WEIGHT = 2;
const KEYWORD_COSINE_SHARE = 0.85;
/**
 * Below this likeness a passage is not about the question. Measured 2026-11-13 with
 * nvidia/nemotron-3.5-embed-8b at 1024 numbers: questions the teachings cover scored 0.41 to 0.51
 * for their best passage, unrelated ones (bitcoin, cricket, code, "hi") 0.11 to 0.22.
 */
export const MIN_COSINE = 0.3;

/** Scales a vector to length 1, so a dot product is the cosine. */
export function unit(v: ArrayLike<number>): Float32Array {
  let sum = 0;
  for (let i = 0; i < v.length; i++) sum += v[i] * v[i];
  const norm = Math.sqrt(sum) || 1;
  const out = new Float32Array(v.length);
  for (let i = 0; i < v.length; i++) out[i] = v[i] / norm;
  return out;
}
