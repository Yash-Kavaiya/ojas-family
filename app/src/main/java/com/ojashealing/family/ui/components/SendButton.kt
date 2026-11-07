package com.ojashealing.family.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.ui.theme.OjasGradients

/** Round saffron send button with the Ojas paper plane; it presses in and tilts as it is tapped. */
@Composable
fun SendButton(
    onClick: () -> Unit,
    enabled: Boolean,
    busy: Boolean = false,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.5f), label = "sendScale")
    val tilt by animateFloatAsState(if (pressed) -18f else 0f, spring(dampingRatio = 0.6f), label = "sendTilt")
    val active = enabled && !busy
    val background = if (active) Brush.linearGradient(OjasGradients.saffron)
    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (active) 6.dp else 0.dp, CircleShape, ambientColor = Color(0xFFE8590C), spotColor = Color(0xFFE8590C))
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = active, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(size * 0.45f), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            Icon(
                painterResource(R.drawable.ic_send_ojas),
                contentDescription = stringResource(R.string.action_send),
                tint = if (active) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(size * 0.52f).graphicsLayer { rotationZ = tilt }
            )
        }
    }
}

/** Full-width saffron send button with a label and the Ojas paper plane, for big composer screens. */
@Composable
fun SendPillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    busy: Boolean = false,
    modifier: Modifier = Modifier
) {
    val active = enabled && !busy
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(if (active) 8.dp else 0.dp, shape, ambientColor = Color(0xFFE8590C), spotColor = Color(0xFFE8590C))
            .clip(shape)
            .background(
                if (active) Brush.horizontalGradient(OjasGradients.saffron)
                else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
            )
            .clickable(enabled = active, role = Role.Button, onClick = onClick),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.material3.Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
            Icon(
                painterResource(R.drawable.ic_send_ojas),
                contentDescription = null,
                tint = if (active) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
