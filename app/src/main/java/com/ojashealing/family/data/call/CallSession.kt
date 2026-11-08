package com.ojashealing.family.data.call

import android.content.Context
import android.media.AudioManager
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Connection { Connecting, Connected, Reconnecting, Failed, Closed }

/**
 * One audio or video call between a member and Swamiji / a senior member, peer to peer over WebRTC.
 * Offer, answer and ICE candidates travel through Firestore (CallRepository); audio never touches a server
 * unless a TURN relay is configured.
 */
class CallSession(context: Context, private val repo: CallRepository, private val myUid: String) {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _connection = MutableStateFlow(Connection.Connecting)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var micTrack: AudioTrack? = null
    private var candidates: ListenerRegistration? = null
    private val pendingRemote = mutableListOf<IceCandidate>()
    private var remoteSet = false
    var callId: String? = null
        private set

    // ---- Video (only for video calls) ----
    /** Set before the call starts: true for a video call. */
    var video: Boolean = false
    /** Shared GL context for the camera and both video views. */
    val egl: EglBase by lazy { EglBase.create() }
    private var capturer: VideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo.asStateFlow()
    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()

    private suspend fun setUp() {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
        val adm = JavaAudioDeviceModule.builder(app).setUseHardwareAcousticEchoCanceler(true).setUseHardwareNoiseSuppressor(true).createAudioDeviceModule()
        val builder = PeerConnectionFactory.builder().setAudioDeviceModule(adm)
        if (video) {
            builder.setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
        }
        val f = builder.createPeerConnectionFactory()
        factory = f

        val ice = mutableListOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        repo.turnServers().forEach { t -> ice += PeerConnection.IceServer.builder(t.urls).setUsername(t.username).setPassword(t.credential).createIceServer() }
        val config = PeerConnection.RTCConfiguration(ice).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // Keep gathering so a new network (Wi-Fi to mobile data) gets candidates without waiting
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        watchNetwork()

        pc = f.createPeerConnection(config, object : PeerConnection.Observer {
            // Candidates found before the call document exists are sent once it does
            override fun onIceCandidate(c: IceCandidate) {
                scope.launch {
                    val id = callId
                    if (id == null) pendingLocal += c else send(id, c)
                }
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                scope.launch { onState(state) }
            }
            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
            override fun onAddStream(s: MediaStream) {}
            override fun onRemoveStream(s: MediaStream) {}
            override fun onDataChannel(d: org.webrtc.DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver, s: Array<out MediaStream>) {
                (r.track() as? VideoTrack)?.let { t -> scope.launch { _remoteVideo.value = t } }
            }
        }) ?: error("Could not start the call")

        val source = f.createAudioSource(MediaConstraints())
        micTrack = f.createAudioTrack("mic", source).also { pc!!.addTrack(it, listOf("ojas")) }
        if (video) startCamera(f)

        audio.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private val pendingLocal = mutableListOf<IceCandidate>()

    // ---- Repairing the connection (ICE restart) ----
    private var isCaller = false
    private var restarts = 0
    private var answeredRestart = 0
    private var lostAt = 0L
    private var repairJob: kotlinx.coroutines.Job? = null
    private var giveUpJob: kotlinx.coroutines.Job? = null
    private var network: android.net.ConnectivityManager.NetworkCallback? = null

    private fun onState(state: PeerConnection.PeerConnectionState) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                lostAt = 0
                repairJob?.cancel(); giveUpJob?.cancel()
                _connection.value = Connection.Connected
            }
            PeerConnection.PeerConnectionState.DISCONNECTED, PeerConnection.PeerConnectionState.FAILED -> {
                // Only after it had connected; before that the ring timeout handles it
                if (_connection.value != Connection.Connected && _connection.value != Connection.Reconnecting) {
                    if (state == PeerConnection.PeerConnectionState.FAILED) _connection.value = Connection.Failed
                    return
                }
                if (lostAt == 0L) lostAt = System.currentTimeMillis()
                _connection.value = Connection.Reconnecting
                val failed = state == PeerConnection.PeerConnectionState.FAILED
                repairJob?.cancel()
                repairJob = scope.launch {
                    if (!failed) kotlinx.coroutines.delay(RESTART_AFTER_MS)
                    if (_connection.value == Connection.Reconnecting &&
                        shouldRestart(isCaller, failed, System.currentTimeMillis() - lostAt, networkChanged = false)) restartIce()
                }
                if (giveUpJob?.isActive != true) giveUpJob = scope.launch {
                    kotlinx.coroutines.delay(GIVE_UP_AFTER_MS)
                    if (_connection.value == Connection.Reconnecting) _connection.value = Connection.Failed
                }
            }
            PeerConnection.PeerConnectionState.CLOSED -> _connection.value = Connection.Closed
            else -> Unit
        }
    }

    /** A new network (Wi-Fi to mobile data, or back): repair at once rather than waiting to drop. */
    private fun watchNetwork() {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        var first = true
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: android.net.Network) {
                if (first) { first = false; return }
                scope.launch {
                    val live = _connection.value == Connection.Connected || _connection.value == Connection.Reconnecting
                    if (live && shouldRestart(isCaller, failed = false, disconnectedForMs = 0, networkChanged = true)) restartIce()
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb); network = cb }
    }

    /** Caller: a fresh offer with new network paths; the callee answers it through the call document. */
    private suspend fun restartIce() {
        val id = callId ?: return
        val p = pc ?: return
        runCatching {
            val offer = p.awaitCreate(offer = true, video = video, iceRestart = true)
            p.awaitSet(offer, local = true)
            restarts++
            repo.sendRestartOffer(id, restarts, offer.description)
        }
    }

    /** Both sides watch the call for the other's repair messages. */
    private fun watchRestarts(id: String) {
        scope.launch {
            repo.observe(id).filterNotNull().collect { c ->
                val p = pc ?: return@collect
                if (!isCaller) c.restartOffer?.takeIf { it.n > answeredRestart }?.let { o ->
                    answeredRestart = o.n
                    runCatching {
                        p.awaitSet(SessionDescription(SessionDescription.Type.OFFER, o.sdp), local = false)
                        val answer = p.awaitCreate(offer = false, video = video)
                        p.awaitSet(answer, local = true)
                        repo.sendRestartAnswer(id, o.n, answer.description)
                    }
                }
                if (isCaller) c.restartAnswer?.takeIf { it.n == restarts && it.n > answeredRestart }?.let { a ->
                    answeredRestart = a.n
                    runCatching { p.awaitSet(SessionDescription(SessionDescription.Type.ANSWER, a.sdp), local = false) }
                }
            }
        }
    }

    /** Round trip, loss and jitter since the last reading, for the quality bars. */
    private var lastLost = 0L
    private var lastReceived = 0L

    suspend fun lineStats(): LineStats? {
        val p = pc ?: return null
        val report = suspendCancellableCoroutine<org.webrtc.RTCStatsReport?> { cont -> p.getStats { r -> if (cont.isActive) cont.resume(r) } } ?: return null
        var rtt: Double? = null
        var lost = 0L
        var received = 0L
        var jitter: Double? = null
        for (s in report.statsMap.values) {
            val m = s.members
            when (s.type) {
                "candidate-pair" -> if (m["nominated"] == true || m["state"] == "succeeded") (m["currentRoundTripTime"] as? Number)?.let { rtt = it.toDouble() * 1000 }
                "inbound-rtp" -> if (m["kind"] == "audio") {
                    lost += (m["packetsLost"] as? Number)?.toLong() ?: 0
                    received += (m["packetsReceived"] as? Number)?.toLong() ?: 0
                    (m["jitter"] as? Number)?.let { jitter = it.toDouble() * 1000 }
                }
            }
        }
        val loss = lossPercent(lastLost, lost, lastReceived, received)
        lastLost = lost; lastReceived = received
        return LineStats(rtt, loss, jitter)
    }

    /** Front camera at 640x480, 24 fps: clear enough for a face, light on mobile data. */
    private fun startCamera(f: PeerConnectionFactory) {
        val e = Camera2Enumerator(app)
        val name = e.deviceNames.firstOrNull { e.isFrontFacing(it) } ?: e.deviceNames.firstOrNull() ?: return
        val cap = e.createCapturer(name, null) ?: return
        val helper = SurfaceTextureHelper.create("ojasCamera", egl.eglBaseContext)
        val src = f.createVideoSource(false)
        cap.initialize(helper, app, src.capturerObserver)
        cap.startCapture(640, 480, 24)
        val track = f.createVideoTrack("cam", src)
        pc!!.addTrack(track, listOf("ojas"))
        capturer = cap; textureHelper = helper; videoSource = src
        _localVideo.value = track
    }

    fun setCamera(on: Boolean) {
        _localVideo.value?.setEnabled(on)
    }

    fun switchCamera() {
        (capturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    private fun send(id: String, c: IceCandidate) {
        scope.launch { repo.addCandidate(id, IceCandidateData(myUid, c.sdpMid.orEmpty(), c.sdpMLineIndex, c.sdp)) }
    }

    private fun listen(id: String) {
        callId = id
        pendingLocal.forEach { send(id, it) }
        pendingLocal.clear()
        candidates = repo.listenCandidates(id, myUid) { c ->
            val ice = IceCandidate(c.sdpMid, c.sdpMLineIndex, c.candidate)
            if (remoteSet) pc?.addIceCandidate(ice) else pendingRemote += ice
        }
    }

    private suspend fun setRemote(sdp: Sdp) {
        val type = if (sdp.type == "offer") SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
        pc!!.awaitSet(SessionDescription(type, sdp.sdp), local = false)
        remoteSet = true
        pendingRemote.forEach { pc?.addIceCandidate(it) }
        pendingRemote.clear()
    }

    /** Rings [calleeUid]; returns the call id once the call document exists. */
    suspend fun call(calleeUid: String, memberUid: String, staffUid: String, options: CallOptions = CallOptions()): Result<String> = runCatching {
        video = options.video
        setUp()
        val offer = pc!!.awaitCreate(offer = true, video = video)
        pc!!.awaitSet(offer, local = true)
        val id = repo.create(myUid, calleeUid, memberUid, staffUid, Sdp("offer", offer.description), options).getOrThrow()
        isCaller = true
        listen(id)
        watchRestarts(id)
        scope.launch {
            val answered = repo.observe(id).filterNotNull().first { it.answer != null || it.status.isOver }
            answered.answer?.let { setRemote(it) }
        }
        id
    }

    /** Answers an incoming call. */
    suspend fun answer(call: Call): Result<Unit> = runCatching {
        val offer = call.offer ?: error("Call has no offer")
        video = call.video
        setUp()
        listen(call.id)
        watchRestarts(call.id)
        setRemote(offer)
        val answer = pc!!.awaitCreate(offer = false, video = video)
        pc!!.awaitSet(answer, local = true)
        repo.answer(call.id, Sdp("answer", answer.description)).getOrThrow()
    }

    fun setMuted(muted: Boolean) {
        micTrack?.setEnabled(!muted)
    }

    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean) {
        audio.isSpeakerphoneOn = on
    }

    fun close() {
        candidates?.remove()
        repairJob?.cancel(); giveUpJob?.cancel()
        network?.let { cb -> runCatching { (app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(cb) } }
        network = null
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { textureHelper?.dispose() }
        capturer = null; videoSource = null; textureHelper = null
        _localVideo.value = null
        _remoteVideo.value = null
        runCatching { pc?.close() }
        runCatching { pc?.dispose() }
        runCatching { factory?.dispose() }
        pc = null
        factory = null
        @Suppress("DEPRECATION")
        audio.isSpeakerphoneOn = false
        audio.mode = AudioManager.MODE_NORMAL
        _connection.value = Connection.Closed
        if (video) runCatching { egl.release() }
        scope.cancel()
    }
}

private suspend fun PeerConnection.awaitCreate(offer: Boolean, video: Boolean = false, iceRestart: Boolean = false): SessionDescription = suspendCancellableCoroutine { cont ->
    val observer = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) { if (cont.isActive) cont.resume(sdp) }
        override fun onCreateFailure(error: String?) { if (cont.isActive) cont.resumeWithException(IllegalStateException(error)) }
        override fun onSetSuccess() {}
        override fun onSetFailure(error: String?) {}
    }
    val constraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (video) "true" else "false"))
        if (iceRestart) mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
    }
    if (offer) createOffer(observer, constraints) else createAnswer(observer, constraints)
}

private suspend fun PeerConnection.awaitSet(sdp: SessionDescription, local: Boolean): Unit = suspendCancellableCoroutine { cont ->
    val observer = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
        override fun onSetFailure(error: String?) { if (cont.isActive) cont.resumeWithException(IllegalStateException(error)) }
    }
    if (local) setLocalDescription(observer, sdp) else setRemoteDescription(observer, sdp)
}
