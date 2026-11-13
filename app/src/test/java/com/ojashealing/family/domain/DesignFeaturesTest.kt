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
import com.ojashealing.family.ui.ask.AnswerPart
import com.ojashealing.family.ui.ask.answerParts
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
    fun aiAnswerCitationsAreSplitOut() {
        assertEquals(
            listOf(AnswerPart.Plain("Sit still "), AnswerPart.Cite(1), AnswerPart.Plain(". Breathe"), AnswerPart.Cite(2), AnswerPart.Cite(3)),
            answerParts("Sit still [1]. Breathe[2][3]")
        )
        assertEquals(listOf(AnswerPart.Plain("No sources")), answerParts("No sources"))
        assertEquals(listOf(AnswerPart.Plain("Keep [x] as text")), answerParts("Keep [x] as text"))
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

    @Test
    fun fitCentresTheWholePicture() {
        val r = com.ojashealing.family.ui.components.fitRect(4000, 2000, 1000f)
        assertEquals(0f, r.left, 0.01f)
        assertEquals(250f, r.top, 0.01f)
        assertEquals(1000f, r.right, 0.01f)
        assertEquals(750f, r.bottom, 0.01f)
    }

    private fun v(m: MemberLevel, r: Role = Role.Member) = Viewer(UserLevel.L1, m, r)

    @Test
    fun chatAndCallsOpenByMemberLevel() {
        assertEquals(false, Permissions.canChat(v(MemberLevel.None), chatPack = false))
        assertEquals(true, Permissions.canChat(v(MemberLevel.None), chatPack = true))
        assertEquals(true, Permissions.canChat(v(MemberLevel.M1), chatPack = false))
        assertEquals(false, Permissions.canAudioCall(v(MemberLevel.M1), callPack = false))
        assertEquals(true, Permissions.canAudioCall(v(MemberLevel.M1), callPack = true))
        assertEquals(true, Permissions.canAudioCall(v(MemberLevel.M2), callPack = false))
        assertEquals(false, Permissions.canVideoCall(v(MemberLevel.M2)))
        assertEquals(true, Permissions.canVideoCall(v(MemberLevel.M3)))
        assertEquals(true, Permissions.canVideoCall(v(MemberLevel.None, Role.Admin)))
    }

    @Test
    fun bookableSlotsAreEveryHalfHourInIndiaTime() {
        val slots = com.ojashealing.family.data.call.daySlots()
        assertEquals(6 to 0, slots.first())
        assertEquals(21 to 30, slots.last())
        assertEquals(32, slots.size)
        // The date picker's UTC midnight for 2026-11-07, at 16:30 IST = 11:00 UTC
        val utcMidnight = 1_794_009_600_000L - 1_794_009_600_000L % 86_400_000L
        val at = com.ojashealing.family.data.call.pickedDateAt(utcMidnight, 16, 30)
        assertEquals(utcMidnight + 11 * 3_600_000L, at)
    }

    @Test
    fun joinOpensTenMinutesBeforeAConfirmedCall() {
        val r = com.ojashealing.family.data.call.CallRequest("r", "m", "M", "", "swamiji", false, 1_000_000_000L, "",
            com.ojashealing.family.data.call.RequestStatus.Confirmed, 0, "", "")
        assertEquals(false, com.ojashealing.family.data.call.canJoinNow(r, r.at - 11 * 60_000))
        assertEquals(true, com.ojashealing.family.data.call.canJoinNow(r, r.at - 9 * 60_000))
        assertEquals(false, com.ojashealing.family.data.call.canJoinNow(r.copy(status = com.ojashealing.family.data.call.RequestStatus.Pending), r.at))
    }

    @Test
    fun callHistoryWording() {
        assertEquals("4:05", com.ojashealing.family.data.call.callDuration(245))
        assertEquals("1:02:03", com.ojashealing.family.data.call.callDuration(3723))
        assertEquals("3.4 MB", com.ojashealing.family.ui.ask.fileSize(3_565_158))
        assertEquals("820 KB", com.ojashealing.family.ui.ask.fileSize(840_000))
    }
}
