package com.deadfest.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dead-Fest is always dark: a light scheme used to turn the app purple/white on phones set to
 * light mode. The palette is the ember-on-ink system from Color.kt.
 */
/**
 * Material colour scheme for the active palette.
 *
 * A composable function rather than a stored value: the palette can change at runtime (Settings →
 * Themes), and a stored scheme would freeze the first palette the app ever rendered. Reading
 * [DeadFestTheme.palette] here both subscribes us to theme changes and re-reads the palette on
 * every recomposition.
 */
@Composable
private fun deadFestColorScheme(): ColorScheme {
    val p = DeadFestTheme.palette
    return darkColorScheme(
        primary = p.ember,
        onPrimary = Color.White,
        primaryContainer = p.emberDeep,
        onPrimaryContainer = Color.White,
        inversePrimary = p.emberGlow,

        secondary = p.amber,
        onSecondary = Color(0xFF2B1B00),
        secondaryContainer = Color(0xFF3A2A00),
        onSecondaryContainer = p.amber,

        tertiary = p.plasma,
        onTertiary = Color(0xFF00272E),
        tertiaryContainer = Color(0xFF003640),
        onTertiaryContainer = p.plasma,

        background = p.ink0,
        onBackground = p.textHi,
        surface = p.ink0,
        onSurface = p.textHi,

        surfaceVariant = p.ink3,
        onSurfaceVariant = p.textMid,
        surfaceTint = p.ember,

        surfaceContainerLowest = p.ink0,
        surfaceContainerLow = p.ink1,
        surfaceContainer = p.ink2,
        surfaceContainerHigh = p.ink3,
        surfaceContainerHighest = p.ink4,

        surfaceBright = p.ink4,
        surfaceDim = p.ink0,

        outline = p.inkLine,
        outlineVariant = p.inkGrid,

        error = p.ember,
        onError = Color.White,
        errorContainer = p.emberDeep,
        onErrorContainer = Color.White,

        inverseSurface = p.ink4,
        inverseOnSurface = p.textHi,
        scrim = Color.Black
    )
}

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
        colorScheme = deadFestColorScheme(),
        typography = Typography,
        shapes = DeadFestShapes,
        content = content
    )
}
