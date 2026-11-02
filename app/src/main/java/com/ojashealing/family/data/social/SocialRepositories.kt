package com.ojashealing.family.data.social

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.ojashealing.family.domain.LocalDateLike
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.Viewer
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private fun FirebaseFirestore.docFlow(path: String) = callbackFlow {
    val reg = document(path).addSnapshotListener { snap, _ -> trySend(snap?.takeIf { it.exists() }) }
    awaitClose { reg.remove() }
}

private fun Query.flow() = callbackFlow {
    val reg = addSnapshotListener { snap, _ -> if (snap != null) trySend(snap.documents) }
    awaitClose { reg.remove() }
}

private fun postFields(author: Author, text: String) = mapOf(
    "authorUid" to author.uid,
    "authorName" to author.name,
    "authorPhoto" to author.photo,
    "authorRole" to author.role.toId(),
    "text" to text.trim(),
    "status" to if (containsLink(text)) "pending" else "visible",
    "createdAt" to FieldValue.serverTimestamp()
)

/**
 * Visible posts plus the viewer's own pending ones (admins get everything). Two queries because
 * Firestore rules only allow queries that cannot return a post the viewer may not read.
 */
private fun FirebaseFirestore.posts(collection: String, viewerUid: String, admin: Boolean, order: Query.Direction): Flow<List<Post>> {
    val base = collection(collection)
    if (admin) return base.orderBy("createdAt", order).limit(200).flow().map { docs -> docs.map { it.toPost() } }
    val visible = base.whereEqualTo("status", "visible").orderBy("createdAt", order).limit(200).flow()
    val mine = base.whereEqualTo("authorUid", viewerUid).whereEqualTo("status", "pending").flow()
    return combine(visible, mine) { v, m ->
        (v + m).map { it.toPost() }.distinctBy { it.id }.sortedBy { if (order == Query.Direction.ASCENDING) it.createdAt else -it.createdAt }
    }
}

class ThoughtRepository(private val db: FirebaseFirestore) {

    /** One listener per visible day, so a denied future thought only hides that day instead of failing all. */
    fun observeThoughts(today: LocalDateLike): Flow<List<Thought>> {
        val days = visibleThoughtIds(today).map { id -> db.docFlow("thoughts/$id") }
        return combine(days) { snaps ->
            snaps.filterNotNull().map { d ->
                Thought(
                    id = d.id,
                    publishAt = d.millis("publishAt"),
                    title = d.getString("title").orEmpty(),
                    text = d.getString("text").orEmpty(),
                    likeCount = d.getLong("likeCount")?.toInt() ?: 0,
                    commentCount = d.getLong("commentCount")?.toInt() ?: 0
                )
            }
        }
    }

    fun observeLiked(thoughtId: String, uid: String): Flow<Boolean> = callbackFlow {
        val reg = db.document("thoughts/$thoughtId/likes/$uid").addSnapshotListener { s, _ -> trySend(s?.exists() == true) }
        awaitClose { reg.remove() }
    }

    fun observeComments(thoughtId: String, viewer: Viewer, uid: String): Flow<List<Post>> =
        db.posts("thoughts/$thoughtId/comments", uid, viewer.role == Role.Admin, Query.Direction.ASCENDING)

    suspend fun setLiked(thoughtId: String, uid: String, liked: Boolean): Result<Unit> = runCatching {
        val ref = db.document("thoughts/$thoughtId/likes/$uid")
        if (liked) ref.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await() else ref.delete().await()
        Unit
    }

    suspend fun addComment(thoughtId: String, author: Author, text: String): Result<Unit> = runCatching {
        db.collection("thoughts/$thoughtId/comments").add(postFields(author, text)).await()
        Unit
    }

    /** Admin only: the thought becomes visible at 00:00 IST on [date]. */
    suspend fun schedule(date: LocalDateLike, title: String, text: String): Result<Unit> = runCatching {
        db.document("thoughts/${date.iso()}").set(
            mapOf("title" to title.trim(), "text" to text.trim(), "publishAt" to Timestamp(Date(istMidnightMillis(date)))),
            SetOptions.merge()
        ).await()
        Unit
    }
}

class CommunityRepository(private val db: FirebaseFirestore) {

