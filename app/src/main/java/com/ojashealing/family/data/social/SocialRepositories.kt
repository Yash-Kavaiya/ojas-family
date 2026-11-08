package com.ojashealing.family.data.social

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.ojashealing.family.data.paging.FEED_PAGE_SIZE
import com.ojashealing.family.data.paging.LiveTailPager
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

/** Level badges shown beside the name; firestore.rules checks they are no higher than the author's real levels. */
internal fun MutableMap<String, Any>.putBadges(author: Author) {
    if (author.userLevel > 0) put("authorLevel", author.userLevel)
    if (author.memberLevel > 0) put("authorMember", author.memberLevel)
}

private fun postFields(author: Author, text: String, imageUrl: String = "", mentions: List<String> = emptyList(), reply: ReplyTo? = null) = buildMap<String, Any> {
    put("authorUid", author.uid)
    put("authorName", author.name)
    put("authorPhoto", author.photo)
    put("authorRole", author.role.toId())
    putBadges(author)
    put("text", text.trim())
    put("status", if (needsReview(text, imageUrl)) "pending" else "visible")
    put("createdAt", FieldValue.serverTimestamp())
    if (imageUrl.isNotEmpty()) put("imageUrl", imageUrl)
    if (mentions.isNotEmpty()) put("mentions", mentions.take(10))
    if (reply != null && reply.parentId.isNotEmpty()) {
        put("parentId", reply.parentId)
        put("replyToName", reply.name.take(80))
    }
}

/**
 * Visible posts plus the viewer's own pending ones (admins get everything). Two queries because
 * Firestore rules only allow queries that cannot return a post the viewer may not read.
 */
private fun FirebaseFirestore.posts(collection: String, viewerUid: String, admin: Boolean, order: Query.Direction): Flow<List<Post>> {
    val base = collection(collection)
    if (admin) return base.orderBy("createdAt", order).limit(200).flow().map { docs -> docs.map { it.toPost() } }
    val visible = base.whereEqualTo("status", "visible").orderBy("createdAt", order).limit(200).flow()
    val mine = base.whereEqualTo("authorUid", viewerUid).whereEqualTo("status", "pending").limit(FEED_PAGE_SIZE.toLong()).flow()
    return combine(visible, mine) { v, m ->
        (v + m).map { it.toPost() }.distinctBy { it.id }.sortedBy { if (order == Query.Direction.ASCENDING) it.createdAt else -it.createdAt }
    }
}

class ThoughtRepository(private val db: FirebaseFirestore) {

    /** One listener per visible day, so a denied future thought only hides that day instead of failing all. */
    fun observeThoughts(today: LocalDateLike): Flow<List<Thought>> {
        val days = visibleThoughtIds(today).map { id -> db.docFlow("thoughts/$id") }
        return combine(days) { snaps -> snaps.filterNotNull().map { it.toThought() } }
    }

    /** Admin only (firestore.rules lets admins read future thoughts): every thought after [today], soonest first. */
    fun observeScheduled(today: LocalDateLike): Flow<List<Thought>> =
        db.collection("thoughts")
            .whereGreaterThan("publishAt", Timestamp(Date(istMidnightMillis(today))))
            .orderBy("publishAt")
            .limit(120)
            .flow()
            .map { docs -> docs.map { it.toThought() } }

    private fun DocumentSnapshot.toThought() = Thought(
        id = id,
        publishAt = millis("publishAt"),
        title = getString("title").orEmpty(),
        text = getString("text").orEmpty(),
        likeCount = getLong("likeCount")?.toInt() ?: 0,
        commentCount = getLong("commentCount")?.toInt() ?: 0,
        imageUrl = getString("imageUrl").orEmpty()
    )

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

    suspend fun addComment(thoughtId: String, author: Author, text: String, imageUrl: String = "", mentions: List<String> = emptyList(), reply: ReplyTo? = null): Result<Unit> = runCatching {
        db.collection("thoughts/$thoughtId/comments").add(postFields(author, text, imageUrl, mentions, reply)).await()
        Unit
    }

