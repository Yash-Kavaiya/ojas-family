package com.ojashealing.family.data.read

import com.ojashealing.family.domain.MemberLevel
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.UserLevel
import com.ojashealing.family.domain.Viewer
import com.ojashealing.family.ui.content.canEditRead
import com.ojashealing.family.ui.content.speechChunks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadQaTest {
    @Test fun voiceLanguageFollowsTheScript() {
        assertEquals("en-IN", ttsLanguageFor("What is protection?"))
        assertEquals("hi-IN", ttsLanguageFor("सुरक्षा क्या है?"))
        assertEquals("gu-IN", ttsLanguageFor("રક્ષણ શું છે?"))
        // A Hindi answer with a few English words is still Hindi
        assertEquals("hi-IN", ttsLanguageFor("ध्यान में blue light की कल्पना करें और शांत रहें"))
        assertEquals("en-IN", ttsLanguageFor(""))
    }

    @Test fun longTextIsSplitAtSentenceEnds() {
        val text = (1..50).joinToString(" ") { "Sentence number $it is here." }
        val parts = speechChunks(text, 200)
        assertTrue(parts.all { it.length <= 200 })
        assertTrue(parts.dropLast(1).all { it.endsWith(".") })
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
        assertEquals(listOf("Short."), speechChunks("  Short.  ", 200))
        // No break anywhere: still cut to size
        assertTrue(speechChunks("x".repeat(1000), 300).all { it.length <= 300 })
    }

    @Test fun bothPartsNeededWithinLimits() {
        assertTrue(validReadQa("Q?", "A."))
        assertFalse(validReadQa(" ", "A."))
        assertFalse(validReadQa("Q?", ""))
        assertFalse(validReadQa("Q".repeat(ReadQuestionMax + 1), "A"))
    }

    @Test fun onlyAdminsAndSwamijiWrite() {
        fun v(r: Role) = Viewer(UserLevel.L1, MemberLevel.None, r)
        assertTrue(canEditRead(v(Role.Admin)))
        assertTrue(canEditRead(v(Role.Swamiji)))
        assertFalse(canEditRead(v(Role.Moderator)))
        assertFalse(canEditRead(v(Role.Member)))
        assertFalse(canEditRead(null))
    }

    @Test fun firstQuestionIsOneNotYetDone() {
        val ids = listOf("a", "b", "c", "d")
        repeat(50) { seed ->
            val pick = ReadTicks.pickFirst(ids, done = setOf("a", "c"), random = kotlin.random.Random(seed))
            assertTrue(pick == "b" || pick == "d")
        }
        // All Done: still one of them, never nothing
        assertTrue(ReadTicks.pickFirst(ids, done = ids.toSet()) in ids)
        assertEquals(null, ReadTicks.pickFirst(emptyList(), done = emptySet()))
    }

    @Test fun firstQuestionChangesBetweenVisits() {
        val ids = (1..10).map { "q$it" }
        val picks = (0 until 30).map { ReadTicks.pickFirst(ids, emptySet(), kotlin.random.Random(it)) }.toSet()
        assertTrue(picks.size > 1)
    }
}
