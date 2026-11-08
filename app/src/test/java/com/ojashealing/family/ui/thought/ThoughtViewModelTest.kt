package com.ojashealing.family.ui.thought

import com.ojashealing.family.data.social.Thought
import com.ojashealing.family.domain.MemberLevel
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.UserLevel
import com.ojashealing.family.domain.Viewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThoughtViewModelTest {

    private fun t(id: String) = Thought(id, 0, "T $id", "x", 0, 0)
    private val thoughts = listOf(t("2026-11-02"), t("2026-11-03"), t("2026-11-01"))
    private fun v(u: UserLevel, m: MemberLevel = MemberLevel.None, r: Role = Role.Member) = Viewer(u, m, r)

    @Test fun selectsTodayByDefaultAndOrdersNewestFirst() {
        val ui = buildThoughtUi(thoughts, selectedId = null, viewer = v(UserLevel.L1), todayId = "2026-11-03")
        assertEquals("2026-11-03", ui.selected?.id)
        assertEquals(listOf("2026-11-03", "2026-11-02", "2026-11-01"), ui.days.map { it.id })
    }

    @Test fun fallsBackToNewestWhenTodayHasNoThought() =
        assertEquals("2026-11-02", buildThoughtUi(thoughts.drop(1).take(0) + listOf(t("2026-11-02")), null, v(UserLevel.L1), "2026-11-03").selected?.id)

    @Test fun likeHiddenWhenViewerCannotLike() {
        assertFalse(buildThoughtUi(thoughts, null, v(UserLevel.L1), "2026-11-03").canLike)
        assertTrue(buildThoughtUi(thoughts, null, v(UserLevel.L2), "2026-11-03").canLike)
    }

    @Test fun commentBoxHiddenBelowLevel3() {
        assertFalse(buildThoughtUi(thoughts, null, v(UserLevel.L2), "2026-11-03").canComment)
        assertTrue(buildThoughtUi(thoughts, null, v(UserLevel.L2, MemberLevel.M2), "2026-11-03").canComment)
    }

    @Test fun onlyAdminsSchedule() {
        assertFalse(buildThoughtUi(thoughts, null, v(UserLevel.L4, r = Role.Swamiji), "2026-11-03").canSchedule)
        assertTrue(buildThoughtUi(thoughts, null, v(UserLevel.L1, r = Role.Admin), "2026-11-03").canSchedule)
    }
}

class ScheduleSummaryTest {

    private fun t(id: String) = Thought(id, 0, "t", "x", 0, 0)

    @Test
    fun countsLastDayAndFirstGap() {
        val s = scheduleSummary(listOf(t("2026-11-09"), t("2026-11-10"), t("2026-11-12")), com.ojashealing.family.domain.LocalDateLike(2026, 11, 8))
        assertEquals(3, s.count)
        assertEquals("2026-11-12", s.lastId)
        assertEquals("2026-11-11", s.nextGapId)
    }

    @Test
    fun nothingScheduledMeansTomorrowIsTheGap() {
        val s = scheduleSummary(emptyList(), com.ojashealing.family.domain.LocalDateLike(2026, 12, 31))
        assertEquals(0, s.count)
        assertEquals(null, s.lastId)
        assertEquals("2027-01-01", s.nextGapId)
    }
}
