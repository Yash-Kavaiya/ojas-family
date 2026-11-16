package com.ojashealing.family.data.notify

/**
 * Notification settings, grouped. Kept in users/{uid}.notificationPrefs; Cloud Functions read the same
 * keys before sending (functions/src/notify.ts: switchOn), so the two must stay in step.
 *
 * Keys: "all"; one per group ("qa"); one per group and action ("qa.mention"). Missing means on.
 */
enum class NotifGroup(val id: String) {
    Critical("critical"), Thought("thought"), Activity("activity"), Announcement("announcement"), Qa("qa");

    /** Critical has one switch; the others have Post, Like & Reply and Mention. */
    val actions: List<NotifAction> get() = if (this == Critical) emptyList() else NotifAction.entries

    companion object {
        fun fromId(id: String?): NotifGroup? = entries.firstOrNull { it.id == id }
    }
}

enum class NotifAction(val id: String) {
    Post("post"), LikeReply("likeReply"), Mention("mention");

    companion object {
        fun fromId(id: String?): NotifAction? = entries.firstOrNull { it.id == id }
    }
}

const val NOTIF_ALL = "all"

fun notifKey(group: NotifGroup, action: NotifAction? = null) = if (action == null) group.id else "${group.id}.${action.id}"

/** Settings saved before the groups existed, and what each now means. */
private val oldKeys = mapOf(
    "announcement.post" to "announcements",
    "thought.post" to "thoughtOfDay",
    "qa.post" to "communityPosts",
    "qa.likeReply" to "communityInteractions",
    "qa.mention" to "communityMentions",
    "thought.likeReply" to "communityInteractions",
    "thought.mention" to "communityMentions",
    "announcement.likeReply" to "communityInteractions",
    "announcement.mention" to "communityMentions",
    "activity.post" to "dailyPosts",
    "activity.likeReply" to "dailyReplies",
    "activity.mention" to "dailyMentions",
)

/** Whether a switch is on: the master switch, then the group, then the action (new key first, else the old one). */
fun switchOn(prefs: Map<String, Boolean>, group: NotifGroup, action: NotifAction? = null): Boolean {
    if (prefs[NOTIF_ALL] == false || prefs[group.id] == false) return false
    if (action == null) return true
    val key = notifKey(group, action)
    prefs[key]?.let { return it }
    return oldKeys[key]?.let { prefs[it] } != false
}

/** Which broadcast topic carries each group's new posts (functions/src/notify.ts: TOPICS). */
val postTopics = mapOf(
    NotifGroup.Announcement to "announcements",
    NotifGroup.Thought to "thoughtOfDay",
    NotifGroup.Qa to "communityPosts",
    NotifGroup.Activity to "dailyPosts",
)
