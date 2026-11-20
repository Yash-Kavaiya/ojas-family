package com.ojashealing.family.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The owner's plan table (2026-11-20): what Free, Silver and Gold open, and when. */
class PermissionsTest {

    @Test fun freeMembersOpenThingsWithTime() {
        assertFalse(Permissions.canLike(freeFor(3)))
        assertTrue(Permissions.canLike(freeFor(7)))
        assertFalse(Permissions.canComment(freeFor(100)))
        assertTrue(Permissions.canComment(freeFor(182)))
        assertTrue(Permissions.canAskQuestion(freeFor(182)))
        assertFalse(Permissions.canAskWithMedia(freeFor(300)))
        assertTrue(Permissions.canAskWithMedia(freeFor(365)))
        assertTrue(Permissions.canDoQuickProtection(freeFor(182)))
        // Never on the free plan, however long
        assertFalse(Permissions.canDoDailyPractices(freeFor(1000)))
        assertFalse(Permissions.canViewDailyActivities(freeFor(1000)))
        assertFalse(Permissions.canHideProfile(freeFor(1000)))
    }

    @Test fun everyoneReadsBooksAndWatchesVideosFromTheFirstDay() = assertTrue(Permissions.canReadUnderstanding(freeFor(0)))

    @Test fun silverOpensSooner() {
        assertTrue(Permissions.canLike(onPlanFor(Plan.Silver, 0)))
        assertTrue(Permissions.canDoQuickProtection(onPlanFor(Plan.Silver, 0)))
        assertFalse(Permissions.canDoDailyPractices(onPlanFor(Plan.Silver, 6)))
        assertTrue(Permissions.canDoDailyPractices(onPlanFor(Plan.Silver, 7)))
        assertFalse(Permissions.canReplyAnnouncement(onPlanFor(Plan.Silver, 0)))
        assertTrue(Permissions.canReplyAnnouncement(onPlanFor(Plan.Silver, 7)))
        assertFalse(Permissions.canViewDailyActivities(onPlanFor(Plan.Silver, 100)))
        assertTrue(Permissions.canViewDailyActivities(onPlanFor(Plan.Silver, 182)))
        assertFalse(Permissions.canReplyDailyActivity(onPlanFor(Plan.Silver, 300)))
        assertTrue(Permissions.canUploadFacePhoto(onPlanFor(Plan.Silver, 365)))
        assertFalse(Permissions.canPostDailyActivity(onPlanFor(Plan.Silver, 1000)))
        assertFalse(Permissions.canHideProfile(onPlanFor(Plan.Silver, 1000)))
    }

    @Test fun goldOpensSoonest() {
        assertTrue(Permissions.canDoDailyPractices(onPlanFor(Plan.Gold, 0)))
        assertTrue(Permissions.canReplyAnnouncement(onPlanFor(Plan.Gold, 0)))
        assertTrue(Permissions.canViewDailyActivities(onPlanFor(Plan.Gold, 7)))
        assertTrue(Permissions.canReplyDailyActivity(onPlanFor(Plan.Gold, 182)))
        assertTrue(Permissions.canUploadFacePhoto(onPlanFor(Plan.Gold, 182)))
        assertFalse(Permissions.canPostDailyActivity(onPlanFor(Plan.Gold, 364)))
        assertTrue(Permissions.canPostDailyActivity(onPlanFor(Plan.Gold, 365)))
        assertTrue(Permissions.canHideProfile(onPlanFor(Plan.Gold, 365)))
    }

    @Test fun aPlanThatRanOutCountsForNothing() {
        val now = System.currentTimeMillis()
        val lapsed = onPlanFor(Plan.Gold, 400).copy(planUntilMs = now - 1000, joinedAtMs = now)
        assertFalse(Permissions.canLike(lapsed))
        assertEquals(Plan.User, lapsed.activePlan())
    }

