package com.ojashealing.family.domain

import com.ojashealing.family.data.analytics.Events
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsEventsTest {
    @Test
    fun `event names are ones Google Analytics accepts`() {
        val names = listOf("practice_start", "practice_complete", "ai_question", "ai_rating", "post_create", "like", "notification_open", "call_start", "points_transfer")
        names.forEach { assertTrue(it, Events.NAME.matches(it)) }
    }

    @Test
    fun `labels are short plain words, never free text`() {
        assertEquals(mapOf("section" to "blue_light"), Events.practice("practice_start", "blue_light").params)
        assertEquals(mapOf("kind" to "activity_reply"), Events.kind("post_create", "Activity Reply").params)
        assertEquals(36, Events.kind("like", "x".repeat(80)).params.getValue("kind").length)
    }

    @Test
    fun `screens are named after their route, or the open tab`() {
        assertEquals("Question", Events.screenName("com.ojashealing.family.ui.main.QuestionRoute/{id}", "Home"))
        assertEquals("Notifications", Events.screenName("com.ojashealing.family.ui.main.NotificationsRoute", "Home"))
        assertEquals("Community", Events.screenName("com.ojashealing.family.ui.main.TabsRoute", "Community"))
        assertEquals("Home", Events.screenName(null, "Home"))
    }
}
