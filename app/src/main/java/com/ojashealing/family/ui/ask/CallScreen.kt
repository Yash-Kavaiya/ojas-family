package com.ojashealing.family.ui.ask

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import com.ojashealing.family.data.call.callClock
import com.ojashealing.family.data.call.CallUi
import com.ojashealing.family.data.call.CallStart
import com.ojashealing.family.data.call.CallPhase
import com.ojashealing.family.data.call.CallManager
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import android.os.Build
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

/**
 * The call screen's view of the app's one call ([CallManager]). Closing the screen does not end the
 * call; only Hang up, Decline, the other side or the ring timeout do.
 */
class CallViewModel(
    private val manager: CallManager,
    /** Null: back to the call already on (from the ongoing-call bar). */
    start: CallStart?,
    myUid: String,
    nameOf: suspend (uid: String) -> String?,
    fallbackName: String,
    /** Opened from the notification's Answer button: answer as soon as the microphone is allowed. */
    val autoAnswer: Boolean = false
) : ViewModel() {

    /** False when another call is already on; the screen then shows that call. */
    val opened = start?.let { manager.open(it, myUid, nameOf, fallbackName) } ?: manager.active
    val ui: StateFlow<CallUi?> = manager.ui
    val session = manager.session

    fun begin() = manager.begin()
    fun answer() = manager.answer()
    fun decline() = manager.decline()
    fun hangUp() = manager.hangUp()
    fun toggleMute() = manager.toggleMute()
    fun toggleCamera() = manager.toggleCamera()
    fun switchCamera() = manager.switchCamera()
    fun toggleSpeaker() = manager.toggleSpeaker()
    fun selectRoute(route: com.ojashealing.family.data.call.AudioRoute) = manager.selectRoute(route)

    override fun onCleared() {
        manager.dismiss()
    }
}

