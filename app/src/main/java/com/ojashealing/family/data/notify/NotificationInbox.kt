package com.ojashealing.family.data.notify

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/** One line in the bell list. */
data class InboxItem(
    val id: String,
    /** Personal lines are the member's own; shared ones are new posts everyone may get. */
    val personal: Boolean,
    val group: NotifGroup,
    val action: NotifAction?,
    val type: String,
    val title: String,
    val body: String,
    val target: String,
    val fromUid: String,
    /** How many events the line stands for ("Meera and 2 others liked your post"). */
    val count: Int,
    val createdAt: Long,
    val unread: Boolean,
)

/** What the member has seen of the shared lines: a time per group, and lines opened one by one. */
data class SeenState(val seen: Map<String, Long> = emptyMap(), val opened: Set<String> = emptySet(), val loaded: Boolean = false)

/** A shared line, before it is known whether it was seen. */
data class SharedLine(val id: String, val group: NotifGroup, val type: String, val title: String, val body: String, val target: String, val fromUid: String, val createdAt: Long)

/**
 * The bell list: the member's own lines and the shared ones together, newest first. A line whose switch is
 * off is left out. A shared line is unread until it is opened or its group was seen after it arrived; one the
 * member wrote themselves is never shown.
 */
fun mergeInbox(
    personal: List<InboxItem>,
    shared: List<SharedLine>,
    state: SeenState,
    prefs: Map<String, Boolean>,
    myUid: String,
    now: Long = System.currentTimeMillis(),
    zone: java.util.TimeZone = java.util.TimeZone.getDefault(),
): List<InboxItem> {
    val sharedItems = shared.filter { it.fromUid != myUid }.mapNotNull { s ->
        val seenAt = state.seen[s.group.id] ?: 0L
        // A thought arrives at 4 am local time: hidden until then here, and new from then on
        val start = if (s.group == NotifGroup.Thought) com.ojashealing.family.data.social.thoughtStartMillis(s.target.substringAfter("thoughts/"), zone) else null
        if (start != null && start > now) return@mapNotNull null
        val at = maxOf(s.createdAt, start ?: 0L)
        InboxItem(
            s.id, personal = false, s.group, NotifAction.Post, s.type, s.title, s.body, s.target, s.fromUid, 1, at,
            unread = at > seenAt && s.id !in state.opened,
        )
    }
    return (personal + sharedItems)
        .filter { switchOn(prefs, it.group, it.action) }
        .sortedByDescending { it.createdAt }
}

/** Unread lines per group, for the bell and the tabs. */
fun unreadByGroup(items: List<InboxItem>): Map<NotifGroup, Int> =
    items.filter { it.unread }.groupingBy { it.group }.eachCount()

private const val LIMIT = 100L

private fun DocumentSnapshot.time(field: String) = getTimestamp(field)?.toDate()?.time ?: 0L

private fun DocumentSnapshot.toPersonal(): InboxItem? {
    val group = NotifGroup.fromId(getString("group")) ?: return null
    return InboxItem(
        id, personal = true, group, NotifAction.fromId(getString("action")), getString("type").orEmpty(),
        getString("title").orEmpty(), getString("body").orEmpty(), getString("target").orEmpty(), getString("fromUid").orEmpty(),
        (getLong("count") ?: 1L).toInt().coerceAtLeast(1),
        // A line just written may not have its server time yet: treat it as now
        time("createdAt").takeIf { it > 0 } ?: System.currentTimeMillis(),
        unread = getBoolean("read") != true,
    )
}

private fun DocumentSnapshot.toShared(): SharedLine? {
    val group = NotifGroup.fromId(getString("group")) ?: return null
    return SharedLine(id, group, getString("type").orEmpty(), getString("title").orEmpty(), getString("body").orEmpty(),
        getString("target").orEmpty(), getString("fromUid").orEmpty(), time("createdAt"))
}

class NotificationInbox(private val db: FirebaseFirestore) {

    private fun stateDoc(uid: String) = db.document("users/$uid/notificationState/main")

    private fun <T> listen(q: Query, map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
        val reg = q.addSnapshotListener { snap, _ -> if (snap != null) trySend(snap.documents.mapNotNull(map)) }
        awaitClose { reg.remove() }
    }

    private fun observeState(uid: String): Flow<SeenState> = callbackFlow {
        val reg = stateDoc(uid).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            // First open with the bell: what was posted before is old news, so the list starts with nothing new
            if (!snap.exists() && !snap.metadata.isFromCache) {
                snap.reference.set(mapOf("seen" to NotifGroup.entries.associate { it.id to FieldValue.serverTimestamp() }), SetOptions.merge())
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            // A "seen" time just written has no server time yet: the phone's estimate stops the counts jumping meanwhile
            val seen = (snap.get("seen", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) ->
                (v as? com.google.firebase.Timestamp)?.toDate()?.time?.let { k to it }
            }.toMap()
            @Suppress("UNCHECKED_CAST")
            val opened = (snap.get("opened") as? List<Any?>).orEmpty().filterIsInstance<String>().toSet()
            trySend(SeenState(seen, opened, loaded = true))
        }
        awaitClose { reg.remove() }
    }

    /** The member's bell list. [paid]: whether they may see Activity, whose new posts are kept apart. */
    fun observe(uid: String, paid: Boolean, prefs: Map<String, Boolean>): Flow<List<InboxItem>> {
        val personal = listen(db.collection("users/$uid/notifications").orderBy("createdAt", Query.Direction.DESCENDING).limit(LIMIT)) { it.toPersonal() }
        val shared = listen(db.collection("broadcasts").orderBy("createdAt", Query.Direction.DESCENDING).limit(LIMIT)) { it.toShared() }
        val paidShared = if (paid) listen(db.collection("paidBroadcasts").orderBy("createdAt", Query.Direction.DESCENDING).limit(LIMIT)) { it.toShared() }
        else flowOf(emptyList())
        return combine(personal, shared, paidShared, observeState(uid)) { mine, s, p, state ->
            mergeInbox(mine, s + p, state, prefs, uid)
        }
    }

    /**
     * A line was opened. [shown]: the list as it is now. The opened list of shared lines only keeps lines
     * still in it, so it stays small.
     */
    suspend fun markRead(uid: String, item: InboxItem, shown: List<InboxItem>) = runCatching {
        if (item.personal) db.document("users/$uid/notifications/${item.id}").update("read", true).await()
        else {
            val opened = (shown.filter { !it.personal && !it.unread }.map { it.id } + item.id).distinct().take(250)
            stateDoc(uid).set(mapOf("opened" to opened), SetOptions.merge()).await()
        }
        Unit
    }

    /** The member looked at a group's new posts (opened its Community section): they are no longer new. */
    suspend fun markGroupSeen(uid: String, group: NotifGroup) = runCatching {
        stateDoc(uid).set(mapOf("seen" to mapOf(group.id to FieldValue.serverTimestamp())), SetOptions.merge()).await()
        Unit
    }

    /** Everything read: the member's own lines one by one, the shared ones by group. */
    suspend fun markAllRead(uid: String, items: List<InboxItem>) = runCatching {
        val batch = db.batch()
        items.filter { it.personal && it.unread }.take(400).forEach { batch.update(db.document("users/$uid/notifications/${it.id}"), "read", true) }
        // Kept small: the opened list only needs lines newer than the groups' seen times
        batch.set(stateDoc(uid), mapOf("seen" to NotifGroup.entries.associate { it.id to FieldValue.serverTimestamp() }, "opened" to emptyList<String>()), SetOptions.merge())
        batch.commit().await()
        Unit
    }
}
