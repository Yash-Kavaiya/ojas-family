package com.ojashealing.family.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLogicTest {

    @Test
    fun seenOnceTheOtherSideReadAfterIt() {
        assertTrue(isSeen(sentAt = 1_000, otherReadAt = 1_000))
        assertTrue(isSeen(sentAt = 1_000, otherReadAt = 5_000))
        assertFalse(isSeen(sentAt = 5_000, otherReadAt = 1_000))
        assertFalse(isSeen(sentAt = 0, otherReadAt = 5_000))
    }

    @Test
    fun typingFadesAfterAFewSeconds() {
        assertTrue(isTyping(typingAt = 10_000, now = 12_000))
        assertFalse(isTyping(typingAt = 10_000, now = 10_000 + TYPING_FRESH_MS + 1))
        assertFalse(isTyping(typingAt = 0, now = 12_000))
    }

    @Test
    fun presenceIsOnlineThenLastSeen() {
        val now = 10 * 60_000L
        assertEquals(PresenceState.Online, presenceOf(now - 30_000, now))
        assertEquals(PresenceState.LastSeen(5), presenceOf(now - 5 * 60_000, now))
        assertEquals(PresenceState.Unknown, presenceOf(null, now))
    }

    @Test
    fun waveformHasTheRightNumberOfBarsInRange() {
        val levels = List(137) { (it * 239) % 32767 }
        val bars = downsample(levels)
        assertEquals(WAVEFORM_BARS, bars.size)
        assertTrue(bars.all { it in 1..WAVEFORM_MAX })
        assertEquals(WAVEFORM_MAX, bars.max())
    }

    @Test
    fun shortRecordingsStillGiveFullWaveforms() {
        assertEquals(WAVEFORM_BARS, downsample(listOf(100, 30_000, 5)).size)
        assertTrue(downsample(emptyList()).isEmpty())
    }

    @Test
    fun unreadCountIsMySide() {
        val t = ChatThread("m", "M", "", "", "", "", 0, false, false, unreadCountStaff = 4, unreadCountMember = 1)
        assertEquals(4, unreadFor(t, iAmStaff = true))
        assertEquals(1, unreadFor(t, iAmStaff = false))
    }
}
