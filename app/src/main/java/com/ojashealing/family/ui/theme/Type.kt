package com.ojashealing.family.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ojashealing.family.R
import java.util.Locale

enum class Script { Latin, Devanagari, Gujarati }

fun scriptFor(locale: Locale): Script = when (locale.language) {
    "hi" -> Script.Devanagari
    "gu" -> Script.Gujarati
    else -> Script.Latin
}

// Each Indic Noto family also carries Latin glyphs, so mixed text keeps one set of metrics
private fun fontFamilyFor(script: Script): FontFamily = when (script) {
    Script.Latin -> FontFamily(
        Font(R.font.noto_sans_regular, FontWeight.Normal),
        Font(R.font.noto_sans_semibold, FontWeight.SemiBold)
    )
    Script.Devanagari -> FontFamily(
        Font(R.font.noto_sans_devanagari_regular, FontWeight.Normal),
        Font(R.font.noto_sans_devanagari_semibold, FontWeight.SemiBold)
    )
    Script.Gujarati -> FontFamily(
        Font(R.font.noto_sans_gujarati_regular, FontWeight.Normal),
        Font(R.font.noto_sans_gujarati_semibold, FontWeight.SemiBold)
    )
}

/**
 * The app's only type scale: display 32/40, title 20/28, body 16/24, label 14/20, caption 12/16.
 * Devanagari and Gujarati get +2sp line height so matras above and below the line are not clipped.
 */
fun ojasTypography(script: Script): Typography {
    val family = fontFamilyFor(script)
    val extraLine = if (script == Script.Latin) 0 else 2

    fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (line + extraLine).sp
    )

    val display = style(32, 40, FontWeight.SemiBold)
    val title = style(20, 28, FontWeight.SemiBold)
    val body = style(16, 24, FontWeight.Normal)
    val label = style(14, 20, FontWeight.SemiBold)
    val caption = style(12, 16, FontWeight.Normal)

    return Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = title,
        headlineMedium = title,
        headlineSmall = title,
        titleLarge = title,
        titleMedium = title,
        titleSmall = label,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = label,
        labelMedium = label,
        labelSmall = caption
    )
}