    /** Admin only: the thought becomes visible at 00:00 IST on [date]; [imageUrl] null keeps the current picture. */
    suspend fun schedule(date: LocalDateLike, title: String, text: String, imageUrl: String? = null): Result<Unit> = runCatching {
        val fields = buildMap<String, Any> {
            put("title", title.trim()); put("text", text.trim()); put("publishAt", Timestamp(Date(istMidnightMillis(date))))
            imageUrl?.let { put("imageUrl", it) }
        }
        db.document("thoughts/${date.iso()}").set(fields, SetOptions.merge()).await()
        Unit
    }
}

class CommunityRepository(private val db: FirebaseFirestore) {

    private val questionsPager = LiveTailPager<Question>(idOf = { it.id })
    private val announcementsPager = LiveTailPager<Announcement>(idOf = { it.id })
    val questionsHasMore = questionsPager.hasMore
    val announcementsHasMore = announcementsPager.hasMore

    private fun DocumentSnapshot.toQuestion() = Question(
        id = id, author = author(), title = getString("title").orEmpty(), body = getString("body").orEmpty(),
        createdAt = millis("createdAt"), likeCount = getLong("likeCount")?.toInt() ?: 0,
        answerCount = getLong("answerCount")?.toInt() ?: 0, status = status(),
        imageUrl = getString("imageUrl").orEmpty()
    )

    private fun DocumentSnapshot.toAnnouncement() = Announcement(
        id, getString("title").orEmpty(), getString("body").orEmpty(), millis("createdAt"),
        MediaKind.fromId(getString("mediaType")), getString("mediaUrl").orEmpty(),
        (getLong("likeCount") ?: 0L).toInt().coerceAtLeast(0), (getLong("commentCount") ?: 0L).toInt().coerceAtLeast(0)
    )

    fun observeQuestions(viewer: Viewer, uid: String): Flow<List<Question>> {
        val base = db.collection("questions")
        val admin = viewer.role == Role.Admin
        val visible = (if (admin) base else base.whereEqualTo("status", "visible"))
            .orderBy("createdAt", Query.Direction.DESCENDING)
        val page = questionsPager.observe(visible) { it.toQuestion() }
        val mine = if (admin) flowOf(emptyList())
        else base.whereEqualTo("authorUid", uid).whereEqualTo("status", "pending").limit(FEED_PAGE_SIZE.toLong()).flow()
            .map { docs -> docs.map { it.toQuestion() } }
        return combine(page, mine) { v, m ->
            (v + m).distinctBy { it.id }.sortedByDescending { it.createdAt }
        }
    }

    suspend fun loadOlderQuestions() = questionsPager.loadMore()

    fun observeLiked(questionId: String, uid: String): Flow<Boolean> = callbackFlow {
        val reg = db.document("questions/$questionId/likes/$uid").addSnapshotListener { s, _ -> trySend(s?.exists() == true) }
        awaitClose { reg.remove() }
    }

    fun observeAnswers(questionId: String, viewer: Viewer, uid: String): Flow<List<Post>> =
        db.posts("questions/$questionId/answers", uid, viewer.role == Role.Admin, Query.Direction.ASCENDING)

