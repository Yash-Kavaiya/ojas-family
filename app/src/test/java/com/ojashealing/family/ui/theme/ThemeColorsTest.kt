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
    fun `there are two themes, Light is Healing Blue and Dark is Forest Sage`() {
        // Older saved choices read as the two themes
        assertEquals(colorsFor(ThemeMode.Light, systemDark = true), colorsFor(ThemeMode.HealingBlue, systemDark = true))
        assertEquals(colorsFor(ThemeMode.Dark, systemDark = false), colorsFor(ThemeMode.ForestSage, systemDark = false))
        // Someone who never chose follows the phone
        assertEquals(colorsFor(ThemeMode.Light, systemDark = true), colorsFor(ThemeMode.System, systemDark = false))
        assertEquals(colorsFor(ThemeMode.Dark, systemDark = false), colorsFor(ThemeMode.System, systemDark = true))
        val (light, lightIsDark) = colorsFor(ThemeMode.Light, systemDark = true)
        val (dark, darkIsDark) = colorsFor(ThemeMode.Dark, systemDark = false)
        assertFalse(lightIsDark)
        assertTrue(darkIsDark)
        // Cards pick their light or dark colours from the page's brightness
        assertTrue(light.background.luminance() > 0.5f)
        assertTrue(dark.background.luminance() < 0.5f)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFEAF3FF), light.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF111A15), dark.background)
        assertNotEquals(light.primary, dark.primary)
    }

    @Test
    fun `the big boxes read well in both themes`() {
        val t = QuickProtectionTint
        val light = colorsFor(ThemeMode.Light, false).first
        val dark = colorsFor(ThemeMode.Dark, false).first
        assertTrue("light heading", contrast(t.titleLight, t.boxLight) >= 4.5)
        assertTrue("dark heading", contrast(t.titleDark, t.boxDark) >= 4.5)
        assertTrue("light text", contrast(light.onSurface, t.boxLight) >= 7.0)
        assertTrue("dark text", contrast(dark.onSurface, t.boxDark) >= 7.0)
        assertTrue("light quiet text", contrast(light.onSurfaceVariant, t.boxLight) >= 4.5)
        assertTrue("dark quiet text", contrast(dark.onSurfaceVariant, t.boxDark) >= 4.5)
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
