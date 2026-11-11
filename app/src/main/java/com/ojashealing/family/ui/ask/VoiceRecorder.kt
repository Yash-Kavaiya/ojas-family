package com.ojashealing.family.ui.ask

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ojashealing.family.R
import com.ojashealing.family.data.chat.WAVEFORM_MAX
import com.ojashealing.family.data.chat.downsample
import com.ojashealing.family.data.practice.clockLabel
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.io.File

/** Longest voice note, as the rules allow. */
const val MAX_VOICE_MS = 5 * 60_000L

/** Shorter than this is taken as a tap, not a recording. */
const val MIN_VOICE_MS = 800L

/** A finished voice note, ready to upload. */
data class VoiceNote(val file: File, val durationMs: Long, val waveform: List<Int>)

/** One MediaRecorder for one voice note: AAC in an .m4a file in the app's cache. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val levels = mutableListOf<Int>()

    val recording get() = recorder != null

    fun start(): Boolean = runCatching {
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val f = File(dir, "voice-${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setMaxDuration(MAX_VOICE_MS.toInt())
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = f
        levels.clear()
        startedAt = System.currentTimeMillis()
        true
    }.getOrElse { release(); false }

    /** The loudness right now, 0..32767; also kept for the waveform. Call about ten times a second. */
    fun sample(): Int = (runCatching { recorder?.maxAmplitude }.getOrNull() ?: 0).also { if (recording) levels += it }

    fun elapsedMs(): Long = if (recording) System.currentTimeMillis() - startedAt else 0L

    /** Stops and returns the note, or null if it was too short to keep. */
    fun stop(): VoiceNote? {
        val duration = elapsedMs()
        val ok = runCatching { recorder?.stop() }.isSuccess
        val f = file
        release()
        if (!ok || f == null || duration < MIN_VOICE_MS) { f?.delete(); return null }
        return VoiceNote(f, duration.coerceAtMost(MAX_VOICE_MS), downsample(levels))
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        file?.delete()
        release()
    }

    private fun release() {
        runCatching { recorder?.release() }
        recorder = null
        file = null
    }
}

/**
 * The mic button: hold to record, let go to send, slide left to cancel.
 * [onHold] fires when recording starts so the bar can switch to the recording view.
 */
@Composable
fun HoldToRecordButton(
    canRecord: () -> Boolean,
    onStart: () -> Boolean,
    onDrag: (Float) -> Unit,
    onRelease: (cancelled: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cancelPx = with(LocalDensity.current) { 120.dp.toPx() }
    val label = stringResource(R.string.voice_hold)
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!canRecord() || !onStart()) return@awaitEachGesture
                    var dx = 0f
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        dx = change.position.x - down.position.x
                        onDrag(dx)
                        change.consume()
                        if (dx < -cancelPx) cancelled = true
                        if (!change.pressed) break
                    }
                    onRelease(cancelled || dx < -cancelPx)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/** While holding the mic: a blinking red dot, the time, the live level and "slide to cancel". */
@Composable
fun RecordingBar(elapsedMs: Long, level: Float, dragX: Float, modifier: Modifier = Modifier) {
    val blink = rememberInfiniteTransition(label = "rec")
    val dot by blink.animateFloat(1f, 0.2f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot")
    Row(
        modifier.fillMaxWidth().height(48.dp).padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        Box(Modifier.size(10.dp).alpha(dot).clip(CircleShape).background(Color(0xFFE53935)))
        Text(clockLabel(elapsedMs), style = MaterialTheme.typography.labelLarge)
        Box(Modifier.width(56.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(level.coerceIn(0.05f, 1f)).height(6.dp).background(MaterialTheme.colorScheme.primary))
        }
        Row(
            Modifier.weight(1f).alpha((1f + dragX / 300f).coerceIn(0.3f, 1f)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.voice_slide_cancel), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ticks while recording and passes the time and level up; stops itself at the 5-minute limit. */
@Composable
fun RecordingTicker(recorder: VoiceRecorder, active: Boolean, onTick: (elapsedMs: Long, level: Float) -> Unit, onLimit: () -> Unit) {
    LaunchedEffect(active) {
        while (active && recorder.recording) {
            val level = recorder.sample() / 32767f
            val elapsed = recorder.elapsedMs()
            onTick(elapsed, level)
            if (elapsed >= MAX_VOICE_MS) { onLimit(); break }
            delay(100)
        }
    }
}

/** A voice note: play/pause, a waveform that fills as it plays, the time, and 1×/1.5×/2× speed. */
@Composable
fun VoiceBubble(url: String, durationMs: Long, waveform: List<Int>, mine: Boolean) {
    // Built on the first press of play, not for every voice note on screen
    val holder = com.ojashealing.family.ui.components.rememberLazyPlayer(url)
    val player = holder.player
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    DisposableEffect(player) {
        val p = player
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (p != null && state == Player.STATE_ENDED) { p.pause(); p.seekTo(0); position = 0 }
            }
        }
        p?.addListener(l)
        onDispose { p?.removeListener(l) }
    }
    LaunchedEffect(player, playing) {
        while (playing) { position = player?.currentPosition ?: 0L; delay(100) }
    }
    val total = if (durationMs > 0) durationMs else (player?.duration ?: 0L).coerceAtLeast(1)
    val progress = (position.toFloat() / total).coerceIn(0f, 1f)
    val played = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.width(240.dp)) {
        IconButton(onClick = { if (playing) player?.pause() else holder.start().apply { playbackParameters = PlaybackParameters(speed) }.play() }, modifier = Modifier.size(40.dp)) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.media_pause else R.string.media_play),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Canvas(Modifier.weight(1f).height(28.dp)) {
            val bars = waveform.ifEmpty { List(32) { 8 } }
            val gap = 2.dp.toPx()
            val w = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1f)
            bars.forEachIndexed { i, v ->
                val h = (v.toFloat() / WAVEFORM_MAX).coerceIn(0.08f, 1f) * size.height
                val x = i * (w + gap)
                drawRoundRect(
                    if (i.toFloat() / bars.size <= progress) played else rest,
                    Offset(x, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2)
                )
            }
        }
        Text(clockLabel(if (playing || position > 0) position else total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (speed == 1f) "1×" else if (speed == 1.5f) "1.5×" else "2×",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = stringResource(R.string.voice_speed)) {
                    speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
                    player?.playbackParameters = PlaybackParameters(speed)
                }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
