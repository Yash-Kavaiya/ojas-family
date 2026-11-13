package com.ojashealing.family.ui.content

import com.ojashealing.family.ui.theme.LocalReducedMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.media3.common.C
import com.ojashealing.family.data.practice.AudioLevelMeter
import com.ojashealing.family.data.practice.MeteredRenderersFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import com.ojashealing.family.ui.theme.Durations
import com.ojashealing.family.ui.theme.OjasEasing
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ojashealing.family.R
import com.ojashealing.family.data.practice.MediaType
import com.ojashealing.family.data.practice.PRACTICE_POINTS
import com.ojashealing.family.data.practice.PracticeMedia
import com.ojashealing.family.data.practice.PracticeMediaRepository
import com.ojashealing.family.data.practice.clockLabel
import com.ojashealing.family.data.practice.watchedEnough
import com.ojashealing.family.data.social.todayIst
import com.ojashealing.family.domain.ContentSection
import com.ojashealing.family.ui.theme.BackgroundDark
import com.ojashealing.family.ui.theme.BrandNumberStyle
import com.ojashealing.family.ui.theme.OjasGradients
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class SessionPhase { Loading, Ready, Finishing, Complete, Failed }

data class SessionUi(
    val media: PracticeMedia? = null,
    val phase: SessionPhase = SessionPhase.Loading,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Time really played (seeking forward does not add to it). */
    val watchedMs: Long = 0,
    /** Furthest point reached by playing; forward seeks stop here. */
    val reachedMs: Long = 0,
    val doneToday: Boolean = false,
    val awarded: Boolean = false,
    /** Points the server gave for this session (practice plus any bonuses). */
    val pointsEarned: Int = 0,
    /** Days in a row with at least one practice, after this one; 0 when unknown. */
    val streak: Int = 0,
    /** Video files start as sound only (eyes closed); the member can bring the picture back. */
    val showVideo: Boolean = false
)

/** What the session asks the embedded YouTube player to do. */
sealed interface YouTubeCommand {
    data object Play : YouTubeCommand
    data object Pause : YouTubeCommand
    data class SeekTo(val ms: Long) : YouTubeCommand
}

/** Saves where a member stopped in today's practice, so they can come back and carry on. */
class PracticeProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("practice_progress", Context.MODE_PRIVATE)
    private fun key(section: ContentSection) = "${section.id}_${todayIst().iso()}"

    /** (position, watched, reached) in ms; zeros when nothing was played today. */
    fun load(section: ContentSection): Triple<Long, Long, Long> {
        val parts = prefs.getString(key(section), null)?.split(",")?.mapNotNull { it.toLongOrNull() }
        return if (parts?.size == 3) Triple(parts[0], parts[1], parts[2]) else Triple(0, 0, 0)
    }

    fun save(section: ContentSection, position: Long, watched: Long, reached: Long) {
        // Yesterday's unfinished sessions are of no use; keep the file small
        val edit = prefs.edit()
        prefs.all.keys.filterNot { it.endsWith(todayIst().iso()) }.forEach(edit::remove)
        edit.putString(key(section), "$position,$watched,$reached").apply()
    }

    fun clear(section: ContentSection) = prefs.edit().remove(key(section)).apply()

    /** Share of today's practice already played, 0..1, for the Practice tab's resume ring. */
    fun share(section: ContentSection, durationSec: Int): Float {
        val (_, watched, _) = load(section)
        return if (durationSec <= 0) 0f else (watched / (durationSec * 1000f)).coerceIn(0f, 1f)
    }
}

