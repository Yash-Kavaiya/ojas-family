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

// The two themes (owner, 2026-11-20). Light is Healing Blue: sky-blue pages, deep healing blue for
// buttons and links, a warm saffron accent.
private val LightColorScheme = lightColorScheme(
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

// Dark is Forest Sage: deep forest-green pages, soft sage for buttons and links, a gentle gold accent
private val DarkColorScheme = darkColorScheme(
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

/**
 * The colours a theme choice uses, and whether they are dark. There are two themes: Light (Healing Blue)
 * and Dark (Forest Sage). Older saved choices map onto them; System, kept for people who never chose,
 * follows the phone.
 */
fun colorsFor(mode: com.ojashealing.family.data.settings.ThemeMode, systemDark: Boolean): Pair<androidx.compose.material3.ColorScheme, Boolean> {
    val dark = mode.isDark(systemDark)
    return (if (dark) DarkColorScheme else LightColorScheme) to dark
}

fun com.ojashealing.family.data.settings.ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    com.ojashealing.family.data.settings.ThemeMode.System -> systemDark
    com.ojashealing.family.data.settings.ThemeMode.Light, com.ojashealing.family.data.settings.ThemeMode.HealingBlue -> false
    com.ojashealing.family.data.settings.ThemeMode.Dark, com.ojashealing.family.data.settings.ThemeMode.ForestSage -> true
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
    /** The theme's colours; null uses the light or dark theme by [darkTheme]. */
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
