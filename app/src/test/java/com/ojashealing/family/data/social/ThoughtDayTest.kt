package com.ojashealing.family.data.social

import com.ojashealing.family.data.notify.NotifGroup
import com.ojashealing.family.data.notify.SeenState
import com.ojashealing.family.data.notify.SharedLine
import com.ojashealing.family.data.notify.mergeInbox
import com.ojashealing.family.domain.LocalDateLike
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.util.TimeZone

class ThoughtDayTest {
    private val newYork = TimeZone.getTimeZone("America/New_York")
    private val india = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(s: String) = OffsetDateTime.parse(s).toInstant().toEpochMilli()

    @Test fun theDayStartsAt4amLocalTime() {
        assertEquals(LocalDateLike(2026, 11, 17), thoughtToday(at("2026-11-18T03:59:00-05:00"), newYork))
        assertEquals(LocalDateLike(2026, 11, 18), thoughtToday(at("2026-11-18T04:00:00-05:00"), newYork))
        assertEquals(LocalDateLike(2026, 11, 17), thoughtToday(at("2026-11-18T03:30:00+05:30"), india))
        assertEquals(LocalDateLike(2026, 11, 18), thoughtToday(at("2026-11-18T04:00:00+05:30"), india))
    }

    @Test fun eachTimeZoneHasItsOwnTopicLikeTheServer() {
        assertEquals("thoughtOfDay_p330", thoughtTopic(330))
        assertEquals("thoughtOfDay_m300", thoughtTopic(-300))
        assertEquals("thoughtOfDay_p0", thoughtTopic(0))
    }

    @Test fun theBellKeepsTomorrowsThoughtHiddenUntil4amHere() {
        // The line is written at 4 am in India: 17:30 the evening before in New York
        val written = at("2026-11-17T17:30:00-05:00")
        val line = SharedLine("t", NotifGroup.Thought, "thought", "Be still", "", "thoughts/2026-11-18", "", written)
        val seen = SeenState(seen = mapOf("thought" to at("2026-11-17T20:00:00-05:00")), opened = emptySet(), loaded = true)

        val evening = mergeInbox(emptyList(), listOf(line), seen, emptyMap(), "me", at("2026-11-17T21:00:00-05:00"), newYork)
        assertTrue(evening.isEmpty())

        val morning = mergeInbox(emptyList(), listOf(line), seen, emptyMap(), "me", at("2026-11-18T05:00:00-05:00"), newYork)
        assertEquals(1, morning.size)
        // It counts from 4 am here, so it is new even though the member looked at thoughts the evening before
        assertEquals(at("2026-11-18T04:00:00-05:00"), morning[0].createdAt)
        assertTrue(morning[0].unread)
    }
}
