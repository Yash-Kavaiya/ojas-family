package com.ojashealing.family.ui.ask

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.R
import com.ojashealing.family.data.call.CallRepository
import com.ojashealing.family.data.call.CallSession
import com.ojashealing.family.data.call.CallStatus
import com.ojashealing.family.data.call.Connection
import com.ojashealing.family.push.CallService
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CallPhase { NeedsMic, Calling, Incoming, Connecting, Connected, Reconnecting, Ended, Declined, Missed, Failed }

data class CallUi(
    val phase: CallPhase,
    val peerName: String,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val video: Boolean = false,
    val cameraOff: Boolean = false
)

/** How the call screen was opened: placing a call, or answering one that is ringing. */
sealed interface CallStart {
    data class Outgoing(
        val calleeUid: String, val memberUid: String, val staffUid: String, val peerName: String,
        val options: com.ojashealing.family.data.call.CallOptions = com.ojashealing.family.data.call.CallOptions()
    ) : CallStart
    data class Incoming(val callId: String, val video: Boolean = false) : CallStart
}

class CallViewModel(
    private val context: Context,
    private val repo: CallRepository,
    private val myUid: String,
    private val start: CallStart,
    private val nameOf: suspend (uid: String) -> String?,
    fallbackName: String
) : ViewModel() {

    private val session = CallSession(context, repo, myUid).apply {
        video = when (start) { is CallStart.Outgoing -> start.options.video; is CallStart.Incoming -> start.video }
    }
    private val _ui = MutableStateFlow(
        CallUi(CallPhase.NeedsMic, (start as? CallStart.Outgoing)?.peerName ?: fallbackName, video = session.video, speaker = session.video)
    )
    val egl get() = session.egl
    val localVideo = session.localVideo
    val remoteVideo = session.remoteVideo
    val ui: StateFlow<CallUi> = _ui.asStateFlow()
    private var watch: Job? = null
    private var started = false

    private fun phase(p: CallPhase) = _ui.update { it.copy(phase = p) }
    private val over get() = _ui.value.phase in setOf(CallPhase.Ended, CallPhase.Declined, CallPhase.Missed, CallPhase.Failed)

    /** Called once the microphone is allowed. */
    fun begin() {
        if (started) return
        started = true
        viewModelScope.launch {
            session.connection.collect { c ->
                if (over) return@collect
                when (c) {
                    Connection.Connected -> {
                        phase(CallPhase.Connected)
                        CallService.start(context, session.callId.orEmpty(), _ui.value.peerName, session.video)
                        if (session.video) session.setSpeaker(true)
                    }
                    Connection.Reconnecting -> phase(CallPhase.Reconnecting)
                    Connection.Failed -> finish(CallPhase.Failed, CallStatus.Ended)
                    else -> Unit
                }
            }
        }
        when (start) {
            is CallStart.Outgoing -> viewModelScope.launch {
                phase(CallPhase.Calling)
                session.call(start.calleeUid, start.memberUid, start.staffUid, start.options)
                    .onSuccess { id ->
                        watchCall(id)
                        // Nobody answered in 45 s: stop ringing their phone
                        delay(45_000)
                        if (_ui.value.phase == CallPhase.Calling) finish(CallPhase.Missed, CallStatus.Missed)
                    }
                    .onFailure { phase(CallPhase.Failed) }
            }
            is CallStart.Incoming -> {
                phase(CallPhase.Incoming)
                watchCall(start.callId)
            }
        }
    }

    private fun watchCall(id: String) {
        watch?.cancel()
        watch = viewModelScope.launch {
            repo.observe(id).filterNotNull().collect { call ->
                val other = if (call.callerUid == myUid) call.calleeUid else call.callerUid
                if (start is CallStart.Incoming) nameOf(other)?.takeIf { it.isNotBlank() }?.let { n -> _ui.update { it.copy(peerName = n) } }
                if (call.video && !_ui.value.video) _ui.update { it.copy(video = true, speaker = true) }
                when (call.status) {
                    CallStatus.Active -> if (_ui.value.phase == CallPhase.Calling) phase(CallPhase.Connecting)
                    CallStatus.Declined -> finish(CallPhase.Declined, null)
                    CallStatus.Missed -> finish(CallPhase.Missed, null)
                    CallStatus.Ended -> finish(CallPhase.Ended, null)
                    CallStatus.Ringing -> Unit
                }
            }
        }
    }

    fun answer() {
        val s = start as? CallStart.Incoming ?: return
        if (_ui.value.phase != CallPhase.Incoming) return
        phase(CallPhase.Connecting)
        viewModelScope.launch {
            val call = repo.observe(s.callId).filterNotNull().first()
            if (call.status != CallStatus.Ringing) return@launch finish(CallPhase.Missed, null)
            session.answer(call).onFailure { finish(CallPhase.Failed, CallStatus.Ended) }
        }
    }

    fun decline() = finish(CallPhase.Declined, CallStatus.Declined)

    fun hangUp() = finish(CallPhase.Ended, CallStatus.Ended)

    fun toggleMute() {
        val m = !_ui.value.muted
        session.setMuted(m)
        _ui.update { it.copy(muted = m) }
    }

    fun toggleCamera() {
        val off = !_ui.value.cameraOff
        session.setCamera(!off)
        _ui.update { it.copy(cameraOff = off) }
    }

    fun switchCamera() = session.switchCamera()

    fun toggleSpeaker() {
        val s = !_ui.value.speaker
        session.setSpeaker(s)
        _ui.update { it.copy(speaker = s) }
    }

    private fun finish(p: CallPhase, write: CallStatus?) {
        if (over) return
        phase(p)
        val id = session.callId ?: (start as? CallStart.Incoming)?.callId
        if (write != null && id != null) viewModelScope.launch { repo.setStatus(id, write) }
        CallService.stop(context)
        session.close()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        if (!over) {
            val id = session.callId ?: (start as? CallStart.Incoming)?.callId
            // The screen is gone: end the call so the other phone stops ringing or talking to nobody
            if (id != null) GlobalScope.launch { repo.setStatus(id, if (_ui.value.phase == CallPhase.Incoming) CallStatus.Declined else CallStatus.Ended) }
            CallService.stop(context)
            session.close()
        }
    }
}

