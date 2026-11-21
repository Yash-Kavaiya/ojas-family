package com.ojashealing.family.ui.help

import com.ojashealing.family.domain.AccessRules
import com.ojashealing.family.domain.MemberLevel
import com.ojashealing.family.domain.Plan
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.UserLevel
import com.ojashealing.family.domain.Viewer
import com.ojashealing.family.domain.aiDailyLimit
import com.ojashealing.family.ui.community.CommunityTab
import com.ojashealing.family.ui.content.PracticePart
import com.ojashealing.family.ui.navigation.MainTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScreenHelpTest {
    @Test fun everyInnerTabHasItsOwnTopic() {
        fun t(tab: MainTab, c: Int = 0, p: Int = 0, a: Int = 0) = helpTopicFor(tab, c, p, a)
        assertEquals(HelpTopic.Thought, t(MainTab.Home))
        assertEquals(HelpTopic.Qa, t(MainTab.Community, c = CommunityTab.QA))
        assertEquals(HelpTopic.Daily, t(MainTab.Community, c = CommunityTab.DAILY))
        assertEquals(HelpTopic.Notices, t(MainTab.Community, c = CommunityTab.NOTICES))
        assertEquals(HelpTopic.Read, t(MainTab.Spiritual, p = PracticePart.READ))
        assertEquals(HelpTopic.Practice, t(MainTab.Spiritual, p = PracticePart.DAILY))
        assertEquals(HelpTopic.Freedom, t(MainTab.Spiritual, p = PracticePart.FREEDOM))
        assertEquals(HelpTopic.Ask, t(MainTab.Ask, a = 0))
        assertEquals(HelpTopic.Video, t(MainTab.Ask, a = 1))
        assertEquals(HelpTopic.Books, t(MainTab.Ask, a = 2))
        assertEquals(HelpTopic.Profile, t(MainTab.Profile))
    }

    @Test fun opensByItselfOnlyTheFirstTimeOnTheMainScreen() {
        val topic = HelpTopic.Qa
        assertTrue(shouldAutoOpenHelp(topic, emptySet(), onTabs = true, profileLoaded = true, sheetOpen = false))
        assertFalse(shouldAutoOpenHelp(topic, setOf(topic.key), onTabs = true, profileLoaded = true, sheetOpen = false))
        assertFalse(shouldAutoOpenHelp(topic, emptySet(), onTabs = false, profileLoaded = true, sheetOpen = false))
        assertFalse(shouldAutoOpenHelp(topic, emptySet(), onTabs = true, profileLoaded = false, sheetOpen = false))
        assertFalse(shouldAutoOpenHelp(topic, emptySet(), onTabs = true, profileLoaded = true, sheetOpen = true))
    }

    private fun label(days: Int?): Int = when (days) {
        AccessRules.WEEK -> 7
        AccessRules.HALF_YEAR -> 6
        AccessRules.YEAR -> 12
        else -> error("no label for $days")
    }

    private val now = 1_800_000_000_000L
    private fun ai(plan: Plan, days: Int): Int {
        val since = now - days * 86_400_000L
        val v = if (plan == Plan.User) Viewer(UserLevel.L1, MemberLevel.None, Role.Member, joinedAtMs = since)
        else Viewer(UserLevel.L1, MemberLevel.None, Role.Member, plan, joinedAtMs = now, planSinceMs = since, planUntilMs = now + 86_400_000L)
        return v.aiDailyLimit(now)
    }

    @Test fun accessTextMatchesThePlanTableInEveryLanguage() {
        val r = AccessRules
        val y = r.YEAR
        val h = r.HALF_YEAR
        val expected = mapOf(
            "help_thought_free" to listOf(label(r.Like.user), label(r.ReplyThought.user)),
            "help_thought_paid" to listOf(label(r.ReplyThought.silver)),
            "help_qa_free" to listOf(label(r.Like.user), label(r.ReplyQa.user), 1, label(r.AskMedia.user), 5),
            "help_qa_silver" to listOf(label(r.ReplyQa.silver), 1, label(r.AskMedia.silver), 5),
            "help_qa_gold" to listOf(label(r.ReplyQa.gold), 1, label(r.AskMedia.gold), 5, label(y), 10),
            "help_daily_free" to emptyList(),
            "help_daily_silver" to listOf(label(r.ActivityRead.silver), label(r.ActivityReply.silver)),
            "help_daily_gold" to listOf(label(r.ActivityRead.gold), label(r.ActivityReply.gold), label(r.ActivityPost.gold)),
            "help_notices_free" to listOf(label(r.Like.user), label(r.ReplyAnnouncement.user)),
            "help_notices_silver" to listOf(label(r.ReplyAnnouncement.silver)),
            "help_notices_gold" to emptyList(),
            "help_practice_free" to listOf(label(r.QuickProtection.user)),
            "help_practice_silver" to listOf(label(r.DailyPractice.silver)),
            "help_practice_gold" to emptyList(),
            "help_ask_body" to listOf(label(r.WithSwamiji.user)),
            "help_ask_free" to listOf(ai(Plan.User, 0), label(h), ai(Plan.User, h), label(y), ai(Plan.User, y)),
            "help_ask_silver" to listOf(ai(Plan.Silver, 0), label(h), ai(Plan.Silver, h)),
            "help_ask_gold" to listOf(ai(Plan.Gold, 0), label(h), ai(Plan.Gold, h), label(y)),
            "help_profile_photo" to listOf(label(r.HealingPhoto.silver), label(r.HealingPhoto.gold)),
            "help_profile_hide" to listOf(label(r.HideProfile.gold)),
        )
        assertEquals(Int.MAX_VALUE, ai(Plan.Gold, y))
        for (folder in listOf("values", "values-hi", "values-gu")) {
            val xml = File("src/main/res/$folder/strings.xml").readText()
            for ((name, numbers) in expected) {
                val text = Regex("""<string name="$name">(.*?)</string>""").find(xml)?.groupValues?.get(1)
                    ?: error("$folder is missing $name")
                assertEquals("$folder/$name", numbers, Regex("""\d+""").findAll(text).map { it.value.toInt() }.toList())
            }
        }
    }
}
