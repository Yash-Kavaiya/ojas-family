package com.ojashealing.family.data.practice

import com.ojashealing.family.domain.ContentSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeDownloadsTest {
    private fun video(s: ContentSection, url: String) = PracticeMedia(s, url, MediaType.Video, 600, "")
    private val a = "https://x/o/practice%2Fblue_light.mp4?alt=media&token=1"
    private val b = "https://x/o/practice%2Fblue_light.mp4?alt=media&token=2"

    @Test fun fileNameIsStableAndKeepsExtension() {
        assertEquals(practiceFileName(a), practiceFileName(a))
        assertNotEquals(practiceFileName(a), practiceFileName(b))
        assertTrue(practiceFileName(a).endsWith(".mp4"))
        assertTrue(practiceFileName("https://x/o/practice%2Fnight.m4a?alt=media").endsWith(".m4a"))
        assertTrue(practiceFileName("https://x/o/whatever?alt=media").endsWith(".mp4"))
    }

    @Test fun emptyConfigDeletesNothing() {
        assertEquals(SyncPlan(emptyList(), emptyList()), planSync(emptyMap(), listOf(practiceFileName(a))))
    }

    @Test fun replacedVideoDeletesOldAndDownloadsNew() {
        val plan = planSync(mapOf(ContentSection.BlueLight to video(ContentSection.BlueLight, b)), listOf(practiceFileName(a)))
        assertEquals(listOf(practiceFileName(a)), plan.delete)
        assertEquals(listOf(b), plan.download.map { it.url })
    }

    @Test fun finishedFileIsKept() {
        val plan = planSync(mapOf(ContentSection.BlueLight to video(ContentSection.BlueLight, a)), listOf(practiceFileName(a)))
        assertEquals(SyncPlan(emptyList(), emptyList()), plan)
    }

    @Test fun partFileIsNotReadyAndIsRedownloaded() {
        val plan = planSync(mapOf(ContentSection.BlueLight to video(ContentSection.BlueLight, a)), listOf(practiceFileName(a) + ".part"))
        assertEquals(listOf(a), plan.download.map { it.url })
        assertTrue(plan.delete.isEmpty())
    }

    @Test fun youTubeIsIgnored() {
        val yt = PracticeMedia(ContentSection.QuickProtection, "KPRvRrJgmVk", MediaType.YouTube, 600, "")
        assertEquals(SyncPlan(emptyList(), emptyList()), planSync(mapOf(ContentSection.QuickProtection to yt), emptyList()))
    }
}