    fun observeAnnouncements(): Flow<List<Announcement>> =
        announcementsPager.observe(
            db.collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING)
        ) { it.toAnnouncement() }

    suspend fun loadOlderAnnouncements() = announcementsPager.loadMore()

    fun observeBlocked(uid: String): Flow<Set<String>> =
        db.collection("users/$uid/blocked").limit(200).flow().map { docs -> docs.map { it.id }.toSet() }

    // ---- Announcement likes and comments (counts kept by functions) ----

    fun observeAnnouncementLiked(id: String, uid: String): Flow<Boolean> = callbackFlow {
        val reg = db.document("announcements/$id/likes/$uid").addSnapshotListener { s, _ -> trySend(s?.exists() == true) }
        awaitClose { reg.remove() }
    }

    fun observeAnnouncementComments(id: String, viewer: Viewer, uid: String): Flow<List<Post>> =
        db.posts("announcements/$id/comments", uid, viewer.role == Role.Admin, Query.Direction.ASCENDING)

    suspend fun setAnnouncementLiked(id: String, uid: String, liked: Boolean): Result<Unit> = runCatching {
        val ref = db.document("announcements/$id/likes/$uid")
        if (liked) ref.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await() else ref.delete().await()
        Unit
    }

    suspend fun commentOnAnnouncement(id: String, author: Author, text: String, imageUrl: String = "", mentions: List<String> = emptyList(), reply: ReplyTo? = null): Result<Unit> = runCatching {
        db.collection("announcements/$id/comments").add(postFields(author, text, imageUrl, mentions, reply)).await()
        Unit
    }

    suspend fun setLiked(questionId: String, uid: String, liked: Boolean): Result<Unit> = runCatching {
        val ref = db.document("questions/$questionId/likes/$uid")
        if (liked) ref.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await() else ref.delete().await()
        Unit
    }

    suspend fun ask(author: Author, title: String, body: String, imageUrl: String = ""): Result<Unit> = runCatching {
        db.collection("questions").add(
            buildMap<String, Any> {
                put("authorUid", author.uid); put("authorName", author.name); put("authorPhoto", author.photo)
                put("authorRole", author.role.toId()); putBadges(author); put("title", title.trim()); put("body", body.trim())
                put("status", if (needsReview("$title $body", imageUrl)) "pending" else "visible")
                put("createdAt", FieldValue.serverTimestamp())
                if (imageUrl.isNotEmpty()) put("imageUrl", imageUrl)
            }
        ).await()
        Unit
    }

    suspend fun answer(questionId: String, author: Author, text: String, imageUrl: String = "", mentions: List<String> = emptyList(), reply: ReplyTo? = null): Result<Unit> = runCatching {
        db.collection("questions/$questionId/answers").add(postFields(author, text, imageUrl, mentions, reply)).await()
        Unit
    }

    suspend fun postAnnouncement(title: String, body: String, media: MediaKind? = null, mediaUrl: String = ""): Result<Unit> = runCatching {
        db.collection("announcements").add(
            buildMap<String, Any> {
                put("title", title.trim()); put("body", body.trim()); put("createdAt", FieldValue.serverTimestamp())
                if (media != null && mediaUrl.isNotEmpty()) { put("mediaType", media.id); put("mediaUrl", mediaUrl) }
            }
        ).await()
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

/** Daily Activities (spec 1.2): paid members read, like and reply; Member Level 3 posts photos. */
class DailyRepository(private val db: FirebaseFirestore) {

    private val postsPager = LiveTailPager<DailyPost>(idOf = { it.id })
    val postsHasMore = postsPager.hasMore

    private fun DocumentSnapshot.toDaily() = DailyPost(
        id = id, author = author(), text = getString("text").orEmpty(), imageUrl = getString("imageUrl").orEmpty(),
        createdAt = millis("createdAt"), likeCount = getLong("likeCount")?.toInt() ?: 0,
        replyCount = getLong("replyCount")?.toInt() ?: 0, status = status()
    )

    fun observePosts(viewer: Viewer, uid: String): Flow<List<DailyPost>> {
        val base = db.collection("dailyPosts")
        val admin = viewer.role == Role.Admin
        val visible = (if (admin) base else base.whereEqualTo("status", "visible"))
            .orderBy("createdAt", Query.Direction.DESCENDING)
        val page = postsPager.observe(visible) { it.toDaily() }
        val mine = if (admin) flowOf(emptyList()) else base.whereEqualTo("authorUid", uid).whereEqualTo("status", "pending")
            .limit(FEED_PAGE_SIZE.toLong()).flow().map { docs -> docs.map { it.toDaily() } }
        return combine(page, mine) { v, m ->
            (v + m).distinctBy { it.id }.sortedByDescending { it.createdAt }
        }
    }

    suspend fun loadOlderPosts() = postsPager.loadMore()

    fun observeLiked(postId: String, uid: String): Flow<Boolean> = callbackFlow {
        val reg = db.document("dailyPosts/$postId/likes/$uid").addSnapshotListener { s, _ -> trySend(s?.exists() == true) }
        awaitClose { reg.remove() }
    }

    fun observeReplies(postId: String, viewer: Viewer, uid: String): Flow<List<Post>> =
        db.posts("dailyPosts/$postId/replies", uid, viewer.role == Role.Admin, Query.Direction.ASCENDING)

    suspend fun setLiked(postId: String, uid: String, liked: Boolean): Result<Unit> = runCatching {
        val ref = db.document("dailyPosts/$postId/likes/$uid")
        if (liked) ref.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await() else ref.delete().await()
        Unit
    }

    suspend fun post(author: Author, text: String, imageUrl: String, mentions: List<String>): Result<Unit> = runCatching {
        db.collection("dailyPosts").add(
            buildMap<String, Any> {
                put("authorUid", author.uid); put("authorName", author.name); put("authorPhoto", author.photo)
                put("authorRole", author.role.toId()); putBadges(author); put("text", text.trim()); put("imageUrl", imageUrl)
                put("status", if (containsLink(text)) "pending" else "visible")
                put("createdAt", FieldValue.serverTimestamp())
                if (mentions.isNotEmpty()) put("mentions", mentions.take(10))
            }
        ).await()
        Unit
    }

    suspend fun reply(postId: String, author: Author, text: String, imageUrl: String = "", mentions: List<String> = emptyList(), reply: ReplyTo? = null): Result<Unit> = runCatching {
        db.collection("dailyPosts/$postId/replies").add(postFields(author, text, imageUrl, mentions, reply)).await()
        Unit
    }
}

class ModerationRepository(private val db: FirebaseFirestore) {

    private val reportsPager = LiveTailPager<Report>(idOf = { it.id })
    val reportsHasMore = reportsPager.hasMore

    /** Pending comments, answers and questions across the app (admin only). */
    fun observePending(): Flow<List<PendingItem>> {
        fun group(name: String) = db.collectionGroup(name).whereEqualTo("status", "pending").limit(100).flow()
        return combine(
            group("comments"), group("answers"), group("replies"),
            db.collection("questions").whereEqualTo("status", "pending").limit(100).flow(),
            db.collection("dailyPosts").whereEqualTo("status", "pending").limit(100).flow()
        ) { c, a, r, q, dp ->
            (c + a + r + q + dp).map { d ->
                PendingItem(
                    path = d.reference.path,
                    text = d.getString("text") ?: listOfNotNull(d.getString("title"), d.getString("body")).joinToString("\n"),
                    author = d.getString("authorName").orEmpty(),
                    createdAt = d.millis("createdAt"),
                    imageUrl = d.getString("imageUrl").orEmpty()
                )
            }.sortedBy { it.createdAt }
        }
    }

    fun observeReports(): Flow<List<Report>> =
        reportsPager.observe(db.collection("reports").orderBy("createdAt", Query.Direction.DESCENDING)) { d ->
            Report(d.id, d.getString("targetPath").orEmpty(), d.getString("reason").orEmpty(), d.millis("createdAt"))
        }

    suspend fun loadOlderReports() = reportsPager.loadMore()

    suspend fun approve(path: String): Result<Unit> = runCatching { db.document(path).update("status", "visible").await(); Unit }
    suspend fun remove(path: String): Result<Unit> = runCatching { db.document(path).delete().await(); Unit }
    suspend fun dismissReport(id: String): Result<Unit> = runCatching { db.document("reports/$id").delete().await(); Unit }
}

/** Likes on any comment, answer or reply, addressed by the comment's document path. */
class CommentLikeRepository(private val db: FirebaseFirestore) {

    fun observeLiked(commentPath: String, uid: String): Flow<Boolean> = callbackFlow {
        val reg = db.document("$commentPath/likes/$uid").addSnapshotListener { s, _ -> trySend(s?.exists() == true) }
        awaitClose { reg.remove() }
    }

    suspend fun setLiked(commentPath: String, uid: String, liked: Boolean): Result<Unit> = runCatching {
        val ref = db.document("$commentPath/likes/$uid")
        if (liked) ref.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await() else ref.delete().await()
        Unit
    }
}
