package com.ojashealing.family.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentTest {

    @Test fun parsesEveryYoutubeUrlShape() {
        listOf(
            "https://youtu.be/Pg54NjjSvzY",
            "https://www.youtube.com/watch?v=Pg54NjjSvzY&t=30s",
            "https://youtube.com/shorts/Pg54NjjSvzY",
            "https://www.youtube.com/embed/Pg54NjjSvzY",
            "https://m.youtube.com/watch?v=Pg54NjjSvzY",
            " Pg54NjjSvzY "
        ).forEach { assertEquals(it, "Pg54NjjSvzY", youtubeIdFrom(it)) }
    }

    @Test fun rejectsNonYoutubeInput() {
        assertNull(youtubeIdFrom("hello"))
        assertNull(youtubeIdFrom("https://example.com/watch?v=Pg54NjjSvzY"))
        assertNull(youtubeIdFrom(""))
    }

    private fun item(section: ContentSection, minUser: Int = 1, minMember: Int = 0) =
        ContentItem(id = "yt_x", section = section, videoId = "Pg54NjjSvzY", title = "t", minUserLevel = minUser, minMemberLevel = minMember)

    private fun viewer(u: UserLevel, m: MemberLevel = MemberLevel.None, r: Role = Role.Member) = Viewer(u, m, r)

    private val day = 86_400_000L
    private fun onPlan(plan: Plan, days: Int) = viewer(UserLevel.L1).copy(
        plan = plan, joinedAtMs = System.currentTimeMillis() - days * day,
        planSinceMs = System.currentTimeMillis() - days * day, planUntilMs = System.currentTimeMillis() + 30 * day,
    )

    @Test fun openSectionsAreOpenForEveryone() {
        assertEquals(Access.Open, accessFor(item(ContentSection.BlueLight), viewer(UserLevel.L1)))
        assertEquals(Access.Open, accessFor(item(ContentSection.Yoga), viewer(UserLevel.L1)))
        assertEquals(Access.Open, accessFor(item(ContentSection.Understanding), viewer(UserLevel.L1)))
    }

    @Test fun quickProtectionWaitsSixMonthsForFreeMembers() {
        assertEquals(Access.Locked(null, null, AccessRules.QuickProtection), accessFor(item(ContentSection.QuickProtection), viewer(UserLevel.L1)))
        assertEquals(Access.Open, accessFor(item(ContentSection.QuickProtection), onPlan(Plan.Silver, 0)))
    }

    @Test fun morningIsForSilverAfterAWeekAndGold() {
        assertEquals(Access.Locked(null, null, AccessRules.DailyPractice), accessFor(item(ContentSection.Morning), viewer(UserLevel.L4)))
        assertEquals(Access.Locked(null, null, AccessRules.DailyPractice), accessFor(item(ContentSection.Morning), onPlan(Plan.Silver, 3)))
        assertEquals(Access.Open, accessFor(item(ContentSection.Morning), onPlan(Plan.Silver, 8)))
        assertEquals(Access.Open, accessFor(item(ContentSection.Night), onPlan(Plan.Gold, 0)))
    }

    @Test fun itemLevelCanOnlyRaiseTheBar() =
        assertEquals(Access.Locked(needsUserLevel = 3, needsMemberLevel = null), accessFor(item(ContentSection.Yoga, minUser = 3), viewer(UserLevel.L2)))

    @Test fun adminSeesEverything() =
        assertEquals(Access.Open, accessFor(item(ContentSection.Night, minUser = 4, minMember = 3), viewer(UserLevel.L1, r = Role.Admin)))

    private fun book(minUser: Int = 1, minMember: Int = 0) =
        Book(id = "b", title = "t", url = "https://example.com/a.pdf", minUserLevel = minUser, minMemberLevel = minMember)

    @Test fun booksAreOpenForEveryone() =
        assertEquals(Access.Open, accessFor(book(), viewer(UserLevel.L1)))

    @Test fun bookLevelCanOnlyRaiseTheBar() {
        assertEquals(Access.Locked(needsUserLevel = 3, needsMemberLevel = null), accessFor(book(minUser = 3), viewer(UserLevel.L2)))
        assertEquals(Access.Locked(needsUserLevel = 3, needsMemberLevel = null), accessFor(book(minUser = 3), viewer(UserLevel.L1)))
    }

    @Test fun staffOpensEveryBook() =
        assertEquals(Access.Open, accessFor(book(minUser = 4, minMember = 3), viewer(UserLevel.L1, r = Role.Admin)))
}
