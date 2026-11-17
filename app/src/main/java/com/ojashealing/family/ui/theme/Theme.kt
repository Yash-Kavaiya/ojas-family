package com.ojashealing.family.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = SaffronLight,
    onPrimary = Color(0xFF4A1800),
    primaryContainer = SaffronDark,
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = SacredGold,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFFE8A8),
    tertiary = HealingCyan,
    onTertiary = Color(0xFF00363D),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDarkBg,
    onSurface = TextPrimaryDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = Color(0xFF180F2C),
    surfaceContainerLow = Color(0xFF241840),
    surfaceContainer = Color(0xFF2A1B47),
    surfaceContainerHigh = Color(0xFF332352),
    surfaceContainerHighest = Color(0xFF3C2B5E),
    outline = CardBorderDark,
    outlineVariant = Color(0xFF3B2A5E)
)

private val LightColorScheme = lightColorScheme(
    primary = SaffronDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2CC),
    onPrimaryContainer = Color(0xFF3A1200),
    secondary = Color(0xFF9A5B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8B0),
    onSecondaryContainer = Color(0xFF2E1C00),
    tertiary = Color(0xFF0369A1),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF0FF),
    onTertiaryContainer = Color(0xFF00304D),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    // The top bar, the tab strips and the bottom bar sit on "surface": the same light purple as the page
    surface = BackgroundLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFE6DAF6),
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color(0xFFFDFBFF),
    surfaceContainerLow = Color(0xFFF8F3FF),
    surfaceContainer = BackgroundLight,
    surfaceContainerHigh = Color(0xFFEDE4F9),
    surfaceContainerHighest = Color(0xFFE6DBF5),
    outline = CardBorderLight,
    outlineVariant = CardBorderSoftLight
)

// Healing Blue Light: sky-blue pages, deep healing blue for buttons and links, a warm saffron accent
private val HealingBlueColorScheme = lightColorScheme(
    primary = Color(0xFF0B5CAD), // 6.4:1 on white
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E6FF),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF00708A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7EEF7),
    onSecondaryContainer = Color(0xFF001F27),
    tertiary = Color(0xFFB14A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE2CC),
    onTertiaryContainer = Color(0xFF3A1200),
    background = Color(0xFFEAF3FF),
    onBackground = Color(0xFF0E1D33),
    surface = Color(0xFFEAF3FF),
    onSurface = Color(0xFF0E1D33),
    surfaceVariant = Color(0xFFD9E6F7),
    onSurfaceVariant = Color(0xFF3D4D63),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F9FF),
    surfaceContainer = Color(0xFFEAF3FF),
    surfaceContainerHigh = Color(0xFFDFEBFA),
    surfaceContainerHighest = Color(0xFFD5E3F5),
    outline = Color(0xFFA9C1E0),
    outlineVariant = Color(0xFFCCDCF0)
)

// Forest Sage Dark: deep forest-green pages, soft sage for buttons and links, a gentle gold accent
private val ForestSageColorScheme = darkColorScheme(
    primary = Color(0xFFA9CFA0), // 9:1 on the page
    onPrimary = Color(0xFF14331A),
    primaryContainer = Color(0xFF2E4A33),
    onPrimaryContainer = Color(0xFFD3EDCB),
    secondary = Color(0xFFE2C98A),
    onSecondary = Color(0xFF3A2E05),
    secondaryContainer = Color(0xFF524519),
    onSecondaryContainer = Color(0xFFFFEDBF),
    tertiary = Color(0xFF8FD3C1),
    onTertiary = Color(0xFF00382F),
    background = Color(0xFF111A15),
    onBackground = Color(0xFFE6EFE7),
    surface = Color(0xFF17221C),
    onSurface = Color(0xFFE6EFE7),
    surfaceVariant = Color(0xFF26352C),
    onSurfaceVariant = Color(0xFFB5C6B9),
    surfaceContainerLowest = Color(0xFF0C130F),
    surfaceContainerLow = Color(0xFF15201A),
    surfaceContainer = Color(0xFF1A2720),
    surfaceContainerHigh = Color(0xFF223128),
    surfaceContainerHighest = Color(0xFF2A3B31),
    outline = Color(0xFF4C6354),
    outlineVariant = Color(0xFF33463A)
)

/** The colours a theme choice uses, and whether they are dark. */
fun colorsFor(mode: com.ojashealing.family.data.settings.ThemeMode, systemDark: Boolean): Pair<androidx.compose.material3.ColorScheme, Boolean> =
    when (mode) {
        com.ojashealing.family.data.settings.ThemeMode.System -> (if (systemDark) DarkColorScheme else LightColorScheme) to systemDark
        com.ojashealing.family.data.settings.ThemeMode.Light -> LightColorScheme to false
        com.ojashealing.family.data.settings.ThemeMode.Dark -> DarkColorScheme to true
        com.ojashealing.family.data.settings.ThemeMode.HealingBlue -> HealingBlueColorScheme to false
        com.ojashealing.family.data.settings.ThemeMode.ForestSage -> ForestSageColorScheme to true
    }

private val OjasShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OjasFamilyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** A named theme's colours; null uses the Ojas light or dark colours. */
    colors: androidx.compose.material3.ColorScheme? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = colors ?: if (darkTheme) DarkColorScheme else LightColorScheme
    val locale = LocalConfiguration.current.locales[0]
    val script = scriptFor(locale)
    val typography = remember(script) { ojasTypography(script) }
    val quote = remember(script) { quoteStyle(script) }
    val reducedMotion = rememberReducedMotion()
    CompositionLocalProvider(LocalQuoteStyle provides quote, LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = OjasShapes,
            content = content
        )
    }
}
