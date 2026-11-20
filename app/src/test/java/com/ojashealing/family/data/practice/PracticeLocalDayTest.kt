package com.ojashealing.family.data.practice

import com.ojashealing.family.domain.LocalDateLike
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Practices and streaks turn over at the member's own midnight, wherever they live. */
class PracticeLocalDayTest {
    private val india = TimeZone.getTimeZone("Asia/Kolkata")
    private val newYork = TimeZone.getTimeZone("America/New_York")
    private val auckland = TimeZone.getTimeZone("Pacific/Auckland")
    private fun utc(iso: String) = java.time.Instant.parse(iso).toEpochMilli()

    @Test fun todayIsThePhonesDate() {
        // 03:00 UTC on 9 Nov: still the evening of 8 Nov in New York, morning of 9 Nov in India
        val t = utc("2026-11-09T03:00:00Z")
        assertEquals(LocalDateLike(2026, 11, 8), practiceToday(t, newYork))
        assertEquals(LocalDateLike(2026, 11, 9), practiceToday(t, india))
        assertEquals(LocalDateLike(2026, 11, 9), practiceToday(t, auckland))
    }

    @Test fun theDayTurnsOverAtLocalMidnight() {
        // New York midnight on 9 Nov is 05:00 UTC
        assertEquals(LocalDateLike(2026, 11, 8), practiceToday(utc("2026-11-09T04:59:59Z"), newYork))
        assertEquals(LocalDateLike(2026, 11, 9), practiceToday(utc("2026-11-09T05:00:00Z"), newYork))
        // India midnight on 9 Nov is 18:30 UTC on 8 Nov
        assertEquals(LocalDateLike(2026, 11, 8), practiceToday(utc("2026-11-08T18:29:59Z"), india))
        assertEquals(LocalDateLike(2026, 11, 9), practiceToday(utc("2026-11-08T18:30:00Z"), india))
    }

    @Test fun theOffsetSentToTheServerFollowsSummerTime() {
        assertEquals(330, utcOffsetMinutes(utc("2026-11-09T03:00:00Z"), india))
        assertEquals(-300, utcOffsetMinutes(utc("2026-11-09T03:00:00Z"), newYork))
        assertEquals(-240, utcOffsetMinutes(utc("2026-07-01T12:00:00Z"), newYork))
        assertEquals(780, utcOffsetMinutes(utc("2026-11-09T03:00:00Z"), auckland))
    }

    @Test fun yesterdaysPracticeIsNotDoneTodayButKeepsTheStreak() {
        val logs = mapOf("2026-11-08" to DayLog(morning = false, night = false, any = true), "2026-11-07" to DayLog(false, false, any = true))
        val nextDay = practiceToday(utc("2026-11-09T05:00:00Z"), newYork)
        val s = streakSummary(logs, nextDay)
        assertFalse(s.doneToday)
        assertEquals(2, s.current)
        val sameDay = streakSummary(logs, practiceToday(utc("2026-11-09T04:00:00Z"), newYork))
        assertTrue(sameDay.doneToday)
        assertEquals(2, sameDay.current)
    }

    @Test fun aDayAlreadyLoggedAheadStaysToday() {
        val local = LocalDateLike(2026, 11, 20)
        // Practised on India's 21 Nov the evening before, by an older app: the server still calls it today
        assertEquals(LocalDateLike(2026, 11, 21), practiceDayOf(local, listOf("2026-11-19", "2026-11-21")))
        // Nothing ahead: the phone's date
        assertEquals(local, practiceDayOf(local, listOf("2026-11-19", "2026-11-20")))
        // Far-future stray data is ignored, as on the server
        assertEquals(local, practiceDayOf(local, listOf("2027-01-01")))
    }

    @Test fun addDaysCrossesMonthsAndYears() {
        assertEquals(LocalDateLike(2027, 1, 1), addDays(LocalDateLike(2026, 12, 30), 2))
        assertEquals(LocalDateLike(2026, 2, 28), addDays(LocalDateLike(2026, 3, 1), -1))
    }
}
