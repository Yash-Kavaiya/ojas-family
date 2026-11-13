package com.ojashealing.family.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StoredSource(val n: Int, val title: String, val url: String = "", val videoId: String? = null, val startSec: Int = 0)

@Serializable
data class StoredMessage(
    val id: String,
    val fromMe: Boolean,
    val text: String,
    val sources: List<StoredSource> = emptyList(),
    val followUps: List<String> = emptyList(),
    val rating: Int = 0,
    val photo: String? = null,
    val at: Long = 0,
)

@Serializable
data class StoredChat(val id: String, val title: String, val updatedAt: Long, val messages: List<StoredMessage> = emptyList())

@Serializable
private data class ChatFile(val chats: List<StoredChat> = emptyList())

/**
 * Ask Swamiji AI conversations, kept only on this phone (one small JSON file per signed-in member).
 * Nothing here is sent anywhere.
 */
class LocalChatStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "ai_chats").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Mutex()
    private val cache = MutableStateFlow<Map<String, List<StoredChat>>>(emptyMap())

    private fun file(uid: String) = File(dir, "${uid.filter { it.isLetterOrDigit() }}.json")

    private suspend fun load(uid: String): List<StoredChat> {
        cache.value[uid]?.let { return it }
        val chats = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString<ChatFile>(file(uid).readText()).chats }.getOrDefault(emptyList())
        }
        cache.value = cache.value + (uid to chats)
        return chats
    }

    private suspend fun save(uid: String, chats: List<StoredChat>) {
        cache.value = cache.value + (uid to chats)
        withContext(Dispatchers.IO) {
            val tmp = File(dir, "${file(uid).name}.tmp")
            tmp.writeText(json.encodeToString(ChatFile.serializer(), ChatFile(chats)))
            tmp.renameTo(file(uid))
        }
    }

    fun observe(uid: String): Flow<List<StoredChat>> = cache.map { it[uid].orEmpty().sortedByDescending { c -> c.updatedAt } }

    suspend fun chats(uid: String): List<StoredChat> = lock.withLock { load(uid) }

    suspend fun chat(uid: String, chatId: String): StoredChat? = chats(uid).firstOrNull { it.id == chatId }

    /** Adds messages to a chat, starting it when [chatId] is new. Keeps the newest 50 chats. */
    suspend fun append(uid: String, chatId: String, title: String, messages: List<StoredMessage>) = lock.withLock {
        val all = load(uid)
        val now = System.currentTimeMillis()
        val existing = all.firstOrNull { it.id == chatId }
        val updated = (existing ?: StoredChat(chatId, title.take(80), now)).let { c ->
            c.copy(updatedAt = now, messages = (c.messages + messages).takeLast(MAX_MESSAGES))
        }
        save(uid, (listOf(updated) + all.filterNot { it.id == chatId }).take(MAX_CHATS))
    }

    suspend fun setRating(uid: String, chatId: String, messageId: String, rating: Int) = lock.withLock {
        val all = load(uid)
        save(uid, all.map { c ->
            if (c.id != chatId) c else c.copy(messages = c.messages.map { if (it.id == messageId) it.copy(rating = rating) else it })
        })
    }

    suspend fun delete(uid: String, chatId: String) = lock.withLock {
        save(uid, load(uid).filterNot { it.id == chatId })
    }

    private companion object {
        const val MAX_CHATS = 50
        const val MAX_MESSAGES = 100
    }
}
