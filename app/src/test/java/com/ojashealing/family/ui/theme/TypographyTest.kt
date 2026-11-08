package com.ojashealing.family.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TypographyTest {

    private fun assertSize(style: TextStyle, size: Int, line: Int) {
        assertEquals(size.sp, style.fontSize)
        assertEquals(line.sp, style.lineHeight)
    }

    @Test
    fun latinScale() {
        val t = ojasTypography(Script.Latin)
        assertSize(t.displayLarge, 24, 30)
        assertSize(t.headlineMedium, 20, 26)
        assertSize(t.titleLarge, 17, 24)
        assertSize(t.bodyLarge, 15, 22)
        assertSize(t.labelLarge, 13, 18)
        assertSize(t.bodySmall, 12, 16)
    }

    @Test
    fun indicScriptsAddFourSpLineHeight() {
        assertEquals(26.sp, ojasTypography(Script.Devanagari).bodyLarge.lineHeight)
        assertEquals(26.sp, ojasTypography(Script.Gujarati).bodyLarge.lineHeight)
    }

    @Test
    fun englishHeadingsAndBodyUseDifferentFaces() {
        val t = ojasTypography(Script.Latin)
        assertNotEquals(t.titleLarge.fontFamily, t.bodyLarge.fontFamily)
        assertEquals(Poppins, t.titleLarge.fontFamily)
    }

    @Test
    fun gujaratiUsesOneFaceThroughout() {
        val t = ojasTypography(Script.Gujarati)
        assertEquals(t.titleLarge.fontFamily, t.bodyLarge.fontFamily)
        assertNotEquals(Poppins, t.bodyLarge.fontFamily)
    }

    @Test
    fun scriptForLocale() {
        assertEquals(Script.Devanagari, scriptFor(Locale.forLanguageTag("hi")))
        assertEquals(Script.Gujarati, scriptFor(Locale.forLanguageTag("gu")))
        assertEquals(Script.Latin, scriptFor(Locale.ENGLISH))
    }
}
