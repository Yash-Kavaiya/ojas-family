package com.ojashealing.family.data.points

import java.util.Calendar
import java.util.TimeZone

/** One Moksha Points award from users/{uid}/pointEvents. [key] is the event id, e.g. "like:thought:2026-11-07". */
data class PointHistoryItem(val key: String, val amount: Int, val reason: String, val at: Long, val note: String = "")

data class LevelTarget(val days: Int, val points: Int)

/** Mirrors config/levels on the server (functions/src/levels.ts DEFAULT_LEVELS). */
data class LevelTargets(
    val l2: LevelTarget = LevelTarget(7, 7),
    val l3: LevelTarget = LevelTarget(90, 200),
    val l4: LevelTarget = LevelTarget(365, 10000)
) {
    /** The next level's target, or null at Level 4. */
    fun next(level: Int): LevelTarget? = when (level) {
        1 -> l2
        2 -> l3
        3 -> l4
        else -> null
    }
}

/** Where each point came from, for the history list's icon and label. */
enum class PointSource { DailyVisit, LikeThought, LikeQuestion, Comment, Question, Answer, Goal, Gift, Other }

fun sourceOf(item: PointHistoryItem): PointSource = when (item.reason) {
    "daily" -> PointSource.DailyVisit
    "like" -> if (item.key.startsWith("like:question:")) PointSource.LikeQuestion else PointSource.LikeThought
    "comment" -> PointSource.Comment
    "question" -> PointSource.Question
    "answer" -> PointSource.Answer
    "goal" -> PointSource.Goal
    "gift" -> PointSource.Gift
    else -> PointSource.Other
}

/** Points per source, biggest first, for the summary chips above the list. */
fun totalsBySource(items: List<PointHistoryItem>): List<Pair<PointSource, Int>> =
    items.groupBy(::sourceOf).map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }

/** Day key (IST, the server's points day) for grouping: yyyy-MM-dd. */
fun istDayKey(millis: Long): String {
    val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply { timeInMillis = millis }
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

/** History grouped by IST day, newest day first; each day keeps its items newest first. */
fun groupByDay(items: List<PointHistoryItem>): List<Pair<String, List<PointHistoryItem>>> =
    items.sortedByDescending { it.at }.groupBy { istDayKey(it.at) }.toList()

/** 0..1 progress to the next level: the weaker of the days and points requirements. */
fun levelProgress(activeDays: Int, points: Int, target: LevelTarget?): Float {
    target ?: return 1f
    val d = if (target.days <= 0) 1f else (activeDays.toFloat() / target.days).coerceIn(0f, 1f)
    val p = if (target.points <= 0) 1f else (points.toFloat() / target.points).coerceIn(0f, 1f)
    return minOf(d, p)
}
