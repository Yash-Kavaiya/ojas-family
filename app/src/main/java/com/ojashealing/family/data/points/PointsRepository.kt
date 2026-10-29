package com.ojashealing.family.data.points

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class PointEvent(val amount: Int, val reason: String, val milestone: Boolean)

interface PointsRepository {
    /** True when today's (IST) point was awarded by this call, false if it was already claimed. */
    suspend fun claimDailyOpen(): Result<Boolean>
    /** Point awards that arrive after collection starts; existing history is not replayed. */
    fun observeNewEvents(uid: String): Flow<PointEvent>
    suspend fun deleteAccount(): Result<Unit>
}

class FirebasePointsRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : PointsRepository {

    override suspend fun claimDailyOpen(): Result<Boolean> = runCatching {
        val data = functions.getHttpsCallable("claimDailyOpen").call().await().getData() as? Map<*, *>
        data?.get("awarded") == true
    }

    override fun observeNewEvents(uid: String): Flow<PointEvent> = callbackFlow {
        var first = true
        val registration = db.collection("users").document(uid).collection("pointEvents")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                // The first snapshot is history; only later additions are new awards worth animating
                if (first) {
                    first = false
                    return@addSnapshotListener
                }
                snap.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED && !it.document.metadata.hasPendingWrites() }
                    .forEach { change ->
                        val d = change.document
                        trySend(
                            PointEvent(
                                amount = d.getLong("amount")?.toInt() ?: 0,
                                reason = d.getString("reason").orEmpty(),
                                milestone = d.getBoolean("milestone") ?: false
                            )
                        )
                    }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        functions.getHttpsCallable("deleteAccount").call().await()
        Unit
    }
}
