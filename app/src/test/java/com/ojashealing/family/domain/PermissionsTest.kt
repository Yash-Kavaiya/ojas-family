package com.ojashealing.family.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {

    private fun v(u: UserLevel, m: MemberLevel = MemberLevel.None, r: Role = Role.Member) = Viewer(u, m, r)

    @Test fun level1Free_cannotLikeOrReadUnderstanding() {
        assertFalse(Permissions.canLike(v(UserLevel.L1)))
        assertFalse(Permissions.canReadUnderstanding(v(UserLevel.L1)))
    }

    @Test fun level2Free_likesReadsAndCanBuyWeeklyChat() {
        val viewer = v(UserLevel.L2)
        assertTrue(Permissions.canLike(viewer))
        assertFalse(Permissions.canComment(viewer))
        assertTrue(Permissions.canReadUnderstanding(viewer))
        assertTrue(Permissions.canBuyWeeklyChat(viewer))
    }

    @Test fun level3Free_commentsAndAsksButNoDailyPractice() {
        val viewer = v(UserLevel.L3)
        assertTrue(Permissions.canComment(viewer))
        assertTrue(Permissions.canAskQuestion(viewer))
        assertFalse(Permissions.canDoDailyPractices(viewer))
    }

    @Test fun level4Free_doesDailyPractice() = assertTrue(Permissions.canDoDailyPractices(v(UserLevel.L4)))

    @Test fun member1_dailyPracticeAndLikesFromLevel1() {
        assertTrue(Permissions.canDoDailyPractices(v(UserLevel.L2, MemberLevel.M1)))
        assertTrue(Permissions.canLike(v(UserLevel.L1, MemberLevel.M1)))
    }

    @Test fun member2_commentsAndMonthlyChat() {
        assertTrue(Permissions.canComment(v(UserLevel.L2, MemberLevel.M2)))
        assertTrue(Permissions.canBuyMonthlyChat(v(UserLevel.L2, MemberLevel.M2)))
        assertFalse(Permissions.canBuyMonthlyChat(v(UserLevel.L2, MemberLevel.M1)))
    }

    @Test fun member3_postsDailyActivityViewsProfilesAnytimeCalls() {
        val viewer = v(UserLevel.L2, MemberLevel.M3)
        assertTrue(Permissions.canPostDailyActivity(viewer))
        assertTrue(Permissions.canViewOtherProfiles(viewer))
        assertTrue(Permissions.canBuyAnytimeCalls(viewer))
    }

    @Test fun admin_hasEveryPermission() {
        val admin = v(UserLevel.L1, r = Role.Admin)
        val all = listOf(
            Permissions::canLike, Permissions::canComment, Permissions::canAskQuestion,
            Permissions::canReadUnderstanding, Permissions::canDoDailyPractices, Permissions::canPostDailyActivity,
            Permissions::canViewOtherProfiles, Permissions::canBuyWeeklyChat, Permissions::canBuyMonthlyChat,
            Permissions::canBuyAnytimeCalls, Permissions::isStaff
        )
        all.forEach { assertTrue(it.name, it(admin)) }
    }
}
