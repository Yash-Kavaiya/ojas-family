package com.ojashealing.family.ui

import com.ojashealing.family.ui.components.findLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTextTest {
    private fun urls(text: String) = findLinks(text).map { it.url }
    private fun shown(text: String) = findLinks(text).map { text.substring(it.range) }

    @Test
    fun `addresses with https, www or just the name all become links`() {
        assertEquals(listOf("https://youtu.be/abc123"), urls("Watch https://youtu.be/abc123 today"))
        assertEquals(listOf("http://example.org/a?b=1&c=2"), urls("see http://example.org/a?b=1&c=2"))
        assertEquals(listOf("https://www.ojashealing.com"), urls("www.ojashealing.com"))
        assertEquals(listOf("https://ojashealing.com/book"), urls("Book at ojashealing.com/book"))
        assertEquals(listOf("https://chat.whatsapp.com/AbC"), urls("join chat.whatsapp.com/AbC"))
    }

    @Test
    fun `ending punctuation stays outside the link`() {
        assertEquals(listOf("https://ojas.in"), shown("Open https://ojas.in."))
        assertEquals(listOf("www.ojas.org"), shown("(see www.ojas.org)"))
        assertEquals(listOf("https://en.wikipedia.org/wiki/Yoga_(philosophy)"), shown("https://en.wikipedia.org/wiki/Yoga_(philosophy), read it"))
        assertEquals(listOf("ojas.com", "t.me/ojas"), shown("ojas.com, t.me/ojas!"))
    }

    @Test
    fun `Gujarati and Hindi around a link do not join it`() {
        assertEquals(listOf("https://ojas.in/a"), shown("આ જુઓ https://ojas.in/a અને"))
        assertEquals(listOf("ojas.in"), shown("यहाँ ojas.in देखें"))
    }

    @Test
    fun `plain words, numbers and emails are not links`() {
        assertTrue(findLinks("Peace is within. e.g. breathe 1.5 times, file.txt").isEmpty())
        assertTrue(findLinks("write to seva@ojashealing.com").isEmpty())
        assertTrue(findLinks("https:// nothing").isEmpty())
        assertTrue(findLinks("").isEmpty())
    }

    @Test
    fun `ranges point at the link inside the text`() {
        val text = "a www.x.com b https://y.in c"
        assertEquals(listOf("www.x.com", "https://y.in"), shown(text))
    }
}