/**
 * One guided practice session. The player lives here so rotating the phone or a short trip to
 * another screen does not restart the video. Only real playing time counts toward finishing.
 */
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PracticeSessionViewModel(
    context: Context,
    val section: ContentSection,
    private val repo: PracticeMediaRepository,
    private val progress: PracticeProgressStore,
    uid: Flow<String?>
) : ViewModel() {

    private val meter = AudioLevelMeter()
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext, MeteredRenderersFactory(context.applicationContext, meter)).build()
    /** Loudness of what is playing right now, 0..1, for the wave. */
    val level: StateFlow<Float> = meter.level
    private val _ui = MutableStateFlow(SessionUi())
    val ui: StateFlow<SessionUi> = _ui.asStateFlow()
    private var started = false

    // YouTube practices play in a view owned by the screen; it reports back here
    private val _youTube = MutableSharedFlow<YouTubeCommand>(extraBufferCapacity = 8)
    val youTubeCommands: SharedFlow<YouTubeCommand> = _youTube.asSharedFlow()
    private var ytPositionMs = 0L
    private var ytPlaying = false
    private val isYouTube get() = _ui.value.media?.type == MediaType.YouTube
    private fun position(): Long = if (isYouTube) ytPositionMs else player.currentPosition
    private fun playingNow(): Boolean = if (isYouTube) ytPlaying else player.isPlaying

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _ui.update { it.copy(playing = isPlaying) }
                if (isPlaying) markStarted() else meter.reset()
            }

            override fun onPlaybackStateChanged(state: Int) {
                _ui.update {
                    it.copy(
                        buffering = state == Player.STATE_BUFFERING,
                        durationMs = if (player.duration > 0) player.duration else it.durationMs
                    )
                }
                if (state == Player.STATE_ENDED) onEnded()
            }
        })
        viewModelScope.launch {
            val media = repo.observeMedia().first()[section]
            if (media == null) {
                _ui.update { it.copy(phase = SessionPhase.Failed) }
                return@launch
            }
            val (pos, watched, reached) = progress.load(section)
            _ui.update {
                it.copy(media = media, phase = SessionPhase.Ready, durationMs = media.durationSec * 1000L, positionMs = pos, watchedMs = watched, reachedMs = reached)
            }
            ytPositionMs = pos
            if (media.type != MediaType.YouTube) {
                // Eyes closed: a video practice starts as sound only
                if (media.type == MediaType.Video) setVideoTrack(false)
                player.setMediaItem(MediaItem.fromUri(media.url), pos)
                player.prepare()
            }
        }
        viewModelScope.launch {
            uid.filterNotNull().flatMapLatest { repo.observeDoneToday(it) }.collect { done ->
                _ui.update { it.copy(doneToday = section in done) }
            }
        }
        // Count only time that really played: a jump forward is not watching
        viewModelScope.launch {
            var last = position()
            var sinceSave = 0
            while (isActive) {
                delay(TICK_MS)
                val pos = position()
                val step = pos - last
                last = pos
                if (!playingNow()) continue
                _ui.update {
                    val counted = if (step in 1..(TICK_MS * 4)) step else 0
                    it.copy(positionMs = pos, watchedMs = it.watchedMs + counted, reachedMs = maxOf(it.reachedMs, pos))
                }
                if (++sinceSave >= 20) {
                    sinceSave = 0
                    save()
                }
            }
        }
    }

    private fun markStarted() {
        if (started) return
        started = true
        viewModelScope.launch { repo.start(section) }
    }

    private fun save() = _ui.value.let { progress.save(section, it.positionMs, it.watchedMs, it.reachedMs) }

    private fun onEnded() {
        val u = _ui.value
        val duration = if (isYouTube) u.durationMs else maxOf(u.durationMs, player.duration)
        _ui.update { it.copy(positionMs = duration, reachedMs = duration) }
        if (watchedEnough(u.watchedMs + TICK_MS * 4, duration)) finish()
        else seekTo(u.reachedMs.coerceAtMost(duration))
    }

    private fun seekTo(ms: Long) {
        if (isYouTube) { ytPositionMs = ms; _youTube.tryEmit(YouTubeCommand.SeekTo(ms)) } else player.seekTo(ms)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun setVideoTrack(on: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !on)
            .build()
    }

    /** Brings the picture back (or hides it again) without stopping the sound. */
    fun toggleVideo() {
        val show = !_ui.value.showVideo
        setVideoTrack(show)
        _ui.update { it.copy(showVideo = show) }
    }

    /** The embedded YouTube player's latest state. */
    fun onYouTubeProgress(positionMs: Long, playing: Boolean) {
        ytPositionMs = positionMs
        if (playing != ytPlaying) {
            ytPlaying = playing
            _ui.update { it.copy(playing = playing, buffering = false) }
            if (playing) markStarted()
        }
    }

    fun onYouTubeBuffering(buffering: Boolean) = _ui.update { it.copy(buffering = buffering) }

    /** The YouTube video reached its end. */
    fun onYouTubeEnded() {
        ytPlaying = false
        _ui.update { it.copy(playing = false) }
        onEnded()
    }

    /** The YouTube player is ready: start where the member stopped earlier today. */
    fun youTubeStartSeconds(): Float = ytPositionMs / 1000f

    fun finish() {
        _ui.update { it.copy(phase = SessionPhase.Finishing) }
        viewModelScope.launch {
            // The start call may still be on its way when a short practice ends
            if (!started) { started = true; repo.start(section) }
            repo.complete(section)
                .onSuccess { r ->
                    progress.clear(section)
                    _ui.update { it.copy(phase = SessionPhase.Complete, awarded = r.awarded, pointsEarned = r.points, streak = r.streak) }
                }
                .onFailure { _ui.update { it.copy(phase = SessionPhase.Failed) } }
        }
    }

    fun togglePlay() {
        if (isYouTube) {
            _youTube.tryEmit(if (ytPlaying) YouTubeCommand.Pause else YouTubeCommand.Play)
            return
        }
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    /** Back freely; forward only up to the furthest point already played. */
    fun seekBy(deltaMs: Long) {
        val u = _ui.value
        val now = position()
        val target = (now + deltaMs).coerceIn(0, maxOf(u.reachedMs, now))
        seekTo(target)
        _ui.update { it.copy(positionMs = target) }
    }

    fun pause() {
        if (isYouTube) _youTube.tryEmit(YouTubeCommand.Pause) else player.pause()
        save()
    }

    /** Back on the Practice tab after finishing: start over if they want to practise again. */
    fun again() {
        seekTo(0)
        _ui.update { it.copy(phase = SessionPhase.Ready, positionMs = 0, watchedMs = 0, reachedMs = 0) }
    }

    override fun onCleared() {
        if (_ui.value.phase != SessionPhase.Complete) save()
        player.release()
    }

    private companion object {
        const val TICK_MS = 250L
    }
}