@Composable
fun CallScreen(vm: CallViewModel, onClose: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val needed = remember(ui.video) {
        if (ui.video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
    }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) vm.begin()
    }
    LaunchedEffect(Unit) {
        if (needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) vm.begin()
        else perms.launch(needed)
    }
    // A video call opened from a notification learns it is video a moment later: ask for the camera then
    LaunchedEffect(ui.video) {
        if (ui.video && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) perms.launch(needed)
    }
    val remote by vm.remoteVideo.collectAsStateWithLifecycle()
    val local by vm.localVideo.collectAsStateWithLifecycle()
    val ended = ui.phase in setOf(CallPhase.Ended, CallPhase.Declined, CallPhase.Missed, CallPhase.Failed)
    LaunchedEffect(ended) { if (ended) { delay(1_500); onClose() } }

    val showVideo = ui.video && (ui.phase == CallPhase.Connected || ui.phase == CallPhase.Reconnecting || ui.phase == CallPhase.Connecting)
    Surface(color = if (showVideo) Color.Black else MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        if (showVideo) Box(Modifier.fillMaxSize()) {
            remote?.let { VideoView(it, vm.egl, mirror = false, modifier = Modifier.fillMaxSize()) }
            local?.takeIf { !ui.cameraOff }?.let {
                VideoView(
                    it, vm.egl, mirror = true,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(Spacing.l).align(Alignment.TopEnd)
                        .size(width = 110.dp, height = 150.dp).clip(RoundedCornerShape(16.dp))
                )
            }
        }
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Spacer(Modifier.height(if (showVideo) Spacing.s else Spacing.xxl))
                if (!showVideo) {
                    Icon(
                        if (ui.video) Icons.Outlined.Videocam else Icons.Outlined.SelfImprovement,
                        contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    ui.peerName,
                    style = if (showVideo) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall,
                    color = if (showVideo) Color.White else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(
                        when (ui.phase) {
                            CallPhase.NeedsMic -> R.string.call_mic_needed
                            CallPhase.Calling -> R.string.call_calling
                            CallPhase.Incoming -> R.string.call_incoming
                            CallPhase.Connecting -> R.string.call_connecting
                            CallPhase.Connected -> R.string.call_connected
                            CallPhase.Reconnecting -> R.string.call_reconnecting
                            CallPhase.Ended -> R.string.call_ended
                            CallPhase.Declined -> R.string.call_declined
                            CallPhase.Missed -> R.string.call_missed
                            CallPhase.Failed -> R.string.call_failed
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
            if (!ended) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                if (ui.phase == CallPhase.Incoming) {
                    RoundButton(Icons.Filled.CallEnd, stringResource(R.string.call_decline), Color(0xFFC62828), vm::decline)
                    RoundButton(Icons.Filled.Call, stringResource(R.string.call_answer), Color(0xFF2E7D32), vm::answer)
                } else if (ui.phase != CallPhase.NeedsMic) {
                    FilledTonalIconToggleButton(checked = ui.muted, onCheckedChange = { vm.toggleMute() }, modifier = Modifier.size(56.dp)) {
                        Icon(if (ui.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, contentDescription = stringResource(R.string.call_mute))
                    }
                    RoundButton(Icons.Filled.CallEnd, stringResource(R.string.call_hang_up), Color(0xFFC62828), vm::hangUp)
                    if (ui.video) {
                        FilledTonalIconToggleButton(checked = ui.cameraOff, onCheckedChange = { vm.toggleCamera() }, modifier = Modifier.size(56.dp)) {
                            Icon(if (ui.cameraOff) Icons.Outlined.VideocamOff else Icons.Outlined.Videocam, contentDescription = stringResource(R.string.call_camera))
                        }
                        FilledTonalIconToggleButton(checked = false, onCheckedChange = { vm.switchCamera() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.Cameraswitch, contentDescription = stringResource(R.string.call_switch_camera))
                        }
                    } else {
                        FilledTonalIconToggleButton(checked = ui.speaker, onCheckedChange = { vm.toggleSpeaker() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.VolumeUp, contentDescription = stringResource(R.string.call_speaker))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp).background(color, CircleShape),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White)
    ) { Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp)) }
}

/** One WebRTC video track drawn in a SurfaceViewRenderer; the renderer is released when it leaves. */
@Composable
private fun VideoView(track: org.webrtc.VideoTrack, egl: org.webrtc.EglBase, mirror: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val renderer = remember(track) {
        org.webrtc.SurfaceViewRenderer(context).apply {
            init(egl.eglBaseContext, null)
            setMirror(mirror)
            setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
        }
    }
    androidx.compose.runtime.DisposableEffect(track, renderer) {
        track.addSink(renderer)
        onDispose {
            runCatching { track.removeSink(renderer) }
            runCatching { renderer.release() }
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(factory = { renderer }, modifier = modifier)
}
