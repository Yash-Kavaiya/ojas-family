package com.ojashealing.family.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One place for motion. Values follow a small set of rules:
 * - presses scale to 0.96 in 150ms; disabled controls never move
 * - frequent actions (tabs, rows) stay at or under 150ms and only fade
 * - entrances rise 12dp and fade over 300ms, staggered 100ms per chunk
 * - exits are quicker than entrances (150ms)
 * - when the system turns animations off, things only crossfade
 */
object OjasEasing {
    /** Material "emphasized decelerate": fast start, long settle. Used for things arriving. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Things leaving: quick and out of the way. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

object Durations {
    const val Quick = 150
    const val Base = 300
    const val Nav = 320
    const val Slow = 700
    const val Stagger = 100
}

/** True when the user switched animations off in Android settings (accessibility). */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

fun <T> enterSpec(delay: Int = 0): FiniteAnimationSpec<T> = tween(Durations.Base, delay, OjasEasing.Emphasized)
fun <T> exitSpec(): FiniteAnimationSpec<T> = tween(Durations.Quick, easing = OjasEasing.Exit)
/** Springs never bounce here: motion should feel calm, not playful. */
fun <T> calmSpring() = spring<T>(dampingRatio = 1f, stiffness = 500f)

/**
 * Press feedback for anything tappable: shrinks to 0.96 while held and springs back.
 * Pass the same [interactionSource] you give to clickable().
 */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, enabled: Boolean = true, pressed: Float = 0.96f): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed && !reduced) pressed else 1f,
        animationSpec = tween(Durations.Quick, easing = OjasEasing.Standard),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * A one-time arrival for a chunk of content (title, body, actions...). Chunks with a higher
 * [index] arrive 100ms later. It plays once per [key]; scrolling back to it doesn't replay.
 */
fun Modifier.staggeredEntrance(index: Int = 0, key: Any = Unit, distance: Dp = 12.dp): Modifier = composed {
    val reduced = LocalReducedMotion.current
    var played by rememberSaveable(key) { androidx.compose.runtime.mutableStateOf(false) }
    val progress = remember(key) { Animatable(if (played) 1f else 0f) }
    val px = with(LocalDensity.current) { distance.toPx() }
    LaunchedEffect(key) {
        if (!played) {
            progress.animateTo(1f, tween(Durations.Base, delayMillis = (index.coerceAtMost(8)) * Durations.Stagger, easing = OjasEasing.Emphasized))
            played = true
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = if (reduced) 0f else (1f - p) * px
    }
}

/** A soft moving sheen used for loading placeholders, shaped like the content that's coming. */
@Composable
fun ShimmerBlock(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val shine = MaterialTheme.colorScheme.surfaceContainerHighest
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = OjasEasing.Standard), RepeatMode.Restart),
        label = "shimmerX",
    )
    Box(
        modifier
            .clip(shape)
            .background(
                if (reduced) Brush.linearGradient(listOf(base, base))
                else Brush.linearGradient(
                    colors = listOf(base, shine, base),
                    start = Offset(x * 600f, 0f),
                    end = Offset(x * 600f + 400f, 200f),
                )
            )
    )
}

/**
 * A value that loops between [from] and [to] for ambient motion (a glow, typing dots). When the
 * system asks for reduced motion it holds still at [rest], so nothing pulses or blinks.
 */
@Composable
fun loopingFloat(
    from: Float,
    to: Float,
    spec: androidx.compose.animation.core.InfiniteRepeatableSpec<Float>,
    rest: Float = to,
    label: String = "loop",
): androidx.compose.runtime.State<Float> =
    if (LocalReducedMotion.current) {
        androidx.compose.runtime.remember(rest) { androidx.compose.runtime.mutableFloatStateOf(rest) }
    } else {
        androidx.compose.animation.core.rememberInfiniteTransition(label = label).animateFloat(from, to, spec, label = label)
    }

/** The hairline around a photo: pure black in light mode, pure white in dark, at 10%, so it never tints the picture's edge. */
@Composable
fun imageOutline(): androidx.compose.ui.graphics.Color {
    val dark = androidx.compose.material3.MaterialTheme.colorScheme.background.red < 0.5f
    return (if (dark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black).copy(alpha = 0.1f)
}
