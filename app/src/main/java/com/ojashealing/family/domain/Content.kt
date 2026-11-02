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
    val published: Boolean = true
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

    /** What would unlock it: either free level [needsUserLevel] or member level [needsMemberLevel]; null = not a way in. */
    data class Locked(val needsUserLevel: Int?, val needsMemberLevel: Int?) : Access
}

fun accessFor(item: ContentItem, viewer: Viewer): Access {
    if (Permissions.isStaff(viewer)) return Access.Open

    // Section defaults from the design's §9; Permissions already knows that members get level rights
    val (sectionOk, sectionUser) = when (item.section) {
        ContentSection.Morning, ContentSection.Night -> Permissions.canDoDailyPractices(viewer) to UserLevel.L4.rank
        ContentSection.Understanding -> Permissions.canReadUnderstanding(viewer) to UserLevel.L2.rank
        else -> true to UserLevel.L1.rank
    }
    if (!sectionOk) {
        return Access.Locked(
            needsUserLevel = maxOf(sectionUser, item.minUserLevel),
            needsMemberLevel = maxOf(MemberLevel.M1.rank, item.minMemberLevel)
        )
    }

    // A document's own minimums can only raise the bar
    val userShort = viewer.userLevel.rank < item.minUserLevel
    val memberShort = item.minMemberLevel > 0 && viewer.memberLevel.rank < item.minMemberLevel
    return if (!userShort && !memberShort) Access.Open
    else Access.Locked(
        needsUserLevel = item.minUserLevel.takeIf { userShort },
        needsMemberLevel = item.minMemberLevel.takeIf { memberShort }
    )
}
