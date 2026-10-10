package com.deadfest.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dead-Fest is always dark: the old light scheme turned the app purple/white
// on phones set to light mode.
private val DarkColorScheme = darkColorScheme(
    primary = RedAccent,
    onPrimary = Color.White,
    primaryContainer = RedDeep,
    onPrimaryContainer = Color.White,
    secondary = TextSecondary,
    onSecondary = Color.White,
    tertiary = Color.DarkGray,
    onTertiary = Color.White,
    background = DarkBg,
    onBackground = Color.White,
    surface = DarkBg,
    onSurface = Color.White,
    surfaceVariant = CardBg,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = CardBg,
    surfaceContainerHigh = CardBgRaised,
    surfaceContainerHighest = CardBgRaised,
    surfaceContainerLow = CardBg,
    outline = Outline,
    outlineVariant = Outline,
    error = RedAccent,
    inverseSurface = CardBgRaised,
    inverseOnSurface = Color.White,
)

@Composable
fun DeadFestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
