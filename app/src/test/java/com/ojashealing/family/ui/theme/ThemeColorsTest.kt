package com.ojashealing.family.ui.theme

import androidx.compose.ui.graphics.luminance
import com.ojashealing.family.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorsTest {
    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val (hi, lo) = listOf(a.luminance() + 0.05, b.luminance() + 0.05).sortedDescending()
        return hi.toDouble() / lo
    }

    @Test
    fun `System, Light and Dark keep the Ojas colours`() {
        assertEquals(colorsFor(ThemeMode.Light, systemDark = true), colorsFor(ThemeMode.System, systemDark = false))
        assertEquals(colorsFor(ThemeMode.Dark, systemDark = false), colorsFor(ThemeMode.System, systemDark = true))
        assertFalse(colorsFor(ThemeMode.Light, systemDark = true).second)
        assertTrue(colorsFor(ThemeMode.Dark, systemDark = false).second)
    }

    @Test
    fun `Healing Blue is a light theme and Forest Sage a dark one, whatever the phone says`() {
        val (blue, blueDark) = colorsFor(ThemeMode.HealingBlue, systemDark = true)
        val (sage, sageDark) = colorsFor(ThemeMode.ForestSage, systemDark = false)
        assertFalse(blueDark)
        assertTrue(sageDark)
        // Cards pick their light or dark colours from the page's brightness
        assertTrue(blue.background.luminance() > 0.5f)
        assertTrue(sage.background.luminance() < 0.5f)
        assertNotEquals(colorsFor(ThemeMode.Light, false).first.primary, blue.primary)
        assertNotEquals(colorsFor(ThemeMode.Dark, false).first.primary, sage.primary)
    }

    @Test
    fun `text and buttons stay readable in the new themes`() {
        for (mode in listOf(ThemeMode.HealingBlue, ThemeMode.ForestSage)) {
            val c = colorsFor(mode, systemDark = false).first
            assertTrue("$mode text", contrast(c.onBackground, c.background) >= 7.0)
            assertTrue("$mode secondary text", contrast(c.onSurfaceVariant, c.surface) >= 4.5)
            assertTrue("$mode button", contrast(c.onPrimary, c.primary) >= 4.5)
            assertTrue("$mode link", contrast(c.primary, c.background) >= 4.5)
        }
    }
}
