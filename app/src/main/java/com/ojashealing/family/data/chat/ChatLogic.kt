package com.ojashealing.family.data.chat

import kotlin.math.sqrt

/** "typing" shows while the other side typed within this long. */
const val TYPING_FRESH_MS = 6_000L

/** How often the screen tells the other side that this side is typing. */
const val TYPING_PING_MS = 3_000L

/** Seen as online when the app was open within this long. */
const val ONLINE_WITHIN_MS = 2 * 60_000L

/** Bars in a voice note's waveform. */
const val WAVEFORM_BARS = 48

/** Loudest bar value; bars run 0..31. */
const val WAVEFORM_MAX = 31

/** A message of mine is seen once the other side has read at or after it was sent. */
fun isSeen(sentAt: Long, otherReadAt: Long): Boolean = sentAt > 0 && otherReadAt >= sentAt

fun isTyping(typingAt: Long, now: Long): Boolean = typingAt > 0 && now - typingAt in 0..TYPING_FRESH_MS

/** The unread count on my side of [thread]. */
fun unreadFor(thread: ChatThread, iAmStaff: Boolean): Int =
    if (iAmStaff) thread.unreadCountStaff else thread.unreadCountMember

/**
 * Turns raw microphone levels (MediaRecorder.maxAmplitude, 0..32767, about ten a second) into
 * [bars] bars of 0..[WAVEFORM_MAX]. A square root makes quiet speech still visible.
 */
fun downsample(levels: List<Int>, bars: Int = WAVEFORM_BARS): List<Int> {
    if (levels.isEmpty() || bars <= 0) return emptyList()
    val peak = levels.max().coerceAtLeast(1)
    return List(bars) { i ->
        val from = i * levels.size / bars
        val to = maxOf(from + 1, (i + 1) * levels.size / bars).coerceAtMost(levels.size)
        val v = levels.subList(from.coerceAtMost(levels.size - 1), to).max().toDouble() / peak
        (sqrt(v) * WAVEFORM_MAX).toInt().coerceIn(1, WAVEFORM_MAX)
    }
}

/** What to show under a name: online, last seen some minutes ago, or nothing known. */
sealed interface PresenceState {
    data object Online : PresenceState
    data class LastSeen(val minutesAgo: Long) : PresenceState
    data object Unknown : PresenceState
}

fun presenceOf(lastActiveAt: Long?, now: Long): PresenceState = when {
    lastActiveAt == null || lastActiveAt <= 0 -> PresenceState.Unknown
    now - lastActiveAt <= ONLINE_WITHIN_MS -> PresenceState.Online
    else -> PresenceState.LastSeen((now - lastActiveAt) / 60_000)
}
