package com.ojashealing.family.data.read

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** A question and its answer in Practice > Read, written by an admin or Swamiji. */
data class ReadQa(
    val id: String,
    /** The Hindi original. */
    val question: String,
    val answer: String,
    val createdAt: Timestamp?,
    val questionEn: String = "",
    val answerEn: String = "",
    val questionGu: String = "",
    val answerGu: String = "",
)

const val ReadQuestionMax = 500
const val ReadAnswerMax = 10_000

/** The question in the app [language]: another phone language gets English; a missing translation shows the Hindi. */
fun ReadQa.questionIn(language: String): String = pick(language, question, questionEn, questionGu)

fun ReadQa.answerIn(language: String): String = pick(language, answer, answerEn, answerGu)

private fun pick(language: String, hindi: String, english: String, gujarati: String): String = when (language) {
    "hi" -> hindi
    "gu" -> gujarati
    else -> english
}.ifBlank { hindi }

/** English and Gujarati as corrected by an admin or Swamiji. */
data class ReadTranslations(val questionEn: String, val answerEn: String, val questionGu: String, val answerGu: String)

/**
 * What to save when a question is changed; a null value removes the field. Changed Hindi clears the
 * translations, so members see the new Hindi until the server's new translation arrives.
 */
fun readUpdateFields(old: ReadQa, question: String, answer: String, translations: ReadTranslations?): Map<String, Any?> {
    val q = question.trim()
    val a = answer.trim()
    val text = mapOf<String, Any?>("question" to q, "answer" to a)
    return when {
        q != old.question || a != old.answer ->
            text + listOf("question_en", "answer_en", "question_gu", "answer_gu").associateWith { null }
        translations == null -> text
        // Only what the editor really changed, so a save never overwrites a newer translation
        else -> text + listOf(
            Triple("question_en", translations.questionEn, old.questionEn),
            Triple("answer_en", translations.answerEn, old.answerEn),
            Triple("question_gu", translations.questionGu, old.questionGu),
            Triple("answer_gu", translations.answerGu, old.answerGu),
        ).filter { (_, new, before) -> new.trim() != before.trim() }
            .associate { (field, new, _) -> field to new.trim() }
    }
}

/** True when someone else changed the Hindi after this editor opened it. */
fun readEditConflict(opened: ReadQa, live: ReadQa?): Boolean =
    live != null && (live.question != opened.question || live.answer != opened.answer)

/** The language a phone voice should read [text] in: Gujarati or Devanagari script, else Indian English. */
fun ttsLanguageFor(text: String): String {
    var hi = 0
    var gu = 0
    var latin = 0
    for (c in text) {
        when (c) {
            in 'ऀ'..'ॿ' -> hi++
            in '઀'..'૿' -> gu++
            in 'A'..'Z', in 'a'..'z' -> latin++
        }
    }
    return when {
        gu > 0 && gu >= hi && gu >= latin -> "gu-IN"
        hi > 0 && hi >= latin -> "hi-IN"
        else -> "en-IN"
    }
}

/** Both parts filled in and within the limits the server rules allow. */
fun validReadQa(question: String, answer: String): Boolean =
    question.isNotBlank() && answer.isNotBlank() && question.trim().length <= ReadQuestionMax && answer.trim().length <= ReadAnswerMax

class ReadQaRepository(private val db: FirebaseFirestore) {
    private val col get() = db.collection("readQa")

    /** Newest first, so what Swamiji just added is at the top. */
    fun observe(): Flow<List<ReadQa>> = callbackFlow {
        val reg = col.orderBy("createdAt", Query.Direction.DESCENDING).limit(300).addSnapshotListener { snap, error ->
            if (snap == null) {
                if (error != null) close(error)
                return@addSnapshotListener
            }
            trySend(snap.documents.mapNotNull { d ->
                val q = d.getString("question")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ReadQa(
                    d.id, q, d.getString("answer").orEmpty(), d.getTimestamp("createdAt"),
                    d.getString("question_en").orEmpty(), d.getString("answer_en").orEmpty(),
                    d.getString("question_gu").orEmpty(), d.getString("answer_gu").orEmpty(),
                )
            })
        }
        awaitClose { reg.remove() }
    }

    suspend fun add(question: String, answer: String, authorUid: String?): Result<Unit> = runCatching {
        col.add(
            mapOf(
                "question" to question.trim(), "answer" to answer.trim(),
                "authorUid" to authorUid.orEmpty(),
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun update(old: ReadQa, question: String, answer: String, translations: ReadTranslations?): Result<Unit> = runCatching {
        val fields = readUpdateFields(old, question, answer, translations).mapValues { it.value ?: FieldValue.delete() }
        col.document(old.id).update(fields + ("updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun delete(id: String): Result<Unit> = runCatching { col.document(id).delete().await() }
}

/** Which questions this member has marked Done, kept on the phone. */
class ReadTicks(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("read_qa_ticks", Context.MODE_PRIVATE)
    private val _read = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    val read: StateFlow<Set<String>> = _read.asStateFlow()

    /** Marks [id] Done, or takes the mark off when [done] is false. */
    fun set(id: String, done: Boolean) {
        if ((id in _read.value) == done) return
        val next = if (done) _read.value + id else _read.value - id
        _read.value = next
        prefs.edit().putStringSet(KEY, next).apply()
    }

    companion object {
        private const val KEY = "read"

        /** The question shown first when Read opens: a random one not yet Done, or any one when all are Done. */
        fun pickFirst(ids: List<String>, done: Set<String>, random: kotlin.random.Random = kotlin.random.Random): String? =
            ids.filter { it !in done }.ifEmpty { ids }.randomOrNull(random)
    }
}
