package com.ojashealing.family.data.call

/** A disconnected call waits this long for the network to come back on its own before reconnecting. */
const val RESTART_AFTER_MS = 3_000L

/** Reconnecting gives up after this long. */
const val GIVE_UP_AFTER_MS = 30_000L

/** What the call's statistics say about the line, read every couple of seconds. */
data class LineStats(val roundTripMs: Double?, val lossPercent: Double?, val jitterMs: Double?)

/**
 * 3 = good, 2 = fair, 1 = poor. Voice holds up to about 300 ms round trip and a few percent loss;
 * beyond 600 ms or 10% loss people talk over each other and words drop.
 */
fun qualityFrom(s: LineStats): Int {
    val rtt = s.roundTripMs ?: 0.0
    val loss = s.lossPercent ?: 0.0
    val jitter = s.jitterMs ?: 0.0
    return when {
        rtt > 600 || loss > 10 || jitter > 100 -> 1
        rtt > 300 || loss > 3 || jitter > 40 -> 2
        else -> 3
    }
}

/** Loss over an interval from two readings of the cumulative counters; null when nothing arrived. */
fun lossPercent(lostBefore: Long, lostNow: Long, receivedBefore: Long, receivedNow: Long): Double? {
    val lost = (lostNow - lostBefore).coerceAtLeast(0)
    val got = (receivedNow - receivedBefore).coerceAtLeast(0)
    return if (lost + got == 0L) null else lost * 100.0 / (lost + got)
}

/**
 * Whether to repair the connection now. Only the caller sends a new offer (so the two phones never
 * both do). A failed connection is repaired at once; a disconnected one after [RESTART_AFTER_MS],
 * in case it comes back by itself; a network change (Wi-Fi to mobile data) at once.
 */
fun shouldRestart(isCaller: Boolean, failed: Boolean, disconnectedForMs: Long, networkChanged: Boolean): Boolean =
    isCaller && (failed || networkChanged || disconnectedForMs >= RESTART_AFTER_MS)
