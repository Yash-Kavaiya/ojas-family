package com.ojashealing.family.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

/** Waits between the tries of [retryBriefly]: about fifteen seconds in all. */
val LISTENER_RETRY_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L)

/**
 * A Firestore listener that fails is closed for good. Most failures pass on their own (the sign-in
 * token had not reached Firestore yet, App Check was still starting, a role just changed), so listen
 * again a few times before letting the error through to whoever handles it.
 */
fun <T> Flow<T>.retryBriefly(delaysMs: List<Long> = LISTENER_RETRY_DELAYS_MS): Flow<T> = retryWhen { _, attempt ->
    val wait = delaysMs.getOrNull(attempt.toInt()) ?: return@retryWhen false
    delay(wait)
    true
}
