package com.ojashealing.family.data.social

import com.ojashealing.family.domain.LocalDateLike
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialRulesTest {

    @Test fun detectsLinks() {
        assertTrue(containsLink("read https://x.com"))
        assertTrue(containsLink("visit www.example.com now"))
        assertTrue(containsLink("http://a"))
        assertFalse(containsLink("Jai Gurudev, thank you"))
    }

    @Test fun visibleThoughtIdsAreTodayAndSixBeforeAcrossMonths() = assertEquals(
        listOf("2026-03-01", "2026-02-28", "2026-02-27", "2026-02-26", "2026-02-25", "2026-02-24", "2026-02-23"),
        visibleThoughtIds(LocalDateLike(2026, 3, 1))
    )

    @Test fun publishAtIsMidnightIst() =
        // 2026-11-03 00:00 IST == 2026-11-02 18:30 UTC
        assertEquals(1793644200000L, istMidnightMillis(LocalDateLike(2026, 11, 3)))
}
