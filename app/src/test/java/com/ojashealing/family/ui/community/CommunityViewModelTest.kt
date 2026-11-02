package com.ojashealing.family.ui.community

import com.ojashealing.family.data.social.Author
import com.ojashealing.family.data.social.Post
import com.ojashealing.family.data.social.PostStatus
import com.ojashealing.family.data.social.Question
import com.ojashealing.family.domain.MemberLevel
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.UserLevel
import com.ojashealing.family.domain.Viewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityViewModelTest {

    private fun author(uid: String, role: Role = Role.Member) = Author(uid, uid, "", role)
    private fun q(id: String, uid: String) = Question(id, author(uid), "t", "b", 0, 0, 0, PostStatus.Visible)
    private fun a(id: String, uid: String, role: Role, at: Long) = Post(id, author(uid, role), "x", at, PostStatus.Visible)

    @Test fun blockedAuthorsHidden() =
        assertEquals(listOf("q1"), visibleQuestions(listOf(q("q1", "ok"), q("q2", "troll")), blocked = setOf("troll")).map { it.id })

    @Test fun askHiddenBelowLevel3() {
        assertFalse(communityPermissions(Viewer(UserLevel.L2, MemberLevel.None, Role.Member)).canAsk)
        assertTrue(communityPermissions(Viewer(UserLevel.L3, MemberLevel.None, Role.Member)).canAsk)
        assertFalse(communityPermissions(Viewer(UserLevel.L3, MemberLevel.None, Role.Member)).canAnnounce)
        assertTrue(communityPermissions(Viewer(UserLevel.L1, MemberLevel.None, Role.Admin)).canAnnounce)
    }

    @Test fun staffAnswersFirst() {
        val answers = listOf(a("m1", "m", Role.Member, 1), a("s1", "sw", Role.Swamiji, 5), a("m2", "m2", Role.Member, 2), a("sm", "se", Role.SeniorMember, 9))
        assertEquals(listOf("s1", "sm", "m1", "m2"), orderAnswers(answers, blocked = emptySet()).map { it.id })
    }

    @Test fun blockedAnswersHidden() =
        assertEquals(listOf("m1"), orderAnswers(listOf(a("m1", "m", Role.Member, 1), a("t1", "troll", Role.Member, 2)), setOf("troll")).map { it.id })
}
