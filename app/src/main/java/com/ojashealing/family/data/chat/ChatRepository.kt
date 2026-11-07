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

data class ChatMessage(val id: String, val senderUid: String, val text: String, val createdAt: Long)

/** A staff member Swamiji can hand a chat to; staff may read every profile. */
data class StaffMember(val uid: String, val name: String, val role: Role)

/** Who the member talks to by default: the account given the swamiji role (config/staff, kept by setRole). */
data class StaffContact(val swamijiUid: String)

class ChatRepository(private val db: FirebaseFirestore) {

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
        val reg = db.document("chats/$memberUid").addSnapshotListener { s, _ -> trySend(s?.takeIf { it.exists() }?.let(::thread)) }
        awaitClose { reg.remove() }
    }

    /** Staff inbox: Swamiji and admins see every chat, a senior member only the ones assigned to them. */
    fun observeInbox(viewer: Viewer, uid: String): Flow<List<ChatThread>> = callbackFlow {
        val base = db.collection("chats")
        val q = if (viewer.role == Role.SeniorMember) base.whereEqualTo("assigneeUid", uid).orderBy("lastAt", Query.Direction.DESCENDING)
        else base.orderBy("lastAt", Query.Direction.DESCENDING)
        val reg = q.limit(200).addSnapshotListener { snap, _ -> if (snap != null) trySend(snap.documents.map(::thread)) }
        awaitClose { reg.remove() }
    }

    fun observeMessages(memberUid: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = db.collection("chats/$memberUid/messages").orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(300)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                trySend(snap.documents.map { d ->
                    ChatMessage(d.id, d.getString("senderUid").orEmpty(), d.getString("text").orEmpty(),
                        d.getTimestamp("createdAt", com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L)
                })
            }
        awaitClose { reg.remove() }
    }

    /** The member's first message creates the chat (rules require an active chat pack). */
    suspend fun send(memberUid: String, senderUid: String, text: String, memberName: String, memberPhoto: String): Result<Unit> = runCatching {
        val chat = db.document("chats/$memberUid")
        if (senderUid == memberUid && !chat.get().await().exists()) {
            chat.set(mapOf("memberUid" to memberUid, "memberName" to memberName, "memberPhoto" to memberPhoto, "createdAt" to FieldValue.serverTimestamp())).await()
        }
        db.collection("chats/$memberUid/messages")
            .add(mapOf("senderUid" to senderUid, "text" to text.trim().take(2000), "createdAt" to FieldValue.serverTimestamp())).await()
        Unit
    }

    suspend fun markRead(memberUid: String, byStaff: Boolean): Result<Unit> = runCatching {
        db.document("chats/$memberUid").set(mapOf((if (byStaff) "unreadByStaff" else "unreadByMember") to false), SetOptions.merge()).await()
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
            trySend(s?.getString("swamijiUid")?.takeIf { it.isNotEmpty() }?.let(::StaffContact))
        }
        awaitClose { reg.remove() }
    }
}
