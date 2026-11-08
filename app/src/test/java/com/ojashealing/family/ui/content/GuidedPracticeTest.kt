package com.ojashealing.family.ui.content

import com.ojashealing.family.data.practice.MediaType
import com.ojashealing.family.data.practice.PracticeMedia
import com.ojashealing.family.data.practice.clockLabel
import com.ojashealing.family.data.practice.minutesLabel
import com.ojashealing.family.data.practice.watchedEnough
import com.ojashealing.family.domain.ContentSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedPracticeTest {

    private fun media(s: ContentSection, secs: Int = 600) = PracticeMedia(s, "https://x/${s.id}.mp4", MediaType.Video, secs, "")

    @Test
    fun cardsFollowSpecOrderAndSkipSectionsWithoutMedia() {
        val cards = buildGuided(
            mapOf(ContentSection.Night to media(ContentSection.Night), ContentSection.BlueLight to media(ContentSection.BlueLight)),
            done = emptySet(), canDaily = true
        ) { _, _ -> 0f to 0L }
        assertEquals(listOf(ContentSection.BlueLight, ContentSection.Night), cards.map { it.section })
    }

    @Test
    fun morningAndNightLockWithoutDailyPracticeAccess() {
        val all = listOf(ContentSection.BlueLight, ContentSection.QuickProtection, ContentSection.Morning, ContentSection.Night)
        val cards = buildGuided(all.associateWith { media(it) }, emptySet(), canDaily = false) { _, _ -> 0f to 0L }
        assertEquals(listOf(false, false, true, true), cards.map { it.locked })
    }

    @Test
    fun doneTodayIgnoresLeftoverProgress() {
        val cards = buildGuided(mapOf(ContentSection.BlueLight to media(ContentSection.BlueLight)), setOf(ContentSection.BlueLight), true) { _, _ -> 0.5f to 300_000L }
        assertTrue(cards.single().doneToday)
        assertEquals(0f, cards.single().share)
        assertEquals(0L, cards.single().resumeMs)
    }

    @Test
    fun onlyRealPlayingTimeCounts() {
        assertTrue(watchedEnough(watchedMs = 540_000, durationMs = 600_000))
        assertFalse(watchedEnough(watchedMs = 120_000, durationMs = 600_000))
        assertFalse(watchedEnough(watchedMs = 0, durationMs = 0))
    }

    @Test
    fun labels() {
        assertEquals("29:18", clockLabel(1_758_000))
        assertEquals("1:02:05", clockLabel(3_725_000))
        assertEquals(29 to true, minutesLabel(1758))
        assertEquals(45 to false, minutesLabel(45))
    }
}
