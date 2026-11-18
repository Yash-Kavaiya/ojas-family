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

class PracticeDownloadWorkerLogicTest {
    private val a = "https://x/o/a.mp4"; private val b = "https://x/o/b.mp4"; private val c = "https://x/o/c.mp4"

    @Test fun mobileDataFetchesOnlyThePracticeAskedFor() {
        assertEquals(listOf(c), urlsToFetch(listOf(a, b, c), only = c) { false })
        assertEquals(listOf(b, c), urlsToFetch(listOf(a, b, c), only = null) { it == a })
        // A practice the admin replaced meanwhile is no longer wanted: nothing to fetch
        assertEquals(emptyList<String>(), urlsToFetch(listOf(a, b), only = c) { false })
    }

    @Test fun oneFailingPracticeDoesNotBlockTheOthers() = kotlinx.coroutines.test.runTest {
        val fetched = mutableListOf<String>()
        val failed = fetchEach(listOf(a, b, c)) { url ->
            if (url == a) throw java.io.IOException("403")
            if (url == b) throw IllegalArgumentException("bad url")
            fetched += url
        }
        assertEquals(listOf(c), fetched)
        assertEquals(listOf(a, b), failed)
    }

    @Test fun cutOffDownloadLeavesNoPartFile() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        val target = java.io.File(dir, "v.mp4")
        val broken = object : java.io.InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 1000) 1 else throw java.io.IOException("connection reset")
        }
        try { saveDownload(broken, 5000, target) {}; org.junit.Assert.fail("should throw") } catch (_: java.io.IOException) {}
        assertTrue(dir.list()!!.isEmpty())
        // Shorter than announced also counts as cut off
        try { saveDownload(ByteArray(10).inputStream(), 20, target) {}; org.junit.Assert.fail("should throw") } catch (_: java.io.IOException) {}
        assertTrue(dir.list()!!.isEmpty())
        saveDownload(ByteArray(20).inputStream(), 20, target) {}
        assertEquals(listOf("v.mp4"), dir.list()!!.toList())
        assertEquals(20L, target.length())
    }
}
