package com.ojashealing.family.ui.content

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.ojashealing.family.ui.theme.LocalReducedMotion
import com.ojashealing.family.ui.theme.OjasEasing
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** The healing blues every session's light and waves are drawn in. */
internal object HealingLight {
    val Deep = Color(0xFF0B3D91)
    val Blue = Color(0xFF2F7CF6)
    val Sky = Color(0xFF7CC4FF)
    val Glow = Color(0xFFBFE6FF)
}

/**
 * Layered waves of light across the screen. Their height follows [level] (0..1, the loudness of
 * what is playing) so the voice and music visibly move them; while paused they settle to a calm
 * ripple. Reading [level] inside the draw step means a new loudness only redraws, never recomposes.
 */
@Composable
fun Wavefront(level: () -> Float, playing: Boolean, modifier: Modifier = Modifier, accent: Color = HealingLight.Sky) {
    val reduced = LocalReducedMotion.current
    val phase = remember { mutableFloatStateOf(0f) }
    // Waves keep flowing only while the practice plays; the clock stops when paused
    LaunchedEffect(playing, reduced) {
        if (!playing || reduced) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) phase.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    val idle by animateFloatAsState(if (playing) 1f else 0.25f, tween(900, easing = OjasEasing.Emphasized), label = "waveIdle")
    val path = remember { Path() }
    val layers = remember {
        // (frequency, speed, amplitude share, alpha, stroke dp)
        listOf(
            WaveLayer(1.2f, 0.55f, 1.00f, 0.95f, 2.6f),
            WaveLayer(1.7f, -0.8f, 0.72f, 0.60f, 2.0f),
            WaveLayer(2.4f, 1.1f, 0.50f, 0.42f, 1.6f),
            WaveLayer(0.8f, 0.35f, 0.85f, 0.28f, 1.2f),
        )
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        val t = phase.floatValue
        val mid = size.height / 2
        // A floor so the wave never goes fully flat while the practice plays
        val loud = (0.12f + 0.88f * level().coerceIn(0f, 1f)) * idle
        val maxAmp = size.height * 0.42f
        val steps = 72
        layers.forEachIndexed { k, layer ->
            path.reset()
            for (i in 0..steps) {
                val x = i / steps.toFloat()
                // Taper both ends so it reads as one travelling front of light, not a cut-off line
                val envelope = sin(PI.toFloat() * x).pow(1.6f)
                val y = mid + maxAmp * loud * layer.amp * envelope *
                    sin(2f * PI.toFloat() * (layer.freq * x) + t * layer.speed * 2.2f + k * 1.3f)
                val px = x * size.width
                if (i == 0) path.moveTo(px, y) else path.lineTo(px, y)
            }
            val brush = Brush.horizontalGradient(
                listOf(Color.Transparent, accent.copy(alpha = layer.alpha), Color.White.copy(alpha = layer.alpha), accent.copy(alpha = layer.alpha), Color.Transparent)
            )
            // A wide faint stroke under a thin bright one gives each wave a glow
            drawPath(path, brush, alpha = 0.25f, style = Stroke(width = layer.stroke * 5f * density, cap = StrokeCap.Round))
            drawPath(path, brush, style = Stroke(width = layer.stroke * density, cap = StrokeCap.Round))
        }
    }
}

private data class WaveLayer(val freq: Float, val speed: Float, val amp: Float, val alpha: Float, val stroke: Float)

/**
 * Soft blue healing light pouring from the top of the screen to the bottom, again and again, like
 * the light the meditation describes washing down through the body. With animations off it rests
 * as a still glow.
 */
@Composable
fun BoxScope.BlueLightSweep(playing: Boolean, level: () -> Float = { 0f }) {
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "sweep")
    val travel by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = OjasEasing.Standard), RepeatMode.Restart),
        label = "travel",
    )
    val breathe by t.animateFloat(
        initialValue = 0.75f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val strength by animateFloatAsState(if (playing) 1f else 0.45f, tween(900), label = "sweepStrength")
    Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
        val h = size.height
        // Always-on glow from above: the source of the light
        drawRect(
            Brush.radialGradient(
                listOf(HealingLight.Blue.copy(alpha = 0.38f * strength * breathe), Color.Transparent),
                center = Offset(size.width / 2, -h * 0.05f),
                radius = size.width * 1.1f,
            ),
            blendMode = BlendMode.Screen,
        )
        if (reduced) return@Canvas
        // The band of light moving down; it gets a little brighter with the voice
        val band = h * 0.38f
        val y = -band + (h + band * 2) * travel
        val boost = 0.85f + 0.3f * level().coerceIn(0f, 1f)
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.35f to HealingLight.Blue.copy(alpha = 0.22f * strength * boost),
                0.5f to HealingLight.Glow.copy(alpha = 0.30f * strength * boost),
                0.65f to HealingLight.Blue.copy(alpha = 0.22f * strength * boost),
                1f to Color.Transparent,
                startY = y - band, endY = y + band,
            ),
            blendMode = BlendMode.Screen,
        )
        // A thin bright leading edge
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.10f * strength), Color.Transparent),
                startY = y - 6 * density, endY = y + 6 * density,
            ),
        )
    }
}

/** One trip of the light from top to bottom. */
private const val SWEEP_MS = 8000
