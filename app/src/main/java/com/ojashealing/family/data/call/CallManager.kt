package com.ojashealing.family.data.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ojashealing.family.push.CallService
import com.ojashealing.family.push.IncomingCallNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.VideoTrack

/**
 * The one call this phone is on, for the whole app. It lives here rather than in a screen so the
 * member can leave the call screen (to chat, or for another app) and the call carries on. Only Hang up,
 * Decline, the other side, or the ring timeout end it. It also plays the ringtone and ringback.
 */
class CallManager(context: Context, private val repo: CallRepository) {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _ui = MutableStateFlow<CallUi?>(null)
    /** Null when there is no call. */
    val ui: StateFlow<CallUi?> = _ui.asStateFlow()

    private val _session = MutableStateFlow<CallSession?>(null)
    /** The live WebRTC session, for its video tracks. */
    val session: StateFlow<CallSession?> = _session.asStateFlow()

    private var start: CallStart? = null
    private var myUid: String = ""
    private var nameOf: suspend (String) -> String? = { null }
    private var jobs = mutableListOf<Job>()
    private var begun = false

    /** The call id once known (always for an incoming call; after placing for an outgoing one). */
    val callId: String? get() = _session.value?.callId ?: start?.knownCallId

    val egl: EglBase? get() = _session.value?.egl
    val active: Boolean get() = _ui.value?.phase?.isOver == false

    /**
     * Opens a call. Returns false (and changes nothing) while another call is on. Opening the same
     * call again, for example from its notification, just shows it.
     */
    fun open(start: CallStart, myUid: String, nameOf: suspend (String) -> String?, fallbackName: String): Boolean {
        if (active) {
            val same = start is CallStart.Incoming && start.callId == callId
            return same || start == this.start
        }
        reset()
        this.start = start
        this.myUid = myUid
        this.nameOf = nameOf
        val video = when (start) { is CallStart.Outgoing -> start.options.video; is CallStart.Incoming -> start.video }
        _session.value = CallSession(app, repo, myUid).apply { this.video = video }
        _ui.value = CallUi(CallPhase.NeedsMic, (start as? CallStart.Outgoing)?.peerName ?: fallbackName, video = video, speaker = video)
        // Ringing shows at once, before the microphone is allowed
        if (start is CallStart.Incoming) {
            phase(CallPhase.Incoming)
            watchCall(start.callId)
        }
        return true
    }

    private fun phase(p: CallPhase) {
        _ui.update { it?.copy(phase = p) }
        sounds()
    }

    /** Called once the microphone is allowed. */
    fun begin() {
        val s = _session.value ?: return
        val st = start ?: return
        if (begun) return
        begun = true
        jobs += scope.launch {
            s.connection.collect { c ->
                if (!active) return@collect
                when (c) {
                    Connection.Connected -> {
                        if (_ui.value?.connectedAt == 0L) _ui.update { it?.copy(connectedAt = System.currentTimeMillis()) }
                        phase(CallPhase.Connected)
                        CallService.start(app, s.callId.orEmpty(), _ui.value?.peerName.orEmpty(), s.video)
                        if (s.video) s.setSpeaker(true)
                    }
                    Connection.Reconnecting -> phase(CallPhase.Reconnecting)
                    Connection.Failed -> finish(CallPhase.Failed, CallStatus.Ended)
                    else -> Unit
                }
            }
        }
        if (st is CallStart.Outgoing) jobs += scope.launch {
            phase(CallPhase.Calling)
            s.call(st.calleeUid, st.memberUid, st.staffUid, st.options)
                .onSuccess { id ->
                    watchCall(id)
                    // Nobody answered: stop ringing their phone
                    delay(RING_TIMEOUT_MS)
                    if (_ui.value?.phase == CallPhase.Calling) finish(CallPhase.Missed, CallStatus.Missed)
                }
                .onFailure { finish(CallPhase.Failed, null) }
        }
    }

    private fun watchCall(id: String) {
        jobs += scope.launch {
            repo.observe(id).filterNotNull().collect { call ->
                val other = if (call.callerUid == myUid) call.calleeUid else call.callerUid
                if (start is CallStart.Incoming) nameOf(other)?.takeIf { it.isNotBlank() }?.let { n -> _ui.update { it?.copy(peerName = n) } }
                if (call.video && _ui.value?.video == false) {
                    _session.value?.video = true
                    _ui.update { it?.copy(video = true, speaker = true) }
                }
                if (call.delivered && _ui.value?.delivered == false) {
                    _ui.update { it?.copy(delivered = true) }
                    sounds()
                }
                when (call.status) {
                    CallStatus.Active -> if (_ui.value?.phase == CallPhase.Calling) phase(CallPhase.Connecting)
                    CallStatus.Declined -> finish(CallPhase.Declined, null)
                    CallStatus.Missed -> finish(CallPhase.Missed, null)
                    CallStatus.Ended -> finish(CallPhase.Ended, null)
                    CallStatus.Ringing -> Unit
                }
            }
        }
        // The callee's phone never showed it either: give up like the caller would
        if (start is CallStart.Incoming) jobs += scope.launch {
            delay(RING_TIMEOUT_MS + 15_000)
            if (_ui.value?.phase == CallPhase.Incoming) finish(CallPhase.Missed, null)
        }
    }

