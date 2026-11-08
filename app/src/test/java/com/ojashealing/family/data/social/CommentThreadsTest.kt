package com.ojashealing.family.data.social

import com.ojashealing.family.domain.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentThreadsTest {

    private fun author(name: String) = Author(name.lowercase(), name, "", Role.Member)

    private fun post(id: String, at: Long, by: String = "Riya", parent: String = "", replyTo: String = "") =
        Post(id, author(by), "text $id", at, PostStatus.Visible, parentId = parent, replyToName = replyTo)

    @Test
    fun repliesHangUnderTheirCommentOldestFirst() {
        val groups = groupReplies(
            listOf(post("a", 1), post("b", 2), post("r2", 5, parent = "a"), post("r1", 3, parent = "a"), post("r3", 4, parent = "b"))
        )
        assertEquals(listOf("a", "b"), groups.map { it.comment.id })
        assertEquals(listOf("r1", "r2"), groups[0].replies.map { it.id })
        assertEquals(listOf("r3"), groups[1].replies.map { it.id })
    }

    @Test
    fun replyWhoseCommentIsGoneShowsAsTopLevel() {
        val groups = groupReplies(listOf(post("a", 1), post("orphan", 2, parent = "removed")))
        assertEquals(listOf("a", "orphan"), groups.map { it.comment.id })
    }

    @Test
    fun replyingToAReplyStaysOneLevelDeep() {
        val reply = post("r1", 3, by = "Amit", parent = "a")
        assertEquals(ReplyTo("a", "Amit"), replyTargetFor(reply))
        assertEquals(ReplyTo("a", "Riya"), replyTargetFor(post("a", 1)))
    }
}
