package com.ojashealing.family.data.social

import com.ojashealing.family.domain.LocalDateLike
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/** The Thought of the Day comes at this hour in each member's own time (functions/src/thoughtTime.ts). */
const val THOUGHT_HOUR = 4

/** Whose thought is today's here: the local date, moving on at 4 am rather than midnight. */
fun thoughtToday(nowMillis: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): LocalDateLike =
    Calendar.getInstance(zone).apply { timeInMillis = nowMillis - THOUGHT_HOUR * 3_600_000L }.let {
        LocalDateLike(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }

/** 4 am local time on the thought's day ("yyyy-mm-dd"), or null when [day] is not a date. */
fun thoughtStartMillis(day: String, zone: TimeZone = TimeZone.getDefault()): Long? {
    val parts = day.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    return Calendar.getInstance(zone).apply { clear(); set(parts[0], parts[1] - 1, parts[2], THOUGHT_HOUR, 0) }.timeInMillis
}

/** The push topic for phones [offsetMinutes] from UTC; the server sends each zone its thought at 4 am. */
fun thoughtTopic(offsetMinutes: Int): String = "thoughtOfDay_${if (offsetMinutes < 0) "m" else "p"}${abs(offsetMinutes)}"

/** This phone's topic right now. */
fun currentThoughtTopic(): String = thoughtTopic(TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000)
