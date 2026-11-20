package com.ojashealing.family.domain

import java.net.URI

// Spec order: 2.1 Blue Healing Light ... 2.6 Other; Understanding belongs to the Ask tab
enum class ContentSection(val id: String) {
    BlueLight("blue_light"),
    QuickProtection("quick_protection"),
    Morning("morning"),
    Night("night"),
    Yoga("yoga"),
    Other("other"),
    Understanding("understanding");

    companion object {
        fun fromId(id: String?): ContentSection? = entries.firstOrNull { it.id == id }
    }
}

/** One Ojas Healing YouTube video placed in a section; Firestore id is "yt_<videoId>". */
data class ContentItem(
    val id: String,
    val section: ContentSection,
    val videoId: String,
    val title: String,
    val minUserLevel: Int = 1,
    val minMemberLevel: Int = 0,
    val order: Long = 0,
    val published: Boolean = true,
    /** Members who have watched it (each counted once). */
    val viewCount: Long = 0,
)

private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")
private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

/** Accepts watch, youtu.be, shorts and embed links or a bare 11-character id; null for anything else. */
fun youtubeIdFrom(input: String): String? {
    val text = input.trim()
    if (videoIdPattern.matches(text)) return text
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    if (uri.host?.lowercase() !in youtubeHosts) return null
    val path = uri.path.orEmpty().trim('/')
    val candidate = when {
        uri.host.equals("youtu.be", ignoreCase = true) -> path
        path == "watch" -> uri.rawQuery.orEmpty().split("&")
            .firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
        path.startsWith("shorts/") || path.startsWith("embed/") -> path.substringAfter('/')
        else -> null
    }
    return candidate?.takeIf { videoIdPattern.matches(it) }
}

sealed interface Access {
    data object Open : Access

    /**
     * What would unlock it: the plan table's [rule], or (for an item an admin restricted) free level
     * [needsUserLevel] or member level [needsMemberLevel]; null = not a way in.
     */
    data class Locked(val needsUserLevel: Int?, val needsMemberLevel: Int?, val rule: AccessRule? = null) : Access
}

/** A PDF book on the Ask tab's Books section: an admin-pasted https link; Firestore collection "books". */
data class Book(
    val id: String,
    val title: String,
    val url: String,
    val author: String = "",
    val minUserLevel: Int = 1,
    val minMemberLevel: Int = 0,
    val order: Long = 0,
    val published: Boolean = true,
    /** The admin's cover picture (https), or empty for the plain cover. */
    val coverUrl: String = "",
    /** Members who have opened it (each counted once). */
    val viewCount: Long = 0,
)

fun accessFor(item: ContentItem, viewer: Viewer): Access {
    if (viewer.hasFullAccess()) return Access.Open

    // Sections the plan table holds back; videos and books are open to everyone from the first day
    val rule = when (item.section) {
        ContentSection.Morning, ContentSection.Night -> AccessRules.DailyPractice
        ContentSection.QuickProtection -> AccessRules.QuickProtection
        else -> null
    }
    if (rule != null && !viewer.allows(rule)) return Access.Locked(null, null, rule)
    return gate(viewer, item.minUserLevel, item.minMemberLevel)
}

/** Books are open to everyone; a book's own minimums (set by an admin) can raise the bar. */
fun accessFor(book: Book, viewer: Viewer): Access {
    if (viewer.hasFullAccess()) return Access.Open
    return gate(viewer, book.minUserLevel, book.minMemberLevel)
}

private fun gate(viewer: Viewer, minUserLevel: Int, minMemberLevel: Int): Access {

    // A document's own minimums can only raise the bar
    val userShort = viewer.userLevel.rank < minUserLevel
    val memberShort = minMemberLevel > 0 && viewer.memberLevel.rank < minMemberLevel
    return if (!userShort && !memberShort) Access.Open
    else Access.Locked(
        needsUserLevel = minUserLevel.takeIf { userShort },
        needsMemberLevel = minMemberLevel.takeIf { memberShort }
    )
}
