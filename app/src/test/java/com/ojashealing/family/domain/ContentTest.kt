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

    @Test fun openSectionsAreOpenForEveryone() =
        assertEquals(Access.Open, accessFor(item(ContentSection.QuickProtection), viewer(UserLevel.L1)))

    @Test fun morningNeedsLevel4OrMember1() {
        assertEquals(Access.Locked(needsUserLevel = 4, needsMemberLevel = 1), accessFor(item(ContentSection.Morning), viewer(UserLevel.L1)))
        assertEquals(Access.Open, accessFor(item(ContentSection.Morning), viewer(UserLevel.L2, MemberLevel.M1)))
    }

    @Test fun understandingNeedsLevel2() =
        assertEquals(Access.Locked(needsUserLevel = 2, needsMemberLevel = 1), accessFor(item(ContentSection.Understanding), viewer(UserLevel.L1)))

    @Test fun itemLevelCanOnlyRaiseTheBar() =
        assertEquals(Access.Locked(needsUserLevel = 3, needsMemberLevel = null), accessFor(item(ContentSection.Yoga, minUser = 3), viewer(UserLevel.L2)))

    @Test fun adminSeesEverything() =
        assertEquals(Access.Open, accessFor(item(ContentSection.Night, minUser = 4, minMember = 3), viewer(UserLevel.L1, r = Role.Admin)))
}
