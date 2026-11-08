package com.ojashealing.family.data.call

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

enum class CallStatus(val id: String) {
    Ringing("ringing"), Active("active"), Ended("ended"), Declined("declined"), Missed("missed");

    val isOver get() = this == Ended || this == Declined || this == Missed

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Ended
    }
}

data class Sdp(val type: String, val sdp: String)

data class Call(
    val id: String,
    val callerUid: String,
    val calleeUid: String,
    val memberUid: String,
    val staffUid: String,
    val status: CallStatus,
    val offer: Sdp?,
    val answer: Sdp?,
    val video: Boolean = false,
    val channel: String = "swamiji",
    val createdAt: Long = 0L,
    /** Seconds the call was connected, set by the server when it ends (0 = never connected). */
    val seconds: Int = 0,
    val callerName: String = "",
    val calleeName: String = "",
    /** The callee's phone has shown the ringing call. */
    val delivered: Boolean = false
)

/** What a call-history row says: missed, declined or how long it lasted. */
fun callOutcome(c: Call): String = when {
    c.seconds > 0 -> "ended"
    c.status == CallStatus.Declined -> "declined"
    c.status == CallStatus.Ringing || c.status == CallStatus.Active -> "ongoing"
    else -> "missed"
}

/** "4:05" for 245 seconds, "1:02:03" past an hour. */
fun callDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Extra details on a new call: video or audio, which conversation, and the booking it belongs to. */
data class CallOptions(
    val video: Boolean = false,
    val channel: String = "swamiji",
    val requestId: String = "",
    val callerName: String = "",
    val calleeName: String = ""
)

data class IceCandidateData(val from: String, val sdpMid: String, val sdpMLineIndex: Int, val candidate: String)

/** TURN relay for networks where direct WebRTC fails; set by an admin in config/turn (optional). */
data class TurnServer(val urls: List<String>, val username: String, val credential: String)

/** Firestore signalling for one-to-one WebRTC calls (design §11 Q4: free WebRTC). */
class CallRepository(private val db: FirebaseFirestore) {

    private fun sdp(v: Any?): Sdp? = (v as? Map<*, *>)?.let { m ->
        val type = m["type"] as? String ?: return null
        Sdp(type, m["sdp"] as? String ?: return null)
    }

    private fun call(d: com.google.firebase.firestore.DocumentSnapshot) = Call(
        id = d.id,
        callerUid = d.getString("callerUid").orEmpty(),
        calleeUid = d.getString("calleeUid").orEmpty(),
        memberUid = d.getString("memberUid").orEmpty(),
        staffUid = d.getString("staffUid").orEmpty(),
        status = CallStatus.fromId(d.getString("status")),
        offer = sdp(d.get("offer")),
        answer = sdp(d.get("answer")),
        video = d.getBoolean("video") == true,
        channel = d.getString("channel") ?: "swamiji",
        createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
        seconds = d.getLong("seconds")?.toInt() ?: 0,
        callerName = d.getString("callerName").orEmpty(),
        calleeName = d.getString("calleeName").orEmpty(),
        delivered = d.get("deliveredAt") != null
    )

    /**
     * Call history, newest first: a member sees their own calls, staff the calls they took part in,
     * admins every call (firestore.rules allows exactly these).
     */
    fun observeHistory(uid: String, isMember: Boolean, isAdmin: Boolean): Flow<List<Call>> = callbackFlow {
        val base = db.collection("calls")
        val q = when {
            isAdmin -> base.orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            isMember -> base.whereEqualTo("memberUid", uid).orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            else -> base.whereEqualTo("staffUid", uid).orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
        }
        val reg = q.limit(200).addSnapshotListener { snap, _ -> if (snap != null) trySend(snap.documents.map(::call)) }
        awaitClose { reg.remove() }
    }

    fun observe(callId: String): Flow<Call?> = callbackFlow {
        val reg = db.document("calls/$callId").addSnapshotListener { s, _ -> trySend(s?.takeIf { it.exists() }?.let(::call)) }
        awaitClose { reg.remove() }
    }

    /** Calls ringing for [uid] right now, so an incoming call shows even without a push. */
    fun observeIncoming(uid: String): Flow<List<Call>> = callbackFlow {
        val reg = db.collection("calls").whereEqualTo("calleeUid", uid).whereEqualTo("status", "ringing")
            .addSnapshotListener { snap, _ -> if (snap != null) trySend(snap.documents.map(::call)) }
        awaitClose { reg.remove() }
    }

    suspend fun create(
        callerUid: String, calleeUid: String, memberUid: String, staffUid: String, offer: Sdp,
        options: CallOptions = CallOptions()
    ): Result<String> = runCatching {
        val ref = db.collection("calls").document()
        ref.set(
            buildMap<String, Any> {
                put("callerUid", callerUid); put("calleeUid", calleeUid); put("memberUid", memberUid); put("staffUid", staffUid)
                put("status", CallStatus.Ringing.id); put("offer", mapOf("type" to offer.type, "sdp" to offer.sdp))
                put("createdAt", FieldValue.serverTimestamp())
                put("video", options.video); put("channel", options.channel)
                if (options.requestId.isNotEmpty()) put("requestId", options.requestId)
                if (options.callerName.isNotEmpty()) put("callerName", options.callerName.take(80))
                if (options.calleeName.isNotEmpty()) put("calleeName", options.calleeName.take(80))
            }
        ).await()
        ref.id
    }

    suspend fun answer(callId: String, answer: Sdp): Result<Unit> = runCatching {
        db.document("calls/$callId").update(mapOf("answer" to mapOf("type" to answer.type, "sdp" to answer.sdp), "status" to CallStatus.Active.id)).await()
        Unit
    }

    suspend fun setStatus(callId: String, status: CallStatus): Result<Unit> = runCatching {
        val fields = buildMap<String, Any> {
            put("status", status.id)
            if (status.isOver) put("endedAt", FieldValue.serverTimestamp())
        }
        db.document("calls/$callId").update(fields).await()
        Unit
    }

    /** The callee's phone says it is ringing, so the caller can show "Ringing" and play ringback. */
    suspend fun markDelivered(callId: String): Result<Unit> = runCatching {
        db.document("calls/$callId").update("deliveredAt", FieldValue.serverTimestamp()).await()
        Unit
    }

    suspend fun addCandidate(callId: String, c: IceCandidateData): Result<Unit> = runCatching {
        db.collection("calls/$callId/candidates")
            .add(mapOf("from" to c.from, "sdpMid" to c.sdpMid, "sdpMLineIndex" to c.sdpMLineIndex, "candidate" to c.candidate)).await()
        Unit
    }

    /** The other side's ICE candidates as they arrive. */
    fun listenCandidates(callId: String, myUid: String, onCandidate: (IceCandidateData) -> Unit): ListenerRegistration =
        db.collection("calls/$callId/candidates").addSnapshotListener { snap, _ ->
            snap?.documentChanges?.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }?.forEach { ch ->
                val d = ch.document
                val from = d.getString("from").orEmpty()
                if (from != myUid) onCandidate(
                    IceCandidateData(from, d.getString("sdpMid").orEmpty(), d.getLong("sdpMLineIndex")?.toInt() ?: 0, d.getString("candidate").orEmpty())
                )
            }
        }

    suspend fun turnServer(): TurnServer? = runCatching {
        val d = db.document("config/turn").get().await()
        @Suppress("UNCHECKED_CAST")
        val urls = (d.get("urls") as? List<String>).orEmpty()
        if (urls.isEmpty()) null else TurnServer(urls, d.getString("username").orEmpty(), d.getString("credential").orEmpty())
    }.getOrNull()
}
