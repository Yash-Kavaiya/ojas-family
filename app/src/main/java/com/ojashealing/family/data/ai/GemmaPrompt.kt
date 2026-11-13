package com.ojashealing.family.data.ai

/** What ML Kit read from a photo the member attached: its words and what it seems to show. */
data class PhotoNote(val text: String, val labels: List<String>) {
    val isEmpty: Boolean get() = text.isBlank() && labels.isEmpty()

    /** The same, as words for searching the teachings. */
    fun searchText(): String = (labels + text).joinToString(" ")
}

/** One earlier turn of the conversation, so a follow-up question has its context. */
data class ChatTurn(val fromMe: Boolean, val text: String)

/**
 * Builds the prompt for Gemma 3 270M in its own chat format. A model this small follows one short,
 * plain request best, so the teachings come first and the request is a single line after them.
 */
object GemmaPrompt {
    private const val USER = "<start_of_turn>user\n"
    private const val MODEL = "<start_of_turn>model\n"
    private const val END = "<end_of_turn>\n"

    private val LANGUAGE = mapOf("en" to "English", "hi" to "Hindi", "gu" to "Gujarati")

    /**
     * Just the question after the teachings. Any rule about how to answer ("in 2 to 5 sentences")
     * makes a 270M model repeat the rule back instead of answering, so only the language is said,
     * and only when it isn't English.
     */
    fun request(question: String, lang: String): String = buildString {
        append("Question: ").append(question.trim())
        if (lang != "en") append("\nAnswer in ").append(LANGUAGE[lang]).append('.')
    }

    fun build(
        question: String,
        passages: List<KnowledgePassage>,
        lang: String,
        history: List<ChatTurn> = emptyList(),
        photo: PhotoNote? = null,
    ): String = buildString {
        // Only the last exchange: older turns crowd out the teachings in a small context
        history.takeLast(2).forEach { t ->
            append(if (t.fromMe) USER else MODEL)
            append(t.text.take(400).trim())
            append('\n').append(END)
        }
        append(USER)
        append("Read these teachings of Swamiji, then answer the question simply.\n\n")
        passages.forEachIndexed { i, p -> append("[${i + 1}] ").append(p.text.trim()).append("\n\n") }
        if (photo != null && !photo.isEmpty) {
            append("The member shared a photo.")
            if (photo.labels.isNotEmpty()) append(" It shows: ").append(photo.labels.joinToString(", ")).append('.')
            if (photo.text.isNotBlank()) append(" Words in it: \"").append(photo.text.take(500).trim()).append('"')
            append("\n\n")
        }
        append(request(question, lang)).append('\n')
        append(END)
        append(MODEL)
    }

    /** Removes the turn markers a small model sometimes writes, and an opening that only talks about the task. */
    fun clean(raw: String): String = raw
        .replace("<end_of_turn>", "")
        .replace("<start_of_turn>model", "")
        .replace("<start_of_turn>user", "")
        .replace("<eos>", "")
        .trim()
        .let(::stripPreamble)

    private val PREAMBLE = Regex(
        """^(okay|ok|sure|certainly|of course|alright|i understand|understood)\b[^.!?\n]*[.!?:]\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val TASK_TALK = Regex("""^(i will|i'll|here is|here's)\b[^.!?\n]*[.!?:]\s*""", RegexOption.IGNORE_CASE)
    private val ANSWER_LABEL = Regex("""^(the answer is|answer)\s*:\s*""", RegexOption.IGNORE_CASE)

    /** Drops an opening like "Okay, I understand. I will use only the teachings..." or "The answer is:". */
    private fun stripPreamble(text: String): String {
        val m = PREAMBLE.find(text)
        val rest = (if (m == null) text else text.substring(m.range.last + 1).replaceFirst(TASK_TALK, ""))
            .trim().replaceFirst(ANSWER_LABEL, "").trim()
        return rest.ifBlank { text }
    }

    /**
     * True when the model's answer adds nothing from the teachings: it is only talk about the task,
     * or it mostly repeats the question ("To do the kundalini meditation."). The app then shows the
     * best matching teaching itself, which is always a real answer in Swamiji's words.
     */
    fun isUseless(answer: String, question: String = "", passages: List<KnowledgePassage> = emptyList()): Boolean {
        val t = answer.trim()
        if (t.length < 40 || PREAMBLE.containsMatchIn(t) || TASK_TALK.containsMatchIn(t)) return true
        if (passages.isEmpty()) return false
        // Words the answer brings from the teachings, beyond the question's own words
        val asked = searchWords(question).toSet()
        val taught = passages.flatMap { searchWords(it.text) }.toSet()
        val added = searchWords(t).filter { it !in asked && it in taught }.toSet()
        return added.size < MIN_TAUGHT_WORDS
    }

    /** The best passages as an answer, in Swamiji's words, without their title lines. */
    fun extract(passages: List<KnowledgePassage>): String =
        passages.take(2).joinToString("\n\n") { it.text.substringAfter('\n', it.text).trim() }.take(900)

    private const val MIN_TAUGHT_WORDS = 4

    private val CRISIS = listOf(
        "suicide", "kill myself", "end my life", "want to die", "self harm", "hurt myself",
        "आत्महत्या", "मरना चाहता", "मरना चाहती", "ખુદને મારી", "આત્મહત્યા", "મરી જવું",
    )

    /** True when the words suggest someone may harm themselves: the app then shows help first. */
    fun soundsLikeCrisis(text: String): Boolean = text.lowercase().let { t -> CRISIS.any { it in t } }

    /** Up to two follow-up questions, from the other passages that matched. */
    fun followUps(question: String, passages: List<KnowledgePassage>, template: (String) -> String): List<String> =
        passages.map { it.title.substringBefore(" · ").substringBefore(" | ").trim() }
            .filter { it.isNotBlank() && !question.contains(it, ignoreCase = true) }
            .distinct()
            .drop(1)
            .take(2)
            .map(template)
}
