package com.ojashealing.family.data.ai

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.StreamResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.TimeUnit

/** A video passage an answer was based on. [startSec] opens the video at the right moment. */
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
)

data class AiQuota(val remaining: Int, val limit: Int)

interface AiRepository {
    fun ask(question: String, chatId: String?, lang: String): Flow<AiStreamEvent>
    suspend fun quota(): Result<AiQuota>
    fun observeChats(uid: String): Flow<List<AiChat>>
    fun observeMessages(uid: String, chatId: String): Flow<List<AiStoredMessage>>
    suspend fun rate(chatId: String, messageId: String, rating: Int): Result<Unit>
    suspend fun deleteChat(chatId: String): Result<Unit>
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

internal fun parseAnswer(m: Map<*, *>): AiAnswer = AiAnswer(
    chatId = m["chatId"] as? String ?: "",
    messageId = m["messageId"] as? String ?: "",
    text = m["answer"] as? String ?: "",
    sources = parseSources(m["sources"]),
    followUps = (m["followUps"] as? List<*>).orEmpty().mapNotNull { it as? String },
    remaining = (m["remaining"] as? Number)?.toInt() ?: 0,
    pointsAwarded = (m["pointsAwarded"] as? Number)?.toInt() ?: 0,
)

class FirebaseAiRepository(private val db: FirebaseFirestore, private val functions: FirebaseFunctions) : AiRepository {

    /**
     * Streams the answer as Gemini writes it: the server sends {text} pieces, then the full result.
     * Cancelling the flow (leaving the screen) stops listening.
     */
    override fun ask(question: String, chatId: String?, lang: String): Flow<AiStreamEvent> = callbackFlow {
        val data = buildMap<String, Any> {
            put("question", question)
            put("lang", lang)
            chatId?.let { put("chatId", it) }
        }
        var subscription: Subscription? = null
        functions.getHttpsCallable("askSwamiji").withTimeout(120, TimeUnit.SECONDS).stream(data).subscribe(object : Subscriber<StreamResponse> {
            override fun onSubscribe(s: Subscription) {
                subscription = s
                s.request(Long.MAX_VALUE)
            }

            override fun onNext(r: StreamResponse) {
                when (r) {
                    is StreamResponse.Message -> ((r.message.getData() as? Map<*, *>)?.get("text") as? String)?.let { trySend(AiStreamEvent.Text(it)) }
                    is StreamResponse.Result -> (r.result.getData() as? Map<*, *>)?.let { trySend(AiStreamEvent.Done(parseAnswer(it))) }
                }
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

    override suspend fun quota(): Result<AiQuota> = runCatching {
        val m = functions.getHttpsCallable("aiQuota").call().await().getData() as? Map<*, *>
        AiQuota((m?.get("remaining") as? Number)?.toInt() ?: 0, (m?.get("limit") as? Number)?.toInt() ?: 0)
    }

    override fun observeChats(uid: String): Flow<List<AiChat>> = callbackFlow {
        val reg = db.collection("users/$uid/aiChats").orderBy("updatedAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                trySend(snap.documents.map { d ->
                    AiChat(d.id, d.getString("title").orEmpty(), d.getString("lastText").orEmpty(), d.getTimestamp("updatedAt")?.toDate()?.time ?: 0L)
                })
            }
        awaitClose { reg.remove() }
    }

    override fun observeMessages(uid: String, chatId: String): Flow<List<AiStoredMessage>> = callbackFlow {
        val reg = db.collection("users/$uid/aiChats/$chatId/messages").orderBy("createdAt").limitToLast(100)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                trySend(snap.documents.map { d ->
                    AiStoredMessage(
                        id = d.id, fromMe = d.getString("role") == "user", text = d.getString("text").orEmpty(),
                        sources = parseSources(d.get("sources")),
                        followUps = (d.get("followUps") as? List<*>).orEmpty().mapNotNull { it as? String },
                        rating = d.getLong("rating")?.toInt() ?: 0,
                    )
                })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun rate(chatId: String, messageId: String, rating: Int): Result<Unit> = runCatching {
        functions.getHttpsCallable("rateAiAnswer").call(mapOf("chatId" to chatId, "messageId" to messageId, "rating" to rating)).await()
        Unit
    }

    override suspend fun deleteChat(chatId: String): Result<Unit> = runCatching {
        functions.getHttpsCallable("deleteAiChat").call(mapOf("chatId" to chatId)).await()
        Unit
    }
}
