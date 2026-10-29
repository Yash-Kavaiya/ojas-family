package com.ojashealing.family.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ojashealing.family.ui.theme.OjasFamilyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LabelOverflowTest {

    @get:Rule val compose = createComposeRule()

    private fun assertLabelsFitOneLine() {
        compose.setContent { OjasFamilyTheme { OjasBottomBar(current = MainTab.Home, onSelect = {}) } }
        MainTab.entries.forEach { compose.onNodeWithTag("nav_${it.name}").assertIsDisplayed() }
        // Each label is a single line: its height equals one line of labelMedium, never two
        val heights = compose.onAllNodesWithTag("nav_label", useUnmergedTree = true).fetchSemanticsNodes().map { it.size.height }
        assertEquals(MainTab.entries.size, heights.size)
        assertEquals(1, heights.toSet().size)
    }

    @Test @Config(qualifiers = "hi")
    fun hindiLabelsFit() = assertLabelsFitOneLine()

    @Test @Config(qualifiers = "gu")
    fun gujaratiLabelsFit() = assertLabelsFitOneLine()

    @Test @Config(qualifiers = "en")
    fun englishLabelsFit() = assertLabelsFitOneLine()
}
