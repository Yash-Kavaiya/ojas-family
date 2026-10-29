package com.ojashealing.family.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.ui.theme.SacredGold
import com.ojashealing.family.ui.theme.SaffronPrimary
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun OjasTopBar(
    profile: UserProfile?,
    modifier: Modifier = Modifier,
    onCounterPositioned: (Offset) -> Unit = {}
) {
    val points = profile?.mokshaPoints ?: 0

    // Count up just as the flying coin lands, then bump the pill
    val shownPoints by animateIntAsState(
        targetValue = points,
        animationSpec = tween(durationMillis = 450, delayMillis = 550),
        label = "mokshaCount"
    )
    val counterBump = remember { Animatable(1f) }
    var lastPoints by remember { mutableIntStateOf(points) }
    LaunchedEffect(points) {
        if (points > lastPoints) {
            delay(550)
            counterBump.animateTo(1.25f, tween(120))
            counterBump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        lastPoints = points
    }

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(SaffronPrimary, SacredGold))),
                contentAlignment = Alignment.Center
            ) {
                Text("ॐ", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile != null) {
                    Text(
                        stringResource(R.string.profile_level, profile.viewer.userLevel.rank),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val pointsLabel = stringResource(R.string.profile_points)
            Row(
                modifier = Modifier
                    .testTag("moksha_points_badge")
                    .graphicsLayer {
                        scaleX = counterBump.value
                        scaleY = counterBump.value
                    }
                    .onPlaced { onCounterPositioned(it.boundsInRoot().center) }
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s)
                    .semantics { contentDescription = "$points $pointsLabel" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(Icons.Filled.Stars, contentDescription = null, tint = SacredGold, modifier = Modifier.size(18.dp))
                Text("$shownPoints", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
