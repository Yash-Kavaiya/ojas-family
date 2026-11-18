package com.ojashealing.family.data.social

import com.ojashealing.family.domain.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MentionsTest {
    private val ram = Author("r", "Ram", "", Role.Member)
    private val ramesh = Author("rm", "Ramesh Patel", "", Role.Member)
    private val asha = Author("a", "Asha", "", Role.Member)

    @Test fun prefixNameIsNotMentionedByALongerOne() {
        assertEquals(listOf("rm"), mentionedUids("Thanks @Ramesh Patel!", listOf(ram, ramesh)))
        assertEquals(listOf("r"), mentionedUids("@Ram, see this", listOf(ram, ramesh)))
        assertEquals(listOf("rm", "r"), mentionedUids("@Ramesh Patel and @Ram", listOf(ram, ramesh)))
        assertEquals(emptyList<String>(), mentionedUids("@Ramu", listOf(ram)))
    }

    @Test fun typingAtBeforeTheCursorAsksForNames() {
        assertEquals("", mentionQuery("Hi @", 4))
        assertEquals("As", mentionQuery("Hi @As", 6))
        assertEquals("As", mentionQuery("@As", 3))
        // A finished word, an e-mail address or a cursor before the @ is not a mention
        assertNull(mentionQuery("Hi @Asha ", 9))
        assertNull(mentionQuery("x@As", 4))
        assertNull(mentionQuery("Hi @As", 2))
    }

    @Test fun pickingANameReplacesTheTypedPartAtTheCursor() {
        assertEquals("Hi @Asha there" to 9, insertMention("Hi @As there", 6, "Asha"))
        // From the + menu, with nothing typed: goes where the cursor is, not at the end
        assertEquals("Hi @Asha there" to 9, insertMention("Hi there", 3, "Asha"))
        assertEquals("@Asha " to 6, insertMention("", 0, "Asha"))
    }

    @Test fun knownNamesAreFoundForColouring() {
        assertEquals(listOf(3..7, 13..25), mentionRanges("Hi @Asha and @Ramesh Patel", listOf("Asha", "Ramesh Patel", "Ram")))
    }

    @Test fun matchingFilterIgnoresCase() {
        assertEquals(listOf(ram, ramesh), mentionMatches("ra", listOf(asha, ram, ramesh)))
        assertEquals(listOf(asha, ram, ramesh), mentionMatches("", listOf(asha, ram, ramesh)))
    }

    @Test fun typedMentionsAreFoundWithoutKnowingTheNames() {
        // First word, plus a second one that starts with a capital ("@Ramesh Patel" but not "@Asha thanks")
        assertEquals(listOf(3..15, 21..25), typedMentionRanges("Hi @Ramesh Patel and @Asha thanks"))
        assertEquals(emptyList<IntRange>(), typedMentionRanges("mail x@ojas.com or @ alone"))
    }
}