    fun answer() {
        val s = start as? CallStart.Incoming ?: return
        val session = _session.value ?: return
        if (_ui.value?.phase != CallPhase.Incoming) return
        phase(CallPhase.Connecting)
        IncomingCallNotifier.cancel(app, s.callId)
        if (!begun) begin()
        jobs += scope.launch {
            val call = repo.observe(s.callId).filterNotNull().first()
            if (call.status != CallStatus.Ringing) return@launch finish(CallPhase.Missed, null)
            session.answer(call).onFailure { finish(CallPhase.Failed, CallStatus.Ended) }
        }
    }

    fun decline() = finish(CallPhase.Declined, CallStatus.Declined)

    fun hangUp() = finish(CallPhase.Ended, CallStatus.Ended)

    fun toggleMute() {
        val m = !(_ui.value?.muted ?: false)
        _session.value?.setMuted(m)
        _ui.update { it?.copy(muted = m) }
    }

    fun toggleCamera() {
        val off = !(_ui.value?.cameraOff ?: false)
        _session.value?.setCamera(!off)
        _ui.update { it?.copy(cameraOff = off) }
    }

    fun switchCamera() = _session.value?.switchCamera()

    fun toggleSpeaker() {
        val s = !(_ui.value?.speaker ?: false)
        _session.value?.setSpeaker(s)
        _ui.update { it?.copy(speaker = s) }
    }

    /** The call screen was closed after the call ended: forget it so the next call starts fresh. */
    fun dismiss() {
        if (!active) reset()
    }

    private fun finish(p: CallPhase, write: CallStatus?) {
        if (!active) return
        phase(p)
        val id = callId
        if (write != null && id != null) scope.launch { repo.setStatus(id, write) }
        id?.let { IncomingCallNotifier.cancel(app, it) }
        CallService.stop(app)
        _session.value?.close()
        jobs.forEach { it.cancel() }
        jobs.clear()
        sounds()
    }

    private fun reset() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        stopSounds()
        _session.value = null
        _ui.value = null
        start = null
        begun = false
    }

    // ---- Ringtone (incoming) and ringback (outgoing, once their phone rings) ----

    private var ringtone: Ringtone? = null
    private var ringback: ToneGenerator? = null
    private var vibrating = false

    private fun sounds() {
        val u = _ui.value
        val incoming = u?.phase == CallPhase.Incoming
        val ringingThem = u?.phase == CallPhase.Calling && u.delivered
        if (incoming) startRingtone() else stopRingtone()
        if (ringingThem) startRingback() else stopRingback()
    }

    private fun startRingtone() {
        if (ringtone?.isPlaying == true) return
        // The notification rang while the app was away; now the screen rings instead
        callId?.let { IncomingCallNotifier.cancel(app, it) }
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            ringtone = runCatching {
                RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
                    audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                    play()
                }
            }.getOrNull()
        }
        if (audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            vibrator()?.vibrate(VibrationEffect.createWaveform(RING_VIBRATION, 0))
            vibrating = true
        }
    }

    private fun stopRingtone() {
        runCatching { ringtone?.stop() }
        ringtone = null
        if (vibrating) { vibrator()?.cancel(); vibrating = false }
    }

    private fun startRingback() {
        if (ringback != null) return
        ringback = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70).apply { startTone(ToneGenerator.TONE_SUP_RINGTONE) } }.getOrNull()
    }

    private fun stopRingback() {
        runCatching { ringback?.stopTone(); ringback?.release() }
        ringback = null
    }

    private fun stopSounds() {
        stopRingtone()
        stopRingback()
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    /** The local and remote video of the live call, or null. */
    val localVideo: VideoTrack? get() = _session.value?.localVideo?.value
    val remoteVideo: VideoTrack? get() = _session.value?.remoteVideo?.value

    companion object {
        /** Ring pattern: wait, buzz, pause, buzz; repeats. */
        val RING_VIBRATION = longArrayOf(0, 800, 400, 800, 1200)
    }
}
