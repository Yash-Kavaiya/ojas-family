package com.ojashealing.family.data.chat

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.Viewer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    val unreadByMember: Boolean
)

/** Which conversation: with Swamiji (chats/) or with the Admin team (adminChats/). */
enum class ChatChannel(val id: String, val collection: String) {
    Swamiji("swamiji", "chats"), Admin("admin", "adminChats");

    companion object {
        fun fromId(id: String?) = if (id == "admin") Admin else Swamiji
    }
}

/** A photo, PDF or audio file sent in chat. */
data class Attachment(val url: String, val name: String, val mime: String, val size: Long) {
    val isImage get() = mime.startsWith("image/")
    val isAudio get() = mime.startsWith("audio/")
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
    val call: CallLog? = null
)

/** A staff member Swamiji can hand a chat to; staff may read every profile. */
data class StaffMember(val uid: String, val name: String, val role: Role)

/** Who the member talks to by default: the account given the swamiji role (config/staff, kept by setRole). */
data class StaffContact(val swamijiUid: String, val supportUid: String = "", val supportName: String = "")

class ChatRepository(private val db: FirebaseFirestore, private val channel: ChatChannel = ChatChannel.Swamiji) {

    /** The same repository for the other conversation. */
    fun on(c: ChatChannel) = if (c == channel) this else ChatRepository(db, c)
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
        unreadByMember = d.getBoolean("unreadByMember") == true
    )

    fun observeThread(memberUid: String): Flow<ChatThread?> = callbackFlow {
        val reg = db.document("$col/$memberUid").addSnapshotListener { s, _ -> trySend(s?.takeIf { it.exists() }?.let(::thread)) }
        awaitClose { reg.remove() }
    }

    /** Staff inbox: Swamiji and admins see every chat, a senior member only the ones assigned to them. */
    fun observeInbox(viewer: Viewer, uid: String): Flow<List<ChatThread>> = callbackFlow {
        val base = db.collection(col)
        val q = if (viewer.role == Role.SeniorMember && this@ChatRepository.channel == ChatChannel.Swamiji) base.whereEqualTo("assigneeUid", uid).orderBy("lastAt", Query.Direction.DESCENDING)
        else base.orderBy("lastAt", Query.Direction.DESCENDING)
        val reg = q.limit(200).addSnapshotListener { snap, _ -> if (snap != null) trySend(snap.documents.map(::thread)) }
        awaitClose { reg.remove() }
    }

    fun observeMessages(memberUid: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = db.collection("$col/$memberUid/messages").orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(300)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                trySend(snap.documents.map { d ->
                    val att = (d.get("attachment") as? Map<*, *>)?.let { a ->
                        Attachment((a["url"] as? String).orEmpty(), (a["name"] as? String).orEmpty(), (a["mime"] as? String).orEmpty(), (a["size"] as? Number)?.toLong() ?: 0L)
                    }?.takeIf { it.url.startsWith("https://") }
                    val call = if (d.getString("kind") == "call") CallLog(d.getBoolean("video") == true, d.getString("status").orEmpty(), d.getLong("seconds")?.toInt() ?: 0) else null
                    ChatMessage(d.id, d.getString("senderUid").orEmpty(), d.getString("text").orEmpty(),
                        d.getTimestamp("createdAt", com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
                        att, call)
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
            attachment?.let { put("attachment", mapOf("url" to it.url, "name" to it.name.take(200), "mime" to it.mime, "size" to it.size)) }
        }).await()
        Unit
    }

    suspend fun markRead(memberUid: String, byStaff: Boolean): Result<Unit> = runCatching {
        db.document("$col/$memberUid").set(mapOf((if (byStaff) "unreadByStaff" else "unreadByMember") to false), SetOptions.merge()).await()
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

    fun observeStaffContact(): Flow<StaffContact?> = callbackFlow {
        val reg = db.document("config/staff").addSnapshotListener { s, _ ->
            val c = s?.let { StaffContact(it.getString("swamijiUid").orEmpty(), it.getString("supportUid").orEmpty(), it.getString("supportName").orEmpty()) }
            trySend(c?.takeIf { it.swamijiUid.isNotEmpty() || it.supportUid.isNotEmpty() })
        }
        awaitClose { reg.remove() }
    }
}
