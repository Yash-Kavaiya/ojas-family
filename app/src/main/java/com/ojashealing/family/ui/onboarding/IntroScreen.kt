package com.ojashealing.family.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import com.ojashealing.family.ui.theme.BrandTitleStyle
import com.ojashealing.family.ui.theme.HealingBlue
import com.ojashealing.family.ui.theme.HealingCyan
import com.ojashealing.family.ui.theme.Spacing

/** How long the light takes to pass from the top of the screen to the bottom. */
private const val SWEEP_MS = 2400

/** The fade into the app after the sweep; sweep plus fade is the 3-second intro. */
private const val FADE_MS = 600

/** The intro's plain background, shown for the moment it takes to read whether the intro has played. */
@Composable
fun IntroBackdrop() {
    Box(Modifier.fillMaxSize().background(BackgroundDark))
}

/**
 * First-launch intro: a band of blue healing light moves down the screen from top to bottom,
 * the lotus appears as the light passes it, then everything fades into the app. A tap skips it.
 */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)
    val sweep = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        sweep.animateTo(1f, tween(SWEEP_MS, easing = FastOutSlowInEasing))
        fade.animateTo(0f, tween(FADE_MS, easing = LinearEasing))
        finish()
    }

    // The lotus sits mid-screen, so it shows as the light's centre reaches the middle
    val logo = ((sweep.value - 0.3f) / 0.3f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .alpha(fade.value)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
        contentAlignment = Alignment.Center
    ) {
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
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Image(
                painter = painterResource(R.drawable.ojas_lotus),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(112.dp).alpha(logo).scale(0.9f + 0.1f * logo).clip(CircleShape)
            )
            Text(stringResource(R.string.app_name), style = BrandTitleStyle, color = Color.White, modifier = Modifier.alpha(logo))
        }
    }
}

/** Kept internal so tests can check the intro stays at 3 seconds. */
internal const val INTRO_TOTAL_MS = SWEEP_MS + FADE_MS