    fun observeQuestions(viewer: Viewer, uid: String): Flow<List<Question>> {
        val base = db.collection("questions")
        val admin = viewer.role == Role.Admin
        val visible = (if (admin) base else base.whereEqualTo("status", "visible"))
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(100).flow()
        val mine = if (admin) flowOf(emptyList()) else base.whereEqualTo("authorUid", uid).whereEqualTo("status", "pending").flow()
        return combine(visible, mine) { v, m ->
            (v + m).distinctBy { it.id }.map { d ->
                Question(
                    id = d.id, author = d.author(), title = d.getString("title").orEmpty(), body = d.getString("body").orEmpty(),
                    createdAt = d.millis("createdAt"), likeCount = d.getLong("likeCount")?.toInt() ?: 0,
                    answerCount = d.getLong("answerCount")?.toInt() ?: 0, status = d.status()
                )
            }.sortedByDescending { it.createdAt }
        }
    }

    fun observeLiked(questionId: String, uid: String): Flow<Boolean> = callbackFlow {
        val reg = db.document("questions/$questionId/likes/$uid").addSnapshotListener { s, _ -> trySend(s?.exists() == true) }
        awaitClose { reg.remove() }
    }

    fun observeAnswers(questionId: String, viewer: Viewer, uid: String): Flow<List<Post>> =
        db.posts("questions/$questionId/answers", uid, viewer.role == Role.Admin, Query.Direction.ASCENDING)

    fun observeAnnouncements(): Flow<List<Announcement>> =
        db.collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING).limit(50).flow()
            .map { docs -> docs.map { Announcement(it.id, it.getString("title").orEmpty(), it.getString("body").orEmpty(), it.millis("createdAt")) } }

    fun observeBlocked(uid: String): Flow<Set<String>> =
        db.collection("users/$uid/blocked").flow().map { docs -> docs.map { it.id }.toSet() }

    suspend fun setLiked(questionId: String, uid: String, liked: Boolean): Result<Unit> = runCatching {
        val ref = db.document("questions/$questionId/likes/$uid")
        if (liked) ref.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await() else ref.delete().await()
        Unit
    }

    suspend fun ask(author: Author, title: String, body: String): Result<Unit> = runCatching {
        db.collection("questions").add(
            mapOf(
                "authorUid" to author.uid, "authorName" to author.name, "authorPhoto" to author.photo, "authorRole" to author.role.toId(),
                "title" to title.trim(), "body" to body.trim(),
                "status" to if (containsLink("$title $body")) "pending" else "visible",
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    suspend fun answer(questionId: String, author: Author, text: String): Result<Unit> = runCatching {
        db.collection("questions/$questionId/answers").add(postFields(author, text)).await()
        Unit
    }

    suspend fun postAnnouncement(title: String, body: String): Result<Unit> = runCatching {
        db.collection("announcements").add(mapOf("title" to title.trim(), "body" to body.trim(), "createdAt" to FieldValue.serverTimestamp())).await()
        Unit
    }

    suspend fun block(uid: String, otherUid: String): Result<Unit> = runCatching {
        db.document("users/$uid/blocked/$otherUid").set(mapOf("at" to FieldValue.serverTimestamp())).await()
        Unit
    }

    suspend fun report(uid: String, targetPath: String, reason: String): Result<Unit> = runCatching {
        db.collection("reports").add(
            mapOf("reporterUid" to uid, "targetPath" to targetPath, "reason" to reason.take(500), "createdAt" to FieldValue.serverTimestamp())
        ).await()
        Unit
    }
}

class ModerationRepository(private val db: FirebaseFirestore) {

    /** Pending comments, answers and questions across the app (admin only). */
    fun observePending(): Flow<List<PendingItem>> {
        fun group(name: String) = db.collectionGroup(name).whereEqualTo("status", "pending").flow()
        return combine(group("comments"), group("answers"), db.collection("questions").whereEqualTo("status", "pending").flow()) { c, a, q ->
            (c + a + q).map { d ->
                PendingItem(
                    path = d.reference.path,
                    text = d.getString("text") ?: listOfNotNull(d.getString("title"), d.getString("body")).joinToString("\n"),
                    author = d.getString("authorName").orEmpty(),
                    createdAt = d.millis("createdAt")
                )
            }.sortedBy { it.createdAt }
        }
    }

    fun observeReports(): Flow<List<Report>> =
        db.collection("reports").orderBy("createdAt", Query.Direction.DESCENDING).flow()
            .map { docs -> docs.map { Report(it.id, it.getString("targetPath").orEmpty(), it.getString("reason").orEmpty(), it.millis("createdAt")) } }

    suspend fun approve(path: String): Result<Unit> = runCatching { db.document(path).update("status", "visible").await(); Unit }
    suspend fun remove(path: String): Result<Unit> = runCatching { db.document(path).delete().await(); Unit }
    suspend fun dismissReport(id: String): Result<Unit> = runCatching { db.document("reports/$id").delete().await(); Unit }
}
