package com.ojashealing.family.domain

import com.ojashealing.family.data.notify.InboxItem
import com.ojashealing.family.data.notify.NotifAction
import com.ojashealing.family.data.notify.NotifGroup
import com.ojashealing.family.data.notify.SeenState
import com.ojashealing.family.data.notify.SharedLine
import com.ojashealing.family.data.notify.mergeInbox
import com.ojashealing.family.data.notify.switchOn
import com.ojashealing.family.data.notify.unreadByGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationInboxTest {
    private fun mine(id: String, group: NotifGroup, action: NotifAction?, at: Long, unread: Boolean = true) =
        InboxItem(id, personal = true, group, action, "like", "Meera", "", "dailyPosts/x", "m2", 1, at, unread)

    private fun shared(id: String, group: NotifGroup, at: Long, from: String = "m2") =
        SharedLine(id, group, "question", "Ravi", "Why?", "questions/$id", from, at)

    @Test
    fun `switches are on until turned off, and a group turns off all of it`() {
        assertTrue(switchOn(emptyMap(), NotifGroup.Qa, NotifAction.Mention))
        assertFalse(switchOn(mapOf("all" to false), NotifGroup.Critical))
        assertFalse(switchOn(mapOf("activity" to false), NotifGroup.Activity, NotifAction.Post))
        assertTrue(switchOn(mapOf("activity" to false), NotifGroup.Qa, NotifAction.Post))
        assertFalse(switchOn(mapOf("qa.likeReply" to false), NotifGroup.Qa, NotifAction.LikeReply))
    }

    @Test
    fun `switches saved before the groups still count, until the new one is set`() {
        assertFalse(switchOn(mapOf("communityMentions" to false), NotifGroup.Thought, NotifAction.Mention))
        assertFalse(switchOn(mapOf("dailyPosts" to false), NotifGroup.Activity, NotifAction.Post))
        assertTrue(switchOn(mapOf("dailyPosts" to false, "activity.post" to true), NotifGroup.Activity, NotifAction.Post))
    }

    @Test
    fun `the list is newest first, leaves out switched-off lines and my own posts`() {
        val list = mergeInbox(
            personal = listOf(mine("p1", NotifGroup.Activity, NotifAction.LikeReply, 300), mine("p2", NotifGroup.Qa, NotifAction.Mention, 100)),
            shared = listOf(shared("s1", NotifGroup.Qa, 200), shared("s2", NotifGroup.Qa, 400, from = "me")),
            state = SeenState(),
            prefs = mapOf("qa.mention" to false),
            myUid = "me",
        )
        assertEquals(listOf("p1", "s1"), list.map { it.id })
    }

    @Test
    fun `a shared line is new until its group is seen or it is opened`() {
        val lines = listOf(shared("old", NotifGroup.Qa, 100), shared("new", NotifGroup.Qa, 300), shared("opened", NotifGroup.Qa, 400))
        val list = mergeInbox(emptyList(), lines, SeenState(seen = mapOf("qa" to 200L), opened = setOf("opened")), emptyMap(), "me")
        assertEquals(listOf("opened" to false, "new" to true, "old" to false), list.map { it.id to it.unread })
    }

    @Test
    fun `counts per group are of unread lines only`() {
        val items = listOf(
            mine("a", NotifGroup.Activity, NotifAction.LikeReply, 1), mine("b", NotifGroup.Activity, NotifAction.Mention, 2),
            mine("c", NotifGroup.Qa, NotifAction.Mention, 3, unread = false), mine("d", NotifGroup.Critical, null, 4),
        )
        assertEquals(mapOf(NotifGroup.Activity to 2, NotifGroup.Critical to 1), unreadByGroup(items))
    }
}