/** The calm night palette every session uses, tinted by its section. */
private data class SessionColors(val top: Color, val accent: Color, val soft: Color)

@Composable
private fun sessionColors(section: ContentSection): SessionColors {
    val tint = section.tint()
    return SessionColors(top = tint.boxDark, accent = tint.titleDark, soft = tint.titleDark.copy(alpha = 0.18f))
}

@Composable
fun PracticeSessionScreen(vm: PracticeSessionViewModel, onClose: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val colors = sessionColors(vm.section)
    val level = vm.level.collectAsStateWithLifecycle()
    val media = ui.media

    // The screen stays on while practising; leaving the app pauses and keeps the place
    val view = LocalView.current
    DisposableEffect(ui.playing) {
        view.keepScreenOn = ui.playing
        onDispose { view.keepScreenOn = false }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.pause() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // YouTube sound can't be measured, so its wave follows a gentle swell while it plays
    val swell = rememberInfiniteTransition(label = "swell")
    val synthetic by swell.animateFloat(0.25f, 0.85f, infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "syn")
    val isYouTube = media?.type == MediaType.YouTube
    val loudness: () -> Float = { if (isYouTube) (if (ui.playing) synthetic else 0f) else level.value }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1B3D), colors.top, BackgroundDark, Color(0xFF0B0718))))
    ) {
        BlueLightSweep(playing = ui.playing, level = loudness)
        FloatingLights(colors.accent)
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(vm.section.label()), style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                    media?.title?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                PointChip(done = ui.doneToday, accent = colors.accent)
            }

            Spacer(Modifier.height(Spacing.m))
            if (media == null) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (ui.phase == SessionPhase.Failed) Text(stringResource(R.string.session_complete_failed), color = Color.White, textAlign = TextAlign.Center)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        CircularProgressIndicator(color = colors.accent, strokeCap = StrokeCap.Round)
                        Text(stringResource(R.string.session_loading), color = Color.White.copy(alpha = 0.8f))
                    }
                }
                return@Column
            }

            val progress = if (ui.durationMs > 0) ui.positionMs.toFloat() / ui.durationMs else 0f
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (isYouTube) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        YouTubeSurface(vm, colors.accent)
                        Spacer(Modifier.height(Spacing.l))
                        TimerRing(progress, ui, colors, Modifier.size(150.dp))
                    }
                } else {
                    AnimatedContent(
                        targetState = ui.showVideo,
                        transitionSpec = {
                            (fadeIn(tween(Durations.Base, easing = OjasEasing.Emphasized)) + scaleIn(tween(Durations.Base, easing = OjasEasing.Emphasized), initialScale = 0.96f)) togetherWith
                                fadeOut(tween(Durations.Quick))
                        },
                        label = "surface",
                    ) { video ->
                        if (video) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                VideoSurface(vm.player, onTap = vm::togglePlay, buffering = ui.buffering, accent = colors.accent)
                                Spacer(Modifier.height(Spacing.l))
                                TimerRing(progress, ui, colors, Modifier.size(150.dp))
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(contentAlignment = Alignment.Center) {
                                    BreathingOrb(ui.playing, colors, Modifier.size(260.dp), level = { level.value })
                                    TimerRing(progress, ui, colors, Modifier.size(260.dp), showClock = false)
                                }
                                Spacer(Modifier.height(Spacing.s))
                                Text(clockLabel(ui.durationMs - ui.positionMs), style = MaterialTheme.typography.displaySmall, color = Color.White)
                                Text(stringResource(R.string.session_left, clockLabel(ui.durationMs)), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            // The healing wave: rises and falls with the voice and the music
            Wavefront(level = loudness, playing = ui.playing, modifier = Modifier.fillMaxWidth().height(96.dp))
            Spacer(Modifier.height(Spacing.s))

            Controls(ui, colors, onToggle = vm::togglePlay, onSeek = vm::seekBy)
            Spacer(Modifier.height(Spacing.s))
            if (media.type == MediaType.Video) {
                TextButton(onClick = vm::toggleVideo) {
                    Icon(if (ui.showVideo) Icons.Outlined.Headphones else Icons.Outlined.Videocam, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(if (ui.showVideo) R.string.session_sound_only else R.string.session_show_video), color = Color.White.copy(alpha = 0.85f))
                }
            }
            Text(
                if (ui.doneToday) stringResource(R.string.session_done_hint) else stringResource(R.string.session_watch_hint, PRACTICE_POINTS),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = Spacing.l)
            )
        }

        CompletionOverlay(ui, colors, onBack = onClose, onRetry = vm::finish, onAgain = vm::again)
    }
}

