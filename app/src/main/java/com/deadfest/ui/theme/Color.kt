package com.deadfest.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Dead-Fest colour system (v2.0.7.1).
 *
 * Two layers:
 *  1. An ink/ember **scale** that new work should use directly.
 *  2. The original **token names** (DarkBg, CardBg, RedAccent, ...) kept as aliases so every
 *     existing screen keeps compiling and instantly inherits the refreshed palette.
 *
 * Nothing here is a breaking change: no name was removed, only refined.
 */

// ------------------------------------------------------------------ ink scale (surfaces)
/** Deepest background. Slightly cool so the ember accents read as warm. */
val Ink0 = Color(0xFF08080A)
/** Screen background. */
val Ink1 = Color(0xFF0D0D11)
/** Standard card / panel. */
val Ink2 = Color(0xFF131318)
/** Raised card, input field, inner tile. */
val Ink3 = Color(0xFF1C1C23)
/** Highest surface: pressed states, chips, dialogs. */
val Ink4 = Color(0xFF26262F)
/** Hairline dividers and borders. */
val InkLine = Color(0xFF272730)
/** Grid/texture strokes (very low contrast). */
val InkGrid = Color(0xFF1A1A20)

// ------------------------------------------------------------------ ember (brand primary)
/** The Dead-Fest red. Unchanged from v2.0.0 so the identity holds. */
val Ember = Color(0xFFFF4B4B)
/** Deep ember for container fills and gradient ends. */
val EmberDeep = Color(0xFFB3261E)
/** Warm highlight used in gradients and glows. */
val EmberGlow = Color(0xFFFF8A5B)
/** Ember at its dimmest — track fills, disabled glows. */
val EmberDim = Color(0xFF4A1512)

// ------------------------------------------------------------------ accents
/** Reward / currency. */
val Amber = Color(0xFFFFD54F)
/** Reward gradient end. */
val AmberDeep = Color(0xFFFFA000)
/** Tech / shield / insulation. */
val Plasma = Color(0xFF4DD0E1)
/** Online presence + positive confirmations. */
val Toxic = Color(0xFF4CE07F)
/** Prestige / progression. */
val Prestige = Color(0xFFB388FF)

// ------------------------------------------------------------------ text
val TextHi = Color(0xFFF5F5F7)
val TextMid = Color(0xFFA2A2B0)
val TextLow = Color(0xFF70707E)

// ------------------------------------------------------------------ legacy aliases
// These are the names the whole app already uses. They now point at the refreshed palette.
val DarkBg = Ink0
val CardBg = Ink2
val CardBgRaised = Ink3
val Outline = InkLine
val TextPrimary = TextHi
val TextSecondary = TextMid
val TextMuted = TextLow
val RedAccent = Ember
val RedDeep = EmberDeep
val LockedColor = Color(0xFF333340)
val Gold = Amber
val Silver = Color(0xFFC8CBD1)
val Bronze = Color(0xFFCD7F32)
val OnlineGreen = Toxic
val ShieldCyan = Plasma
