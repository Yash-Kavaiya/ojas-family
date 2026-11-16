package com.ojashealing.family.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.ui.theme.BackgroundDark
import kotlin.math.sin
import com.ojashealing.family.ui.theme.BrandTitleStyle
import com.ojashealing.family.ui.theme.HealingBlue
import com.ojashealing.family.ui.theme.HealingCyan
import com.ojashealing.family.ui.theme.Spacing

/** First install: the light crosses in 2 seconds, then the lotus rests. Together these are 5 seconds. */
private const val FIRST_SWEEP_MS = 2_000
private const val FIRST_AFTER_MS = 3_000

/** Every later open: the blue light travels from the top of the screen to the bottom. */
private const val RETURN_MS = 3_000

private const val FADE_MS = 400

/** Slow in, slow out, so the light is easy to follow on the way down. */
private val DownwardLight = CubicBezierEasing(0.42f, 0.0f, 0.2f, 1f)

/** The intro's plain background, shown for the moment it takes to read whether the intro has played. */
@Composable
fun IntroBackdrop() {
    Box(Modifier.fillMaxSize().background(BackgroundDark))
}

/**
 * Blue healing light at the start. The first open after install is 5 seconds: the light moves
 * down, then the lotus and name rest. Every later open is 3 seconds of that same light moving
 * from the top of the screen to the bottom. A tap skips it.
 *
 * With [faceUrl] (the member's approved face photo, from Member Level 3) there is no logo: the photo
 * fills the screen and the light uncovers it on its way down (see [FaceIntro]).
 */
@Composable
fun IntroScreen(firstLaunch: Boolean, onFinished: () -> Unit, faceUrl: String = "") {
    val finish by rememberUpdatedState(onFinished)
    val sweep = remember { Animatable(0f) }
    val after = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    // The photo plays only once it has loaded; if it cannot load in time the usual intro plays instead
    var face by remember(faceUrl, firstLaunch) { mutableStateOf(if (faceUrl.isEmpty() || firstLaunch) FaceLoad.None else FaceLoad.Loading) }
    val withFace = face != FaceLoad.None
    LaunchedEffect(firstLaunch, faceUrl) {
        if (face == FaceLoad.Loading) {
            val got = withTimeoutOrNull(FACE_WAIT_MS) { snapshotFlow { face }.first { it != FaceLoad.Loading } }
            if (got == FaceLoad.Ready) {
                // The light uncovers the photo on its way down, then the photo rests in a soft glow
                sweep.animateTo(1f, tween(FACE_SWEEP_MS, easing = DownwardLight))
                after.animateTo(1f, tween(FACE_REST_MS, easing = LinearEasing))
                fade.animateTo(0f, tween(FADE_MS, easing = LinearEasing))
                finish()
                return@LaunchedEffect
            }
            face = FaceLoad.None
        }
        if (firstLaunch) {
            sweep.animateTo(1f, tween(FIRST_SWEEP_MS, easing = FastOutSlowInEasing))
            after.animateTo(1f, tween(FIRST_AFTER_MS - FADE_MS, easing = LinearEasing))
            fade.animateTo(0f, tween(FADE_MS, easing = LinearEasing))
        } else {
            coroutineScope {
                launch { sweep.animateTo(1f, tween(RETURN_MS, easing = DownwardLight)) }
                delay(RETURN_MS - FADE_MS.toLong())
                fade.animateTo(0f, tween(FADE_MS, easing = LinearEasing))
            }
        }
        finish()
    }

    if (withFace) {
        FaceIntro(
            faceUrl, sweep = { sweep.value }, rest = { after.value }, fade = { fade.value }, onTap = { finish() },
            onLoaded = { ok -> if (face == FaceLoad.Loading) face = if (ok) FaceLoad.Ready else FaceLoad.None },
        )
        return
    }

    // The lotus sits mid-screen, so it shows as the light's centre reaches the middle
    val logo = ((sweep.value - 0.3f) / 0.3f).coerceIn(0f, 1f)
    val pulse = sin(after.value * Math.PI * 3).toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .alpha(fade.value)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
        contentAlignment = Alignment.Center
    ) {
        val light: @Composable () -> Unit = {
        Canvas(Modifier.fillMaxSize()) {
            val band = size.height * 0.45f
            // The band's centre travels from above the top edge to below the bottom edge
            val centre = -band / 2 + sweep.value * (size.height + band)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.3f to HealingCyan.copy(alpha = 0.22f),
                    0.5f to HealingBlue.copy(alpha = 0.75f),
                    0.7f to HealingCyan.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                    startY = centre - band / 2,
                    endY = centre + band / 2
                ),
                topLeft = Offset(0f, centre - band / 2),
                size = Size(size.width, band)
            )
            // A thin bright line at the front of the light
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFBFE9FF), Color.Transparent)),
                topLeft = Offset(0f, centre - 1.dp.toPx()),
                size = Size(size.width, 2.dp.toPx())
            )
            // After the sweep, a blue glow breathes behind the lotus for the rest of the intro
            if (after.value > 0f) {
                val glow = 0.55f + 0.35f * pulse
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(HealingCyan.copy(alpha = 0.55f * glow), HealingBlue.copy(alpha = 0.12f * glow), Color.Transparent),
                        center = center,
                        radius = size.minDimension * 0.42f
                    ),
                    radius = size.minDimension * 0.42f,
                    center = center
                )
            }
        }
        }
        light()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Image(
                painter = painterResource(R.drawable.ojas_lotus),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(112.dp).alpha(logo).scale((0.9f + 0.1f * logo) * (1f + 0.05f * pulse * logo)).clip(CircleShape)
            )
            Text(stringResource(R.string.app_name), style = BrandTitleStyle, color = Color.White, modifier = Modifier.alpha(logo))
        }
    }
}

