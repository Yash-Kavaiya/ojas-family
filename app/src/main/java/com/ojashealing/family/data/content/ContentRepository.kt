package com.ojashealing.family.data.content

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.ojashealing.family.domain.Book
import com.ojashealing.family.domain.ContentItem
import com.ojashealing.family.domain.ContentSection
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await

interface ContentRepository {
    /** All content docs ordered by section then position; callers drop unpublished items for non-admins. */
    fun observe(): Flow<List<ContentItem>>
    /** The Ask tab's PDF books, by position; callers drop unpublished ones for non-staff. */
    fun observeBooks(): Flow<List<Book>> = kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun addVideo(url: String, section: ContentSection): Result<Unit>
    /** Returns how many new videos were created. */
    suspend fun importPlaylist(playlistId: String, section: ContentSection): Result<Int>
    suspend fun setPublished(id: String, published: Boolean): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}

class FirestoreContentRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val scope: kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
) : ContentRepository {

    // One live listener each, shared by every screen and kept 5 s after the last one leaves, so
    // switching tabs shows the last list at once instead of starting over
    private val sharedContent by lazy { contentFlow().shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1) }
    private val sharedBooks by lazy { booksFlow().shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1) }

    override fun observe(): Flow<List<ContentItem>> = sharedContent
    override fun observeBooks(): Flow<List<Book>> = sharedBooks

    private fun contentFlow(): Flow<List<ContentItem>> = callbackFlow {
        val registration = db.collection("content").limit(500).addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener
            val items = snap.documents.mapNotNull { it.toContentItem() }
                .sortedWith(compareBy<ContentItem>({ it.section.ordinal }, { it.order }))
            trySend(items)
        }
        awaitClose { registration.remove() }
    }

    private fun booksFlow(): Flow<List<Book>> = callbackFlow {
        val registration = db.collection("books").limit(300).addSnapshotListener { snap, error ->
            // Shown as "no books yet" rather than a spinner that never ends (e.g. before the rules are deployed)
            if (error != null || snap == null) { trySend(emptyList()); return@addSnapshotListener }
            trySend(snap.documents.mapNotNull { it.toBook() }.sortedWith(compareBy<Book>({ it.order }, { it.title })))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun addVideo(url: String, section: ContentSection): Result<Unit> = runCatching {
        functions.getHttpsCallable("addVideo").call(mapOf("url" to url, "section" to section.id)).await()
        Unit
    }

    override suspend fun importPlaylist(playlistId: String, section: ContentSection): Result<Int> = runCatching {
        val data = functions.getHttpsCallable("importPlaylist")
            .call(mapOf("playlistId" to playlistId, "section" to section.id)).await().getData() as? Map<*, *>
        (data?.get("created") as? Number)?.toInt() ?: 0
    }

    override suspend fun setPublished(id: String, published: Boolean): Result<Unit> = runCatching {
        db.collection("content").document(id).update("published", published).await()
        Unit
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        db.collection("content").document(id).delete().await()
        Unit
    }
}

// Only https links are opened; anything else is skipped
private fun DocumentSnapshot.toBook(): Book? {
    val url = getString("url")?.trim()?.takeIf { it.startsWith("https://") } ?: return null
    val title = getString("title")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return Book(
        id = id,
        title = title,
        url = url,
        author = getString("author").orEmpty(),
        minUserLevel = getLong("minUserLevel")?.toInt() ?: 1,
        minMemberLevel = getLong("minMemberLevel")?.toInt() ?: 0,
        order = getLong("order") ?: 0,
        published = getBoolean("published") ?: true,
        coverUrl = getString("coverUrl")?.trim()?.takeIf { it.startsWith("https://") }.orEmpty(),
        viewCount = getLong("viewCount") ?: 0,
    )
}

// Docs with an unknown section (from a newer app version) are skipped rather than crashing older apps
private fun DocumentSnapshot.toContentItem(): ContentItem? {
    val section = ContentSection.fromId(getString("section")) ?: return null
    val videoId = getString("videoId") ?: return null
    return ContentItem(
        id = id,
        section = section,
        videoId = videoId,
        title = getString("title").orEmpty(),
        minUserLevel = getLong("minUserLevel")?.toInt() ?: 1,
        minMemberLevel = getLong("minMemberLevel")?.toInt() ?: 0,
        order = getLong("order") ?: 0,
        published = getBoolean("published") ?: true,
        viewCount = getLong("viewCount") ?: 0,
    )
}
