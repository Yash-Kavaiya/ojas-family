package com.ojashealing.family.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A teaching passage an answer was based on. [startSec] opens the video at the right moment. */
data class AiSource(val n: Int, val title: String, val url: String, val videoId: String?, val startSec: Int)

data class AiAnswer(
    val chatId: String,
    val messageId: String,
    val text: String,
    val sources: List<AiSource>,
    val followUps: List<String>,
    val remaining: Int,
    val pointsAwarded: Int,
)

/** While an answer is written: what the phone is doing, pieces of text as they come, then the answer. */
sealed interface AiStreamEvent {
    data class Stage(val stage: AiStage) : AiStreamEvent
    data class Text(val piece: String) : AiStreamEvent
    /** The text written so far is replaced (a cleaned-up or fallback answer). */
    data class Replace(val text: String) : AiStreamEvent
    data class Done(val answer: AiAnswer) : AiStreamEvent
}

enum class AiStage { Searching, Loading, Writing }

data class AiChat(val id: String, val title: String, val lastText: String, val updatedAt: Long)

data class AiStoredMessage(
    val id: String,
    val fromMe: Boolean,
    val text: String,
    val sources: List<AiSource>,
    val followUps: List<String>,
    val rating: Int,
    val photoPath: String? = null,
)

data class AiQuota(val remaining: Int, val limit: Int, val enabled: Boolean = true)

/** A photo the member attached: a private copy on the phone and what ML Kit read from it. */
data class AttachedPhoto(val path: String, val note: PhotoNote)

/** Words the answer may need in the member's language, passed in from the screen's resources. */
data class AiTexts(
    /** Shown when no teaching matches; the model isn't asked, so no question is used. */
    val noMatch: String,
    /** Shown first when the words suggest someone may harm themselves. */
    val crisis: String,
    /** "Tell me more about %s", for follow-up chips. */
    val followUpTemplate: String,
    /** The question text when only a photo is sent. */
    val photoQuestion: String,
)

interface AiRepository {
    val model: StateFlow<ModelState>
    suspend fun downloadModel()
    suspend fun removeModel()
    fun ask(question: String, chatId: String?, lang: String, photo: AttachedPhoto?, texts: AiTexts): Flow<AiStreamEvent>
    suspend fun quota(): Result<AiQuota>
    fun observeChats(uid: String): Flow<List<AiChat>>
    fun observeMessages(uid: String, chatId: String): Flow<List<AiStoredMessage>>
    suspend fun rate(chatId: String, messageId: String, rating: Int): Result<Unit>
    suspend fun deleteChat(chatId: String): Result<Unit>
    suspend fun readPhoto(uri: Uri): Result<AttachedPhoto>
    suspend fun readPhoto(bitmap: Bitmap): Result<AttachedPhoto>
}

/**
 * Ask Swamiji AI on the phone: the question is matched against Ojas Healing's teachings on the
 * phone ([KnowledgeSearch]), and Gemma 3 270M writes the answer from the best passages
 * ([GemmaEngine]). Chats stay on the phone ([LocalChatStore]). The server is asked only to count
 * the question against the daily allowance, and receives an answer only when the member rates it.
 */
