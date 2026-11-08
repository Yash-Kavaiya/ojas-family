package com.ojashealing.family.data.content

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.ojashealing.family.domain.ContentItem
import com.ojashealing.family.domain.ContentSection
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface ContentRepository {
    /** All content docs ordered by section then position; callers drop unpublished items for non-admins. */
    fun observe(): Flow<List<ContentItem>>
    suspend fun addVideo(url: String, section: ContentSection): Result<Unit>
    /** Returns how many new videos were created. */
    suspend fun importPlaylist(playlistId: String, section: ContentSection): Result<Int>
    suspend fun setPublished(id: String, published: Boolean): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}

class FirestoreContentRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : ContentRepository {

    override fun observe(): Flow<List<ContentItem>> = callbackFlow {
        val registration = db.collection("content").limit(500).addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener
            val items = snap.documents.mapNotNull { it.toContentItem() }
                .sortedWith(compareBy<ContentItem>({ it.section.ordinal }, { it.order }))
            trySend(items)
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
        published = getBoolean("published") ?: true
    )
}
