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

// Light Palette
val BackgroundLight = Color(0xFFFFF4E2) // cream
val SurfaceLight = Color(0xFFFFFCF6)
val CardBorderLight = Color(0xFFEADBC2)
val TextPrimaryLight = Color(0xFF21172B)
val TextSecondaryLight = Color(0xFF6B5E7C)

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
