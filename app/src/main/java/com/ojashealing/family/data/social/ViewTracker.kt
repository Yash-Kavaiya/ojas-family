package com.ojashealing.family.data.social

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** "999", "1.2k", "12k", "1.5M": a view count short enough to sit beside the likes. */
fun shortCount(n: Long): String {
    fun one(v: Double, unit: String): String {
        val r = Math.floor(v * 10) / 10
        return (if (r >= 10 || r == Math.floor(r)) r.toLong().toString() else "%.1f".format(java.util.Locale.US, r)) + unit
    }
    return when {
        n < 1_000 -> n.coerceAtLeast(0).toString()
        n < 1_000_000 -> one(n / 1_000.0, "k")
        else -> one(n / 1_000_000.0, "M")
    }
}

/** What this phone already told the server it saw, so a view is sent only once. */
interface SeenStore {
    fun has(key: String): Boolean
    fun add(key: String)
}

class PrefsSeenStore(context: Context) : SeenStore {
    private val prefs = context.applicationContext.getSharedPreferences("views_sent", Context.MODE_PRIVATE)
    override fun has(key: String) = prefs.getBoolean(key, false)
    override fun add(key: String) { prefs.edit().putBoolean(key, true).apply() }
}

/**
 * Counts that a member saw a post, video or book: once per member per item, shown to everyone as
 * the item's viewCount. [send] writes "[path]/views/[uid]"; the rules refuse a second one, and a
 * function adds it to the count. A failed send (offline) is tried again the next time it is seen.
 */
class ViewTracker(
    private val uid: () -> String?,
    private val seen: SeenStore,
    private val send: suspend (path: String, uid: String) -> Unit,
) {
    suspend fun markViewed(path: String) {
        val me = uid() ?: return
        val key = "$me|$path"
        if (seen.has(key)) return
        runCatching { send(path, me) }.onSuccess { seen.add(key) }
    }

    companion object {
        fun firestore(db: FirebaseFirestore, uid: () -> String?, seen: SeenStore) = ViewTracker(uid, seen) { path, me ->
            // Already counted (e.g. on another phone): the rules refuse it, which is the same as done
            val result = runCatching { db.document("$path/views/$me").set(mapOf("createdAt" to FieldValue.serverTimestamp())).await() }
            val refused = (result.exceptionOrNull() as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
            if (result.isFailure && !refused) throw result.exceptionOrNull()!!
        }
    }
}
