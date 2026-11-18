package com.ojashealing.family.data.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserCountsTest {
    @Test fun readsTheServerAnswer() {
        assertEquals(UserCounts(1234, 56), userCountsFrom(mapOf("users" to 1234, "members" to 56L)))
    }

    @Test fun aBrokenAnswerShowsNothing() {
        assertNull(userCountsFrom(null))
        assertNull(userCountsFrom(mapOf("users" to 3)))
        assertNull(userCountsFrom("x"))
    }
}
