package com.ojashealing.family.ui.components

import com.ojashealing.family.ui.theme.loopingFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ojashealing.family.ui.theme.OjasGradients
import com.ojashealing.family.ui.theme.Spacing

/** Where a roadmap step stands. */
enum class StepState { Done, Current, Waiting, Upcoming }

/** One stop on a roadmap. [progress] (0..1) shows a bar under the current step. */
data class RoadStep(
    val title: String,
    val detail: String = "",
    val state: StepState,
    val icon: ImageVector? = null,
    val progress: Float? = null,
    val progressLabel: String = ""
)

/**
 * A vertical roadmap: a dot per step joined by a line. Done steps are filled saffron with a tick,
 * the current step pulses, waiting steps show an hourglass and later steps are locked.
 * [action] puts a button under the current step.
 */
@Composable
fun Roadmap(steps: List<RoadStep>, modifier: Modifier = Modifier, action: (@Composable (Int) -> Unit)? = null) {
    Column(modifier) {
        steps.forEachIndexed { i, step ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    StepDot(step)
                    if (i < steps.lastIndex) {
                        val doneLine = step.state == StepState.Done
                        Box(
                            Modifier.width(3.dp).weight(1f).clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (doneLine) Brush.verticalGradient(OjasGradients.saffron)
                                    else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))
                                )
                        )
                    }
                }
                Column(
                    Modifier.weight(1f).padding(bottom = if (i < steps.lastIndex) Spacing.l else 0.dp, top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val faded = step.state == StepState.Upcoming
                    Text(
                        step.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (faded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (step.detail.isNotEmpty()) {
                        Text(step.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    step.progress?.let { p ->
                        val shown by animateFloatAsState(p.coerceIn(0f, 1f), tween(900), label = "roadProgress")
                        LinearProgressIndicator(
                            progress = { shown },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer,
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {}
                        )
                        if (step.progressLabel.isNotEmpty()) {
                            Text(step.progressLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (step.state == StepState.Current) action?.invoke(i)
                }
            }
        }
    }
}

@Composable
private fun StepDot(step: RoadStep) {
    val ring by loopingFloat(1f, 1.18f, infiniteRepeatable(tween(900), RepeatMode.Reverse), rest = 1f, label = "ring")
    when (step.state) {
        StepState.Done -> Box(
            Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(OjasGradients.saffron)),
            contentAlignment = Alignment.Center
        ) { Icon(step.icon ?: Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        StepState.Current -> Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(32.dp).scale(ring).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) { step.icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp)) } }
        }
        StepState.Waiting -> Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp)) }
        StepState.Upcoming -> Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
    }
}
