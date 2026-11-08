package com.ojashealing.family.data.call

enum class CallPhase { NeedsMic, Calling, Incoming, Connecting, Connected, Reconnecting, Ended, Declined, Missed, Failed }

val CallPhase.isOver get() = this == CallPhase.Ended || this == CallPhase.Declined || this == CallPhase.Missed || this == CallPhase.Failed

data class CallUi(
    val phase: CallPhase,
    val peerName: String,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val video: Boolean = false,
    val cameraOff: Boolean = false,
    /** The other phone is ringing (it showed the call), so the caller sees "Ringing…" and hears ringback. */
    val delivered: Boolean = false,
    /** When the call connected on this phone, for the timer; 0 before. */
    val connectedAt: Long = 0,
    /** Where the voice comes out, and what else can be chosen. */
    val route: AudioRoute? = null,
    val routes: List<AudioRoute> = emptyList(),
    /** 3 good, 2 fair, 1 poor. */
    val quality: Int = 3
)

/** How the call screen was opened: placing a call, or answering one that is ringing. */
sealed interface CallStart {
    data class Outgoing(
        val calleeUid: String, val memberUid: String, val staffUid: String, val peerName: String,
        val options: CallOptions = CallOptions()
    ) : CallStart
    data class Incoming(val callId: String, val video: Boolean = false) : CallStart
}

/** The call a start belongs to, once known; an outgoing call has none until it is placed. */
val CallStart.knownCallId: String? get() = (this as? CallStart.Incoming)?.callId

/** "4:05" or "1:02:05" for the in-call timer. */
fun callClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Rings that have gone unanswered this long are given up on. */
const val RING_TIMEOUT_MS = 45_000L
