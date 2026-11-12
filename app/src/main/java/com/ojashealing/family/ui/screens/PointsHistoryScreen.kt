package com.ojashealing.family.ui.screens

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import com.ojashealing.family.ui.theme.Durations
import com.ojashealing.family.ui.theme.OjasEasing
import com.ojashealing.family.ui.theme.staggeredEntrance
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ojashealing.family.ui.adaptive.LoadOlderWhenEnd
import com.ojashealing.family.ui.adaptive.loadOlderRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HelpCenter
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.points.LevelTargets
import com.ojashealing.family.data.points.PointHistoryItem
import com.ojashealing.family.data.points.PointSource
import com.ojashealing.family.data.points.groupByDay
import com.ojashealing.family.data.points.istDayKey
import com.ojashealing.family.data.points.levelProgress
import com.ojashealing.family.data.points.sourceOf
import com.ojashealing.family.data.points.totalsBySource
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.ui.components.EmptyState
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.NameBadges
import com.ojashealing.family.ui.theme.OjasGradients
import com.ojashealing.family.ui.theme.Spacing
import java.util.Date

private data class SourceLook(val icon: ImageVector, val color: Color, val label: Int)

private fun look(source: PointSource): SourceLook = when (source) {
    PointSource.DailyVisit -> SourceLook(Icons.Filled.WbSunny, Color(0xFFF59E0B), R.string.points_src_daily)
    PointSource.LikeThought -> SourceLook(Icons.Filled.Favorite, Color(0xFFE11D48), R.string.points_src_like_thought)
    PointSource.LikeQuestion -> SourceLook(Icons.Filled.ThumbUp, Color(0xFFDB2777), R.string.points_src_like_question)
    PointSource.Comment -> SourceLook(Icons.Filled.ChatBubble, Color(0xFF0284C7), R.string.points_src_comment)
    PointSource.Question -> SourceLook(Icons.Filled.HelpCenter, Color(0xFF7C3AED), R.string.points_src_question)
    PointSource.Answer -> SourceLook(Icons.Filled.QuestionAnswer, Color(0xFF0F9D8A), R.string.points_src_answer)
    PointSource.Goal -> SourceLook(Icons.Filled.EmojiEvents, Color(0xFFD69E00), R.string.points_src_goal)
    PointSource.Practice -> SourceLook(Icons.Filled.SelfImprovement, Color(0xFF0284C7), R.string.points_src_practice)
    PointSource.Gift -> SourceLook(Icons.Filled.CardGiftcard, Color(0xFFE8590C), R.string.points_src_gift)
    PointSource.PracticeBonus -> SourceLook(Icons.Filled.CheckCircle, Color(0xFF0F9D8A), R.string.points_src_practice_bonus)
    PointSource.Streak -> SourceLook(Icons.Filled.LocalFireDepartment, Color(0xFFFF7A2F), R.string.points_src_streak)
    PointSource.Profile -> SourceLook(Icons.Filled.Person, Color(0xFF7C3AED), R.string.points_src_profile)
    PointSource.AskAi -> SourceLook(Icons.Filled.AutoAwesome, Color(0xFF2F7CF6), R.string.points_src_ai)
    PointSource.GiftReceived -> SourceLook(Icons.Filled.CardGiftcard, Color(0xFFE8590C), R.string.points_src_gift_in)
    PointSource.GiftSent -> SourceLook(Icons.Filled.CardGiftcard, Color(0xFF9A8FA8), R.string.points_src_gift_out)
    PointSource.StaffAdd -> SourceLook(Icons.Filled.Stars, Color(0xFFD69E00), R.string.points_src_staff_add)
    PointSource.StaffDeduct -> SourceLook(Icons.Filled.Stars, Color(0xFF9A8FA8), R.string.points_src_staff_deduct)
    PointSource.Reversed -> SourceLook(Icons.Filled.CardGiftcard, Color(0xFF9A8FA8), R.string.points_src_reversed)
    PointSource.Other -> SourceLook(Icons.Filled.Stars, Color(0xFF9A8FA8), R.string.points_src_other)
}

/** Notes worth showing: messages people wrote. Practice and profile notes are internal ids. */
private fun showsNote(source: PointSource): Boolean = source in setOf(
    PointSource.Gift, PointSource.GiftReceived, PointSource.GiftSent, PointSource.StaffAdd, PointSource.StaffDeduct, PointSource.Reversed, PointSource.Other,
)

@Composable
private fun sourceLabel(item: PointHistoryItem): String {
    val src = sourceOf(item)
    val res = look(src).label
    return if (src == PointSource.GiftReceived || src == PointSource.GiftSent) stringResource(res, item.otherName.ifBlank { "…" }) else stringResource(res)
}

