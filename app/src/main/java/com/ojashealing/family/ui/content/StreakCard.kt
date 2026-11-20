package com.ojashealing.family.ui.content

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.practice.StreakSummary
import com.ojashealing.family.domain.LocalDateLike
import com.ojashealing.family.ui.theme.BrandNumberStyle
import com.ojashealing.family.ui.theme.Durations
import com.ojashealing.family.ui.theme.LocalReducedMotion
import com.ojashealing.family.ui.theme.OjasEasing
import com.ojashealing.family.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

/** Streak milestones the card counts toward, one after another. */
val STREAK_MILESTONES = listOf(3, 7, 21, 40, 108)

/** The next milestone above [current], or the last one once all are passed. */
fun nextMilestone(current: Int): Int = STREAK_MILESTONES.firstOrNull { it > current } ?: STREAK_MILESTONES.last()

private val FlameOrange = Color(0xFFFF8A3D)
private val FlameGold = Color(0xFFFFD54F)

/**
 * The top of the Practice tab: the current run of days with practice, the last 7 days as dots,
 * and how far it is to the next milestone. Zero days shows an invitation instead of a sad "0".
 */
@Composable
fun StreakCard(streak: StreakSummary, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val count = remember { Animatable(0f) }
    LaunchedEffect(streak.current) {
        if (reduced) count.snapTo(streak.current.toFloat())
        else count.animateTo(streak.current.toFloat(), tween(Durations.Slow + 200, easing = OjasEasing.Emphasized))
    }
    val lit = streak.current > 0
    // Read only while drawing (graphicsLayer below), so the card is not recomposed on every frame
    val flicker = com.ojashealing.family.ui.theme.loopingFloat(
        0.94f, 1.06f, infiniteRepeatable(tween(1100, easing = OjasEasing.Standard), RepeatMode.Reverse), rest = 1f, label = "flicker", active = lit,
    )
    val target = nextMilestone(streak.current)
    val toGoal by animateFloatAsState((streak.current.toFloat() / target).coerceIn(0f, 1f), tween(Durations.Slow, easing = OjasEasing.Emphasized), label = "goal")
    val description = pluralStringResource(R.plurals.streak_days_in_row, streak.current, streak.current)
    // The Quick Protection box colours, light or dark with the theme
    val box = com.ojashealing.family.ui.theme.featureBoxColors()

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(box.container)
            .padding(Spacing.l)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (lit) {
                    Canvas(Modifier.size(64.dp).graphicsLayer { scaleX = flicker.value; scaleY = flicker.value }) {
                        drawCircle(Brush.radialGradient(listOf(FlameOrange.copy(alpha = 0.45f), Color.Transparent)))
                    }
                }
                Icon(
                    Icons.Filled.LocalFireDepartment, null,
                    tint = if (lit) FlameOrange else box.muted,
                    modifier = Modifier.size(44.dp).graphicsLayer { scaleX = flicker.value; scaleY = flicker.value },
                )
            }
            Column(Modifier.weight(1f)) {
                if (lit) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Changes only when the whole number does, not on every frame of the count-up
                        val shownCount by remember { androidx.compose.runtime.derivedStateOf { count.value.toInt() } }
                        Text("$shownCount", style = com.ojashealing.family.ui.theme.BrandNumberLargeStyle, color = box.title)
                        Text(
                            pluralStringResource(R.plurals.streak_days_label, streak.current),
                            style = MaterialTheme.typography.titleSmall, color = box.muted,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        if (streak.doneToday) stringResource(R.string.streak_today_done) else stringResource(R.string.streak_keep_going),
                        style = MaterialTheme.typography.bodySmall, color = box.muted,
                    )
                } else {
                    Text(stringResource(R.string.streak_start_title), style = MaterialTheme.typography.titleMedium, color = box.title)
                    Text(stringResource(R.string.streak_start_body), style = MaterialTheme.typography.bodySmall, color = box.muted)
                }
            }
            if (streak.best > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.streak_best), style = MaterialTheme.typography.labelSmall, color = box.muted)
                    Text("${streak.best}", style = BrandNumberStyle, color = box.title)
                }
            }
        }

        WeekDots(streak, box)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(stringResource(R.string.streak_next_goal, target), style = MaterialTheme.typography.labelMedium, color = box.muted, modifier = Modifier.weight(1f))
                Text("${streak.current}/$target", style = MaterialTheme.typography.labelMedium, color = box.muted)
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(box.track)) {
                Box(
                    Modifier.fillMaxWidth(toGoal).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(FlameOrange, FlameGold)))
                )
            }
        }
    }
}

/** Mon..Sun style row: filled when practised, a ring around today, quiet when missed. */
@Composable
private fun WeekDots(streak: StreakSummary, box: com.ojashealing.family.ui.theme.FeatureBoxColors) {
    val locale = LocalConfiguration.current.locales[0]
    val letters = remember(streak.week, locale) {
        val f = SimpleDateFormat("EEEEE", locale).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
        streak.week.map { (iso, _) ->
            val d = LocalDateLike.parse(iso)
            if (d == null) "" else {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply { clear(); set(d.year, d.month - 1, d.day, 12, 0) }
                f.format(cal.time)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        streak.week.forEachIndexed { i, (_, done) ->
            val today = i == streak.week.lastIndex
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val dot = Modifier.size(30.dp).clip(CircleShape)
                Box(
                    when {
                        done -> dot.background(Brush.linearGradient(listOf(FlameOrange, FlameGold)))
                        today -> dot.border(2.dp, box.title, CircleShape)
                        else -> dot.background(box.track)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Filled.Check, null, tint = Color(0xFF3B1600), modifier = Modifier.size(16.dp))
                }
                Text(
                    letters.getOrElse(i) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (today) box.title else box.muted,
                )
            }
        }
    }
}
