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
    /** Every award, newest first: how each Moksha Point was earned. */
    fun observeHistory(uid: String): Flow<List<PointHistoryItem>> = kotlinx.coroutines.flow.flowOf(emptyList())
    /** Level 2 to 4 targets from config/levels (spec defaults until loaded). */
    fun observeLevelTargets(): Flow<LevelTargets> = kotlinx.coroutines.flow.flowOf(LevelTargets())
}

class FirebasePointsRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : PointsRepository {

    override suspend fun claimDailyOpen(): Result<Boolean> = runCatching {
        val data = functions.getHttpsCallable("claimDailyOpen").call().await().getData() as? Map<*, *>
        // On the admin email list: the server just made this account admin; fetch a token with the new role
        if (data?.get("roleChanged") == true) com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()
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

    override fun observeHistory(uid: String): Flow<List<PointHistoryItem>> = callbackFlow {
        val registration = db.collection("users").document(uid).collection("pointEvents")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(500)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                trySend(snap.documents.map { d ->
                    PointHistoryItem(
                        key = d.id,
                        amount = d.getLong("amount")?.toInt() ?: 0,
                        reason = d.getString("reason").orEmpty(),
                        at = d.getTimestamp("createdAt", com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
                        note = d.getString("note").orEmpty()
                    )
                })
            }
        awaitClose { registration.remove() }
    }

    override fun observeLevelTargets(): Flow<LevelTargets> = callbackFlow {
        trySend(LevelTargets())
        val registration = db.document("config/levels").addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) return@addSnapshotListener
            fun t(name: String, d: LevelTarget): LevelTarget {
                val m = snap.get(name) as? Map<*, *> ?: return d
                return LevelTarget((m["days"] as? Number)?.toInt() ?: d.days, (m["points"] as? Number)?.toInt() ?: d.points)
            }
            val def = LevelTargets()
            trySend(LevelTargets(t("l2", def.l2), t("l3", def.l3), t("l4", def.l4)))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        functions.getHttpsCallable("deleteAccount").call().await()
        Unit
    }
}
