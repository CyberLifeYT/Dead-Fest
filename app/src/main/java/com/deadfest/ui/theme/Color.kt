package com.deadfest.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Dead-Fest colour system (v2.0.7.1, themed in v2.0.8).
 *
 * Two layers:
 *  1. An ink/ember **scale** that new work should use directly.
 *  2. The original **token names** (DarkBg, CardBg, RedAccent, ...) kept as aliases so every
 *     existing screen keeps compiling and instantly inherits the refreshed palette.
 *
 * Nothing here is a breaking change: no name was removed, only refined.
 *
 * **Themed.** Every constant below is a getter that reads the active [DeadFestPalette] from
 * [DeadFestTheme]. Switching the app theme therefore restyles the entire app at once — no screen
 * holds a colour of its own. See [Palette.kt] for which colours are themed and which are held
 * constant on purpose.
 *
 * The palette fields are the single source of truth; these names exist so the thousand-odd call
 * sites across the screens keep working unchanged.
 */

// ------------------------------------------------------------------ ink scale (surfaces)
/** Deepest background. Slightly cool so the ember accents read as warm. */
val Ink0: Color get() = DeadFestTheme.palette.ink0
/** Screen background. */
val Ink1: Color get() = DeadFestTheme.palette.ink1
/** Standard card / panel. */
val Ink2: Color get() = DeadFestTheme.palette.ink2
/** Raised card, input field, inner tile. */
val Ink3: Color get() = DeadFestTheme.palette.ink3
/** Highest surface: pressed states, chips, dialogs. */
val Ink4: Color get() = DeadFestTheme.palette.ink4
/** Hairline dividers and borders. */
val InkLine: Color get() = DeadFestTheme.palette.inkLine
/** Grid/texture strokes (very low contrast). */
val InkGrid: Color get() = DeadFestTheme.palette.inkGrid

// ------------------------------------------------------------------ ember (brand primary)
/** The Dead-Fest red. Unchanged from v2.0.0 so the identity holds. */
val Ember: Color get() = DeadFestTheme.palette.ember
/** Deep ember for container fills and gradient ends. */
val EmberDeep: Color get() = DeadFestTheme.palette.emberDeep
/** Warm highlight used in gradients and glows. */
val EmberGlow: Color get() = DeadFestTheme.palette.emberGlow
/** Ember at its dimmest — track fills, disabled glows. */
val EmberDim: Color get() = DeadFestTheme.palette.emberDim

// ------------------------------------------------------------------ accents
/** Reward / currency. */
val Amber: Color get() = DeadFestTheme.palette.amber
/** Reward gradient end. */
val AmberDeep: Color get() = DeadFestTheme.palette.amberDeep
/** Tech / shield / insulation. */
val Plasma: Color get() = DeadFestTheme.palette.plasma
/** Online presence + positive confirmations. */
val Toxic: Color get() = DeadFestTheme.palette.toxic
/** Prestige / progression. */
val Prestige: Color get() = DeadFestTheme.palette.prestige

// ------------------------------------------------------------------ text
val TextHi: Color get() = DeadFestTheme.palette.textHi
val TextMid: Color get() = DeadFestTheme.palette.textMid
val TextLow: Color get() = DeadFestTheme.palette.textLow

// ------------------------------------------------------------------ legacy aliases
// These are the names the whole app already uses. They now point at the themed palette.
val DarkBg: Color get() = DeadFestTheme.palette.ink0
val CardBg: Color get() = DeadFestTheme.palette.ink2
val CardBgRaised: Color get() = DeadFestTheme.palette.ink3
val Outline: Color get() = DeadFestTheme.palette.inkLine
val TextPrimary: Color get() = DeadFestTheme.palette.textHi
val TextSecondary: Color get() = DeadFestTheme.palette.textMid
val TextMuted: Color get() = DeadFestTheme.palette.textLow
val RedAccent: Color get() = DeadFestTheme.palette.ember
val RedDeep: Color get() = DeadFestTheme.palette.emberDeep
val LockedColor: Color get() = DeadFestTheme.palette.locked
val Gold: Color get() = DeadFestTheme.palette.amber
val Silver: Color get() = DeadFestTheme.palette.silver
val Bronze: Color get() = DeadFestTheme.palette.bronze
val OnlineGreen: Color get() = DeadFestTheme.palette.toxic
val ShieldCyan: Color get() = DeadFestTheme.palette.plasma

/**
 * Label colour for text drawn on a solid [background] (buttons, badges).
 *
 * White wherever it reaches 3:1 — the WCAG minimum for bold labels — so the default red keeps
 * its white buttons; near-black otherwise. Without this, the Halloween orange (2.6:1) and Toxic
 * Storm lime (2.0:1) themes rendered white button text that was hard to read.
 */
fun readableOn(background: Color): Color =
    if (1.05 / (background.luminance() + 0.05) >= 3.0) Color.White else Color(0xFF0B0B0E)