/** Tapping the Moksha Points pill opens this: the total, progress to the next level, and every award. */
@Composable
fun PointsHistoryScreen(
    profile: UserProfile?,
    history: List<PointHistoryItem>?,
    targets: LevelTargets,
    onBack: () -> Unit,
    hasMore: Boolean = false,
    onLoadOlder: () -> Unit = {},
    onHowItWorks: () -> Unit = {},
    onSend: (() -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                Text(stringResource(R.string.points_history_title), style = MaterialTheme.typography.titleLarge)
            }
            if (profile == null || history == null) {
                LoadingState(Modifier.weight(1f))
                return@Column
            }
            val historyState = rememberLazyListState()
            LoadOlderWhenEnd(historyState, hasMore, history.size, onLoadOlder)
            LazyColumn(
                state = historyState,
                contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                item(key = "hero", contentType = "hero") { PointsHero(profile, targets, Modifier.staggeredEntrance(0, key = "hero")) }
                item(key = "actions", contentType = "actions") {
                    Row(Modifier.fillMaxWidth().staggeredEntrance(1, key = "actions"), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedButton(onClick = onHowItWorks, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.points_how_it_works), maxLines = 1)
                        }
                        if (onSend != null) Button(onClick = onSend, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.CardGiftcard, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.points_send), maxLines = 1)
                        }
                    }
                }
                val totals = totalsBySource(history)
                if (totals.isNotEmpty()) item(key = "totals", contentType = "totals") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(vertical = Spacing.s)) {
                        items(totals, key = { it.first.name }, contentType = { "total" }) { (src, sum) -> TotalChip(src, sum) }
                    }
                }
                if (history.isEmpty()) item(key = "empty") {
                    EmptyState(Icons.Filled.Stars, stringResource(R.string.points_history_empty), stringResource(R.string.points_history_empty_body))
                }
                groupByDay(history).forEach { (day, items) ->
                    item(key = "day-$day") {
                        Text(
                            dayTitle(day, items.first().at),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.m, bottom = Spacing.xs)
                        )
                    }
                    items(items, key = { it.key }, contentType = { "event" }) { HistoryRow(it) }
                }
                loadOlderRow(hasMore, onLoadOlder)
            }
        }
    }
}

@Composable
private fun dayTitle(day: String, at: Long): String {
    val now = System.currentTimeMillis()
    return when (day) {
        istDayKey(now) -> stringResource(R.string.home_today)
        istDayKey(now - 86_400_000L) -> stringResource(R.string.points_yesterday)
        else -> DateFormat.getLongDateFormat(LocalContext.current).format(Date(at))
    }
}

@Composable
private fun PointsHero(profile: UserProfile, targets: LevelTargets, modifier: Modifier = Modifier) {
    val level = profile.viewer.userLevel.rank
    val next = targets.next(level)
    // The balance counts up to its value on arrival and whenever it changes
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(profile.mokshaPoints) {
        shown.animateTo(profile.mokshaPoints.toFloat(), tween(Durations.Slow + 300, easing = OjasEasing.Emphasized))
    }
    val progress by animateFloatAsState(
        levelProgress(profile.activeDays, profile.earnedPoints, next), tween(Durations.Slow, easing = OjasEasing.Emphasized), label = "levelProgress",
    )
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(OjasGradients.sunrise))
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Stars, contentDescription = null, tint = Color(0xFFFFF3C4), modifier = Modifier.size(30.dp))
            }
            Column {
                Text(stringResource(R.string.profile_points), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.9f))
                Text("${shown.value.toInt()}", style = MaterialTheme.typography.displaySmall, color = Color.White)
            }
        }
        if (profile.earnedPoints != profile.mokshaPoints) {
            Text(
                stringResource(R.string.points_earned) + ": ${profile.earnedPoints}",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.profile_level, level), style = MaterialTheme.typography.titleMedium, color = Color.White)
            NameBadges(profile.viewer.role, level, profile.viewer.memberLevel.rank, 18.dp)
        }
        if (next != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {}
            )
            Text(
                stringResource(
                    R.string.points_next_level,
                    level + 1,
                    (next.points - profile.earnedPoints).coerceAtLeast(0),
                    (next.days - profile.activeDays).coerceAtLeast(0)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        } else {
            Text(stringResource(R.string.points_top_level), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
    }
}

@Composable
private fun TotalChip(src: PointSource, sum: Int) {
    val l = look(src)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(l.color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(l.icon, contentDescription = null, tint = l.color, modifier = Modifier.size(16.dp))
        val name = if (src == PointSource.GiftReceived || src == PointSource.GiftSent) stringResource(R.string.points_src_gift) else stringResource(l.label)
        Text("$name · +$sum", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun HistoryRow(item: PointHistoryItem) {
    val l = look(sourceOf(item))
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(l.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(l.icon, contentDescription = null, tint = l.color, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(sourceLabel(item), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.note.isNotEmpty() && showsNote(sourceOf(item))) {
                Text("“${item.note}”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (item.at > 0) {
                Text(DateFormat.getTimeFormat(context).format(Date(item.at)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            (if (item.amount >= 0) "+" else "") + item.amount,
            style = MaterialTheme.typography.titleMedium,
            color = if (item.amount >= 0) Color(0xFF15803D) else MaterialTheme.colorScheme.error
        )
    }
}
