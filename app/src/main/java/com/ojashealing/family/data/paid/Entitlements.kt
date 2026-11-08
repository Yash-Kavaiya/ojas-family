package com.ojashealing.family.data.paid

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Paid access kinds; ids match users/{uid}/entitlements/{id} written by Cloud Functions. */
enum class PaidAccess(val id: String) { Membership("membership"), Chat("chat"), Call("call") }

/** One paid access: active until [expiresAt]; [credits] is calls left in a weekly pack (null = unlimited). */
data class Entitlement(val access: PaidAccess, val expiresAt: Long, val credits: Int?) {
    fun isActive(now: Long = System.currentTimeMillis()) = expiresAt > now
    fun canCall(now: Long = System.currentTimeMillis()) = isActive(now) && (credits == null || credits > 0)
}

data class Entitlements(val byAccess: Map<PaidAccess, Entitlement> = emptyMap()) {
    operator fun get(access: PaidAccess): Entitlement? = byAccess[access]
    fun active(access: PaidAccess, now: Long = System.currentTimeMillis()) = byAccess[access]?.isActive(now) == true
}

/** Products on sale; ids must match Play Console and PRODUCTS in functions/src/billing.ts. */
enum class Product(val id: String, val subscription: Boolean, val access: PaidAccess) {
    Membership("membership_monthly", true, PaidAccess.Membership),
    ChatWeek("chat_week", false, PaidAccess.Chat),
    ChatMonth("chat_monthly", true, PaidAccess.Chat),
    CallWeek("call_week", false, PaidAccess.Call),
    CallMonth("call_monthly", true, PaidAccess.Call);

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}

class PaidRepository(private val db: FirebaseFirestore, private val functions: FirebaseFunctions) {

    fun observe(uid: String): Flow<Entitlements> = callbackFlow {
        val reg = db.collection("users/$uid/entitlements").limit(20).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            val map = snap.documents.mapNotNull { d ->
                val access = PaidAccess.entries.firstOrNull { it.id == d.id } ?: return@mapNotNull null
                access to Entitlement(access, d.getTimestamp("expiresAt")?.toDate()?.time ?: 0L, d.getLong("credits")?.toInt())
            }.toMap()
            trySend(Entitlements(map))
        }
        awaitClose { reg.remove() }
    }

    /** Sends a Play purchase to the server, which checks it with Google before granting. */
    suspend fun verify(productId: String, purchaseToken: String): Result<Unit> = runCatching {
        functions.getHttpsCallable("verifyPurchase").call(mapOf("productId" to productId, "purchaseToken" to purchaseToken)).await()
        Unit
    }

    /** Admin: grant (or revoke) access for a payment taken outside the app. */
    suspend fun adminGrant(uid: String, access: PaidAccess, days: Int, credits: Int?, revoke: Boolean = false): Result<Unit> = runCatching {
        val data = buildMap<String, Any> {
            put("uid", uid); put("entitlement", access.id); put("days", days); put("revoke", revoke)
            credits?.let { put("credits", it) }
        }
        functions.getHttpsCallable("adminGrant").call(data).await()
        Unit
    }

    /** Admin: change a member's role (member, senior_member, swamiji, admin). */
    suspend fun setRole(uid: String, role: String): Result<Unit> = runCatching {
        functions.getHttpsCallable("setRole").call(mapOf("uid" to uid, "role" to role)).await()
        Unit
    }
}
