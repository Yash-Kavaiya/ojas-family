package com.ojashealing.family.data.chat

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.ojashealing.family.data.paging.LiveTailPager
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.Viewer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.tasks.await

/** One member's private chat with Swamiji (or the senior member Swamiji assigned). Doc id = member uid. */
data class ChatThread(
    val memberUid: String,
    val memberName: String,
    val memberPhoto: String,
    val assigneeUid: String,
    val assigneeName: String,
    val lastMessage: String,
    val lastAt: Long,
    val unreadByStaff: Boolean,
    val unreadByMember: Boolean,
    val unreadCountStaff: Int = 0,
    val unreadCountMember: Int = 0,
    /** When each side last had the chat open; a message is "seen" once the other side's time passes it. */
    val memberReadAt: Long = 0,
    val staffReadAt: Long = 0,
    /** When each side last typed; "typing" shows for a few seconds after. */
    val typingMember: Long = 0,
    val typingStaff: Long = 0
)

/** Which conversation: with Swamiji (chats/) or with the Admin team (adminChats/). */
enum class ChatChannel(val id: String, val collection: String) {
    Swamiji("swamiji", "chats"), Admin("admin", "adminChats");

    companion object {
        fun fromId(id: String?) = if (id == "admin") Admin else Swamiji
    }
}

/** A photo, PDF or audio file sent in chat. */
data class Attachment(
    val url: String,
    val name: String,
    val mime: String,
    val size: Long,
    /** Voice notes only: their length and a few dozen loudness bars (0..31) for the waveform. */
    val durationMs: Long = 0,
    val waveform: List<Int> = emptyList()
) {
    val isImage get() = mime.startsWith("image/")
    val isAudio get() = mime.startsWith("audio/")
    val isVoice get() = isAudio && waveform.isNotEmpty()
    val isPdf get() = mime == "application/pdf"
}

/** A call-log line the server adds when a call ends. */
data class CallLog(val video: Boolean, val status: String, val seconds: Int)

data class ChatMessage(
    val id: String,
    val senderUid: String,
    val text: String,
    val createdAt: Long,
    val attachment: Attachment? = null,
    val call: CallLog? = null,
    /** Still on its way to the server (offline or just sent). */
    val pending: Boolean = false
)

/** A member or staff member's pinned/muted choice for one conversation. */
data class ChatPref(val pinned: Boolean = false, val muted: Boolean = false)

/** The key for a conversation's pref and its mute check on the server: "swamiji_<memberUid>". */
fun prefKey(channel: ChatChannel, memberUid: String) = "${channel.id}_$memberUid"

/** A staff member Swamiji can hand a chat to; staff may read every profile. */
data class StaffMember(val uid: String, val name: String, val role: Role)

/** Who the member talks to by default: the account given the swamiji role (config/staff, kept by setRole). */
data class StaffContact(val swamijiUid: String, val supportUid: String = "", val supportName: String = "")

class ChatRepository(private val db: FirebaseFirestore, private val channel: ChatChannel = ChatChannel.Swamiji) {

    private val other by lazy { ChatRepository(db, if (channel == ChatChannel.Swamiji) ChatChannel.Admin else ChatChannel.Swamiji) }
    private val inboxPager = LiveTailPager<ChatThread>(idOf = { it.memberUid })
    val inboxHasMore = inboxPager.hasMore

    /** The same repository for the other conversation. One instance, so its page cursor stays put. */
    fun on(c: ChatChannel) = if (c == channel) this else other
    private val col get() = channel.collection

