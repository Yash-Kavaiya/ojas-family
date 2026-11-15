package com.ojashealing.family.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.goals.Goal
import com.ojashealing.family.data.goals.GoalKind
import com.ojashealing.family.data.goals.GoalLevel
import com.ojashealing.family.data.goals.GoalStatus
import com.ojashealing.family.data.goals.UNLOCK_AFTER
import com.ojashealing.family.data.goals.unlockedLevels
import com.ojashealing.family.data.social.todayIst
import com.ojashealing.family.data.social.visibleThoughtIds
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.theme.Spacing

/** Today and yesterday in India ("yyyy-MM-dd"): a daily goal's count is alive only if it was ticked on one of them. */
fun istTodayAndYesterday(): Pair<String, String> = visibleThoughtIds(todayIst()).let { it[0] to it[1] }

@Composable
private fun GoalLevel.label(): String = stringResource(
    when (this) {
        GoalLevel.Easy -> R.string.goal_level_easy
        GoalLevel.Medium -> R.string.goal_level_medium
        GoalLevel.Hard -> R.string.goal_level_hard
    }
)

@Composable
private fun LevelChip(level: GoalLevel) {
    val color = when (level) {
        GoalLevel.Easy -> MaterialTheme.colorScheme.tertiary
        GoalLevel.Medium -> MaterialTheme.colorScheme.primary
        GoalLevel.Hard -> MaterialTheme.colorScheme.error
    }
    Surface(color = color.copy(alpha = 0.14f), contentColor = color, shape = RoundedCornerShape(50)) {
        Text(level.label(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

/**
 * Practice > Freedom Practice: every goal, in two parts. Sadhana goals are chosen by the member and
 * ticked every day; they are listed easy to hard and each level opens as the one before is achieved.
 * Activities are done and shared as a photo (posted in Community > Activity; paid members).
 */
@Composable
fun FreedomPractice(
    state: ProfileGoalsState,
    canSharePhoto: Boolean,
    ticking: String?,
    tickFailed: Boolean,
    onSubmit: (Goal) -> Unit,
    onTick: (Goal) -> Unit,
    onMembership: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.loaded) {
        LoadingState(modifier)
        return
    }
    val lang = goalLanguage()
    val (today, yesterday) = istTodayAndYesterday()
    val active = state.goals.filter { it.active }
    val byLevel = compareBy<Goal>({ it.level.ordinal }, { it.order })
    val activities = active.filter { it.kind == GoalKind.Photo }.sortedWith(byLevel)
    val sadhana = active.filter { it.kind.isSadhana }.sortedWith(byLevel)
    val achievedIds = state.achieved.map { it.id }.toSet()
    val open = unlockedLevels(active, achievedIds)
    val nextLocked = GoalLevel.entries.firstOrNull { it !in open && sadhana.any { g -> g.level == it } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.m, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        if (active.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.profile_goals_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@LazyColumn
        }
        if (sadhana.isNotEmpty()) item(key = "sadhanaTitle", contentType = "title") {
            PartHeader(stringResource(R.string.freedom_sadhana_title), stringResource(R.string.freedom_sadhana_body))
        }
        if (tickFailed) item(key = "tickFailed") {
            Text(stringResource(R.string.goal_tick_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        items(sadhana.filter { it.level in open }, key = { "s_" + it.id }, contentType = { "goal" }) { g ->
            val p = state.progress[g.id]
            GoalCard(g, lang) {
                when {
                    g.id in achievedIds -> StatusLine(Icons.Outlined.CheckCircle, stringResource(R.string.goal_achieved))
                    g.kind != GoalKind.Check -> Text(stringResource(R.string.goal_practice_auto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        val streak = p?.streakOn(today, yesterday) ?: 0
                        LinearProgressIndicator(progress = { (streak.toFloat() / g.days).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text(
                            if (streak > 0) stringResource(R.string.goal_day_of, streak, g.days) else stringResource(R.string.goal_days_target, g.days),
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (p?.tickedOn(today) == true) StatusLine(Icons.Outlined.CheckCircle, stringResource(R.string.goal_ticked_today))
                        else Button(onClick = { onTick(g) }, enabled = ticking == null, modifier = Modifier.fillMaxWidth()) {
                            if (ticking == g.id) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(stringResource(R.string.goal_tick_today))
                        }
                    }
                }
            }
        }
        if (nextLocked != null) item(key = "locked", contentType = "locked") {
            val before = GoalLevel.entries[nextLocked.ordinal - 1]
            val need = minOf(UNLOCK_AFTER, sadhana.count { it.level == before })
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.goal_level_locked, need, before.label(), nextLocked.label()),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (activities.isNotEmpty()) {
            item(key = "activitiesTitle", contentType = "title") {
                PartHeader(stringResource(R.string.freedom_activities_title), stringResource(R.string.freedom_activities_body), Modifier.padding(top = Spacing.m))
            }
            if (!canSharePhoto) item(key = "activitiesLocked") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.freedom_activities_locked), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onMembership) { Text(stringResource(R.string.freedom_see_membership)) }
                }
            }
            items(activities, key = { "a_" + it.id }, contentType = { "goal" }) { g ->
                val status = state.progress[g.id]?.status
                GoalCard(g, lang) {
                    when (status) {
                        GoalStatus.Approved -> StatusLine(Icons.Outlined.CheckCircle, stringResource(R.string.goal_achieved))
                        GoalStatus.Submitted -> StatusLine(Icons.Outlined.HourglassTop, stringResource(R.string.goal_waiting))
                        else -> {
                            if (status == GoalStatus.Rejected) Text(stringResource(R.string.goal_rejected_retry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { onSubmit(g) }, enabled = canSharePhoto, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.goal_submit_photo), modifier = Modifier.padding(start = Spacing.s))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartHeader(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** One goal: its name, level, what to do and the reward, then [action] (its state or button). */
@Composable
private fun GoalCard(goal: Goal, lang: String, action: @Composable () -> Unit) {
    OjasCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(goal.titleIn(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            LevelChip(goal.level)
        }
        goal.descriptionIn(lang).takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.goal_reward, goal.reward), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action()
    }
}

/**
 * The profile's own summary, filled in on its own: the sadhana being done now with its day count,
 * photos waiting for review, and everything achieved. The goals themselves live in Freedom Practice.
 */
@Composable
fun ProfileGoalsCard(state: ProfileGoalsState, onOpen: () -> Unit) {
    val lang = goalLanguage()
    val (today, yesterday) = istTodayAndYesterday()
    OjasCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.practice_tab_freedom), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        if (!state.loaded) return@OjasCard
        val doing = state.goals.filter { g ->
            val p = state.progress[g.id]
            p != null && p.status == GoalStatus.Active && p.streakOn(today, yesterday) > 0
        }.sortedBy { it.order }
        if (doing.isEmpty() && state.waiting.isEmpty() && state.achieved.isEmpty()) {
            Text(stringResource(R.string.profile_goals_auto), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        doing.forEach { g ->
            GoalLine(stringResource(R.string.goal_doing_days, g.titleIn(lang), state.progress[g.id]?.streakOn(today, yesterday) ?: 0, g.days), Icons.Outlined.LocalFireDepartment)
        }
        state.waiting.forEach { g -> GoalLine(g.titleIn(lang) + " · " + stringResource(R.string.goal_waiting), Icons.Outlined.HourglassTop) }
        if (state.achieved.isNotEmpty()) {
            if (doing.isNotEmpty() || state.waiting.isNotEmpty()) HorizontalDivider()
            Text(stringResource(R.string.profile_achieved), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            state.achieved.forEach { g -> GoalLine(achievedLabel(g, lang), Icons.Outlined.CheckCircle) }
        }
        TextButton(onClick = onOpen) { Text(stringResource(R.string.profile_goals_open)) }
    }
}

/** An achieved goal's line; sadhana goals also say for how many days they were done. */
@Composable
fun achievedLabel(goal: Goal, lang: String): String =
    if (goal.kind.isSadhana) stringResource(R.string.goal_achieved_days, goal.titleIn(lang), goal.days) else goal.titleIn(lang)

@Composable
private fun GoalLine(title: String, icon: ImageVector) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}
