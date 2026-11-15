package com.ojashealing.family.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.ui.theme.Spacing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ojashealing.family.ui.theme.HealingBlue
import com.ojashealing.family.ui.theme.HealingCyan
import com.ojashealing.family.ui.theme.OjasEasing
import com.ojashealing.family.ui.theme.loopingFloat
import com.ojashealing.family.ui.theme.staggeredEntrance
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Loading: soft blue healing light pours from the top of the area to the bottom, again and again, with
 * the lotus resting in the middle. It fills whatever space it is given, on any screen size, and has no
 * background of its own. With animations off the light rests as a still glow.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.a11y_loading)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val travel = loopingFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = OjasEasing.Standard), RepeatMode.Restart), rest = 0.5f, label = "loadingLight")
    val breathe = loopingFloat(0.92f, 1.06f, infiniteRepeatable(tween(1900, easing = OjasEasing.Standard), RepeatMode.Reverse), rest = 1f, label = "loadingLotus")
    // Stronger on the light theme, where pale blue would be lost on the purple page
    val strength = if (dark) 0.55f else 0.42f
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = loadingLabel }
            .drawBehind {
                val band = size.height * 0.5f
                // The band's centre travels from above the top edge to below the bottom edge
                val centre = -band / 2 + travel.value * (size.height + band)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.3f to HealingCyan.copy(alpha = 0.28f * strength),
                        0.5f to HealingBlue.copy(alpha = strength),
                        0.7f to HealingCyan.copy(alpha = 0.28f * strength),
                        1f to Color.Transparent,
                        startY = centre - band / 2,
                        endY = centre + band / 2,
                    ),
                    topLeft = Offset(0f, centre - band / 2),
                    size = Size(size.width, band),
                )
                // A thin bright line at the front of the light
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFBFE9FF).copy(alpha = if (dark) 0.9f else 0.7f), Color.Transparent)),
                    topLeft = Offset(0f, centre - 1.dp.toPx()),
                    size = Size(size.width, 2.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ojas_lotus),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp).graphicsLayer { scaleX = breathe.value; scaleY = breathe.value }.clip(CircleShape),
        )
    }
}

/** A small inline spinner for places where the area is too small for skeleton cards. */
@Composable
fun LoadingSpinner(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

// Empty lists always explain what will appear here and, when possible, what to do next
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null
) {
    CenteredMessage(modifier = modifier, icon = icon, title = title, body = body) {
        action?.let { (label, onClick) -> Button(onClick = onClick) { Text(label) } }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    CenteredMessage(
        modifier = modifier,
        icon = Icons.Outlined.CloudOff,
        title = stringResource(R.string.error_generic),
        body = message
    ) {
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun CenteredMessage(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    body: String,
    action: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .staggeredEntrance(0, key = title)
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggeredEntrance(1, key = title)
        )
        Text(
            text = body,
            modifier = Modifier.staggeredEntrance(2, key = title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Box(Modifier.staggeredEntrance(3, key = title)) { action() }
    }
}

// Shown on content the viewer's level has not unlocked yet, e.g. "Unlocks at Level 2"
@Composable
fun LevelLockBadge(requirement: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(requirement, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
