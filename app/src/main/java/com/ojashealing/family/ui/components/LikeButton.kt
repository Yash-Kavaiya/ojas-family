package com.ojashealing.family.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.ui.theme.LotusRose
import com.ojashealing.family.ui.theme.SacredGold
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/** The one like button for thoughts and posts: the heart pops and a small burst plays when liking. */
@Composable
fun LikeButton(liked: Boolean, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) } // 0 → 1 while the burst plays; 1 = idle (invisible)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(48.dp)) {
            val t = burst.value
            if (t >= 1f) return@Canvas
            val alpha = 1f - t
            val r = size.minDimension / 2 * (0.45f + 0.55f * t)
            repeat(BURST_DOTS) { i ->
                val angle = Math.toRadians(i * 360.0 / BURST_DOTS - 90).toFloat()
                drawCircle(
                    color = (if (i % 2 == 0) LotusRose else SacredGold).copy(alpha = alpha),
                    radius = 3.dp.toPx() * (1f - t * 0.5f),
                    center = center + Offset(cos(angle) * r, sin(angle) * r)
                )
            }
        }
        IconButton(
            onClick = {
                if (!liked) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        scale.snapTo(0.6f)
                        scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                    }
                    scope.launch {
                        burst.snapTo(0f)
                        burst.animateTo(1f, tween(450))
                    }
                }
                onToggle()
            },
            enabled = enabled
        ) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(R.string.action_like),
                tint = if (liked) LotusRose else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            )
        }
    }
}

private const val BURST_DOTS = 8
