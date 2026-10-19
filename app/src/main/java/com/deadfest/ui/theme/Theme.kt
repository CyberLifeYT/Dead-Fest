package com.deadfest.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dead-Fest is always dark: a light scheme used to turn the app purple/white on phones set to
 * light mode. The palette is the ember-on-ink system from Color.kt.
 */
private val DeadFestColorScheme = darkColorScheme(
    primary = Ember,
    onPrimary = Color.White,
    primaryContainer = EmberDeep,
    onPrimaryContainer = Color.White,
    inversePrimary = EmberGlow,

    secondary = Amber,
    onSecondary = Color(0xFF2B1B00),
    secondaryContainer = Color(0xFF3A2A00),
    onSecondaryContainer = Amber,

    tertiary = Plasma,
    onTertiary = Color(0xFF00272E),
    tertiaryContainer = Color(0xFF003640),
    onTertiaryContainer = Plasma,

    background = Ink0,
    onBackground = TextHi,
    surface = Ink0,
    onSurface = TextHi,

    surfaceVariant = Ink3,
    onSurfaceVariant = TextMid,
    surfaceTint = Ember,

    surfaceContainerLowest = Ink0,
    surfaceContainerLow = Ink1,
    surfaceContainer = Ink2,
    surfaceContainerHigh = Ink3,
    surfaceContainerHighest = Ink4,

    surfaceBright = Ink4,
    surfaceDim = Ink0,

    outline = InkLine,
    outlineVariant = InkGrid,

    error = Ember,
    onError = Color.White,
    errorContainer = EmberDeep,
    onErrorContainer = Color.White,

    inverseSurface = Ink4,
    inverseOnSurface = TextHi,
    scrim = Color.Black
)

/** Material component shapes, matched to [Radius] so Material and hand-rolled UI agree. */
private val DeadFestShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xxl)
)

@Composable
fun DeadFestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DeadFestColorScheme,
        typography = Typography,
        shapes = DeadFestShapes,
        content = content
    )
}
