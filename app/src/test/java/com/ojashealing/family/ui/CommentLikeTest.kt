package com.ojashealing.family.ui

import com.ojashealing.family.ui.components.shownLikeCount
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentLikeTest {

    @Test
    fun aTapShowsAtOnceBeforeTheServerCounts() {
        assertEquals(4, shownLikeCount(count = 3, base = 3, wasLiked = false, nowLiked = true))
        assertEquals(2, shownLikeCount(count = 3, base = 3, wasLiked = true, nowLiked = false))
    }

    @Test
    fun theServerCountWinsOnceItMoves() {
        assertEquals(4, shownLikeCount(count = 4, base = 3, wasLiked = false, nowLiked = true))
        assertEquals(7, shownLikeCount(count = 7, base = 3, wasLiked = false, nowLiked = true))
    }

    @Test
    fun neverBelowZeroAndNoTapMeansServerCount() {
        assertEquals(0, shownLikeCount(count = 0, base = 0, wasLiked = true, nowLiked = false))
        assertEquals(5, shownLikeCount(count = 5, base = 0, wasLiked = false, nowLiked = null))
    }
}
