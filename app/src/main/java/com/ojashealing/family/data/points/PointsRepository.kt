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
    fun historyHasMore(uid: String): Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
    suspend fun loadOlderHistory(uid: String) {}
    /** Level 2 to 4 targets from config/levels (spec defaults until loaded). */
    fun observeLevelTargets(): Flow<LevelTargets> = kotlinx.coroutines.flow.flowOf(LevelTargets())
    /** What each activity earns, from config/points (defaults until loaded). */
    fun observePointsConfig(): Flow<PointsConfig> = kotlinx.coroutines.flow.flowOf(PointsConfig())
    /** Members whose name starts with [prefix], to send points to. */
    suspend fun findGiftRecipients(prefix: String): Result<List<GiftRecipient>> = Result.success(emptyList())
    /** Sends [amount] of my points to [toUid]; returns my new balance. */
    suspend fun sendPoints(toUid: String, amount: Int, note: String): Result<Int> = Result.failure(UnsupportedOperationException())
    /** Swamiji / admin: change a member's points. [mode] is add, deduct or set. */
    suspend fun staffAdjust(uid: String, mode: String, amount: Int, countForLevel: Boolean, note: String): Result<Int> = Result.failure(UnsupportedOperationException())
    suspend fun setTransfersFrozen(uid: String, frozen: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException())
    suspend fun reverseTransfer(transferId: String): Result<Int> = Result.failure(UnsupportedOperationException())
    suspend fun updatePointsConfig(values: Map<String, Any>): Result<Unit> = Result.failure(UnsupportedOperationException())
    /** Swamiji / admin: the latest gifts a member sent or received. */
    suspend fun recentTransfers(uid: String): Result<List<TransferRow>> = Result.success(emptyList())
    /** Swamiji / admin: whether a member's gifting is paused. */
    suspend fun transfersFrozen(uid: String): Result<Boolean> = Result.success(false)
}

data class GiftRecipient(val uid: String, val name: String, val photoUrl: String, val userLevel: Int)

data class TransferRow(val id: String, val fromName: String, val toName: String, val amount: Int, val note: String, val at: Long, val reversed: Boolean, val sent: Boolean)

/** The server's message for a refused call (limits, balance), or null to show a generic error. */
fun serverMessage(e: Throwable): String? =
    (e as? com.google.firebase.functions.FirebaseFunctionsException)
        ?.takeIf { it.code != com.google.firebase.functions.FirebaseFunctionsException.Code.INTERNAL && it.code != com.google.firebase.functions.FirebaseFunctionsException.Code.UNAVAILABLE }
        ?.message

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

    private var historyUid: String? = null
    private val historyPager = com.ojashealing.family.data.paging.LiveTailPager<PointHistoryItem>(idOf = { it.key })

    override fun historyHasMore(uid: String): Flow<Boolean> = historyPager.hasMore

    override suspend fun loadOlderHistory(uid: String) {
        if (historyUid == uid) historyPager.loadMore()
    }

    override fun observeHistory(uid: String): Flow<List<PointHistoryItem>> {
        historyUid = uid
        val query = db.collection("users").document(uid).collection("pointEvents")
            .orderBy("createdAt", Query.Direction.DESCENDING)
        return historyPager.observe(query) { d ->
            PointHistoryItem(
                key = d.id,
                amount = d.getLong("amount")?.toInt() ?: 0,
                reason = d.getString("reason").orEmpty(),
                at = d.getTimestamp("createdAt", com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
                note = d.getString("note").orEmpty(),
                otherName = d.getString("otherName").orEmpty()
            )
        }
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

    override fun observePointsConfig(): Flow<PointsConfig> = callbackFlow {
        trySend(PointsConfig())
        val registration = db.document("config/points").addSnapshotListener { snap, _ ->
            if (snap != null) trySend(PointsConfig.from(snap.data))
        }
        awaitClose { registration.remove() }
    }

    private suspend fun call(name: String, data: Map<String, Any?>): Map<*, *>? =
        functions.getHttpsCallable(name).call(data).await().getData() as? Map<*, *>

    override suspend fun findGiftRecipients(prefix: String): Result<List<GiftRecipient>> = runCatching {
        (call("findGiftRecipients", mapOf("prefix" to prefix))?.get("members") as? List<*>).orEmpty().mapNotNull { r ->
            val m = r as? Map<*, *> ?: return@mapNotNull null
            GiftRecipient(m["uid"] as? String ?: return@mapNotNull null, m["name"] as? String ?: "", m["photoUrl"] as? String ?: "", (m["userLevel"] as? Number)?.toInt() ?: 1)
        }
    }

    override suspend fun sendPoints(toUid: String, amount: Int, note: String): Result<Int> = runCatching {
        (call("transferPoints", mapOf("toUid" to toUid, "amount" to amount, "note" to note))?.get("balance") as? Number)?.toInt() ?: 0
    }

    override suspend fun staffAdjust(uid: String, mode: String, amount: Int, countForLevel: Boolean, note: String): Result<Int> = runCatching {
        val target = if (countForLevel) "earned" else "balance"
        (call("staffAdjustPoints", mapOf("uid" to uid, "mode" to mode, "amount" to amount, "target" to target, "note" to note))?.get("balance") as? Number)?.toInt() ?: 0
    }

    override suspend fun setTransfersFrozen(uid: String, frozen: Boolean): Result<Unit> = runCatching {
        call("setTransfersFrozen", mapOf("uid" to uid, "frozen" to frozen))
        Unit
    }

    override suspend fun reverseTransfer(transferId: String): Result<Int> = runCatching {
        (call("reverseTransfer", mapOf("transferId" to transferId))?.get("returned") as? Number)?.toInt() ?: 0
    }

    override suspend fun updatePointsConfig(values: Map<String, Any>): Result<Unit> = runCatching {
        call("updatePointsConfig", values)
        Unit
    }

    override suspend fun recentTransfers(uid: String): Result<List<TransferRow>> = runCatching {
        val col = db.collection("pointTransfers")
        val sent = col.whereEqualTo("fromUid", uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(10).get().await()
        val got = col.whereEqualTo("toUid", uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(10).get().await()
        (sent.documents.map { it to true } + got.documents.map { it to false }).map { (d, out) ->
            TransferRow(
                id = d.id, fromName = d.getString("fromName").orEmpty(), toName = d.getString("toName").orEmpty(),
                amount = d.getLong("amount")?.toInt() ?: 0, note = d.getString("note").orEmpty(),
                at = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L, reversed = d.getString("status") == "reversed", sent = out,
            )
        }.sortedByDescending { it.at }
    }

    override suspend fun transfersFrozen(uid: String): Result<Boolean> = runCatching {
        db.document("users/$uid").get().await().getBoolean("transfersFrozen") == true
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        // The server also removes posts, conversations and files; allow it the time it is given there
        functions.getHttpsCallable("deleteAccount").apply { setTimeout(300, java.util.concurrent.TimeUnit.SECONDS) }.call().await()
        Unit
    }
}
