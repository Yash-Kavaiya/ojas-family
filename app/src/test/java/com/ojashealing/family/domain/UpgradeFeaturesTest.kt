package com.ojashealing.family.domain

import com.ojashealing.family.data.points.PointHistoryItem
import com.ojashealing.family.data.points.PointSource
import com.ojashealing.family.data.points.PointsConfig
import com.ojashealing.family.data.points.sourceOf
import com.ojashealing.family.data.points.totalsBySource
import com.ojashealing.family.data.practice.DayLog
import com.ojashealing.family.data.practice.MediaType
import com.ojashealing.family.data.practice.lastDays
import com.ojashealing.family.data.practice.parsePracticeMedia
import com.ojashealing.family.data.practice.streakSummary
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.ui.content.SectionUi
import com.ojashealing.family.ui.content.nextMilestone
import com.ojashealing.family.ui.content.practiceRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeFeaturesTest {

    private fun profile(
        photo: String = "", name: String = "", dob: String = "", gender: String = "", country: String = "",
        state: String = "", city: String = "", about: String = "", language: String = "",
    ) = UserProfile(
        uid = "u", displayName = name, photoUrl = photo, dob = dob, gender = gender, country = country, state = state, city = city,
        language = language, profileComplete = true, hideProfile = false, hideGoals = false, notificationPrefs = emptyMap(),
        morningReminder = "06:00", nightReminder = "21:30", mokshaPoints = 0, activeDays = 0, viewer = Viewer(UserLevel.L1, MemberLevel.None, Role.Member), about = about,
    )

    @Test
    fun profileCompletionCountsEachItem() {
        assertEquals(0, profileCompletion(profile()).percent)
        val half = profileCompletion(profile(name = "Meera", language = "gu", gender = "female"))
        assertEquals(setOf(ProfileItem.Name, ProfileItem.Language, ProfileItem.Gender), half.done)
        assertEquals(43, half.percent)
        val full = profileCompletion(profile("p", "Meera", "1990-01-01", "female", "IN", "GJ", "Rajkot", "Meditating every day", "gu"))
        assertTrue(full.complete)
        assertEquals(100, full.percent)
        assertTrue(full.missing.isEmpty())
        // A short "about" and a country without a state or city don't count
        val partial = profileCompletion(profile(about = "hi", country = "IN"))
        assertFalse(ProfileItem.About in partial.done)
        assertFalse(ProfileItem.Location in partial.done)
    }

    @Test
    fun profileItemsMatchTheServer() {
        // firebase/functions/src/profilePoints.ts PROFILE_ITEMS
        assertEquals(listOf("photo", "name", "dob", "gender", "location", "about", "language"), ProfileItem.entries.map { it.id })
    }

    @Test
    fun streakCountsAnyPracticeAndKeepsTodayOpen() {
        val today = LocalDateLike(2026, 11, 12)
        val days = lastDays(today, 10)
        // Yesterday and the two days before: today not yet practised doesn't break it
        val logs = mapOf(days[1] to DayLog(false, false, any = true), days[2] to DayLog(true, false), days[3] to DayLog(false, true), days[5] to DayLog(false, false, any = true))
        val s = streakSummary(logs, today)
        assertEquals(3, s.current)
        assertEquals(3, s.best)
        assertFalse(s.doneToday)
        assertEquals(7, s.week.size)
        assertEquals(days[0], s.week.last().first)
        assertEquals(listOf(false, true, false, true, true, true, false), s.week.map { it.second })
        val withToday = streakSummary(logs + (days[0] to DayLog(false, false, any = true)), today)
        assertEquals(4, withToday.current)
        assertTrue(withToday.doneToday)
    }

    @Test
    fun milestonesStepUp() {
        assertEquals(3, nextMilestone(0))
        assertEquals(7, nextMilestone(3))
        assertEquals(21, nextMilestone(20))
        assertEquals(108, nextMilestone(500))
    }

    @Test
    fun practiceMediaReadsYouTubeEntries() {
        val yt = parsePracticeMedia(ContentSection.QuickProtection, mapOf("kind" to "youtube", "videoId" to "KPRvRrJgmVk", "durationSec" to 600, "title" to "Vidhi 1"))
        assertEquals(MediaType.YouTube, yt?.type)
        assertEquals("KPRvRrJgmVk", yt?.url)
        assertEquals(MediaType.Audio, parsePracticeMedia(ContentSection.Night, mapOf("kind" to "audio", "url" to "https://x/a.m4a", "durationSec" to 60))?.type)
        assertNull(parsePracticeMedia(ContentSection.Night, mapOf("kind" to "video", "url" to "", "durationSec" to 60)))
        assertNull(parsePracticeMedia(ContentSection.Night, mapOf("kind" to "youtube", "durationSec" to 60)))
    }

    @Test
    fun practiceTabShowsOneMeditationPerGuidedSection() {
        fun item(id: String, s: ContentSection) = ContentItem(id = id, section = s, videoId = id, title = id) to Access.Open
        val sections = listOf(
            SectionUi(ContentSection.BlueLight, listOf(item("b1", ContentSection.BlueLight), item("b2", ContentSection.BlueLight))),
            SectionUi(ContentSection.QuickProtection, listOf(item("v1", ContentSection.QuickProtection), item("v2", ContentSection.QuickProtection))),
            SectionUi(ContentSection.Morning, listOf(item("m1", ContentSection.Morning), item("m2", ContentSection.Morning))),
        )
        // Blue light has its own media: no YouTube row; the others show only their first video
        val rows = practiceRows(sections, setOf(ContentSection.BlueLight))!!
        assertEquals(listOf(ContentSection.QuickProtection, ContentSection.Morning), rows.map { it.section })
        assertEquals(listOf("v1"), rows[0].items.map { it.first.id })
        assertEquals(listOf("m1"), rows[1].items.map { it.first.id })
    }

    @Test
    fun pointsConfigReadsServerValuesWithDefaults() {
        val c = PointsConfig.from(mapOf("practice" to 7L, "transfersEnabled" to false, "streakBonusEvery" to 0))
        assertEquals(7, c.practice)
        assertFalse(c.transfersEnabled)
        assertEquals(1, c.streakBonusEvery)
        assertEquals(PointsConfig().profileField, c.profileField)
        assertEquals(PointsConfig(), PointsConfig.from(null))
    }

    @Test
    fun newPointSourcesAndSentGiftsAreNotEarnings() {
        fun h(reason: String, amount: Int) = PointHistoryItem("k-$reason", amount, reason, 0)
        assertEquals(PointSource.GiftReceived, sourceOf(h("transfer_in", 5)))
        assertEquals(PointSource.GiftSent, sourceOf(h("transfer_out", -5)))
        assertEquals(PointSource.Streak, sourceOf(h("streak_bonus", 10)))
        assertEquals(PointSource.Profile, sourceOf(h("profile_complete", 25)))
        assertEquals(PointSource.AskAi, sourceOf(h("ai_question", 1)))
        val totals = totalsBySource(listOf(h("transfer_out", -5), h("transfer_in", 3), h("daily", 1)))
        assertEquals(listOf(PointSource.GiftReceived, PointSource.DailyVisit), totals.map { it.first })
    }
}
