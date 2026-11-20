package com.ojashealing.family.ui.content

import com.ojashealing.family.domain.Access
import com.ojashealing.family.domain.Book
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
        assertEquals(Access.Locked(null, null, com.ojashealing.family.domain.AccessRules.DailyPractice), morning.second)
    }

    @Test fun unpublishedHiddenFromMembersButShownToAdmins() {
        assertTrue(buildPractice(items, l1).flatMap { it.items }.none { it.first.id == "hidden" })
        val admin = l1.copy(role = Role.Admin)
        assertTrue(buildPractice(items, admin).flatMap { it.items }.any { it.first.id == "hidden" })
    }

    @Test fun unpublishedBooksHiddenExceptForStaff() {
        val books = listOf(
            Book(id = "b1", title = "One", url = "https://example.com/1.pdf"),
            Book(id = "b2", title = "Two", url = "https://example.com/2.pdf", published = false)
        )
        val forMember = buildBooks(books, l1)
        assertEquals(listOf("b1"), forMember.map { it.first.id })
        assertTrue(forMember.single().second is Access.Open)
        assertEquals(listOf("b1", "b2"), buildBooks(books, l1.copy(role = Role.Admin)).map { it.first.id })
    }
}
