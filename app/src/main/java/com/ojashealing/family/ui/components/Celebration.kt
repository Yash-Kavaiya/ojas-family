package com.ojashealing.family.ui.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ojashealing.family.R
import com.ojashealing.family.ui.theme.OjasGradients
import com.ojashealing.family.ui.theme.Spacing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

private val ConfettiColors = listOf(
    Color(0xFFFF9A3C), Color(0xFFE8590C), Color(0xFFFFD54F), Color(0xFFD9467A),
    Color(0xFF7C3AED), Color(0xFF0F9D8A), Color(0xFF1D9BF0), Color(0xFF43A047)
)

private class Piece(val x: Float, val delay: Float, val speed: Float, val drift: Float, val spin: Float, val color: Color, val w: Float, val h: Float, val round: Boolean)

/**
 * A celebration over the whole screen: confetti falls, a trophy springs in, and the message shows.
 * The member closes it with the button (or by tapping anywhere).
 */
@Composable
fun CelebrationDialog(title: String, message: String, onDismiss: () -> Unit) {
    val fall = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val pieces = remember {
        val r = Random(7)
        List(90) {
            Piece(
                x = r.nextFloat(), delay = r.nextFloat() * 0.35f, speed = 0.75f + r.nextFloat() * 0.6f,
                drift = (r.nextFloat() - 0.5f) * 0.25f, spin = (r.nextFloat() - 0.5f) * 1440f,
                color = ConfettiColors[r.nextInt(ConfettiColors.size)],
                w = 6f + r.nextFloat() * 8f, h = 10f + r.nextFloat() * 10f, round = r.nextInt(4) == 0
            )
        }
    }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        fall.animateTo(1f, tween(3200, easing = LinearEasing))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val t = fall.value
                pieces.forEach { p ->
                    val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                    if (local <= 0f || local >= 1f) return@forEach
                    val y = -40f + local * p.speed * (size.height + 80f)
                    val x = p.x * size.width + sin(local * 2 * PI.toFloat() * 2) * 30f + p.drift * size.width * local
                    val alpha = if (local > 0.85f) (1f - local) / 0.15f else 1f
                    rotate(p.spin * local, Offset(x, y)) {
                        if (p.round) drawCircle(p.color.copy(alpha = alpha), radius = p.w / 1.5f, center = Offset(x, y))
                        else drawRect(p.color.copy(alpha = alpha), topLeft = Offset(x - p.w / 2, y - p.h / 2), size = Size(p.w, p.h))
                    }
                }
                // A soft starburst behind the trophy
                val c = center
                val rays = 12
                val len = size.minDimension * 0.32f * pop.value
                repeat(rays) { i ->
                    val a = (i * 2 * PI / rays).toFloat() + t
                    drawLine(
                        Color(0xFFFFE08A).copy(alpha = 0.35f * (1f - t * 0.6f)),
                        c + Offset(cos(a), sin(a)) * (len * 0.35f),
                        c + Offset(cos(a), sin(a)) * len,
                        strokeWidth = 6f
                    )
                }
            }
            Column(
                Modifier
                    .padding(Spacing.xl)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(OjasGradients.gold)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFF9A5B00), modifier = Modifier.size(52.dp)) }
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onDismiss) { Text(stringResource(R.string.celebrate_ok)) }
            }
        }
    }
}

/**
 * Which of [ids] are new since last time, remembered on the phone under [key]. The first time it
 * runs it only records what exists, so goals finished before this update are not celebrated again.
 */
fun newlyReached(context: Context, key: String, ids: Set<String>): Set<String> {
    val prefs = context.getSharedPreferences("ojas_celebrations", Context.MODE_PRIVATE)
    val seen = prefs.getStringSet(key, null)
    if (seen == null) {
        prefs.edit().putStringSet(key, ids).apply()
        return emptySet()
    }
    return ids - seen
}

/** Marks [ids] as celebrated. */
fun markCelebrated(context: Context, key: String, ids: Set<String>) {
    val prefs = context.getSharedPreferences("ojas_celebrations", Context.MODE_PRIVATE)
    val seen = prefs.getStringSet(key, emptySet()).orEmpty()
    prefs.edit().putStringSet(key, seen + ids).apply()
}
