package com.ojashealing.family.ui.content

import com.ojashealing.family.domain.Access
import com.ojashealing.family.domain.ContentItem
import com.ojashealing.family.domain.ContentSection
import com.ojashealing.family.domain.MemberLevel
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.UserLevel
import com.ojashealing.family.domain.Viewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentViewModelTest {

    private fun item(id: String, section: ContentSection, published: Boolean = true) =
        ContentItem(id = id, section = section, videoId = "Pg54NjjSvzY", title = id, published = published)

    private val l1 = Viewer(UserLevel.L1, MemberLevel.None, Role.Member)
    private val items = listOf(
        item("yoga1", ContentSection.Yoga),
        item("blue1", ContentSection.BlueLight),
        item("morning1", ContentSection.Morning),
        item("und1", ContentSection.Understanding),
        item("hidden", ContentSection.Other, published = false)
    )

    @Test fun practiceGroupsBySectionInSpecOrder() =
        assertEquals(listOf(ContentSection.BlueLight, ContentSection.Morning, ContentSection.Yoga), buildPractice(items, l1).map { it.section })

    @Test fun emptySectionsAreHidden() =
        assertTrue(buildPractice(items, l1).none { it.section == ContentSection.QuickProtection })

    @Test fun understandingExcludedFromPractice() {
        assertTrue(buildPractice(items, l1).none { it.section == ContentSection.Understanding })
        assertEquals(listOf("und1"), buildUnderstanding(items, l1).map { it.first.id })
    }

    @Test fun lockedItemsCarryRequirement() {
        val morning = buildPractice(items, l1).first { it.section == ContentSection.Morning }.items.single()
        assertEquals(Access.Locked(needsUserLevel = 4, needsMemberLevel = 1), morning.second)
    }

    @Test fun unpublishedHiddenFromMembersButShownToAdmins() {
        assertTrue(buildPractice(items, l1).flatMap { it.items }.none { it.first.id == "hidden" })
        val admin = l1.copy(role = Role.Admin)
        assertTrue(buildPractice(items, admin).flatMap { it.items }.any { it.first.id == "hidden" })
    }
}
