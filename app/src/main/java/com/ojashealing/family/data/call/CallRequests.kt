package com.ojashealing.family.data.call

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

enum class RequestStatus(val id: String) {
    Pending("pending"), Proposed("proposed"), Confirmed("confirmed"), Declined("declined"), Cancelled("cancelled"), Done("done");

    val isOpen get() = this == Pending || this == Proposed || this == Confirmed

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Pending
    }
}

/** A call the member asked for at a time; staff confirm it, decline it, or propose another time. */
data class CallRequest(
    val id: String,
    val memberUid: String,
    val memberName: String,
    val memberPhoto: String,
    val channel: String,
    val video: Boolean,
    val at: Long,
    val note: String,
    val status: RequestStatus,
    val proposedAt: Long,
    val staffUid: String,
    val staffName: String
)

val IST: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

/** Bookable times on a day: every 30 minutes from 06:00 to 21:30 India time. */
fun daySlots(): List<Pair<Int, Int>> = (6 * 60..21 * 60 + 30 step 30).map { it / 60 to it % 60 }

/** The moment for [hour]:[minute] India time on the day [dayMillis] (any time that day) falls on. */
fun istMillis(dayMillis: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance(IST).apply {
        timeInMillis = dayMillis
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** The Material date picker gives UTC midnight of the chosen date; this is that date at [hour]:[minute] India time. */
fun pickedDateAt(utcMidnight: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
    return Calendar.getInstance(IST).apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
    }.timeInMillis
}

/** "Join" works from 10 minutes before a confirmed call until an hour after its start. */
fun canJoinNow(r: CallRequest, now: Long = System.currentTimeMillis()): Boolean =
    r.status == RequestStatus.Confirmed && now >= r.at - 10 * 60_000 && now <= r.at + 60 * 60_000

class CallRequestRepository(private val db: FirebaseFirestore) {

    private fun request(d: DocumentSnapshot) = CallRequest(
        id = d.id,
        memberUid = d.getString("memberUid").orEmpty(),
        memberName = d.getString("memberName").orEmpty(),
        memberPhoto = d.getString("memberPhoto").orEmpty(),
        channel = d.getString("channel") ?: "swamiji",
        video = d.getBoolean("video") == true,
        at = d.getTimestamp("at")?.toDate()?.time ?: 0L,
        note = d.getString("note").orEmpty(),
        status = RequestStatus.fromId(d.getString("status")),
        proposedAt = d.getTimestamp("proposedAt")?.toDate()?.time ?: 0L,
        staffUid = d.getString("staffUid").orEmpty(),
        staffName = d.getString("staffName").orEmpty()
    )

    private fun Query.live(): Flow<List<CallRequest>> = callbackFlow {
        val reg = limit(200).addSnapshotListener { snap, _ -> if (snap != null) trySend(snap.documents.map(::request)) }
        awaitClose { reg.remove() }
    }

    fun observe(id: String): Flow<CallRequest?> = callbackFlow {
        val reg = db.document("callRequests/$id").addSnapshotListener { s, _ -> trySend(s?.takeIf { it.exists() }?.let(::request)) }
        awaitClose { reg.remove() }
    }

    /** The member's own requests, newest time first. */
    fun observeMine(uid: String): Flow<List<CallRequest>> =
        db.collection("callRequests").whereEqualTo("memberUid", uid).orderBy("at", Query.Direction.DESCENDING).live()

    /** Staff: requests for the conversations they answer (admins: both). */
    fun observeForStaff(swamijiSide: Boolean, adminSide: Boolean): Flow<List<CallRequest>> {
        val base = db.collection("callRequests")
        val s = if (swamijiSide) base.whereEqualTo("channel", "swamiji").orderBy("at", Query.Direction.DESCENDING).live() else kotlinx.coroutines.flow.flowOf(emptyList())
        val a = if (adminSide) base.whereEqualTo("channel", "admin").orderBy("at", Query.Direction.DESCENDING).live() else kotlinx.coroutines.flow.flowOf(emptyList())
        return combine(s, a) { x, y -> (x + y).sortedByDescending { it.at } }
    }

    suspend fun request(memberUid: String, memberName: String, memberPhoto: String, channel: String, video: Boolean, at: Long, note: String): Result<String> = runCatching {
        val ref = db.collection("callRequests").document()
        ref.set(
            mapOf(
                "memberUid" to memberUid, "memberName" to memberName.take(80), "memberPhoto" to memberPhoto,
                "channel" to channel, "video" to video, "at" to Timestamp(Date(at)), "note" to note.trim().take(500),
                "status" to RequestStatus.Pending.id, "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        ref.id
    }

    /** Staff answer: confirm (optionally at a time), decline, or propose another time. */
    suspend fun answer(id: String, status: RequestStatus, staffUid: String, staffName: String, proposedAt: Long? = null): Result<Unit> = runCatching {
        val fields = buildMap<String, Any> {
            put("status", status.id); put("staffUid", staffUid); put("staffName", staffName.take(80))
            proposedAt?.let { put("proposedAt", Timestamp(Date(it))) }
        }
        db.document("callRequests/$id").update(fields).await()
        Unit
    }

    /** Member: cancel, or accept the time staff proposed. */
    suspend fun cancel(id: String): Result<Unit> = runCatching {
        db.document("callRequests/$id").update("status", RequestStatus.Cancelled.id).await()
        Unit
    }

    suspend fun acceptProposal(r: CallRequest): Result<Unit> = runCatching {
        db.document("callRequests/${r.id}").update(mapOf("status" to RequestStatus.Confirmed.id, "at" to Timestamp(Date(r.proposedAt)))).await()
        Unit
    }
}
