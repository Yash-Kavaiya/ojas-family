package com.ojashealing.family.data.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStateTest {

    @Test
    fun timerReadsLikeAPhone() {
        assertEquals("0:00", callClock(0))
        assertEquals("4:05", callClock(245_000))
        assertEquals("1:02:05", callClock(3_725_000))
    }

    @Test
    fun overPhasesEndTheCall() {
        assertTrue(CallPhase.Missed.isOver)
        assertTrue(CallPhase.Declined.isOver)
        assertFalse(CallPhase.Incoming.isOver)
        assertFalse(CallPhase.Reconnecting.isOver)
    }

    @Test
    fun anIncomingStartKnowsItsCall() {
        assertEquals("c1", CallStart.Incoming("c1").knownCallId)
        assertEquals(null, CallStart.Outgoing("s", "m", "s", "Swamiji").knownCallId)
    }
}