class OnDeviceAiRepository(
    context: Context,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val storage: FirebaseStorage,
) : AiRepository {
    private val app = context.applicationContext
    private val engine = GemmaEngine(app, storage)
    private val photos = PhotoReader(app)
    private val chats = LocalChatStore(app)
    private val json = Json { ignoreUnknownKeys = true }
    private val knowledgeFile = File(app.filesDir, "ai_knowledge.json")
    private val photoDir = File(app.filesDir, "ai_photos").apply { mkdirs() }
    private val knowledgeLock = Mutex()
    private var search: KnowledgeSearch? = null
    private var knowledgePath = "ai/knowledge.json"
    private var topK = 3

    override val model: StateFlow<ModelState> = engine.state

    override suspend fun downloadModel() = engine.download()

    override suspend fun removeModel() = engine.remove()

    override suspend fun quota(): Result<AiQuota> = runCatching {
        val m = functions.getHttpsCallable("aiQuota").call().await().getData() as? Map<*, *>
        (m?.get("knowledgePath") as? String)?.takeIf { it.isNotBlank() }?.let { knowledgePath = it }
        (m?.get("topK") as? Number)?.toInt()?.let { topK = it.coerceIn(1, 6) }
        engine.configure((m?.get("modelPath") as? String).orEmpty(), (m?.get("modelBytes") as? Number)?.toLong() ?: 0L)
        AiQuota(
            remaining = (m?.get("remaining") as? Number)?.toInt() ?: 0,
            limit = (m?.get("limit") as? Number)?.toInt() ?: 0,
            enabled = m?.get("enabled") != false,
        )
    }.onFailure { engine.configure("", 0) }

    /** The teachings, from the phone's copy; refreshed from Storage a few times a day. */
    private suspend fun knowledge(): KnowledgeSearch = knowledgeLock.withLock {
        val fresh = knowledgeFile.exists() && System.currentTimeMillis() - knowledgeFile.lastModified() < KNOWLEDGE_MAX_AGE_MS
        search?.takeIf { fresh }?.let { return it }
        withContext(Dispatchers.IO) {
            if (!fresh) {
                runCatching {
                    val bytes = storage.reference.child(knowledgePath).getBytes(KNOWLEDGE_MAX_BYTES).await()
                    json.decodeFromString<KnowledgeFile>(bytes.decodeToString()) // only keep a file that reads
                    knowledgeFile.writeBytes(bytes)
                }
            }
            val passages = runCatching { json.decodeFromString<KnowledgeFile>(knowledgeFile.readText()).passages }.getOrDefault(emptyList())
            if (passages.isEmpty()) throw IllegalStateException("The teachings could not be loaded. Check the internet and try again.")
            KnowledgeSearch(passages)
        }.also { search = it }
    }

    override fun ask(question: String, chatId: String?, lang: String, photo: AttachedPhoto?, texts: AiTexts): Flow<AiStreamEvent> = flow {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Please sign in again")
        val q = question.trim().ifBlank { if (photo != null) texts.photoQuestion else "" }
        val id = chatId ?: UUID.randomUUID().toString()
        val history = chats.chat(uid, id)?.messages.orEmpty().map { ChatTurn(it.fromMe, it.text) }
        val asked = StoredMessage("q-" + UUID.randomUUID(), fromMe = true, text = q, photo = photo?.path, at = System.currentTimeMillis())

        suspend fun finish(text: String, sources: List<AiSource>, followUps: List<String>, remaining: Int, points: Int) {
            val answerId = "a-" + UUID.randomUUID()
            chats.append(uid, id, q, listOf(asked, StoredMessage(
                answerId, fromMe = false, text = text,
                sources = sources.map { StoredSource(it.n, it.title, it.url, it.videoId, it.startSec) },
                followUps = followUps, at = System.currentTimeMillis(),
            )))
            emit(AiStreamEvent.Done(AiAnswer(id, answerId, text, sources, followUps, remaining, points)))
        }

        // Someone who may be in danger gets help straight away, before any teaching
        if (GemmaPrompt.soundsLikeCrisis(q)) {
            finish(texts.crisis, emptyList(), emptyList(), -1, 0)
            return@flow
        }

        emit(AiStreamEvent.Stage(AiStage.Searching))
        val query = listOfNotNull(q, photo?.note?.searchText(), history.lastOrNull { it.fromMe }?.text).joinToString(" ")
        // Answer in the language the question is written in (Hindi typed in an English app gets Hindi)
        val answerLang = questionLanguage(q, lang)
        var passages = knowledge().search(query, topK, answerLang)
        if (passages.isEmpty()) {
            // Nothing in the teachings: say so honestly instead of letting a small model guess
            finish(texts.noMatch, emptyList(), emptyList(), -1, 0)
            return@flow
        }

        if (engine.state.value !is ModelState.Ready) throw IllegalStateException("Download Ask Swamiji AI first")
        // Counted only now, when the model will really answer
        val started = functions.getHttpsCallable("startAiQuestion").call().await().getData() as? Map<*, *>
        val remaining = (started?.get("remaining") as? Number)?.toInt() ?: 0
        val points = (started?.get("pointsAwarded") as? Number)?.toInt() ?: 0

        emit(AiStreamEvent.Stage(AiStage.Loading))
        var prompt = GemmaPrompt.build(q, passages, answerLang, history, photo?.note)
        // Keep room for the answer: drop the weakest passages, then shorten what is left
        while (engine.tokens(prompt) > GemmaEngine.MAX_PROMPT_TOKENS && passages.size > 1) {
            passages = passages.dropLast(1)
            prompt = GemmaPrompt.build(q, passages, answerLang, history, photo?.note)
        }
        if (engine.tokens(prompt) > GemmaEngine.MAX_PROMPT_TOKENS) {
            passages = passages.map { it.copy(text = it.text.take(500)) }
            prompt = GemmaPrompt.build(q, passages, answerLang, emptyList(), photo?.note)
        }

        emit(AiStreamEvent.Stage(AiStage.Writing))
        var shown = ""
        engine.generate(prompt).collect { full ->
            val visible = GemmaPrompt.clean(full)
            if (visible.length > shown.length && visible.startsWith(shown)) emit(AiStreamEvent.Text(visible.substring(shown.length)))
            shown = visible
        }
        // When the small model only talks about the task, Swamiji's own words from the best passage answer instead
        val answer = GemmaPrompt.clean(shown).let { if (GemmaPrompt.isUseless(it, q, passages)) GemmaPrompt.extract(passages) else it }
        if (answer.isBlank()) throw IllegalStateException("Swamiji AI could not answer just now. Please try again.")
        if (answer != shown) emit(AiStreamEvent.Replace(answer))
        val sources = passages.mapIndexed { i, p -> AiSource(i + 1, p.title, p.url, p.videoId, p.startSec) }
            .distinctBy { it.videoId ?: it.url.ifBlank { it.title } }
            .mapIndexed { i, s -> s.copy(n = i + 1) }
        finish(answer, sources, GemmaPrompt.followUps(q, passages) { texts.followUpTemplate.format(it) }, remaining, points)
    }

    override fun observeChats(uid: String): Flow<List<AiChat>> = chats.observe(uid).map { list ->
        list.map { c -> AiChat(c.id, c.title, c.messages.lastOrNull()?.text.orEmpty().take(140), c.updatedAt) }
    }

    override fun observeMessages(uid: String, chatId: String): Flow<List<AiStoredMessage>> = flow {
        emit(chats.chat(uid, chatId)?.messages.orEmpty().map { m ->
            AiStoredMessage(
                m.id, m.fromMe, m.text, m.sources.map { AiSource(it.n, it.title, it.url, it.videoId, it.startSec) },
                m.followUps, m.rating, m.photo?.takeIf { File(it).exists() },
            )
        })
    }

    override suspend fun rate(chatId: String, messageId: String, rating: Int): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        chats.setRating(uid, chatId, messageId, rating)
        val messages = chats.chat(uid, chatId)?.messages.orEmpty()
        val i = messages.indexOfFirst { it.id == messageId }
        val data = buildMap<String, Any> {
            put("messageId", messageId)
            put("rating", rating)
            if (rating != 0 && i >= 0) {
                // Only an answer the member rates leaves the phone, so admins can improve the teachings
                put("answer", messages[i].text)
                messages.getOrNull(i - 1)?.takeIf { it.fromMe }?.let {
                    put("question", it.text)
                    put("hadImage", it.photo != null)
                }
            }
        }
        functions.getHttpsCallable("rateAiAnswer").call(data).await()
        Unit
    }

    override suspend fun deleteChat(chatId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        chats.chat(uid, chatId)?.messages?.mapNotNull { it.photo }?.forEach { File(it).delete() }
        chats.delete(uid, chatId)
    }

    override suspend fun readPhoto(uri: Uri): Result<AttachedPhoto> = runCatching {
        val copy = File(photoDir, "${UUID.randomUUID()}.jpg")
        withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
                ?: throw IllegalStateException("The photo could not be opened")
        }
        AttachedPhoto(copy.absolutePath, photos.read(Uri.fromFile(copy)))
    }

    override suspend fun readPhoto(bitmap: Bitmap): Result<AttachedPhoto> = runCatching {
        val copy = File(photoDir, "${UUID.randomUUID()}.jpg")
        withContext(Dispatchers.IO) { copy.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        AttachedPhoto(copy.absolutePath, photos.read(bitmap))
    }

    private companion object {
        const val KNOWLEDGE_MAX_AGE_MS = 6L * 60 * 60 * 1000
        const val KNOWLEDGE_MAX_BYTES = 20L * 1024 * 1024
    }
}
