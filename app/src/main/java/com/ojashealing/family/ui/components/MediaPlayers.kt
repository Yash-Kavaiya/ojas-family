package com.ojashealing.family.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ojashealing.family.R
import com.ojashealing.family.ui.theme.OjasGradients
import kotlinx.coroutines.delay

/** One ExoPlayer for a composable's lifetime, prepared for [url] and released when it leaves. */
@Composable
private fun rememberPlayer(url: String): ExoPlayer {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/**
 * A player that is only built when the member first presses play. A chat or feed can show many
 * voice notes and audio clips at once; opening a player for each one (and starting to download it)
 * wastes mobile data and can run the phone out of audio decoders.
 */
class LazyPlayer internal constructor(private val context: android.content.Context, private val url: String) {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set

    /** The player, built and prepared on first use. */
    fun start(): ExoPlayer = player ?: ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(url))
        prepare()
    }.also { player = it }

    internal fun release() {
        player?.release()
        player = null
    }
}

@Composable
fun rememberLazyPlayer(url: String): LazyPlayer {
    val context = LocalContext.current.applicationContext
    val holder = remember(url) { LazyPlayer(context, url) }
    DisposableEffect(holder) { onDispose { holder.release() } }
    return holder
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** A compact audio player card: play/pause, seek bar and times. */
@Composable
fun AudioPlayer(url: String, modifier: Modifier = Modifier) {
    val holder = rememberLazyPlayer(url)
    val player = holder.player
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val p = player
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (p == null) return
                if (state == Player.STATE_READY) duration = p.duration.coerceAtLeast(0)
                if (state == Player.STATE_ENDED) { p.pause(); p.seekTo(0) }
            }
        }
        p?.addListener(listener)
        onDispose { p?.removeListener(listener) }
    }
    LaunchedEffect(player, playing) {
        while (playing) {
            position = player?.currentPosition ?: 0L
            delay(250)
        }
        position = player?.currentPosition ?: 0L
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(OjasGradients.saffron)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = { if (playing) player?.pause() else holder.start().play() }) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.media_pause else R.string.media_play),
                    tint = Color.White
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Slider(
                value = if (dragging) dragValue else if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    player?.seekTo((dragValue * duration).toLong())
                    position = (dragValue * duration).toLong()
                    dragging = false
                },
                // Nothing to seek in until the clip has been started once
                enabled = duration > 0,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text(clock(position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(clock(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** An inline 16:9 video with the standard Media3 controls; it does not start on its own. */
@OptIn(UnstableApi::class)
@Composable
fun InlineVideoPlayer(url: String, modifier: Modifier = Modifier) {
    val player = rememberPlayer(url)
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
    )
}
