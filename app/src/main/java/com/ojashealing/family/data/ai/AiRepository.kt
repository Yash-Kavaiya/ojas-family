package com.ojashealing.family.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.StreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** A teaching passage an answer was based on. [startSec] opens the video at the right moment. */
data class AiSource(val n: Int, val title: String, val url: String, val videoId: String?, val startSec: Int)

data class AiAnswer(
    val chatId: String,
    val messageId: String,
    val text: String,
    val sources: List<AiSource>,
    val followUps: List<String>,
    /** -1 when the answer used none of today's questions. */
    val remaining: Int,
    val pointsAwarded: Int,
)

/** While an answer is written: pieces of text as they come, then the finished answer. */
sealed interface AiStreamEvent {
    data class Text(val piece: String) : AiStreamEvent
    data class Done(val answer: AiAnswer) : AiStreamEvent
}

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

/** What ML Kit read from a photo the member attached: its words and what it seems to show. */
data class PhotoNote(val text: String, val labels: List<String>) {
    val isEmpty: Boolean get() = text.isBlank() && labels.isEmpty()
}

/** A photo the member attached: a private copy on the phone and what ML Kit read from it. */
data class AttachedPhoto(val path: String, val note: PhotoNote)

/** Fixed replies in the member's language, passed in from the screen's resources. */
data class AiTexts(
    /** Shown when no teaching matches; no question is used. */
    val noMatch: String,
    /** Shown first when the words suggest someone may harm themselves. */
    val crisis: String,
    /** The question text when only a photo is sent. */
    val photoQuestion: String,
)

interface AiRepository {
    fun ask(question: String, chatId: String?, lang: String, photo: AttachedPhoto?, texts: AiTexts): Flow<AiStreamEvent>
    suspend fun quota(): Result<AiQuota>
    fun observeChats(uid: String): Flow<List<AiChat>>
    fun observeMessages(uid: String, chatId: String): Flow<List<AiStoredMessage>>
    suspend fun rate(chatId: String, messageId: String, rating: Int): Result<Unit>
    suspend fun deleteChat(chatId: String): Result<Unit>
    suspend fun readPhoto(uri: Uri): Result<AttachedPhoto>
    suspend fun readPhoto(bitmap: Bitmap): Result<AttachedPhoto>
}

internal fun parseSources(raw: Any?): List<AiSource> = (raw as? List<*>).orEmpty().mapNotNull { s ->
    val m = s as? Map<*, *> ?: return@mapNotNull null
    AiSource(
        n = (m["n"] as? Number)?.toInt() ?: 0,
        title = m["title"] as? String ?: "",
        url = m["url"] as? String ?: "",
        videoId = m["videoId"] as? String,
        startSec = (m["startSec"] as? Number)?.toInt() ?: 0,
    )
}

/**
 * Ask Swamiji AI: the server finds the matching teachings and a hosted model writes the answer
 * (the askSwamiji function; the API key never reaches the app). Photos are read on the phone with
 * ML Kit and only their words and labels are sent. Chats are kept on the phone ([LocalChatStore]).
 */
class RemoteAiRepository(
    context: Context,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
) : AiRepository {
    private val app = context.applicationContext
    private val photos = PhotoReader(app)
    private val chats = LocalChatStore(app)
    private val photoDir = File(app.filesDir, "ai_photos").apply { mkdirs() }

    override suspend fun quota(): Result<AiQuota> = runCatching {
        val m = functions.getHttpsCallable("aiQuota").call().await().getData() as? Map<*, *>
        AiQuota(
            remaining = (m?.get("remaining") as? Number)?.toInt() ?: 0,
            limit = (m?.get("limit") as? Number)?.toInt() ?: 0,
            enabled = m?.get("enabled") != false,
        )
    }

    /** The server's stream: {text} pieces as the answer is written, then the full result. */
    private fun call(data: Map<String, Any>): Flow<StreamResponse> = callbackFlow {
        var subscription: Subscription? = null
        functions.getHttpsCallable("askSwamiji").withTimeout(120, TimeUnit.SECONDS).stream(data).subscribe(object : Subscriber<StreamResponse> {
            override fun onSubscribe(s: Subscription) {
                subscription = s
                s.request(Long.MAX_VALUE)
            }

            override fun onNext(r: StreamResponse) {
                trySend(r)
            }

            override fun onError(t: Throwable) {
                close(t)
            }

            override fun onComplete() {
                close()
            }
        })
        awaitClose { subscription?.cancel() }
    }

    override fun ask(question: String, chatId: String?, lang: String, photo: AttachedPhoto?, texts: AiTexts): Flow<AiStreamEvent> = flow {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Please sign in again")
        val q = question.trim()
        val shownQuestion = q.ifBlank { texts.photoQuestion }
        val id = chatId ?: UUID.randomUUID().toString()
        val history = chats.chat(uid, id)?.messages.orEmpty().takeLast(4)
        val data = buildMap<String, Any> {
            put("question", q)
            put("lang", lang)
            if (history.isNotEmpty()) put("history", history.map { mapOf("fromMe" to it.fromMe, "text" to it.text.take(600)) })
            photo?.note?.let {
                put("photoText", it.text)
                put("photoLabels", it.labels)
            }
        }
        var result: Map<*, *>? = null
        call(data).collect { r ->
            when (r) {
                is StreamResponse.Message -> ((r.message.getData() as? Map<*, *>)?.get("text") as? String)?.let { emit(AiStreamEvent.Text(it)) }
                is StreamResponse.Result -> result = r.result.getData() as? Map<*, *>
            }
        }
        val m = result ?: throw IllegalStateException("Swamiji AI could not answer just now. Please try again.")
        val text = when (m["kind"]) {
            "crisis" -> texts.crisis
            "noMatch" -> texts.noMatch
            else -> (m["answer"] as? String).orEmpty()
        }
        if (text.isBlank()) throw IllegalStateException("Swamiji AI could not answer just now. Please try again.")
        val sources = parseSources(m["sources"])
        val followUps = (m["followUps"] as? List<*>).orEmpty().mapNotNull { it as? String }
        val answerId = "a-" + UUID.randomUUID()
        val now = System.currentTimeMillis()
        chats.append(uid, id, shownQuestion, listOf(
            StoredMessage("q-" + UUID.randomUUID(), fromMe = true, text = shownQuestion, photo = photo?.path, at = now),
            StoredMessage(
                answerId, fromMe = false, text = text,
                sources = sources.map { StoredSource(it.n, it.title, it.url, it.videoId, it.startSec) }, followUps = followUps, at = now + 1,
            ),
        ))
        emit(AiStreamEvent.Done(AiAnswer(
            id, answerId, text, sources, followUps,
            remaining = (m["remaining"] as? Number)?.toInt() ?: -1,
            pointsAwarded = (m["pointsAwarded"] as? Number)?.toInt() ?: 0,
        )))
    }

    override fun observeChats(uid: String): Flow<List<AiChat>> = flow {
        chats.chats(uid) // loads this member's chats from the phone
        emitAll(chats.observe(uid).map { list ->
            list.map { c -> AiChat(c.id, c.title, c.messages.lastOrNull()?.text.orEmpty().take(140), c.updatedAt) }
        })
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
                // Chats live on the phone, so a rated answer is sent along for admins to review
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
}
