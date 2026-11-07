package com.ojashealing.family.data.social

import com.google.firebase.firestore.DocumentSnapshot
import com.ojashealing.family.domain.LocalDateLike
import com.ojashealing.family.domain.Role
import java.util.Calendar
import java.util.TimeZone

/** Who wrote a post, copied onto the post when it is written. */
/** A post's author snapshot. [userLevel] and [memberLevel] are 0 on posts written before badges existed. */
data class Author(
    val uid: String,
    val name: String,
    val photo: String,
    val role: Role,
    val userLevel: Int = 0,
    val memberLevel: Int = 0
)

data class Thought(
    val id: String, // IST date "yyyy-MM-dd"
    val publishAt: Long,
    val title: String,
    val text: String,
    val likeCount: Int,
    val commentCount: Int,
    val imageUrl: String = ""
)

enum class PostStatus { Visible, Pending }

data class Post(
    val id: String,
    val author: Author,
    val text: String,
    val createdAt: Long,
    val status: PostStatus,
    val imageUrl: String = ""
)

/** A Daily Activities photo post by a Member Level 3 member (spec 1.2). */
data class DailyPost(
    val id: String,
    val author: Author,
    val text: String,
    val imageUrl: String,
    val createdAt: Long,
    val likeCount: Int,
    val replyCount: Int,
    val status: PostStatus
)

data class Question(
    val id: String,
    val author: Author,
    val title: String,
    val body: String,
    val createdAt: Long,
    val likeCount: Int,
    val answerCount: Int,
    val status: PostStatus,
    val imageUrl: String = ""
)

/** What an announcement carries besides text. Stored as "image", "audio" or "video"; absent for text only. */
enum class MediaKind(val id: String) {
    Image("image"), Audio("audio"), Video("video");

    companion object {
        fun fromId(id: String?): MediaKind? = entries.firstOrNull { it.id == id }
    }
}

data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val mediaKind: MediaKind? = null,
    val mediaUrl: String = "",
    val likeCount: Int = 0,
    val commentCount: Int = 0
)

/** A post waiting for an admin, or a member's report. [path] is the Firestore document path. */
data class PendingItem(val path: String, val text: String, val author: String, val createdAt: Long, val imageUrl: String = "")
data class Report(val id: String, val targetPath: String, val reason: String, val createdAt: Long)

private val linkPattern = Regex("""https?://|www\.""", RegexOption.IGNORE_CASE)

// Must match hasLink() in firestore.rules, which rejects a 'visible' post that contains a link
fun containsLink(text: String): Boolean = linkPattern.containsMatchIn(text)

// Links and pictures wait for an admin (firestore.rules validPost)
fun needsReview(text: String, imageUrl: String): Boolean = containsLink(text) || imageUrl.isNotEmpty()

/** People named with @ in [text], out of those offered by the mention picker (at most 10, as the rules allow). */
fun mentionedUids(text: String, candidates: List<Author>): List<String> =
    candidates.filter { it.name.isNotBlank() && text.contains("@${it.name}") }.map { it.uid }.distinct().take(10)

private val ist: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

fun todayIst(): LocalDateLike = Calendar.getInstance(ist).let {
    LocalDateLike(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
}

/** Thought ids a member may open: today and the six days before, newest first. */
fun visibleThoughtIds(todayIst: LocalDateLike): List<String> {
    val cal = Calendar.getInstance(ist).apply { clear(); set(todayIst.year, todayIst.month - 1, todayIst.day) }
    return (0 until 7).map {
        val id = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        cal.add(Calendar.DAY_OF_MONTH, -1)
        id
    }
}

fun istMidnightMillis(date: LocalDateLike): Long =
    Calendar.getInstance(ist).apply { clear(); set(date.year, date.month - 1, date.day) }.timeInMillis

fun Role.toId(): String = when (this) {
    Role.Member -> "member"
    Role.SeniorMember -> "senior_member"
    Role.Swamiji -> "swamiji"
    Role.Admin -> "admin"
}

internal fun DocumentSnapshot.author() = Author(
    uid = getString("authorUid").orEmpty(),
    name = getString("authorName").orEmpty(),
    photo = getString("authorPhoto").orEmpty(),
    role = Role.fromId(getString("authorRole")),
    userLevel = getLong("authorLevel")?.toInt() ?: 0,
    memberLevel = getLong("authorMember")?.toInt() ?: 0
)

internal fun DocumentSnapshot.status() = if (getString("status") == "pending") PostStatus.Pending else PostStatus.Visible

internal fun DocumentSnapshot.millis(field: String) = getTimestamp(field)?.toDate()?.time ?: 0L

internal fun DocumentSnapshot.toPost() =
    Post(id, author(), getString("text").orEmpty(), millis("createdAt"), status(), getString("imageUrl").orEmpty())
