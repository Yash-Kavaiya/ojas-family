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
 * Builds the prompt for Gemma 3 270M in its own chat format. A model this small follows short,
 * plain instructions best, so they stay brief, and it sees only a few passages.
 */
object GemmaPrompt {
    private const val USER = "<start_of_turn>user\n"
    private const val MODEL = "<start_of_turn>model\n"
    private const val END = "<end_of_turn>\n"

    private val LANGUAGE = mapOf("en" to "English", "hi" to "Hindi", "gu" to "Gujarati")

    fun instructions(lang: String): String = listOf(
        "You are Ask Swamiji, a kind and calm guide of Ojas Healing.",
        "Answer the question using only the teachings below.",
        "Keep it short: 2 to 5 simple sentences, or a few numbered steps for a practice.",
        "If the teachings do not answer it, say you are not sure and suggest asking Swamiji in the app.",
        "Never diagnose illness or tell anyone to stop medicine.",
        "Answer in ${LANGUAGE[lang] ?: "English"}.",
    ).joinToString("\n")

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
        append(instructions(lang)).append("\n\n")
        append("Teachings:\n")
        passages.forEachIndexed { i, p -> append("[${i + 1}] ").append(p.text.trim()).append("\n\n") }
        if (photo != null && !photo.isEmpty) {
            append("The member shared a photo.")
            if (photo.labels.isNotEmpty()) append(" It shows: ").append(photo.labels.joinToString(", ")).append('.')
            if (photo.text.isNotBlank()) append(" Words in it: \"").append(photo.text.take(500).trim()).append("\"")
            append("\n\n")
        }
        append("Question: ").append(question.trim()).append('\n')
        append(END)
        append(MODEL)
    }

    /** Removes the turn markers a small model sometimes writes into its answer. */
    fun clean(raw: String): String = raw
        .replace("<end_of_turn>", "")
        .replace("<start_of_turn>model", "")
        .replace("<start_of_turn>user", "")
        .replace("<eos>", "")
        .trim()

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