/**
 * Quick Protection streams from the Ojas Healing YouTube channel. The player stays visible (as
 * YouTube requires), tells the session what it is doing, and takes play, pause and seek from it.
 */
@Composable
private fun YouTubeSurface(vm: PracticeSessionViewModel, accent: Color) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoId = vm.ui.collectAsStateWithLifecycle().value.media?.url ?: return
    var ytPlayer by remember { mutableStateOf<YouTubePlayer?>(null) }
    // The channel can switch off playback outside YouTube for a video; then say so and offer the YouTube app
    var blocked by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(ytPlayer) {
        val p = ytPlayer ?: return@LaunchedEffect
        vm.youTubeCommands.collect { c ->
            when (c) {
                YouTubeCommand.Play -> p.play()
                YouTubeCommand.Pause -> p.pause()
                is YouTubeCommand.SeekTo -> p.seekTo(c.ms / 1000f)
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                YouTubePlayerView(ctx).apply {
                    // Pauses on ON_STOP and releases on ON_DESTROY: no background playback (YouTube terms)
                    lifecycleOwner.lifecycle.addObserver(this)
                    addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                        private var playing = false
                        private var lastSecond = 0f
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            ytPlayer = youTubePlayer
                            youTubePlayer.cueVideo(videoId, vm.youTubeStartSeconds())
                        }
                        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                            playing = state == PlayerConstants.PlayerState.PLAYING
                            vm.onYouTubeBuffering(state == PlayerConstants.PlayerState.BUFFERING)
                            if (state == PlayerConstants.PlayerState.ENDED) vm.onYouTubeEnded()
                            else vm.onYouTubeProgress((lastSecond * 1000).toLong(), playing)
                        }
                        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                            lastSecond = second
                            vm.onYouTubeProgress((second * 1000).toLong(), playing)
                        }
                        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                            blocked = true
                            vm.onYouTubeBuffering(false)
                        }
                    })
                }
            },
            onRelease = { v ->
                lifecycleOwner.lifecycle.removeObserver(v)
                v.release()
            }
        )
        if (blocked) {
            Column(
                Modifier.fillMaxSize().background(Color(0xF00B1530)).padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.session_youtube_blocked), color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId"))) } },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = BackgroundDark),
                ) { Text(stringResource(R.string.video_open_youtube)) }
            }
        }
    }
}

