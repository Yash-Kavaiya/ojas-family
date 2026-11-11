package com.ojashealing.family.domain

import com.ojashealing.family.data.paid.Product
import com.ojashealing.family.data.practice.DayLog
import com.ojashealing.family.data.practice.lastDays
import com.ojashealing.family.data.practice.practiceStatus
import com.ojashealing.family.data.social.Author
import com.ojashealing.family.data.social.mentionedUids
import com.ojashealing.family.data.social.needsReview
import com.ojashealing.family.data.user.ageFromBirthMonth
import com.ojashealing.family.push.CallReminderPlan
import com.ojashealing.family.push.callAlarmInstants
import com.ojashealing.family.push.callRemindersToKeep
import com.ojashealing.family.push.nextOccurrence
import com.ojashealing.family.ui.paid.PlanGate
import com.ojashealing.family.ui.paid.gateFor
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {

    private val today = LocalDateLike(2026, 11, 7)
    private fun viewer(user: UserLevel, member: MemberLevel = MemberLevel.None, role: Role = Role.Member) = Viewer(user, member, role)

    @Test
    fun streakCountsYesterdayWhenTodayNotDoneYet() {
        val days = lastDays(today, 4) // 07, 06, 05, 04
        val logs = mapOf(days[1] to DayLog(true, true), days[2] to DayLog(true, false), days[3] to DayLog(false, false))
        val s = practiceStatus(logs, today)
        assertFalse(s.morningToday)
        assertEquals(2, s.morningStreak)
        assertEquals(1, s.bothStreak)
    }

    @Test
    fun streakIncludesToday() {
        val days = lastDays(today, 2)
        val s = practiceStatus(mapOf(days[0] to DayLog(true, false), days[1] to DayLog(true, false)), today)
        assertTrue(s.morningToday)
        assertEquals(2, s.morningStreak)
    }

    @Test
    fun lastDaysCrossesMonth() {
        assertEquals(listOf("2026-03-01", "2026-02-28"), lastDays(LocalDateLike(2026, 3, 1), 2))
    }

    @Test
    fun planGates() {
        assertEquals(PlanGate.Open, gateFor(Product.Membership, viewer(UserLevel.L1)))
        assertEquals(PlanGate.NeedsLevel(2), gateFor(Product.ChatWeek, viewer(UserLevel.L1)))
        assertEquals(PlanGate.Open, gateFor(Product.CallWeek, viewer(UserLevel.L2)))
        assertEquals(PlanGate.NeedsMember(2), gateFor(Product.ChatMonth, viewer(UserLevel.L4, MemberLevel.M1)))
        assertEquals(PlanGate.NeedsMember(3), gateFor(Product.CallMonth, viewer(UserLevel.L4, MemberLevel.M2)))
        assertEquals(PlanGate.Open, gateFor(Product.CallMonth, viewer(UserLevel.L1, role = Role.Admin)))
    }

    @Test
    fun paidAccessRules() {
        assertFalse(Permissions.canViewDailyActivities(viewer(UserLevel.L4)))
        assertTrue(Permissions.canViewDailyActivities(viewer(UserLevel.L1, MemberLevel.M1)))
        assertFalse(Permissions.canPostDailyActivity(viewer(UserLevel.L4, MemberLevel.M2)))
        assertTrue(Permissions.canPostDailyActivity(viewer(UserLevel.L1, MemberLevel.M3)))
        assertFalse(Permissions.canAttachImage(viewer(UserLevel.L4)))
        assertTrue(Permissions.canAttachImage(viewer(UserLevel.L1, MemberLevel.M1)))
    }

    @Test
    fun picturesAndLinksNeedReview() {
        assertTrue(needsReview("see www.x.com", ""))
        assertTrue(needsReview("hello", "https://img"))
        assertFalse(needsReview("hello", ""))
    }

    @Test
    fun mentionsOnlyPeopleOfferedAndNamed() {
        val a = Author("a", "Asha", "", Role.Member)
        val b = Author("b", "Bharat", "", Role.Member)
        assertEquals(listOf("a"), mentionedUids("Thank you @Asha", listOf(a, b)))
        assertEquals(emptyList<String>(), mentionedUids("@Someone", listOf(a, b)))
    }

    @Test
    fun ageFromBirthMonthCountsWholeYears() {
        assertEquals(36, ageFromBirthMonth("1990-11", today))
        assertEquals(35, ageFromBirthMonth("1990-12", today))
        assertNull(ageFromBirthMonth("", today))
    }

    @Test
    fun reminderNextOccurrence() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 7, 7, 0, 0) }
        val morning = nextOccurrence("06:00", now)!!
        assertEquals(8, morning.get(Calendar.DAY_OF_MONTH)) // 06:00 already passed: tomorrow
        val night = nextOccurrence("21:30", now)!!
        assertEquals(7, night.get(Calendar.DAY_OF_MONTH))
        assertEquals(21, night.get(Calendar.HOUR_OF_DAY))
        assertNull(nextOccurrence("25:00", now))
    }

    @Test
    fun callReminderRingsFifteenMinutesBeforeAndAtTheStart() {
        val at = 1_000_000_000_000L
        val ahead = at - 2 * 60 * 60 * 1000
        assertEquals(
            listOf("soon" to at - 15 * 60_000, "now" to at),
            callAlarmInstants(at, ahead),
        )
        // Inside the last 15 minutes the early reminder has passed, so only the start remains.
        assertEquals(listOf("now" to at), callAlarmInstants(at, at - 5 * 60_000))
        assertEquals(emptyList<Pair<String, Long>>(), callAlarmInstants(at, at + 1))
    }

    @Test
    fun confirmedCallReminderStaysUntilTenMinutesAfterTheStart() {
        val at = 5_000_000L
        val plan = CallReminderPlan("a", at, video = true, peer = "Swamiji")
        assertEquals(listOf(plan), callRemindersToKeep(listOf(plan, plan), at))
        assertEquals(emptyList<CallReminderPlan>(), callRemindersToKeep(listOf(plan), at + 10 * 60_000 + 1))
    }
}