@Composable
fun CallScreen(vm: CallViewModel, onClose: () -> Unit, onMinimise: () -> Unit = onClose) {
    val maybeUi by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Nothing to show (the call was cleared): leave
    val ui = maybeUi ?: run { LaunchedEffect(Unit) { onClose() }; return }
    val session by vm.session.collectAsStateWithLifecycle()

    // Picture-in-picture: a video call keeps showing in a small window when leaving the app
    val activity = context as? android.app.Activity
    var inPip by remember { mutableStateOf(false) }
    val componentActivity = activity as? androidx.activity.ComponentActivity
    DisposableEffect(componentActivity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { componentActivity?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    val pipReady = ui.video && (ui.phase == CallPhase.Connected || ui.phase == CallPhase.Reconnecting)
    DisposableEffect(activity, pipReady) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching {
            activity?.setPictureInPictureParams(
                android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(9, 16)).setAutoEnterEnabled(pipReady).build()
            )
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching {
                activity?.setPictureInPictureParams(android.app.PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
            }
        }
    }

    // Only while a call is on screen may the app show over the lock screen and wake it
    DisposableEffect(activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { activity?.setShowWhenLocked(true); activity?.setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") activity?.window?.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { activity?.setShowWhenLocked(false); activity?.setTurnScreenOn(false) }
            else @Suppress("DEPRECATION") activity?.window?.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }
    val needed = remember(ui.video) {
        if (ui.video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
    }
    var micOk by remember { mutableStateOf(false) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) { micOk = true; vm.begin() }
    }
    LaunchedEffect(Unit) {
        if (needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) { micOk = true; vm.begin() }
        else perms.launch(needed)
    }
    // Answer tapped on the notification: answer as soon as the microphone may be used
    LaunchedEffect(micOk, ui.phase) {
        if (vm.autoAnswer && micOk && ui.phase == CallPhase.Incoming) vm.answer()
    }
    // The call timer
    val now by produceState(System.currentTimeMillis(), ui.connectedAt) { while (true) { value = System.currentTimeMillis(); delay(1_000) } }
    // A video call opened from a notification learns it is video a moment later: ask for the camera then
    LaunchedEffect(ui.video) {
        if (ui.video && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) perms.launch(needed)
    }
    val remote = session?.remoteVideo?.collectAsStateWithLifecycle()?.value
    val local = session?.localVideo?.collectAsStateWithLifecycle()?.value
    val egl = session?.egl
    val ended = ui.phase in setOf(CallPhase.Ended, CallPhase.Declined, CallPhase.Missed, CallPhase.Failed)
    LaunchedEffect(ended) { if (ended) { delay(1_500); onClose() } }

    val showVideo = ui.video && (ui.phase == CallPhase.Connected || ui.phase == CallPhase.Reconnecting || ui.phase == CallPhase.Connecting)
    if (inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (egl != null) remote?.let { VideoView(it, egl, mirror = false, modifier = Modifier.fillMaxSize()) }
        }
        return
    }
    var routing by remember { mutableStateOf(false) }
    Surface(color = if (showVideo) Color.Black else MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        if (showVideo) Box(Modifier.fillMaxSize()) {
            if (egl != null) remote?.let { VideoView(it, egl, mirror = false, modifier = Modifier.fillMaxSize()) }
            if (egl != null) local?.takeIf { !ui.cameraOff }?.let {
                VideoView(
                    it, egl, mirror = true,
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
                // Leave the screen and keep talking: the bar at the top of the app brings you back
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!ended && ui.phase != CallPhase.Incoming) {
                        IconButton(onClick = onMinimise) {
                            Icon(
                                Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.call_minimise),
                                tint = if (showVideo) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (ui.phase == CallPhase.Connected || ui.phase == CallPhase.Reconnecting) QualityBars(ui.quality, light = showVideo)
                }
                Spacer(Modifier.height(if (showVideo) 0.dp else Spacing.xl))
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
                val connectedFor = if (ui.connectedAt > 0) callClock(now - ui.connectedAt) else null
                Text(
                    if (ui.phase == CallPhase.Connected && connectedFor != null) connectedFor else stringResource(
                        when (ui.phase) {
                            CallPhase.NeedsMic -> R.string.call_mic_needed
                            CallPhase.Calling -> if (ui.delivered) R.string.call_ringing else R.string.call_calling
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
                if (ui.phase == CallPhase.Connected && ui.quality == 1) {
                    Text(stringResource(R.string.call_poor_connection), style = MaterialTheme.typography.labelMedium, color = Color(0xFFE65100))
                }
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
                    }
                    // Speaker / earpiece, or a menu when a Bluetooth or wired headset is there too
                    Box {
                        val many = ui.routes.size > 2
                        FilledTonalIconToggleButton(
                            checked = ui.route == com.ojashealing.family.data.call.AudioRoute.Speaker,
                            onCheckedChange = { if (many) routing = true else vm.toggleSpeaker() },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(routeIcon(ui.route), contentDescription = stringResource(if (many) R.string.call_audio_route else R.string.call_speaker))
                        }
                        androidx.compose.material3.DropdownMenu(expanded = routing, onDismissRequest = { routing = false }) {
                            ui.routes.forEach { r ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(routeLabel(r))) },
                                    leadingIcon = { Icon(routeIcon(r), contentDescription = null) },
                                    trailingIcon = { if (r == ui.route) Icon(Icons.Outlined.Check, contentDescription = null) },
                                    onClick = { routing = false; vm.selectRoute(r) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun routeIcon(r: com.ojashealing.family.data.call.AudioRoute?) = when (r) {
    com.ojashealing.family.data.call.AudioRoute.Speaker -> Icons.Outlined.VolumeUp
    com.ojashealing.family.data.call.AudioRoute.Bluetooth -> Icons.Outlined.Bluetooth
    com.ojashealing.family.data.call.AudioRoute.Wired -> Icons.Outlined.Headphones
    else -> Icons.Outlined.PhoneInTalk
}

private fun routeLabel(r: com.ojashealing.family.data.call.AudioRoute) = when (r) {
    com.ojashealing.family.data.call.AudioRoute.Earpiece -> R.string.call_route_earpiece
    com.ojashealing.family.data.call.AudioRoute.Speaker -> R.string.call_route_speaker
    com.ojashealing.family.data.call.AudioRoute.Bluetooth -> R.string.call_route_bluetooth
    com.ojashealing.family.data.call.AudioRoute.Wired -> R.string.call_route_wired
}

/** Three bars, like a phone's signal: green good, amber fair, red poor. */
@Composable
private fun QualityBars(quality: Int, light: Boolean) {
    val on = when (quality) { 3 -> Color(0xFF43A047); 2 -> Color(0xFFF9A825); else -> Color(0xFFE53935) }
    val off = if (light) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline
    val label = stringResource(R.string.call_quality, quality)
    Row(
        Modifier.padding(end = Spacing.s).semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom
    ) {
        (1..3).forEach { i -> Box(Modifier.size(width = 4.dp, height = (5 + i * 4).dp).background(if (i <= quality) on else off, RoundedCornerShape(1.dp))) }
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