/** A "+1" chip until today's point is earned, then a quiet tick. */
@Composable
private fun PointChip(done: Boolean, accent: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (done) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .then(if (done) Modifier else Modifier.background(Brush.linearGradient(OjasGradients.gold)))
            .padding(horizontal = Spacing.m, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(if (done) Icons.Outlined.TaskAlt else Icons.Outlined.Stars, contentDescription = null, tint = if (done) accent else Color(0xFF7A4500), modifier = Modifier.size(16.dp))
        Text(
            if (done) stringResource(R.string.guided_done) else stringResource(R.string.guided_earn, PRACTICE_POINTS),
            style = MaterialTheme.typography.labelMedium,
            color = if (done) Color.White else Color(0xFF3B2300)
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(player: ExoPlayer, onTap: () -> Unit, buffering: Boolean, accent: Color) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        if (buffering) CircularProgressIndicator(color = accent)
    }
}

/** Remaining time inside a ring that fills as the practice goes on, with a glowing head. */
@Composable
private fun TimerRing(progress: Float, ui: SessionUi, colors: SessionColors, modifier: Modifier, showClock: Boolean = true) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            rotate(-90f) {
                drawArc(
                    Brush.sweepGradient(listOf(colors.accent.copy(alpha = 0.5f), colors.accent, Color.White)),
                    0f, 360f * shown, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            if (shown > 0f) {
                val angle = (360f * shown - 90f) * (PI / 180f).toFloat()
                val r = (size.minDimension - stroke) / 2
                val head = Offset(center.x + r * cos(angle), center.y + r * sin(angle))
                drawCircle(colors.accent.copy(alpha = 0.35f), stroke * 1.4f, head)
                drawCircle(Color.White, stroke * 0.45f, head)
            }
        }
        if (showClock) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(clockLabel(ui.durationMs - ui.positionMs), style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text(stringResource(R.string.session_left, clockLabel(ui.durationMs)), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

/**
 * Eyes closed: a lotus in soft blue light that grows on the in-breath and settles on the
 * out-breath. Its glow also swells with the sound, so the voice seems to come from the light.
 */
@Composable
private fun BreathingOrb(playing: Boolean, colors: SessionColors, modifier: Modifier, level: () -> Float = { 0f }) {
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "breath")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val b = if (playing && !reduced) breath else 0.4f
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(18.dp)) {
            val r = size.minDimension / 2
            val voice = level().coerceIn(0f, 1f)
            // Outer halo that follows the sound
            drawCircle(
                Brush.radialGradient(
                    listOf(HealingLight.Sky.copy(alpha = 0.10f + 0.30f * voice), Color.Transparent),
                    center = center, radius = r * (0.95f + 0.12f * voice),
                ),
                radius = r * (0.95f + 0.12f * voice),
            )
            for (i in 3 downTo 1) {
                drawCircle(colors.accent.copy(alpha = 0.06f * i + 0.1f * b), r * (0.52f + 0.14f * i * (0.7f + 0.3f * b)))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.ojas_lotus), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(104.dp).scale(0.9f + 0.15f * b).clip(CircleShape)
            )
            if (playing) {
                Spacer(Modifier.height(Spacing.s))
                Text(
                    stringResource(if (breath < 0.5f) R.string.session_breathe_in else R.string.session_breathe_out),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.6f + 0.4f * b)
                )
            }
        }
    }
}

private const val BREATH_MS = 4000

@Composable
private fun Controls(ui: SessionUi, colors: SessionColors, onToggle: () -> Unit, onSeek: (Long) -> Unit) {
    val halo = rememberInfiniteTransition(label = "halo")
    val pulse by halo.animateFloat(1f, 1.18f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        RoundControl(Icons.Outlined.Replay10, stringResource(R.string.session_rewind), enabled = ui.positionMs > 0) { onSeek(-10_000) }
        Box(contentAlignment = Alignment.Center) {
            // A soft halo breathes around the button while paused, inviting a tap
            if (!ui.playing) Box(Modifier.size(84.dp).scale(pulse).clip(CircleShape).background(colors.accent.copy(alpha = 0.18f)))
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(OjasGradients.saffron))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (ui.playing) R.string.media_pause else R.string.media_play),
                    tint = Color.White, modifier = Modifier.size(40.dp)
                )
            }
        }
        RoundControl(Icons.Outlined.Forward10, stringResource(R.string.session_forward), enabled = ui.positionMs + 1_000 < ui.reachedMs) { onSeek(10_000) }
    }
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = if (enabled) 0.12f else 0.05f))
    ) { Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f)) }
}

