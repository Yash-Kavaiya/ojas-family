package com.ojashealing.family.data

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryTest {

    /** A listener that fails [failures] times, then delivers "profile". */
    private fun listener(failures: Int): Pair<() -> Int, kotlinx.coroutines.flow.Flow<String>> {
        var starts = 0
        return { starts } to flow {
            starts++
            if (starts <= failures) throw IOException("permission denied for a moment")
            emit("profile")
        }
    }

    @Test fun aPassingFailureIsRetriedAndNeverReachesTheCaller() = runTest {
        val (starts, source) = listener(failures = 2)
        val seen = source.retryBriefly().catch { emit("gave up") }.toList()
        assertEquals(listOf("profile"), seen)
        assertEquals(3, starts())
        assertEquals(3_000L, currentTime) // waited 1 s, then 2 s
    }

    @Test fun aLastingFailureIsLetThroughAfterTheLastTry() = runTest {
        val (starts, source) = listener(failures = Int.MAX_VALUE)
        val seen = source.retryBriefly().catch { emit("gave up") }.toList()
        assertEquals(listOf("gave up"), seen)
        assertEquals(LISTENER_RETRY_DELAYS_MS.size + 1, starts())
    }
}
