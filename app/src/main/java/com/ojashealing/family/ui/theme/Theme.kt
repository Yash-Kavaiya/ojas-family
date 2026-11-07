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
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF4E9DA),
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFBF6),
    surfaceContainer = Color(0xFFFAF1E5),
    surfaceContainerHigh = Color(0xFFF5EADC),
    surfaceContainerHighest = Color(0xFFEFE2D0),
    outline = CardBorderLight,
    outlineVariant = CardBorderSoftLight
)

private val OjasShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OjasFamilyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val locale = LocalConfiguration.current.locales[0]
    val script = scriptFor(locale)
    val typography = remember(script) { ojasTypography(script) }
    val quote = remember(script) { quoteStyle(script) }
    CompositionLocalProvider(LocalQuoteStyle provides quote) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = OjasShapes,
            content = content
        )
    }
}
