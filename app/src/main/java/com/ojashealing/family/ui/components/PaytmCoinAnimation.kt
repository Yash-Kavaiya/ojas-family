package com.ojashealing.family.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.ojashealing.family.R
import com.ojashealing.family.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** How long a like or comment's blue star takes to reach the points counter, including its opening glow. */
internal const val REWARD_LAND_MS = 1800

/** A Moksha Point award to animate. isMilestone shows the full burst; otherwise a blue star drifts to the counter. */
data class RewardEvent(
    val amount: Int,
    val reason: String,
    val isMilestone: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

// pointEvents store a reason id written by Cloud Functions; unknown ids fall back to the id itself
@Composable
fun rewardReasonLabel(reason: String): String = when (reason) {
    "daily" -> stringResource(R.string.reward_daily)
    // Goals and gifts also get the big animation: say what it was for in the member's language, not the server's key
    "goal" -> stringResource(R.string.points_src_goal)
    "gift" -> stringResource(R.string.points_src_gift)
    "practice_media" -> stringResource(R.string.points_src_practice)
    else -> stringResource(R.string.points_src_other)
}

// Screen positions the blue star drifts between, both in root coordinates
class RewardAnchors {
    var lastTap by mutableStateOf<Offset?>(null)
    var counterCenter by mutableStateOf<Offset?>(null)
}

@Composable
fun PaytmCoinOverlay(
    rewardEvent: RewardEvent?,
    anchors: RewardAnchors,
    onDismiss: () -> Unit
) {
    if (rewardEvent == null) return

    // Everyday likes and comments get the slow blue star; milestones get the full burst
    if (!rewardEvent.isMilestone) {
        StarFlyToCounter(rewardEvent = rewardEvent, anchors = anchors, onDismiss = onDismiss)
        return
    }

    val context = LocalContext.current

    // Trigger haptic vibration like Paytm
    LaunchedEffect(rewardEvent.timestamp) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 50, 60), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(80)
                }
            }
        } catch (_: Exception) {}

        // Held long enough to see the star open, then the points line
        delay(3200)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            PaytmBurstEffect(
                amount = rewardEvent.amount,
                reason = rewardReasonLabel(rewardEvent.reason),
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun PaytmBurstEffect(
    amount: Int,
    reason: String,
    onDismiss: () -> Unit
) {
    // Particle burst state
    val burstProgress = remember { Animatable(0f) }
    val coinScale = remember { Animatable(0.2f) }
    val coinRotation = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Coin shoots up with elastic bounce
        coinScale.animateTo(
            targetValue = 1.15f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        coinScale.animateTo(1.0f, animationSpec = tween(480))
    }

    LaunchedEffect(Unit) {
        burstProgress.animateTo(1f, animationSpec = tween(2200, easing = FastOutSlowInEasing))
    }

    // Gentle rotating aura rays
    val infiniteTransition = rememberInfiniteTransition(label = "aura")
    val rayRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rayRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            // Slow blue aura behind the star, the same light as the healing-light intro
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = rayRotation }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val rayCount = 16
                val maxRadius = size.width / 2f
                for (i in 0 until rayCount) {
                    val angle = (i * 360f / rayCount) * (Math.PI / 180.0)
                    val nextAngle = ((i + 0.45f) * 360f / rayCount) * (Math.PI / 180.0)

                    val p1 = Offset(
                        (center.x + maxRadius * cos(angle)).toFloat(),
                        (center.y + maxRadius * sin(angle)).toFloat()
                    )
                    val p2 = Offset(
                        (center.x + maxRadius * cos(nextAngle)).toFloat(),
                        (center.y + maxRadius * sin(nextAngle)).toFloat()
                    )

                    val path = Path().apply {
                        moveTo(center.x, center.y)
                        lineTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        close()
                    }

                    drawPath(
                        path = path,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFBFE9FF).copy(alpha = 0.5f),
                                HealingBlue.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = maxRadius
                        )
                    )
                }
            }

            // Bursting Confetti / Golden Particles
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val particleCount = 28
                val p = burstProgress.value

                for (i in 0 until particleCount) {
                    val seed = i * 137
                    val angle = (seed % 360) * (Math.PI / 180.0)
                    val speed = 70f + (seed % 80)
                    val distance = speed * p * 1.8f
                    val x = (center.x + distance * cos(angle)).toFloat()
                    val y = (center.y + distance * sin(angle)).toFloat()

                    val alpha = (1f - p).coerceIn(0f, 1f)
                    val color = when (i % 4) {
                        0 -> Color(0xFFF4FBFF)
                        1 -> Color(0xFF7AD7FF)
                        2 -> HealingBlue
                        else -> HealingCyan
                    }

                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = (5f - p * 2.5f).coerceAtLeast(1.5f),
                        center = Offset(x, y)
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .size(168.dp)
                    .graphicsLayer {
                        scaleX = coinScale.value
                        scaleY = coinScale.value
                    }
            ) {
                drawLightStar(Offset(size.width / 2f, size.height / 2f), size.minDimension * 0.32f, 1f)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Badge Container with "+1 Moksha Point"
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF22173F)
            ),
            elevation = CardDefaults.cardElevation(10.dp),
            modifier = Modifier.graphicsLayer {
                scaleX = coinScale.value
                scaleY = coinScale.value
            }
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = null,
                        tint = HealingCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.reward_points, amount),
                        style = MaterialTheme.typography.titleLarge,
                        color = SacredGold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StarFlyToCounter(
    rewardEvent: RewardEvent,
    anchors: RewardAnchors,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val starPx = with(density) { 18.dp.toPx() }

    // Captured once per event so later taps do not bend the path mid-flight
    val start = remember(rewardEvent.timestamp) {
        anchors.lastTap ?: Offset(view.width / 2f, view.height / 2f)
    }
    val end = remember(rewardEvent.timestamp) {
        anchors.counterCenter ?: Offset(view.width - starPx, starPx * 2)
    }

    val pop = remember(rewardEvent.timestamp) { Animatable(0f) }
    val flight = remember(rewardEvent.timestamp) { Animatable(0f) }

    LaunchedEffect(rewardEvent.timestamp) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.KEYBOARD_TAP
        )
        pop.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        flight.animateTo(1f, tween(REWARD_LAND_MS - 400, easing = LinearOutSlowInEasing))
        onDismiss()
    }

    // A lifted arc, with a soft sideways drift that settles as the star reaches the counter
    val t = flight.value
    val control = Offset((start.x + end.x) / 2f, minOf(start.y, end.y) - starPx * 4)
    val along = quad(start, control, end, t)
    val wobble = (sin(t * Math.PI * 2) * starPx * 0.85f * (1f - t)).toFloat()
    val tangent = quad(start, control, end, (t + 0.02f).coerceAtMost(1f)) - along
    val len = kotlin.math.hypot(tangent.x.toDouble(), tangent.y.toDouble()).toFloat().coerceAtLeast(1f)
    val side = Offset(-tangent.y / len, tangent.x / len)
    val at = along + side * wobble

    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "+${rewardEvent.amount}",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF4FC3F7),
            modifier = Modifier
                .offset { IntOffset(start.x.roundToInt() - starPx.roundToInt(), (start.y - starPx * (1.8f + t)).roundToInt()) }
                .graphicsLayer { alpha = (pop.value * (1f - t)).coerceIn(0f, 1f) }
        )

        Canvas(Modifier.fillMaxSize()) {
            val trail = listOf(0.22f, 0.14f, 0.07f)
            for (back in trail) {
                val u = t - back
                if (u <= 0f) continue
                val p = quad(start, control, end, u)
                drawLightStar(p, starPx * (0.35f + 0.2f * (1f - back)), alpha = pop.value * (1f - back / 0.28f) * 0.55f)
            }
            val pulse = 0.92f + 0.08f * sin(t * Math.PI * 4).toFloat()
            drawLightStar(at, starPx * pulse * (1f - 0.2f * t), alpha = pop.value)
        }
    }
}

