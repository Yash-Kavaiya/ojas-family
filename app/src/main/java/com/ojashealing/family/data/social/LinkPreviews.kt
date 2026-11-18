package com.ojashealing.family.data.social

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** The card under a message with a web address: the page's title, a line about it, its picture and site. */
data class LinkPreviewData(val url: String, val title: String, val description: String, val image: String, val site: String)

/** The "linkPreview" function's answer, or null when the page has nothing worth showing. */
fun linkPreviewFrom(data: Any?, url: String): LinkPreviewData? {
    val m = data as? Map<*, *> ?: return null
    fun str(key: String) = (m[key] as? String)?.trim().orEmpty()
    val title = str("title")
    if (title.isEmpty()) return null
    val image = str("image").takeIf { it.startsWith("https://") }.orEmpty()
    return LinkPreviewData(url, title, str("description"), image, str("site"))
}

/**
 * Looks up each address once while the app runs, however many cards show it at the same time.
 * A failed lookup (offline) shows no card and is tried again the next time the card appears.
 */
class LinkPreviewLoader(private val call: suspend (url: String) -> Any?) {
    private val lock = Mutex()
    private val known = LinkedHashMap<String, CompletableDeferred<LinkPreviewData?>>()

    suspend fun load(url: String): LinkPreviewData? {
        val (job, mine) = lock.withLock {
            known[url]?.let { return@withLock it to false }
            if (known.size >= 300) known.remove(known.keys.first())
            CompletableDeferred<LinkPreviewData?>().also { known[url] = it } to true
        }
        if (!mine) return job.await()
        val result = runCatching { call(url) }
        if (result.isFailure) lock.withLock { known.remove(url) }
        return linkPreviewFrom(result.getOrNull(), url).also { job.complete(it) }
    }

    companion object {
        fun remote(functions: FirebaseFunctions) = LinkPreviewLoader { url ->
            functions.getHttpsCallable("linkPreview").call(mapOf("url" to url)).await().getData()
        }
    }
}
