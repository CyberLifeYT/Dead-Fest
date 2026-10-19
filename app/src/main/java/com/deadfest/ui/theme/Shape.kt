package com.deadfest.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shape + spacing + elevation tokens (v2.0.7.1).
 *
 * Every radius in the app comes from one of these buckets so cards, panels, chips and dialogs
 * stay visually consistent. Add new sizes here rather than inventing one-off `RoundedCornerShape`
 * values inside screens.
 */
object Radius {
    /** Tiny chips and badges. */
    val xs = 6.dp
    /** Inline tiles, icon boxes. */
    val sm = 10.dp
    /** Inputs, list rows. */
    val md = 14.dp
    /** Standard cards. */
    val lg = 18.dp
    /** Hero panels, sheets. */
    val xl = 22.dp
    /** Dialogs and full-bleed containers. */
    val xxl = 28.dp
    /** Fully rounded (pills, avatars). */
    val pill = 100.dp
}

/**
 * Vertical rhythm. Screens use [gutter] for edge padding and one of the gap steps between
 * blocks, which is what keeps spacing looking deliberate instead of accidental.
 */
object Spacing {
    val gutter = 16.dp
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 28.dp
    val xxl = 40.dp

    /** Bottom padding for any scrollable content so the nav bar never covers the last row. */
    val scrollBottom = 28.dp
}

/** Border weights. Hairlines are deliberately 1dp so they stay crisp on low-density screens. */
object Stroke {
    val hair = 1.dp
    val emphasis = 1.5.dp
    val bold = 2.dp
}

/** Alpha values for the tinted `accent.copy(alpha = ...)` fills used across the app. */
object Tint {
    /** Background wash behind an accent icon. */
    const val wash = 0.12f
    /** Slightly stronger fill (buttons-in-cards, selected nav). */
    const val fill = 0.18f
    /** Border for an accent-outlined element. */
    const val edge = 0.45f
    /** Strong border (focused / destructive). */
    const val edgeStrong = 0.70f
}
