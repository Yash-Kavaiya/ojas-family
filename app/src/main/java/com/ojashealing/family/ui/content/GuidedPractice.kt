package com.ojashealing.family.ui.content

import com.ojashealing.family.ui.theme.loopingFloat
import androidx.compose.runtime.remember
import com.ojashealing.family.ui.theme.pressScale
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.R
import com.ojashealing.family.data.practice.GuidedSections
import com.ojashealing.family.data.practice.MediaType
import com.ojashealing.family.data.practice.PRACTICE_POINTS
import com.ojashealing.family.data.practice.PracticeMedia
import com.ojashealing.family.data.practice.PracticeMediaRepository
import com.ojashealing.family.data.practice.PracticeStatus
import com.ojashealing.family.data.practice.clockLabel
import com.ojashealing.family.data.practice.minutesLabel
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.domain.ContentSection
import com.ojashealing.family.domain.Permissions
import com.ojashealing.family.ui.theme.OjasGradients
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** One guided practice card: its media, whether today's point is earned, how far they got, and access. */
data class GuidedCard(val media: PracticeMedia, val doneToday: Boolean, val share: Float, val resumeMs: Long, val locked: Boolean) {
    val section: ContentSection get() = media.section
}

/** Cards in the spec's order for the sections that have media; locked when the viewer may not open it yet. */
fun buildGuided(
    media: Map<ContentSection, PracticeMedia>,
    done: Set<ContentSection>,
    canDaily: Boolean,
    progress: (ContentSection, Int) -> Pair<Float, Long>
): List<GuidedCard> = GuidedSections.mapNotNull { s ->
    val m = media[s] ?: return@mapNotNull null
    val (share, resume) = if (s in done) 0f to 0L else progress(s, m.durationSec)
    GuidedCard(m, s in done, share, resume, locked = (s == ContentSection.Morning || s == ContentSection.Night) && !canDaily)
}

@OptIn(ExperimentalCoroutinesApi::class)
class GuidedPracticeViewModel(
    repo: PracticeMediaRepository,
    profile: Flow<UserProfile?>,
    private val progress: PracticeProgressStore
) : ViewModel() {
    private val refreshTick = MutableStateFlow(0)

    /** Null until loaded; empty when no guided media is set yet. */
    val cards: StateFlow<List<GuidedCard>?> = profile.filterNotNull().flatMapLatest { p ->
        combine(repo.observeMedia(), repo.observeDoneToday(p.uid), refreshTick) { media, done, _ ->
            buildGuided(media, done, Permissions.canDoDailyPractices(p.viewer)) { s, secs ->
                progress.share(s, secs) to progress.load(s).first
            }
        }
    }.catch { emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Re-read saved progress, e.g. when coming back from a session. */
    fun refresh() {
        refreshTick.value++
    }
}

fun ContentSection.guidedIcon(): ImageVector = when (this) {
    ContentSection.BlueLight -> Icons.Outlined.WaterDrop
    ContentSection.QuickProtection -> Icons.Outlined.Shield
    ContentSection.Morning -> Icons.Outlined.WbTwilight
    ContentSection.Night -> Icons.Outlined.Bedtime
    else -> Icons.Outlined.Stars
}

/** "2 of 4 done today" with one segment per practice, filled as each is finished. */
@Composable
fun GuidedHeader(cards: List<GuidedCard>, modifier: Modifier = Modifier) {
    val done = cards.count { it.doneToday }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.background(Brush.linearGradient(OjasGradients.twilight)).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.guided_title), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(stringResource(R.string.guided_progress, done, cards.size), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Brush.linearGradient(OjasGradients.gold)).padding(horizontal = Spacing.m, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Outlined.Stars, null, tint = Color(0xFF7A4500), modifier = Modifier.size(16.dp))
                    Text("${done * PRACTICE_POINTS}/${cards.size * PRACTICE_POINTS}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF3B2300))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cards.forEach { c ->
                    val fill by animateFloatAsState(if (c.doneToday) 1f else c.share, tween(700), label = "seg")
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth(fill).height(6.dp).background(c.section.tint().titleDark))
                    }
                }
            }
        }
    }
}

/** A guided practice: glowing icon, length and kind, and today's state (earn, resume, done or locked). */
@Composable
fun GuidedPracticeCard(card: GuidedCard, streak: PracticeStatus?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val tint = card.section.tint()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val box = if (dark) tint.boxDark else tint.boxLight
    val accent = if (dark) tint.titleDark else tint.titleLight
    val pulse by loopingFloat(0.85f, 1.15f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), rest = 1f, label = "p")

    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        onClick = onOpen, shape = RoundedCornerShape(20.dp), color = box,
        interactionSource = press,
        modifier = modifier.fillMaxWidth().pressScale(press, pressed = 0.98f),
    ) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            // The icon breathes in a soft halo; a ring around it shows how much of today's practice is played
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (!card.doneToday && !card.locked) Box(Modifier.size(58.dp).scale(pulse).clip(CircleShape).background(accent.copy(alpha = 0.16f)))
                Canvas(Modifier.size(60.dp)) {
                    val stroke = 4.dp.toPx()
                    drawCircle(accent.copy(alpha = 0.18f), (size.minDimension - stroke) / 2, style = Stroke(stroke))
                    val sweep = if (card.doneToday) 360f else 360f * card.share
                    if (sweep > 0f) drawArc(accent, -90f, sweep, false, Offset(stroke / 2, stroke / 2),
                        androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(
                        if (card.locked) Icons.Filled.Lock else if (card.doneToday) Icons.Outlined.TaskAlt else card.section.guidedIcon(),
                        contentDescription = null, tint = box, modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(card.section.label()), style = MaterialTheme.typography.titleMedium, color = accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (card.media.title.isNotBlank()) {
                    Text(card.media.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(if (card.media.type == MediaType.Audio) Icons.Outlined.Headphones else Icons.Outlined.Videocam, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    val (n, minutes) = minutesLabel(card.media.durationSec)
                    Text(
                        stringResource(if (minutes) R.string.guided_minutes else R.string.guided_seconds, n) + " · " +
                            stringResource(if (card.media.type == MediaType.Audio) R.string.media_audio else R.string.media_video),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Morning and night also build the 21-day streak
                val streakDays = when (card.section) {
                    ContentSection.Morning -> streak?.morningStreak
                    ContentSection.Night -> streak?.bothStreak
                    else -> null
                }
                if (streakDays != null && streakDays > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Outlined.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.practice_streak, streakDays), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            StatusTag(card, accent)
        }
    }
}

@Composable
private fun StatusTag(card: GuidedCard, accent: Color) {
    when {
        card.locked -> Text(stringResource(R.string.guided_locked), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            modifier = Modifier.padding(start = Spacing.xs).fillMaxWidth(0.3f))
        card.doneToday -> Text(stringResource(R.string.guided_done), style = MaterialTheme.typography.labelMedium, color = accent)
        else -> Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(OjasGradients.saffron)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.media_play), tint = Color.White)
            }
            Text(
                if (card.resumeMs > 5_000) stringResource(R.string.guided_resume, clockLabel(card.resumeMs)) else stringResource(R.string.guided_earn, PRACTICE_POINTS),
                style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1
            )
        }
    }
}