/**
 * The opening with the member's own photo: no logo, the whole photo as large as the screen allows.
 * A band of blue healing light pours from the top to the bottom and uncovers the photo as it passes
 * (a dark curtain is drawn below the light), leaving a faint blue wash behind it; the photo settles
 * from a slight zoom, then a soft glow breathes from above until it fades. Animated values are read
 * only while drawing and there is no blur or offscreen layer, so each frame is cheap.
 */
@Composable
private fun FaceIntro(url: String, sweep: () -> Float, rest: () -> Float, fade: () -> Float, onTap: () -> Unit, onLoaded: (Boolean) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade() }
            .background(BackgroundDark)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The photo settles from a slight zoom as the light passes
                    val s = 1.08f - 0.08f * FastOutSlowInEasing.transform(sweep())
                    scaleX = s; scaleY = s
                }
                // A soft blue glow fills the space around the photo
                .background(Brush.radialGradient(listOf(HealingBlue.copy(alpha = 0.45f), BackgroundDark)))
        ) {
            // Only from the phone's cache (Home keeps it there): asking the server first would hold the opening up
            val context = LocalContext.current
            val request = remember(url) {
                ImageRequest.Builder(context).data(url).networkCachePolicy(CachePolicy.DISABLED).crossfade(false).build()
            }
            // The whole photo, as large as it fits
            coil.compose.AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { onLoaded(true) },
                onError = { onLoaded(false) },
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val s = sweep()
            val h = size.height
            val band = h * 0.42f
            val front = -band / 2 + s * (h + band)
            // Below the light the screen is still dark; the photo shows only where the light has passed
            val edge = band * 0.35f
            if (front - edge < h) {
                val top = (front - edge).coerceAtLeast(0f)
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to BackgroundDark, startY = front - edge, endY = front + edge),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, h - top),
                )
            }
            // A faint blue wash over the part the light has already passed
            drawRect(
                Brush.verticalGradient(
                    0f to HealingBlue.copy(alpha = 0.10f),
                    1f to HealingCyan.copy(alpha = 0.04f),
                    endY = front.coerceAtLeast(1f),
                ),
                size = Size(size.width, front.coerceIn(0f, h)),
            )
            if (s < 1f) {
                // The band of light itself, strongest at its centre
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to HealingCyan.copy(alpha = 0.20f),
                        0.5f to HealingBlue.copy(alpha = 0.55f),
                        0.65f to HealingCyan.copy(alpha = 0.20f),
                        1f to Color.Transparent,
                        startY = front - band / 2, endY = front + band / 2,
                    ),
                    topLeft = Offset(0f, front - band / 2),
                    size = Size(size.width, band),
                )
                // A thin bright line leading the light
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFBFE9FF), Color.Transparent)),
                    topLeft = Offset(0f, front - 1.dp.toPx()),
                    size = Size(size.width, 2.dp.toPx()),
                )
            }
            // Once the light has passed, a gentle glow breathes down from the top
            val r = rest()
            if (r > 0f) {
                val glow = (0.5f + 0.5f * sin(r * Math.PI * 2).toFloat()) * (1f - r * 0.4f)
                drawRect(
                    Brush.radialGradient(
                        listOf(HealingCyan.copy(alpha = 0.35f * glow), HealingBlue.copy(alpha = 0.10f * glow), Color.Transparent),
                        center = Offset(size.width / 2, 0f),
                        radius = h * 0.7f,
                    ),
                )
            }
        }
    }
}

private enum class FaceLoad { None, Loading, Ready }

/** How long the intro waits for the photo (it is usually in the phone's image cache) before playing without it. */
private const val FACE_WAIT_MS = 1_500L

/** With the member's photo: the light's trip down, then the photo rests in its glow. */
private const val FACE_SWEEP_MS = 2_200
private const val FACE_REST_MS = 1_000

/** First open after install. Later opens use [RETURN_INTRO_MS]. */
internal const val INTRO_TOTAL_MS = FIRST_SWEEP_MS + FIRST_AFTER_MS

internal const val RETURN_INTRO_MS = RETURN_MS
