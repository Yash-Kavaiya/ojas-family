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
import com.ojashealing.family.ui.theme.ShimmerBlock
import com.ojashealing.family.ui.theme.staggeredEntrance
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Loading shows the shape of what's coming (cards with a soft sheen) instead of a bare spinner,
 * so the screen doesn't jump when content arrives.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.a11y_loading)
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.l).semantics { contentDescription = loadingLabel },
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        repeat(3) { i ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.s + 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    ShimmerBlock(Modifier.size(40.dp), CircleShape)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        ShimmerBlock(Modifier.width(140.dp).height(14.dp), RoundedCornerShape(7.dp))
                        ShimmerBlock(Modifier.width(90.dp).height(10.dp), RoundedCornerShape(5.dp))
                    }
                }
                ShimmerBlock(Modifier.fillMaxWidth().height(if (i == 0) 120.dp else 56.dp), RoundedCornerShape(14.dp))
            }
        }
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
