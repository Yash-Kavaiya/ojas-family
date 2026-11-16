package com.ojashealing.family.domain

import com.ojashealing.family.data.about.AboutLink
import com.ojashealing.family.data.about.OJAS_YOUTUBE
import com.ojashealing.family.ui.thought.Social
import com.ojashealing.family.ui.thought.socialLinks
import com.ojashealing.family.ui.thought.socialOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialLinksTest {
    @Test
    fun `a link is recognised by its web address`() {
        assertEquals(Social.WhatsApp, socialOf("https://wa.me/919999999999"))
        assertEquals(Social.WhatsApp, socialOf("https://chat.whatsapp.com/AbC"))
        assertEquals(Social.YouTube, socialOf("https://www.youtube.com/@OjasHealing"))
        assertEquals(Social.Facebook, socialOf("https://m.facebook.com/ojashealing"))
        assertEquals(Social.Instagram, socialOf("https://www.instagram.com/ojashealing/"))
        assertEquals(Social.Threads, socialOf("https://www.threads.net/@ojashealing"))
        assertEquals(Social.Telegram, socialOf("https://t.me/ojashealing"))
        assertEquals(Social.Reddit, socialOf("https://www.reddit.com/r/ojashealing"))
        assertEquals(Social.Quora, socialOf("https://ojashealing.quora.com/"))
        assertNull(socialOf("https://ojashealing.com/book"))
        // A look-alike address is not the platform
        assertNull(socialOf("https://notfacebook.com/x"))
    }

    @Test
    fun `icons come in a fixed order, one per platform, and YouTube is always there`() {
        assertEquals(listOf(Social.YouTube to OJAS_YOUTUBE), socialLinks(emptyList()))
        val links = listOf(
            AboutLink("Telegram", "https://t.me/ojas"), AboutLink("Book", "https://ojashealing.com/book"),
            AboutLink("WhatsApp", "https://wa.me/1"), AboutLink("WhatsApp group", "https://chat.whatsapp.com/2"),
            AboutLink("Our channel", "https://youtube.com/@Other"),
        )
        assertEquals(
            listOf(Social.WhatsApp to "https://wa.me/1", Social.YouTube to "https://youtube.com/@Other", Social.Telegram to "https://t.me/ojas"),
            socialLinks(links),
        )
    }
}
