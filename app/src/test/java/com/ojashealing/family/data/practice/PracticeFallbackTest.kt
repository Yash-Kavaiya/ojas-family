package com.ojashealing.family.data.practice

import com.ojashealing.family.domain.ContentSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticeFallbackTest {
    private val config = mapOf(
        "kind" to "youtube", "videoId" to "KPRvRrJgmVk", "durationSec" to 1200, "title" to "Vidhi 1",
        "fallbackVideoId" to "QFNoym-jpbY", "fallbackDurationSec" to 282,
    )

    @Test fun `a YouTube practice reads its fallback video`() {
        val m = parsePracticeMedia(ContentSection.QuickProtection, config)!!
        assertEquals("QFNoym-jpbY", m.fallbackVideoId)
        val f = m.withFallback()!!
        assertEquals("QFNoym-jpbY", f.url)
        assertEquals(282, f.durationSec)
        // The fallback has no fallback of its own, so it can't loop
        assertNull(f.withFallback())
    }

    @Test fun `no fallback when none is set, or it has no length`() {
        assertNull(parsePracticeMedia(ContentSection.QuickProtection, config - "fallbackVideoId")!!.withFallback())
        assertNull(parsePracticeMedia(ContentSection.QuickProtection, config + ("fallbackDurationSec" to 0))!!.withFallback())
        // Files never use a YouTube fallback
        val file = mapOf("kind" to "video", "url" to "https://x/v.mp4", "durationSec" to 600, "fallbackVideoId" to "QFNoym-jpbY", "fallbackDurationSec" to 282)
        assertNull(parsePracticeMedia(ContentSection.BlueLight, file)!!.fallbackVideoId)
    }
}
