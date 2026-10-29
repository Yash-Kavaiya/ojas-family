package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.OjasRepository
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun PaytmCoinOverlay(
    rewardEvent: OjasRepository.RewardEvent?,
    onDismiss: () -> Unit
) {
    if (rewardEvent == null) return

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

        // Auto dismiss after 2.2 seconds
        delay(2200)
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
                reason = rewardEvent.reason,
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
        coinScale.animateTo(1.0f, animationSpec = tween(150))
    }

    LaunchedEffect(Unit) {
        burstProgress.animateTo(1f, animationSpec = tween(1200, easing = FastOutSlowInEasing))
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
            // Rotating Sunburst / Aura Rays behind coin
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rayRotation)
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
                                Color(0xFFFFD54F).copy(alpha = 0.45f),
                                Color(0xFFFFA000).copy(alpha = 0.15f),
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
                        0 -> Color(0xFFFFD700)
                        1 -> Color(0xFFFF9800)
                        2 -> Color(0xFF00E5FF)
                        else -> Color(0xFFFF4081)
                    }

                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = (5f - p * 2.5f).coerceAtLeast(1.5f),
                        center = Offset(x, y)
                    )
                }
            }

            // Central Glowing Paytm-style Gold Moksha Coin
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(coinScale.value)
                    .shadow(18.dp, shape = CircleShape, spotColor = Color(0xFFFFD54F))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFF9C4),
                                Color(0xFFFFD54F),
                                Color(0xFFFFA000),
                                Color(0xFFE65100)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Outer coin rim
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFFFF176),
                                    Color(0xFFFFB300),
                                    Color(0xFFB7410E)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "ॐ",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF3E1F00),
                            lineHeight = 38.sp
                        )
                        Text(
                            text = "MOKSHA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            color = Color(0xFF3E1F00)
                        )
                    }
                }
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
            modifier = Modifier.scale(coinScale.value)
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
                        tint = SacredGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "+$amount Moksha Point${if (amount > 1) "s" else ""}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = SacredGold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = reason,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Ojas Energy Aligned • Body Chakra Rising",
                    fontSize = 11.sp,
                    color = HealingCyan,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
