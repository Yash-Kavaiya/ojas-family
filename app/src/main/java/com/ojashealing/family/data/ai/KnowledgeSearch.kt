package com.ojashealing.family.data.ai

import kotlinx.serialization.Serializable
import kotlin.math.ln

/** One passage of Ojas Healing's teachings, from ai/knowledge.json (see scripts/buildKnowledge.ts). */
@Serializable
data class KnowledgePassage(
    val id: String,
    val title: String,
    val text: String,
    val url: String = "",
    val videoId: String? = null,
    val startSec: Int = 0,
    val sourceType: String = "",
    val lang: String = "en",
)

@Serializable
data class KnowledgeFile(val version: Long = 0, val passages: List<KnowledgePassage> = emptyList())

/** Words too common to tell passages apart, in English, Hindi and Gujarati. */
private val STOP_WORDS = setOf(
    "a", "an", "the", "is", "are", "am", "was", "were", "be", "to", "of", "in", "on", "for", "and", "or", "it", "this", "that",
    "i", "me", "my", "we", "you", "your", "he", "she", "they", "what", "how", "why", "when", "which", "who", "do", "does", "did",
    "can", "should", "will", "with", "about", "from", "at", "by", "as", "so", "if", "please", "tell", "explain", "swamiji", "ji",
    "है", "हैं", "का", "की", "के", "को", "में", "से", "और", "या", "यह", "वह", "क्या", "कैसे", "क्यों", "कब", "मैं", "मुझे", "मेरा", "हम", "आप",
    "पर", "भी", "तो", "ही", "था", "थी", "हो", "कर", "करें", "करना", "बताइए", "बताओ", "स्वामीजी",
    "છે", "છો", "નો", "ની", "નું", "ને", "માં", "થી", "અને", "કે", "આ", "તે", "શું", "કેવી", "રીતે", "કેમ", "ક્યારે", "હું", "મને", "મારું", "તમે", "પણ", "સ્વામીજી",
)

/** Splits text into search words: letters and digits of any script, lower case, common words dropped. */
fun searchWords(text: String): List<String> =
    Regex("""[\p{L}\p{M}\p{N}]+""").findAll(text.lowercase()).map { it.value }
        .filter { it.length > 1 && it !in STOP_WORDS }
        .map(::stem)
        .toList()

/** A light English stem so "meditations", "meditating" and "meditation" meet. */
private fun stem(w: String): String {
    if (w.any { it.code > 0x7F }) return w
    for (suffix in listOf("ations", "ation", "ings", "ing", "ies", "es", "s", "ed")) {
        if (w.length > suffix.length + 3 && w.endsWith(suffix)) return w.dropLast(suffix.length)
    }
    return w
}

/**
 * Ranks passages for a question with BM25, the classic keyword search, right on the phone. Words
 * in a passage's title count extra, since titles name the practice ("Blue Light Meditation").
 */
class KnowledgeSearch(private val passages: List<KnowledgePassage>) {
    private val docs = passages.map { p -> searchWords(p.title).let { t -> t + t + searchWords(p.text) } }
    private val avgLength = docs.map { it.size }.average().takeIf { it > 0 } ?: 1.0
    private val docFreq: Map<String, Int> = HashMap<String, Int>().also { df ->
        docs.forEach { d -> d.toSet().forEach { w -> df[w] = (df[w] ?: 0) + 1 } }
    }
    private val termFreq = docs.map { d -> d.groupingBy { it }.eachCount() }

    val size: Int get() = passages.size

    /** The best [k] passages for [question], best first; none when no word of the question matches. */
    fun search(question: String, k: Int, lang: String? = null): List<KnowledgePassage> {
        val words = searchWords(question).distinct()
        if (words.isEmpty() || passages.isEmpty()) return emptyList()
        val n = passages.size
        val scored = passages.indices.mapNotNull { i ->
            val tf = termFreq[i]
            var score = 0.0
            for (w in words) {
                val f = tf[w] ?: continue
                val df = docFreq[w] ?: continue
                val idf = ln(1 + (n - df + 0.5) / (df + 0.5))
                score += idf * (f * (K1 + 1)) / (f + K1 * (1 - B + B * docs[i].size / avgLength))
            }
            // A passage in the member's own language reads better when two are close
            if (score > 0 && lang != null && passages[i].lang == lang) score *= 1.15
            if (score > 0) i to score else null
        }
        val best = scored.maxOfOrNull { it.second } ?: return emptyList()
        return scored.filter { it.second >= best * MIN_SHARE_OF_BEST }
            .sortedByDescending { it.second }
            .map { passages[it.first] }
            .distinctBy { it.text.take(80) }
            .take(k)
    }

    private companion object {
        const val K1 = 1.4
        const val B = 0.75
        // Passages far weaker than the best one are noise for a small model
        const val MIN_SHARE_OF_BEST = 0.35
    }
}
