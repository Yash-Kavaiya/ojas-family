package com.ojashealing.family.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceAiTest {
    private val passages = listOf(
        KnowledgePassage("1", "Blue Light Meditation", "Blue Light Meditation\nSit straight and imagine blue healing light entering from the crown.", videoId = "a"),
        KnowledgePassage("2", "Protection Vidhi 1", "Protection Vidhi 1\nBuild a shield of divine energy around your body every morning.", videoId = "b", startSec = 60),
        KnowledgePassage("3", "Sleep", "Sleep\nBefore sleeping, thank the divine and relax each part of the body.", lang = "en"),
        KnowledgePassage("4", "नींद का ध्यान", "नींद का ध्यान\nसोने से पहले शरीर के हर अंग को ढीला छोड़ें।", lang = "hi"),
    )
    private val search = KnowledgeSearch(passages)

    @Test fun `finds the practice the question names`() {
        assertEquals("1", search.search("How do I do the blue light meditation?", 3).first().id)
        assertEquals("2", search.search("protection shield", 3).first().id)
    }

    @Test fun `stems and ignores common words`() {
        assertEquals(listOf("meditat"), searchWords("How do I meditate?").map { it.take(7) })
        assertEquals("1", search.search("meditations with light", 3).first().id)
    }

    @Test fun `searches Hindi words too`() {
        assertEquals("4", search.search("नींद नहीं आती", 3).first().id)
    }

    @Test fun `no matching word means no passages`() {
        assertTrue(search.search("cricket score", 3).isEmpty())
        assertTrue(search.search("what is", 3).isEmpty())
        assertTrue(KnowledgeSearch(emptyList()).search("blue light", 3).isEmpty())
    }

    @Test fun `weak matches are left out`() {
        val hits = search.search("blue light crown healing", 3)
        assertEquals(listOf("1"), hits.map { it.id })
    }

    @Test fun `prompt uses Gemma turns, the passages, the photo and the language`() {
        val p = GemmaPrompt.build(
            "What is this?", passages.take(2), "hi",
            history = listOf(ChatTurn(true, "Hello"), ChatTurn(false, "Namaste")),
            photo = PhotoNote("Om Namah Shivaya", listOf("Candle")),
        )
        assertTrue(p.startsWith("<start_of_turn>user\nHello"))
        assertTrue(p.contains("<start_of_turn>model\nNamaste"))
        assertTrue(p.contains("[2] Protection Vidhi 1"))
        assertTrue(p.contains("It shows: Candle."))
        assertTrue(p.contains("\"Om Namah Shivaya\""))
        assertTrue(p.contains("Answer in Hindi."))
        assertTrue(p.endsWith("<end_of_turn>\n<start_of_turn>model\n"))
    }

    @Test fun `cleans turn markers from the answer`() {
        assertEquals("Sit still.", GemmaPrompt.clean("Sit still.<end_of_turn>"))
    }

    @Test fun `crisis words are noticed in any of the three languages`() {
        assertTrue(GemmaPrompt.soundsLikeCrisis("I want to end my life"))
        assertTrue(GemmaPrompt.soundsLikeCrisis("मैं आत्महत्या के बारे में सोचता हूँ"))
        assertFalse(GemmaPrompt.soundsLikeCrisis("How do I meditate?"))
    }

    @Test fun `follow-ups come from the other passages`() {
        val f = GemmaPrompt.followUps("blue light?", passages.take(3)) { "Tell me about $it" }
        assertEquals(listOf("Tell me about Protection Vidhi 1", "Tell me about Sleep"), f)
    }
}
