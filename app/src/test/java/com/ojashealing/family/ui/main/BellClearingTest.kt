package com.ojashealing.family.ui.main

import com.ojashealing.family.data.notify.InboxItem
import com.ojashealing.family.data.notify.NotifAction
import com.ojashealing.family.data.notify.NotifGroup
import com.ojashealing.family.data.notify.linesAbout
import com.ojashealing.family.ui.community.CommunityTab
import com.ojashealing.family.ui.navigation.MainTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BellClearingTest {
    private fun line(id: String, target: String, unread: Boolean = true, personal: Boolean = true) =
        InboxItem(id, personal, NotifGroup.Qa, NotifAction.LikeReply, "answer", "Ravi", "", target, "x", 1, 0L, unread)

    @Test fun openingAPostReadsEveryUnreadLineAboutIt() {
        val items = listOf(
            line("a", "questions/q1"), line("b", "questions/q1", personal = false), line("c", "questions/q1", unread = false),
            line("d", "questions/q2"), line("e", "questions/q10"),
        )
        assertEquals(listOf("a", "b"), linesAbout(items, "questions/q1").map { it.id })
    }

    @Test fun theTabOnScreenIsTheGroupWhoseNewPostsAreSeen() {
        assertEquals(NotifGroup.Thought, groupOnScreen(MainTab.Home, CommunityTab.QA))
        assertEquals(NotifGroup.Activity, groupOnScreen(MainTab.Community, CommunityTab.DAILY))
        assertEquals(NotifGroup.Announcement, groupOnScreen(MainTab.Community, CommunityTab.NOTICES))
        assertEquals(NotifGroup.Qa, groupOnScreen(MainTab.Community, CommunityTab.QA))
        assertNull(groupOnScreen(MainTab.Spiritual, CommunityTab.QA))
    }
}