    @Test fun aMemberGetsTheBetterOfTheirOwnTimeAndTheirPlan() {
        val longFreeNowSilver = onPlanFor(Plan.Silver, 0).copy(joinedAtMs = System.currentTimeMillis() - 200 * 86_400_000L)
        assertTrue(Permissions.canComment(longFreeNowSilver))
    }

    @Test fun dailyLimits() {
        assertEquals(5, freeFor(0).aiDailyLimit())
        assertEquals(50, freeFor(182).aiDailyLimit())
        assertEquals(100, freeFor(365).aiDailyLimit())
        assertEquals(50, onPlanFor(Plan.Silver, 0).aiDailyLimit())
        assertEquals(100, onPlanFor(Plan.Gold, 182).aiDailyLimit())
        assertEquals(Int.MAX_VALUE, onPlanFor(Plan.Gold, 365).aiDailyLimit())
        assertEquals(0, freeFor(100).qaDailyLimit())
        assertEquals(1, onPlanFor(Plan.Silver, 7).qaDailyLimit())
        assertEquals(5, onPlanFor(Plan.Gold, 182).qaDailyLimit())
        assertEquals(10, onPlanFor(Plan.Gold, 365).qaDailyLimit())
    }

    @Test fun daysUntilSaysWhenItOpensOnTheCurrentPlan() {
        assertEquals(4, freeFor(3).daysUntil(AccessRules.Like))
        assertEquals(0, freeFor(10).daysUntil(AccessRules.Like))
        assertNull(freeFor(3).daysUntil(AccessRules.DailyPractice))
    }

    @Test fun swamijiAndAdminsHaveEverything() {
        for (role in listOf(Role.Admin, Role.Swamiji)) {
            val v = freeFor(0, role)
            val all = listOf(
                Permissions::canLike, Permissions::canComment, Permissions::canAskQuestion, Permissions::canAskWithMedia,
                Permissions::canDoDailyPractices, Permissions::canDoQuickProtection, Permissions::canPostDailyActivity,
                Permissions::canViewDailyActivities, Permissions::canReplyDailyActivity, Permissions::canHideProfile,
                Permissions::canUploadFacePhoto, Permissions::isStaff,
            )
            all.forEach { assertTrue("${role.name} ${it.name}", it(v)) }
            assertEquals(Int.MAX_VALUE, v.aiDailyLimit())
        }
    }

    @Test fun moderatorsFollowTheirOwnPlan() {
        val mod = freeFor(0, Role.Moderator)
        assertTrue(Permissions.isStaff(mod))
        assertFalse(Permissions.canLike(mod))
        assertFalse(Permissions.canViewDailyActivities(mod))
        assertEquals(Role.Moderator, Role.fromId("senior_member"))
        assertEquals(Role.Moderator, Role.fromId("moderator"))
    }

    @Test fun onlySwamijiAndAdminsSeeHowManyUsersAndMembers() {
        assertTrue(Permissions.canSeeUserCounts(freeFor(0, Role.Swamiji)))
        assertTrue(Permissions.canSeeUserCounts(freeFor(0, Role.Admin)))
        assertFalse(Permissions.canSeeUserCounts(freeFor(0, Role.Moderator)))
        assertFalse(Permissions.canSeeUserCounts(onPlanFor(Plan.Gold, 400)))
    }

    @Test fun chatAndCallPacksAsBefore() {
        // Booking with the team is open to everyone; monthly chat and anytime calls still follow member level (part 2)
        assertTrue(Permissions.canBuyWeeklyChat(freeFor(0)))
        assertTrue(Permissions.canBuyMonthlyChat(Viewer(UserLevel.L2, MemberLevel.M2, Role.Member)))
        assertFalse(Permissions.canBuyMonthlyChat(Viewer(UserLevel.L2, MemberLevel.M1, Role.Member)))
        assertTrue(Permissions.canBuyAnytimeCalls(Viewer(UserLevel.L2, MemberLevel.M3, Role.Member)))
    }
}
