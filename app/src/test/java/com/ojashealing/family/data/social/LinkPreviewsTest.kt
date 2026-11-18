package com.ojashealing.family.data.social

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPreviewsTest {
    @Test fun readsTheServersAnswer() {
        val p = linkPreviewFrom(mapOf("title" to "Hello", "description" to "Line", "image" to "https://e.com/a.jpg", "site" to "E"), "https://e.com")
        assertEquals(LinkPreviewData("https://e.com", "Hello", "Line", "https://e.com/a.jpg", "E"), p)
    }

    @Test fun nothingToShowWithoutATitle() {
        assertNull(linkPreviewFrom(emptyMap<String, Any>(), "https://e.com"))
        assertNull(linkPreviewFrom(null, "https://e.com"))
        assertNull(linkPreviewFrom(mapOf("title" to "  "), "https://e.com"))
    }

    @Test fun aPictureThatIsNotSecureIsLeftOut() {
        assertEquals("", linkPreviewFrom(mapOf("title" to "T", "image" to "http://e.com/a.jpg"), "https://e.com")!!.image)
    }

    @Test fun asksTheServerOncePerAddressEvenWhenManyCardsAskTogether() = runTest {
        var calls = 0
        val loader = LinkPreviewLoader { url -> calls++; delay(10); mapOf("title" to "T for $url") }
        val results = (1..5).map { async { loader.load("https://e.com") } }.awaitAll()
        assertEquals(1, calls)
        assertEquals("T for https://e.com", results.first()!!.title)
        loader.load("https://e.com")
        assertEquals(1, calls)
    }

    @Test fun aFailureShowsNothingAndIsTriedAgainLater() = runTest {
        var calls = 0
        val loader = LinkPreviewLoader { calls++; if (calls == 1) error("offline") else mapOf("title" to "T") }
        assertNull(loader.load("https://e.com"))
        assertEquals("T", loader.load("https://e.com")!!.title)
    }

    @Test fun aPageWithNothingToShowIsNotAskedAgain() = runTest {
        var calls = 0
        val loader = LinkPreviewLoader { calls++; emptyMap<String, Any>() }
        assertNull(loader.load("https://e.com"))
        assertNull(loader.load("https://e.com"))
        assertEquals(1, calls)
    }
}
