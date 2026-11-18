package com.ojashealing.family.data.social

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewTrackerTest {
    @Test fun countsAreShortened() {
        assertEquals("0", shortCount(0))
        assertEquals("999", shortCount(999))
        assertEquals("1k", shortCount(1000))
        assertEquals("1.2k", shortCount(1249))
        assertEquals("12k", shortCount(12_400))
        assertEquals("1.5M", shortCount(1_500_000))
    }

    @Test fun eachItemIsSentOncePerMember() = runTest {
        val sent = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val t = ViewTracker(uid = { "u1" }, seen = InMemorySeen(seen)) { path, _ -> sent += path }
        t.markViewed("books/b1")
        t.markViewed("books/b1")
        t.markViewed("announcements/n1")
        assertEquals(listOf("books/b1", "announcements/n1"), sent)
        // Another member on the same phone counts again
        val t2 = ViewTracker(uid = { "u2" }, seen = InMemorySeen(seen)) { path, _ -> sent += path }
        t2.markViewed("books/b1")
        assertEquals(3, sent.size)
    }

    @Test fun aFailedSendIsTriedAgainNextTime() = runTest {
        var fail = true
        val sent = mutableListOf<String>()
        val t = ViewTracker(uid = { "u1" }, seen = InMemorySeen(mutableSetOf())) { path, _ ->
            if (fail) error("offline") else sent += path
        }
        t.markViewed("books/b1")
        fail = false
        t.markViewed("books/b1")
        assertEquals(listOf("books/b1"), sent)
    }

    @Test fun signedOutSendsNothing() = runTest {
        val sent = mutableListOf<String>()
        ViewTracker(uid = { null }, seen = InMemorySeen(mutableSetOf())) { path, _ -> sent += path }.markViewed("books/b1")
        assertEquals(emptyList<String>(), sent)
    }
}

private class InMemorySeen(private val set: MutableSet<String>) : SeenStore {
    override fun has(key: String) = key in set
    override fun add(key: String) { set += key }
}
