package com.ojashealing.family.data.paging

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * A live first page plus older pages loaded one at a time. The snapshot listener
 * never sees more than [pageSize] documents. Older pages are one-shot reads.
 */
class LiveTailPager<T>(
    private val pageSize: Int = FEED_PAGE_SIZE,
    private val idOf: (T) -> String,
) {
    private val head = MutableStateFlow<List<T>>(emptyList())
    private val older = MutableStateFlow<List<T>>(emptyList())
    val hasMore = MutableStateFlow(false)
    private val gate = Mutex()
    private var tail: DocumentSnapshot? = null
    private var query: Query? = null
    private var mapper: ((DocumentSnapshot) -> T)? = null

    fun observe(source: Query, map: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        query = source
        mapper = map
        older.value = emptyList()
        tail = null
        hasMore.value = false
        var registration: ListenerRegistration? = null
        registration = source.limit(pageSize.toLong()).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            val page = snap.documents.map(map)
            head.value = page
            if (older.value.isEmpty()) {
                tail = snap.documents.lastOrNull()
                hasMore.value = snap.size() >= pageSize
            }
            trySend(merged(page, older.value))
        }
        val job = launch {
            older.collect { trySend(merged(head.value, it)) }
        }
        awaitClose {
            job.cancel()
            registration?.remove()
        }
    }

    /**
     * Loads the next older page. A failed read (offline, or the listener's query is no longer allowed)
     * changes nothing, so scrolling to the end again retries; it must never crash the screen.
     */
    suspend fun loadMore() {
        val source = query ?: return
        val map = mapper ?: return
        if (!hasMore.value) return
        gate.withLock {
            // Read inside the lock: a second call waiting here continues from the page the first one loaded
            val cursor = tail ?: return
            if (!hasMore.value) return
            val snap = try {
                source.startAfter(cursor).limit(pageSize.toLong()).get().await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return
            }
            val incoming = snap.documents.map(map)
            val shown = (head.value + older.value).map(idOf).toSet()
            val page = nextPage(shown, incoming, pageSize, idOf)
            if (page.added.isNotEmpty()) older.value = older.value + page.added
            tail = snap.documents.lastOrNull() ?: cursor
            hasMore.value = snap.size() >= pageSize
        }
    }

    private fun merged(headItems: List<T>, olderItems: List<T>): List<T> {
        if (olderItems.isEmpty()) return headItems
        val page = nextPage(headItems.map(idOf).toSet(), olderItems, olderItems.size.coerceAtLeast(1), idOf)
        return headItems + page.added
    }
}