    private fun thread(d: com.google.firebase.firestore.DocumentSnapshot) = ChatThread(
        memberUid = d.id,
        memberName = d.getString("memberName").orEmpty(),
        memberPhoto = d.getString("memberPhoto").orEmpty(),
        assigneeUid = d.getString("assigneeUid").orEmpty(),
        assigneeName = d.getString("assigneeName").orEmpty(),
        lastMessage = d.getString("lastMessage").orEmpty(),
        lastAt = d.getTimestamp("lastAt")?.toDate()?.time ?: 0L,
        unreadByStaff = d.getBoolean("unreadByStaff") == true,
        unreadByMember = d.getBoolean("unreadByMember") == true,
        unreadCountStaff = d.getLong("unreadCountStaff")?.toInt() ?: if (d.getBoolean("unreadByStaff") == true) 1 else 0,
        unreadCountMember = d.getLong("unreadCountMember")?.toInt() ?: if (d.getBoolean("unreadByMember") == true) 1 else 0,
        memberReadAt = d.millisOf("memberReadAt"),
        staffReadAt = d.millisOf("staffReadAt"),
        typingMember = d.millisOf("typingMember"),
        typingStaff = d.millisOf("typingStaff")
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.millisOf(field: String) =
        getTimestamp(field, com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L

    fun observeThread(memberUid: String): Flow<ChatThread?> = callbackFlow {
        val reg = db.document("$col/$memberUid").addSnapshotListener { s, _ -> trySend(s?.takeIf { it.exists() }?.let(::thread)) }
        awaitClose { reg.remove() }
    }

    /** Staff inbox: Swamiji and admins see every chat, a senior member only the ones assigned to them. */
    fun observeInbox(viewer: Viewer, uid: String): Flow<List<ChatThread>> = callbackFlow {
        val base = db.collection(col)
        val q = if (viewer.role == Role.SeniorMember && this@ChatRepository.channel == ChatChannel.Swamiji) base.whereEqualTo("assigneeUid", uid).orderBy("lastAt", Query.Direction.DESCENDING)
        else base.orderBy("lastAt", Query.Direction.DESCENDING)
        inboxPager.observe(q, ::thread).collect { trySend(it) }
        awaitClose { }
    }

    suspend fun loadOlderInbox() = inboxPager.loadMore()

    fun observeMessages(memberUid: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = db.collection("$col/$memberUid/messages").orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(300)
            // Pending writes too, so a message shows a clock until the server has it
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snap, _ ->
                if (snap == null) return@addSnapshotListener
                trySend(snap.documents.map { d ->
                    val att = (d.get("attachment") as? Map<*, *>)?.let { a ->
                        Attachment(
                            (a["url"] as? String).orEmpty(), (a["name"] as? String).orEmpty(), (a["mime"] as? String).orEmpty(), (a["size"] as? Number)?.toLong() ?: 0L,
                            durationMs = (a["durationMs"] as? Number)?.toLong() ?: 0L,
                            waveform = (a["waveform"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() }
                        )
                    }?.takeIf { it.url.startsWith("https://") }
                    val call = if (d.getString("kind") == "call") CallLog(d.getBoolean("video") == true, d.getString("status").orEmpty(), d.getLong("seconds")?.toInt() ?: 0) else null
                    ChatMessage(d.id, d.getString("senderUid").orEmpty(), d.getString("text").orEmpty(),
                        d.getTimestamp("createdAt", com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
                        att, call, pending = d.metadata.hasPendingWrites())
                })
            }
        awaitClose { reg.remove() }
    }

    /** The member's first message creates the chat (rules require an active chat pack). */
    suspend fun send(memberUid: String, senderUid: String, text: String, memberName: String, memberPhoto: String, attachment: Attachment? = null): Result<Unit> = runCatching {
        val chat = db.document("$col/$memberUid")
        if (senderUid == memberUid && !chat.get().await().exists()) {
            chat.set(mapOf("memberUid" to memberUid, "memberName" to memberName, "memberPhoto" to memberPhoto, "createdAt" to FieldValue.serverTimestamp())).await()
        }
        db.collection("$col/$memberUid/messages").add(buildMap<String, Any> {
            put("senderUid", senderUid); put("text", text.trim().take(2000)); put("createdAt", FieldValue.serverTimestamp())
            attachment?.let {
                put("attachment", buildMap<String, Any> {
                    put("url", it.url); put("name", it.name.take(200)); put("mime", it.mime); put("size", it.size)
                    if (it.durationMs > 0) put("durationMs", it.durationMs.coerceAtMost(300_000))
                    if (it.waveform.isNotEmpty()) put("waveform", it.waveform.take(64))
                })
            }
        }).await()
        Unit
    }

    /** Clears my unread count and records when I read, which turns the other side's ticks blue. */
    suspend fun markRead(memberUid: String, byStaff: Boolean): Result<Unit> = runCatching {
        val side = if (byStaff) "Staff" else "Member"
        db.document("$col/$memberUid").update(
            mapOf(
                "unreadBy$side" to false,
                "unreadCount$side" to 0,
                (if (byStaff) "staffReadAt" else "memberReadAt") to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    /** "typing" for the other side; the screen calls this at most every few seconds while typing. */
    suspend fun setTyping(memberUid: String, byStaff: Boolean): Result<Unit> = runCatching {
        db.document("$col/$memberUid").update(if (byStaff) "typingStaff" else "typingMember", FieldValue.serverTimestamp()).await()
        Unit
    }

    /** My pinned and muted conversations, by [prefKey]. */
    fun observePrefs(uid: String): Flow<Map<String, ChatPref>> = callbackFlow {
        val reg = db.collection("users/$uid/chatPrefs").addSnapshotListener { snap, _ ->
            if (snap != null) trySend(snap.documents.filter { it.id != QUICK_REPLIES }.associate { it.id to ChatPref(it.getBoolean("pinned") == true, it.getBoolean("muted") == true) })
        }
        awaitClose { reg.remove() }
    }

    suspend fun setPref(uid: String, key: String, pref: ChatPref): Result<Unit> = runCatching {
        db.document("users/$uid/chatPrefs/$key").set(mapOf("pinned" to pref.pinned, "muted" to pref.muted)).await()
        Unit
    }

    /** A staff member's saved replies, shown as chips above the message box. */
    fun observeQuickReplies(uid: String): Flow<List<String>> = callbackFlow {
        val reg = db.document("users/$uid/chatPrefs/$QUICK_REPLIES").addSnapshotListener { s, _ ->
            trySend((s?.get("items") as? List<*>).orEmpty().mapNotNull { it as? String })
        }
        awaitClose { reg.remove() }
    }

    suspend fun setQuickReplies(uid: String, items: List<String>): Result<Unit> = runCatching {
        db.document("users/$uid/chatPrefs/$QUICK_REPLIES").set(mapOf("items" to items.map { it.take(300) }.take(20))).await()
        Unit
    }

    /** Swamiji or admin hands the chat to a senior member; an empty uid takes it back. */
    suspend fun assign(memberUid: String, staff: StaffMember?): Result<Unit> = runCatching {
        db.document("chats/$memberUid").update(mapOf("assigneeUid" to (staff?.uid ?: ""), "assigneeName" to (staff?.name ?: ""))).await()
        Unit
    }

    suspend fun seniorMembers(): Result<List<StaffMember>> = runCatching {
        db.collection("users").whereEqualTo("role", "senior_member").get().await().documents
            .map { StaffMember(it.id, it.getString("displayName").orEmpty(), Role.SeniorMember) }
    }

    private companion object {
        const val QUICK_REPLIES = "quickReplies"
    }

    fun observeStaffContact(): Flow<StaffContact?> = callbackFlow {
        val reg = db.document("config/staff").addSnapshotListener { s, _ ->
            val c = s?.let { StaffContact(it.getString("swamijiUid").orEmpty(), it.getString("supportUid").orEmpty(), it.getString("supportName").orEmpty()) }
            trySend(c?.takeIf { it.swamijiUid.isNotEmpty() || it.supportUid.isNotEmpty() })
        }
        awaitClose { reg.remove() }
    }
}
