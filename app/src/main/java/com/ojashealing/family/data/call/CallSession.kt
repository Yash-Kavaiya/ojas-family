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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Connection { Connecting, Connected, Reconnecting, Failed, Closed }

/**
 * One audio call between a member and Swamiji / a senior member, peer to peer over WebRTC.
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

    private suspend fun setUp() {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
        val adm = JavaAudioDeviceModule.builder(app).setUseHardwareAcousticEchoCanceler(true).setUseHardwareNoiseSuppressor(true).createAudioDeviceModule()
        val f = PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory()
        factory = f

        val ice = mutableListOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        repo.turnServer()?.let { t -> ice += PeerConnection.IceServer.builder(t.urls).setUsername(t.username).setPassword(t.credential).createIceServer() }
        val config = PeerConnection.RTCConfiguration(ice).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }

        pc = f.createPeerConnection(config, object : PeerConnection.Observer {
            // Candidates found before the call document exists are sent once it does
            override fun onIceCandidate(c: IceCandidate) {
                scope.launch {
                    val id = callId
                    if (id == null) pendingLocal += c else send(id, c)
                }
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                _connection.value = when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> Connection.Connected
                    PeerConnection.PeerConnectionState.DISCONNECTED -> Connection.Reconnecting
                    PeerConnection.PeerConnectionState.FAILED -> Connection.Failed
                    PeerConnection.PeerConnectionState.CLOSED -> Connection.Closed
                    else -> _connection.value
                }
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
            override fun onAddTrack(r: RtpReceiver, s: Array<out MediaStream>) {}
        }) ?: error("Could not start the call")

        val source = f.createAudioSource(MediaConstraints())
        micTrack = f.createAudioTrack("mic", source).also { pc!!.addTrack(it, listOf("ojas")) }

        audio.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private val pendingLocal = mutableListOf<IceCandidate>()

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
    suspend fun call(calleeUid: String, memberUid: String, staffUid: String): Result<String> = runCatching {
        setUp()
        val offer = pc!!.awaitCreate(offer = true)
        pc!!.awaitSet(offer, local = true)
        val id = repo.create(myUid, calleeUid, memberUid, staffUid, Sdp("offer", offer.description)).getOrThrow()
        listen(id)
        scope.launch {
            val answered = repo.observe(id).filterNotNull().first { it.answer != null || it.status.isOver }
            answered.answer?.let { setRemote(it) }
        }
        id
    }

    /** Answers an incoming call. */
    suspend fun answer(call: Call): Result<Unit> = runCatching {
        val offer = call.offer ?: error("Call has no offer")
        setUp()
        listen(call.id)
        setRemote(offer)
        val answer = pc!!.awaitCreate(offer = false)
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
        runCatching { pc?.close() }
        runCatching { pc?.dispose() }
        runCatching { factory?.dispose() }
        pc = null
        factory = null
        @Suppress("DEPRECATION")
        audio.isSpeakerphoneOn = false
        audio.mode = AudioManager.MODE_NORMAL
        _connection.value = Connection.Closed
        scope.cancel()
    }
}

private suspend fun PeerConnection.awaitCreate(offer: Boolean): SessionDescription = suspendCancellableCoroutine { cont ->
    val observer = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) { if (cont.isActive) cont.resume(sdp) }
        override fun onCreateFailure(error: String?) { if (cont.isActive) cont.resumeWithException(IllegalStateException(error)) }
        override fun onSetSuccess() {}
        override fun onSetFailure(error: String?) {}
    }
    val constraints = MediaConstraints().apply { mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")) }
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
