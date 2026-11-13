package com.ojashealing.family.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
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

/** Poppins: the brand face for English headings, buttons and labels. */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

/** Lato: calm, very readable English body text. */
private val Lato = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_regular, FontWeight.Medium),
    Font(R.font.lato_bold, FontWeight.SemiBold),
    Font(R.font.lato_bold, FontWeight.Bold)
)

/** Hind Vadodara: Gujarati (it also carries Latin, so names typed in English match). */
private val HindVadodara = FontFamily(
    Font(R.font.hind_vadodara_regular, FontWeight.Normal),
    Font(R.font.hind_vadodara_medium, FontWeight.Medium),
    Font(R.font.hind_vadodara_semibold, FontWeight.SemiBold),
    Font(R.font.hind_vadodara_bold, FontWeight.Bold)
)

/** Hind: the Devanagari sister of Hind Vadodara, so Hindi and Gujarati screens look alike. */
private val Hind = FontFamily(
    Font(R.font.hind_regular, FontWeight.Normal),
    Font(R.font.hind_medium, FontWeight.Medium),
    Font(R.font.hind_semibold, FontWeight.SemiBold),
    Font(R.font.hind_bold, FontWeight.Bold)
)

/** Lora italic for the English Thought of the Day quote. Variable font: weight applies on Android 8+. */
@OptIn(ExperimentalTextApi::class)
private val LoraItalic = FontFamily(
    if (Build.VERSION.SDK_INT >= 26) {
        Font(R.font.lora_italic, FontWeight.Medium, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(500)))
    } else {
        Font(R.font.lora_italic, FontWeight.Medium, FontStyle.Italic)
    }
)

private fun headingFamily(script: Script) = when (script) {
    Script.Latin -> Poppins
    Script.Devanagari -> Hind
    Script.Gujarati -> HindVadodara
}

private fun bodyFamily(script: Script) = when (script) {
    Script.Latin -> Lato
    Script.Devanagari -> Hind
    Script.Gujarati -> HindVadodara
}

/**
 * Compact type scale, close to LinkedIn: display 24/30, headline 20/26, title 17/24, title-medium 15/22,
 * body 15/22, body-medium 14/20, label 13/18, caption 12/16. Hind and Hind Vadodara get +4sp line height so matras above
 * and below the line are never clipped.
 */
fun ojasTypography(script: Script): Typography {
    val heading = headingFamily(script)
    val body = bodyFamily(script)
    val extraLine = if (script == Script.Latin) 0 else 4

    fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (line + extraLine).sp,
        letterSpacing = spacing.sp
    )

    val display = style(heading, 24, 30, FontWeight.Bold, -0.2)
    val headline = style(heading, 20, 26, FontWeight.SemiBold)
    val title = style(heading, 17, 24, FontWeight.SemiBold)
    val titleMedium = style(heading, 15, 22, FontWeight.SemiBold)
    val bodyL = style(body, 15, 22, FontWeight.Normal)
    val bodyM = style(body, 14, 20, FontWeight.Normal)
    val label = style(heading, 13, 18, FontWeight.Medium, 0.1)
    val caption = style(body, 12, 16, FontWeight.Normal, 0.2)

    return Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = headline,
        headlineMedium = headline,
        headlineSmall = headline,
        titleLarge = title,
        titleMedium = titleMedium,
        titleSmall = label,
        bodyLarge = bodyL,
        bodyMedium = bodyM,
        bodySmall = caption,
        labelLarge = label,
        labelMedium = label.copy(fontSize = 12.sp, lineHeight = (16 + extraLine).sp),
        labelSmall = caption
    )
}

/** The Thought of the Day quote: Lora italic in English, the script's own face in Hindi and Gujarati. */
fun quoteStyle(script: Script): TextStyle = when (script) {
    Script.Latin -> TextStyle(fontFamily = LoraItalic, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 26.sp)
    else -> TextStyle(fontFamily = headingFamily(script), fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 30.sp)
}

val LocalQuoteStyle = staticCompositionLocalOf { TextStyle.Default }

/** The "Ojas Family" wordmark: Poppins Bold whatever the app language. */
val BrandTitleStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp)

/** Numbers on coins and counters: Poppins SemiBold, so digits look the same in every language. */
val BrandNumberStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)

/** Big counting numbers: a streak, a points balance, an amount to send. */
val BrandNumberLargeStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp)

/** Small point tags such as "+5". */
val BrandNumberSmallStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)

/** The digit inside a small level coin. */
val CoinDigitStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 10.sp)