/** Slow motes of light drifting upward behind everything. */
@Composable
private fun BoxScope.FloatingLights(accent: Color) {
    val t = rememberInfiniteTransition(label = "motes")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "d")
    val motes = remember { List(18) { i -> Triple((i * 0.137f + 0.05f) % 1f, (i * 0.61f) % 1f, 2f + (i % 4) * 1.5f) } }
    Canvas(Modifier.matchParentSize()) {
        motes.forEachIndexed { i, (x, y0, r) ->
            val speed = 0.6f + (i % 3) * 0.25f
            val y = ((y0 - drift * speed) % 1f + 1f) % 1f
            drawCircle(accent.copy(alpha = 0.10f + 0.12f * ((i % 5) / 4f)), r.dp.toPx() * 2.2f, Offset(x * size.width, y * size.height))
            drawCircle(Color.White.copy(alpha = 0.35f), r.dp.toPx() * 0.5f, Offset(x * size.width, y * size.height))
        }
    }
}

/** Light rings open out, the lotus appears and the point is shown; or the reason it was not recorded. */
@Composable
private fun CompletionOverlay(ui: SessionUi, colors: SessionColors, onBack: () -> Unit, onRetry: () -> Unit, onAgain: () -> Unit) {
    val show = ui.phase == SessionPhase.Complete || ui.phase == SessionPhase.Finishing || (ui.phase == SessionPhase.Failed && ui.media != null)
    AnimatedVisibility(show, enter = fadeIn(tween(500)), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color(0xE6120A22)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
            val rings = rememberInfiniteTransition(label = "rings")
            val wave by rings.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "w")
            if (ui.phase == SessionPhase.Complete) {
                Canvas(Modifier.size(360.dp)) {
                    for (k in 0 until 3) {
                        val p = (wave + k / 3f) % 1f
                        drawCircle(colors.accent.copy(alpha = (1f - p) * 0.45f), size.minDimension / 2 * (0.3f + 0.7f * p), style = Stroke(3.dp.toPx()))
                    }
                }
            }
            Column(
                Modifier.padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                when (ui.phase) {
                    SessionPhase.Finishing -> CircularProgressIndicator(color = colors.accent)
                    SessionPhase.Complete -> {
                        AnimatedVisibility(true, enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()) {
                            Image(painterResource(R.drawable.ojas_lotus), null, contentScale = ContentScale.Crop, modifier = Modifier.size(112.dp).clip(CircleShape))
                        }
                        Text(stringResource(R.string.session_complete_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        if (ui.awarded) {
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(Brush.linearGradient(OjasGradients.gold)).padding(horizontal = Spacing.l, vertical = Spacing.s),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Outlined.Stars, null, tint = Color(0xFF7A4500))
                                Text(stringResource(R.string.session_complete_points, ui.pointsEarned.coerceAtLeast(1)), style = BrandNumberStyle, color = Color(0xFF3B2300))
                            }
                        } else {
                            Text(stringResource(R.string.session_complete_again), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                        }
                        if (ui.streak > 0) StreakBadge(ui.streak)
                        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = BackgroundDark)) {
                            Text(stringResource(R.string.session_back))
                        }
                        TextButton(onClick = onAgain) { Text(stringResource(R.string.media_play), color = Color.White.copy(alpha = 0.8f)) }
                    }
                    else -> {
                        Text(stringResource(R.string.session_complete_failed), style = MaterialTheme.typography.bodyMedium, color = Color.White, textAlign = TextAlign.Center)
                        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = BackgroundDark)) {
                            Text(stringResource(R.string.session_retry))
                        }
                        TextButton(onClick = onBack) { Text(stringResource(R.string.session_back), color = Color.White.copy(alpha = 0.8f)) }
                    }
                }
            }
        }
    }
}


/** "🔥 5 days in a row": the flame pops in a moment after the lotus, then the number counts up. */
@Composable
private fun StreakBadge(days: Int) {
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(days) {
        delay(450)
        shown.animateTo(1f, tween(Durations.Slow, easing = OjasEasing.Emphasized))
    }
    // The day just added ticks over once the badge has mostly arrived
    val count = if (shown.value >= 0.6f) days else (days - 1).coerceAtLeast(0)
    Row(
        Modifier
            .graphicsLayer { alpha = shown.value.coerceAtLeast(0.001f); scaleX = 0.8f + 0.2f * shown.value; scaleY = scaleX }
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFFF8A3D))
        Text(
            pluralStringResource(R.plurals.streak_days_in_row, count, count),
            style = MaterialTheme.typography.titleSmall, color = Color.White,
        )
    }
}
