package com.ojashealing.family.data.presence

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** How often an open app says "I am here". */
const val HEARTBEAT_MS = 60_000L

/**
 * Online / last seen: presence/{uid}.lastActiveAt, written while the app is in front.
 * Members may read staff presence and staff may read everyone's (firestore.rules).
 */
class PresenceRepository(private val db: FirebaseFirestore) {

    suspend fun heartbeat(uid: String): Result<Unit> = runCatching {
        db.document("presence/$uid").set(mapOf("lastActiveAt" to FieldValue.serverTimestamp())).await()
        Unit
    }

    /** Null when unknown or not allowed to see. */
    fun observe(uid: String): Flow<Long?> = callbackFlow {
        val reg = db.document("presence/$uid").addSnapshotListener { s, e ->
            trySend(if (e != null) null else s?.getTimestamp("lastActiveAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time)
        }
        awaitClose { reg.remove() }
    }
}
