package com.ojashealing.family.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyTest {

    private fun assertSize(style: TextStyle, size: Int, line: Int) {
        assertEquals(size.sp, style.fontSize)
        assertEquals(line.sp, style.lineHeight)
    }

    @Test
    fun latinScaleMatchesSpec() {
        val t = ojasTypography(Script.Latin)
        assertSize(t.displayLarge, 32, 40)
        assertSize(t.titleLarge, 20, 28)
        assertSize(t.bodyLarge, 16, 24)
        assertSize(t.labelLarge, 14, 20)
        assertSize(t.bodySmall, 12, 16)
    }

    @Test
    fun indicScriptsAddTwoSpLineHeight() {
        assertEquals(26.sp, ojasTypography(Script.Devanagari).bodyLarge.lineHeight)
        assertEquals(26.sp, ojasTypography(Script.Gujarati).bodyLarge.lineHeight)
    }

    @Test
    fun onlyTwoWeights() {
        val t = ojasTypography(Script.Latin)
        val styles = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium,
            t.headlineSmall, t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium,
            t.bodySmall, t.labelLarge, t.labelMedium, t.labelSmall
        )
        styles.forEach { assertTrue(it.fontWeight in setOf(FontWeight.Normal, FontWeight.SemiBold)) }
    }

    @Test
    fun scriptForLocale() {
        assertEquals(Script.Devanagari, scriptFor(Locale.forLanguageTag("hi")))
        assertEquals(Script.Gujarati, scriptFor(Locale.forLanguageTag("gu")))
        assertEquals(Script.Latin, scriptFor(Locale.ENGLISH))
    }
}
