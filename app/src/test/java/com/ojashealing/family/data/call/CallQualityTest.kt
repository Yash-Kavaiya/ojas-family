package com.ojashealing.family.data.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallQualityTest {

    @Test
    fun barsFollowRoundTripLossAndJitter() {
        assertEquals(3, qualityFrom(LineStats(80.0, 0.5, 10.0)))
        assertEquals(2, qualityFrom(LineStats(350.0, 0.0, 10.0)))
        assertEquals(2, qualityFrom(LineStats(80.0, 5.0, 10.0)))
        assertEquals(1, qualityFrom(LineStats(700.0, 0.0, 10.0)))
        assertEquals(1, qualityFrom(LineStats(80.0, 15.0, 10.0)))
        assertEquals(3, qualityFrom(LineStats(null, null, null)))
    }

    @Test
    fun lossIsOverTheLastInterval() {
        assertEquals(10.0, lossPercent(lostBefore = 5, lostNow = 15, receivedBefore = 100, receivedNow = 190)!!, 0.001)
        assertEquals(null, lossPercent(5, 5, 100, 100))
    }

    @Test
    fun onlyTheCallerRepairsAndOnlyWhenNeeded() {
        assertFalse(shouldRestart(isCaller = false, failed = true, disconnectedForMs = 10_000, networkChanged = true))
        assertTrue(shouldRestart(isCaller = true, failed = true, disconnectedForMs = 0, networkChanged = false))
        assertTrue(shouldRestart(isCaller = true, failed = false, disconnectedForMs = 0, networkChanged = true))
        assertFalse(shouldRestart(isCaller = true, failed = false, disconnectedForMs = 1_000, networkChanged = false))
        assertTrue(shouldRestart(isCaller = true, failed = false, disconnectedForMs = RESTART_AFTER_MS, networkChanged = false))
    }
}
