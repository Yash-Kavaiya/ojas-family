package com.ojashealing.family.data.points

import java.util.Calendar
import java.util.TimeZone

/** One Moksha Points award from users/{uid}/pointEvents. [key] is the event id, e.g. "like:thought:2026-11-07". */
data class PointHistoryItem(
    val key: String,
    val amount: Int,
    val reason: String,
    val at: Long,
    val note: String = "",
    /** For gifts between members: the other member's name. */
    val otherName: String = "",
)

/**
 * What each activity earns and the rules for sending points, from config/points. Defaults mirror
 * firebase/functions/src/pointsConfig.ts DEFAULT_POINTS.
 */
data class PointsConfig(
    val dailyOpen: Int = 1,
    val practice: Int = 2,
    val allPracticesBonus: Int = 3,
    val streakBonus: Int = 10,
    val streakBonusEvery: Int = 7,
    val like: Int = 1,
    val comment: Int = 1,
    val question: Int = 1,
    val answer: Int = 1,
    val profileField: Int = 5,
    val profileComplete: Int = 25,
    val aiDaily: Int = 1,
    val transfersEnabled: Boolean = true,
    val maxTransfer: Int = 100,
    val dailySendCap: Int = 100,
    val minLevelToSend: Int = 2,
) {
    companion object {
        fun from(m: Map<String, Any?>?): PointsConfig {
            val d = PointsConfig()
            m ?: return d
            fun n(k: String, def: Int) = (m[k] as? Number)?.toInt() ?: def
            return PointsConfig(
                dailyOpen = n("dailyOpen", d.dailyOpen), practice = n("practice", d.practice),
                allPracticesBonus = n("allPracticesBonus", d.allPracticesBonus), streakBonus = n("streakBonus", d.streakBonus),
                streakBonusEvery = n("streakBonusEvery", d.streakBonusEvery).coerceAtLeast(1),
                like = n("like", d.like), comment = n("comment", d.comment), question = n("question", d.question), answer = n("answer", d.answer),
                profileField = n("profileField", d.profileField), profileComplete = n("profileComplete", d.profileComplete),
                aiDaily = n("aiDaily", d.aiDaily), transfersEnabled = m["transfersEnabled"] as? Boolean ?: d.transfersEnabled,
                maxTransfer = n("maxTransfer", d.maxTransfer), dailySendCap = n("dailySendCap", d.dailySendCap),
                minLevelToSend = n("minLevelToSend", d.minLevelToSend),
            )
        }
    }

    /** The editable numbers, for Swamiji's and admins' editor, in the order shown. */
    fun numbers(): List<Pair<String, Int>> = listOf(
        "dailyOpen" to dailyOpen, "practice" to practice, "allPracticesBonus" to allPracticesBonus,
        "streakBonus" to streakBonus, "streakBonusEvery" to streakBonusEvery, "like" to like, "comment" to comment,
        "question" to question, "answer" to answer, "profileField" to profileField, "profileComplete" to profileComplete,
        "aiDaily" to aiDaily, "maxTransfer" to maxTransfer, "dailySendCap" to dailySendCap, "minLevelToSend" to minLevelToSend,
    )
}

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
enum class PointSource {
    DailyVisit, LikeThought, LikeQuestion, Comment, Question, Answer, Goal, Practice, PracticeBonus, Streak,
    Profile, AskAi, Gift, GiftReceived, GiftSent, StaffAdd, StaffDeduct, Reversed, Other
}

fun sourceOf(item: PointHistoryItem): PointSource = when (item.reason) {
    "daily" -> PointSource.DailyVisit
    "like" -> if (item.key.startsWith("like:question:")) PointSource.LikeQuestion else PointSource.LikeThought
    "comment" -> PointSource.Comment
    "question" -> PointSource.Question
    "answer" -> PointSource.Answer
    "goal" -> PointSource.Goal
    "gift" -> PointSource.Gift
    "practice_media" -> PointSource.Practice
    "practice_bonus" -> PointSource.PracticeBonus
    "streak_bonus" -> PointSource.Streak
    "profile", "profile_complete" -> PointSource.Profile
    "ai_question" -> PointSource.AskAi
    "transfer_in" -> PointSource.GiftReceived
    "transfer_out" -> PointSource.GiftSent
    "staff_add" -> PointSource.StaffAdd
    "staff_deduct" -> PointSource.StaffDeduct
    "transfer_reversed" -> PointSource.Reversed
    else -> PointSource.Other
}

/** Points earned per source, biggest first, for the summary chips above the list (gifts sent are not earnings). */
fun totalsBySource(items: List<PointHistoryItem>): List<Pair<PointSource, Int>> =
    items.groupBy(::sourceOf).map { (k, v) -> k to v.sumOf { it.amount } }.filter { it.second > 0 }.sortedByDescending { it.second }

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
