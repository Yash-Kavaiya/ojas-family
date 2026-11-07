package com.ojashealing.family.domain

import com.ojashealing.family.data.points.LevelTarget
import com.ojashealing.family.data.points.LevelTargets
import com.ojashealing.family.data.points.PointHistoryItem
import com.ojashealing.family.data.points.PointSource
import com.ojashealing.family.data.points.groupByDay
import com.ojashealing.family.data.points.istDayKey
import com.ojashealing.family.data.points.levelProgress
import com.ojashealing.family.data.points.sourceOf
import com.ojashealing.family.data.points.totalsBySource
import com.ojashealing.family.data.social.MediaKind
import com.ojashealing.family.ui.ask.AiTopic
import com.ojashealing.family.ui.ask.aiTopicFor
import com.ojashealing.family.ui.components.roleBadgeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DesignFeaturesTest {

    private fun item(key: String, reason: String, amount: Int = 1, at: Long = 0) = PointHistoryItem(key, amount, reason, at)

    @Test
    fun likesAreSplitByWhatWasLiked() {
        assertEquals(PointSource.LikeThought, sourceOf(item("like:thought:2026-11-07", "like")))
        assertEquals(PointSource.LikeQuestion, sourceOf(item("like:question:abc", "like")))
        assertEquals(PointSource.Gift, sourceOf(item("gift:1:x", "gift")))
        assertEquals(PointSource.Other, sourceOf(item("x", "something-new")))
    }

    @Test
    fun totalsAreSummedAndSortedBiggestFirst() {
        val totals = totalsBySource(listOf(item("a", "daily"), item("b", "daily"), item("c", "gift", 50)))
        assertEquals(listOf(PointSource.Gift to 50, PointSource.DailyVisit to 2), totals)
    }

    @Test
    fun historyGroupsByIndianDayNewestFirst() {
        // 2026-11-06 20:00 UTC is already 2026-11-07 in India
        val lateUtc = 1_793_995_200_000L
        assertEquals("2026-11-07", istDayKey(lateUtc))
        val groups = groupByDay(listOf(item("old", "daily", at = lateUtc - 2 * 86_400_000L), item("new", "daily", at = lateUtc)))
        assertEquals(listOf("2026-11-07", "2026-11-05"), groups.map { it.first })
    }

    @Test
    fun levelProgressUsesTheWeakerRequirement() {
        assertEquals(0.5f, levelProgress(activeDays = 45, points = 200, target = LevelTarget(90, 200)), 0.001f)
        assertEquals(1f, levelProgress(10, 10, null), 0.001f)
        assertEquals(LevelTarget(365, 10000), LevelTargets().next(3))
        assertNull(LevelTargets().next(4))
    }

    @Test
    fun aiPicksATopicInAnyLanguage() {
        assertEquals(AiTopic.Meditation, aiTopicFor("How do I start meditating?"))
        assertEquals(AiTopic.Stress, aiTopicFor("मुझे तनाव महसूस होता है"))
        assertEquals(AiTopic.Chakra, aiTopicFor("ચક્ર શું છે?"))
        assertEquals(AiTopic.Sleep, aiTopicFor("I can't sleep well"))
        assertEquals(AiTopic.General, aiTopicFor("Hello"))
        assertEquals(AiTopic.General, aiTopicFor("I will still try"))
    }

    @Test
    fun onlyStaffRolesGetATick() {
        assertNull(roleBadgeStyle(Role.Member))
        assertNotNull(roleBadgeStyle(Role.Swamiji))
        assertNotNull(roleBadgeStyle(Role.Admin))
        assertNotNull(roleBadgeStyle(Role.SeniorMember))
    }

    @Test
    fun mediaKindsRoundTrip() {
        MediaKind.entries.forEach { assertEquals(it, MediaKind.fromId(it.id)) }
        assertNull(MediaKind.fromId(null))
        assertNull(MediaKind.fromId("pdf"))
    }

    @Test
    fun cropAtNoZoomTakesTheCentreSquare() {
        val r = com.ojashealing.family.ui.components.cropRectFor(4000, 3000, 1000f, 1f, androidx.compose.ui.geometry.Offset.Zero)
        assertEquals(com.ojashealing.family.ui.components.CropRect(500, 0, 3000), r)
    }

    @Test
    fun cropFollowsZoomAndPanAndNeverLeavesThePicture() {
        val zoomed = com.ojashealing.family.ui.components.cropRectFor(2000, 2000, 1000f, 2f, androidx.compose.ui.geometry.Offset.Zero)
        assertEquals(com.ojashealing.family.ui.components.CropRect(500, 500, 1000), zoomed)
        val far = com.ojashealing.family.ui.components.clampPan(2000, 2000, 1000f, 2f, androidx.compose.ui.geometry.Offset(5000f, -5000f))
        assertEquals(androidx.compose.ui.geometry.Offset(500f, -500f), far)
        val leftEdge = com.ojashealing.family.ui.components.cropRectFor(2000, 2000, 1000f, 2f, far)
        assertEquals(0, leftEdge.left)
        assertEquals(1000, leftEdge.top)
    }

    @Test
    fun hidingTheProfileOpensAtLevelThree() {
        assertEquals(false, Permissions.canHideProfile(Viewer(UserLevel.L2, MemberLevel.M3, Role.Member)))
        assertEquals(true, Permissions.canHideProfile(Viewer(UserLevel.L3, MemberLevel.None, Role.Member)))
        assertEquals(true, Permissions.canHideProfile(Viewer(UserLevel.L1, MemberLevel.None, Role.Swamiji)))
    }
}
