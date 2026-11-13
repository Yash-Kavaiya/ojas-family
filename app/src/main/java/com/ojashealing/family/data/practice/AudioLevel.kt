package com.ojashealing.family.data.practice

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Listens to the practice's own sound as the player decodes it (no microphone, no permission) and
 * turns it into a 0..1 loudness the wave animation follows. It rises quickly and falls slowly, so
 * the wave moves with the voice and music instead of flickering on every sample.
 */
@OptIn(UnstableApi::class)
class AudioLevelMeter : TeeAudioProcessor.AudioBufferSink {
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    @Volatile private var encoding = C.ENCODING_PCM_16BIT
    private var smoothed = 0f
    /** The loudest recent level, so quiet and loud recordings both fill the screen nicely. */
    private var peak = 0.05f

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.encoding = encoding
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        // The player still needs this buffer: read a copy, never move its position
        val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val raw = when (encoding) {
            C.ENCODING_PCM_16BIT -> rms16(b)
            C.ENCODING_PCM_FLOAT -> rmsFloat(b)
            else -> return
        }
        peak = maxOf(raw, peak * 0.995f, 0.05f)
        val target = (raw / peak).coerceIn(0f, 1f)
        smoothed = if (target > smoothed) smoothed + (target - smoothed) * 0.6f else smoothed + (target - smoothed) * 0.12f
        _level.value = smoothed
    }

    /** Back to silence, e.g. when playback pauses. */
    fun reset() {
        smoothed = 0f
        _level.value = 0f
    }

    private fun rms16(b: ByteBuffer): Float {
        val n = b.remaining() / 2
        if (n == 0) return 0f
        var sum = 0.0
        // Every second sample is plenty for a loudness reading and halves the work
        var i = 0
        var counted = 0
        while (i < n) {
            val s = b.getShort(b.position() + i * 2) / 32768f
            sum += s * s
            counted++
            i += 2
        }
        return sqrt(sum / counted).toFloat()
    }

    private fun rmsFloat(b: ByteBuffer): Float {
        val n = b.remaining() / 4
        if (n == 0) return 0f
        var sum = 0.0
        var i = 0
        var counted = 0
        while (i < n) {
            val s = b.getFloat(b.position() + i * 4)
            sum += s * s
            counted++
            i += 2
        }
        return sqrt(sum / counted).toFloat()
    }
}

/** Builds ExoPlayer's audio path with [meter] listening in, without changing what is heard. */
@OptIn(UnstableApi::class)
class MeteredRenderersFactory(context: Context, private val meter: AudioLevelMeter) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(TeeAudioProcessor(meter)))
            .build()
}