private fun quad(a: Offset, b: Offset, c: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(u * u * a.x + 2 * u * t * b.x + t * t * c.x, u * u * a.y + 2 * u * t * b.y + t * t * c.y)
}

/** A four-point star of blue healing light: white core, cyan points, soft blue halo. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLightStar(center: Offset, outer: Float, alpha: Float) {
    if (alpha <= 0f || outer <= 0f) return
    drawCircle(HealingBlue.copy(alpha = 0.22f * alpha), outer * 2.1f, center)
    drawCircle(Color(0xFF7AD7FF).copy(alpha = 0.35f * alpha), outer * 1.25f, center)
    drawPath(
        starPath(center, outer, outer * 0.38f),
        Brush.radialGradient(listOf(Color(0xFFF7FCFF), Color(0xFFBFE9FF), HealingBlue), center = center, radius = outer),
        alpha = alpha
    )
    drawPath(starPath(center, outer * 0.42f, outer * 0.12f, rotationDeg = 45f), Color.White.copy(alpha = 0.9f * alpha))
}

private fun starPath(center: Offset, outer: Float, inner: Float, rotationDeg: Float = -90f): Path {
    val path = Path()
    val rot = Math.toRadians(rotationDeg.toDouble())
    val points = 4
    val steps = points * 2
    for (i in 0 until steps) {
        val r = if (i % 2 == 0) outer else inner
        val a = rot + i * Math.PI / points
        val x = center.x + (r * cos(a)).toFloat()
        val y = center.y + (r * sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
