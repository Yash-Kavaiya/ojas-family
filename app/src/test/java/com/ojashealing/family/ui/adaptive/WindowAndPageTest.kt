package com.ojashealing.family.ui.adaptive

import com.ojashealing.family.data.paging.FEED_PAGE_SIZE
import com.ojashealing.family.data.paging.nextPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowAndPageTest {

    @Test fun compactWidthUsesBottomBarAndStackedDetail() {
        val chrome = decideWindowChrome(COMPACT_WIDTH_DP - 1)
        assertEquals(PrimaryNav.BottomBar, chrome.primaryNav)
        assertEquals(DetailMode.Stacked, chrome.detail)
    }

    @Test fun expandedWidthUsesRailAndListBesideDetail() {
        val chrome = decideWindowChrome(EXPANDED_WIDTH_DP)
        assertEquals(PrimaryNav.Rail, chrome.primaryNav)
        assertEquals(DetailMode.BesideList, chrome.detail)
    }

    @Test fun mediumWidthUsesRailAndStillStacksDetail() {
        val chrome = decideWindowChrome(COMPACT_WIDTH_DP)
        assertEquals(PrimaryNav.Rail, chrome.primaryNav)
        assertEquals(DetailMode.Stacked, chrome.detail)
        assertEquals(DetailMode.Stacked, decideWindowChrome(EXPANDED_WIDTH_DP - 1).detail)
    }

    @Test fun secondPageDropsIdsAlreadyShownAndStaysWithinPageSize() {
        val first = (1..FEED_PAGE_SIZE).map { "id$it" }
        val incoming = listOf("id${FEED_PAGE_SIZE - 1}", "id$FEED_PAGE_SIZE") +
            (FEED_PAGE_SIZE + 1..FEED_PAGE_SIZE * 3).map { "id$it" }
        val page = nextPage(first.toSet(), incoming, FEED_PAGE_SIZE) { it }
        assertTrue(page.added.none { it in first })
        assertTrue(page.added.size <= FEED_PAGE_SIZE)
        assertEquals(FEED_PAGE_SIZE, page.added.size)
        assertEquals("id${FEED_PAGE_SIZE + FEED_PAGE_SIZE}", page.added.last())
        assertEquals(page.added.last(), page.cursor)
    }

    @Test fun shortIncomingPageHasNoFurtherCursor() {
        val page = nextPage(setOf("a"), listOf("a", "b", "c"), FEED_PAGE_SIZE) { it }
        assertEquals(listOf("b", "c"), page.added)
        assertNull(page.cursor)
    }

    @Test fun draftAndScrollRoundTrip() {
        val original = DraftScroll(draft = "a note for swamiji", index = 4, offset = 18)
        assertEquals(original, DraftScroll.restore(original.saved()))
    }
}
