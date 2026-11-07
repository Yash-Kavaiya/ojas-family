package com.ojashealing.family.ui.theme

import androidx.compose.ui.graphics.Color

// Spiritual Sattvic Palette
val SaffronPrimary = Color(0xFFE65100)
val SaffronLight = Color(0xFFFFB74D)
val SaffronDark = Color(0xFFBF360C)

val SacredGold = Color(0xFFFFD54F)
val GoldAmber = Color(0xFFFFA000)
val GoldDark = Color(0xFFFF6F00)

val HealingCyan = Color(0xFF00ACC1)
val HealingBlue = Color(0xFF0284C7)
val DeepIndigo = Color(0xFF1A1235)
val SurfaceDark = Color(0xFF140E26)
val CardDark = Color(0xFF342256)
val CardBorderDark = Color(0xFF4A3577)

val LotusRose = Color(0xFFE91E63)
val SpiritualTeal = Color(0xFF00897B)
val ChakraGreen = Color(0xFF43A047)

// Light Palette: white cards on warm ivory, deep text, every text colour at least 4.5:1 on white
val BackgroundLight = Color(0xFFFBF4EA) // warm ivory
val SurfaceLight = Color(0xFFFFFFFF)
val CardBorderLight = Color(0xFFDCC8AA)
val CardBorderSoftLight = Color(0xFFEDE1CF)
val TextPrimaryLight = Color(0xFF1C1326)
val TextSecondaryLight = Color(0xFF54476A)
val SaffronDeep = Color(0xFFC94A00) // primary on light: 4.7:1 on white

// Badges
val VerifiedBlue = Color(0xFF1D9BF0)
val SwamijiGold = Color(0xFFE8A200)
val AdminPurple = Color(0xFF7C3AED)
val SeniorTeal = Color(0xFF0F9D8A)
val LevelBronze = Color(0xFFB7703A)
val LevelSilver = Color(0xFF7D8A99)
val LevelGold = Color(0xFFD69E00)
val LevelLotus = Color(0xFFD9467A)

// Dark Palette
val BackgroundDark = Color(0xFF1E1236) // dark purple, not black
val SurfaceDarkBg = Color(0xFF2A1B47)
val TextPrimaryDark = Color(0xFFFBF8FF)
val TextSecondaryDark = Color(0xFFB5A8CA)

/** Each practice section's own box colour and title colour, so sections read apart at a glance. */
data class SectionTint(val boxLight: Color, val boxDark: Color, val titleLight: Color, val titleDark: Color)

val BlueLightTint = SectionTint(Color(0xFFE3F0FF), Color(0xFF1D2F57), Color(0xFF0B5CAD), Color(0xFF9CCBFF))
val QuickProtectionTint = SectionTint(Color(0xFFDDF4F0), Color(0xFF15404A), Color(0xFF00796B), Color(0xFF86E0D1))
val MorningTint = SectionTint(Color(0xFFFFE8CC), Color(0xFF4A2A1A), Color(0xFFC2410C), Color(0xFFFFB98A))
val NightTint = SectionTint(Color(0xFFE9E3FB), Color(0xFF2F2463), Color(0xFF4C3BB5), Color(0xFFC0B4FF))
val YogaTint = SectionTint(Color(0xFFE2F3DF), Color(0xFF1E3F2B), Color(0xFF2E7D32), Color(0xFFA4DCA6))
val OtherTint = SectionTint(Color(0xFFFCE3EC), Color(0xFF4A1E3A), Color(0xFFC2185B), Color(0xFFF7A9C8))
val UnderstandingTint = SectionTint(Color(0xFFFFF1C7), Color(0xFF45381B), Color(0xFF9A6500), Color(0xFFF3D27A))

/** Brand gradients for headers, the send button and highlight cards. */
object OjasGradients {
    val saffron = listOf(Color(0xFFFF9A3C), Color(0xFFE8590C))
    val sunrise = listOf(Color(0xFFFFB347), Color(0xFFFF7A1A), Color(0xFFD9467A))
    val twilight = listOf(Color(0xFF4C2A85), Color(0xFF2A1B47))
    val gold = listOf(Color(0xFFFFE08A), Color(0xFFF2B33D))
}

/** One announcement colour: a pastel card for light mode, a deep card for dark mode, and an accent for each. */
data class NoticeTint(val cardLight: Color, val cardDark: Color, val accentLight: Color, val accentDark: Color, val borderLight: Color, val borderDark: Color)

/** Announcements cycle through these so neighbouring cards read apart. */
val NoticeTints = listOf(
    NoticeTint(Color(0xFFFFF0E3), Color(0xFF3D2416), Color(0xFFC94A00), Color(0xFFFFB27A), Color(0xFFF6D3B5), Color(0xFF5E3A22)), // saffron
    NoticeTint(Color(0xFFFDEBF2), Color(0xFF3B1A2B), Color(0xFFB8336A), Color(0xFFF59BC1), Color(0xFFF3C6D8), Color(0xFF5C2944)), // lotus
    NoticeTint(Color(0xFFE5F6F3), Color(0xFF123634), Color(0xFF00796B), Color(0xFF7FDCCB), Color(0xFFBFE6DF), Color(0xFF1F5450)), // teal
    NoticeTint(Color(0xFFECEBFC), Color(0xFF221D4A), Color(0xFF4C3BB5), Color(0xFFB9AEFF), Color(0xFFCFCAF5), Color(0xFF37306E)), // indigo
    NoticeTint(Color(0xFFE9F5E4), Color(0xFF1B3322), Color(0xFF2E7D32), Color(0xFF9AD69C), Color(0xFFC8E4BE), Color(0xFF2C4F35)), // green
    NoticeTint(Color(0xFFFFF6D9), Color(0xFF3A3115), Color(0xFF9A6500), Color(0xFFF3D27A), Color(0xFFF0DFA6), Color(0xFF594B20))  // gold
)

/** The same announcement always gets the same colour. */
fun noticeTintFor(id: String): NoticeTint = NoticeTints[Math.floorMod(id.hashCode(), NoticeTints.size)]
