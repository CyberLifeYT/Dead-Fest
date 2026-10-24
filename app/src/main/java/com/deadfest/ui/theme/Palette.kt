package com.deadfest.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * Dead-Fest app themes (v2.0.8).
 *
 * Every theme is a full [DeadFestPalette]. The semantic accents are deliberately identical in all
 * of them: amber is always currency, toxic always success/online, plasma always tech and shield,
 * prestige always progression. Re-colouring those would make a "success" pill look like a warning,
 * so a theme is allowed to restyle the **surfaces** (ink) and the **brand** (ember) and nothing else.
 * That is what keeps the app feeling like Dead-Fest in every season: same dark, same grunge, same
 * ember-on-ink identity — just a different ink and a different brand temperature.
 */
data class DeadFestPalette(
    val name: String,
    // ink scale (surfaces)
    val ink0: Color,
    val ink1: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val inkLine: Color,
    val inkGrid: Color,
    // ember (brand primary)
    val ember: Color,
    val emberDeep: Color,
    val emberGlow: Color,
    val emberDim: Color,
    // accents (semantic — see the class doc: never themed)
    val amber: Color,
    val amberDeep: Color,
    val plasma: Color,
    val toxic: Color,
    val prestige: Color,
    // text
    val textHi: Color,
    val textMid: Color,
    val textLow: Color,
    // misc
    val locked: Color,
    val silver: Color,
    val bronze: Color
)

/** The shipped v2.0.7 look. Every other theme is defined as a variation on this. */
val DefaultPalette = DeadFestPalette(
    name = "Default",
    ink0 = Color(0xFF08080A),
    ink1 = Color(0xFF0D0D11),
    ink2 = Color(0xFF131318),
    ink3 = Color(0xFF1C1C23),
    ink4 = Color(0xFF26262F),
    inkLine = Color(0xFF272730),
    inkGrid = Color(0xFF1A1A20),
    ember = Color(0xFFFF4B4B),
    emberDeep = Color(0xFFB3261E),
    emberGlow = Color(0xFFFF8A5B),
    emberDim = Color(0xFF4A1512),
    amber = Color(0xFFFFD54F),
    amberDeep = Color(0xFFFFA000),
    plasma = Color(0xFF4DD0E1),
    toxic = Color(0xFF4CE07F),
    prestige = Color(0xFFB388FF),
    textHi = Color(0xFFF5F5F7),
    textMid = Color(0xFFA2A2B0),
    textLow = Color(0xFF70707E),
    locked = Color(0xFF333340),
    silver = Color(0xFFC8CBD1),
    bronze = Color(0xFFCD7F32)
)

/** Halloween: violet-ink with a pumpkin brand. */
val HalloweenPalette = DeadFestPalette(
    name = "Halloween",
    ink0 = Color(0xFF0A0812),
    ink1 = Color(0xFF100C1A),
    ink2 = Color(0xFF171126),
    ink3 = Color(0xFF201735),
    ink4 = Color(0xFF2A1F45),
    inkLine = Color(0xFF2E2547),
    inkGrid = Color(0xFF1D1630),
    ember = Color(0xFFFF7A18),
    emberDeep = Color(0xFFC2410C),
    emberGlow = Color(0xFFFFB347),
    emberDim = Color(0xFF5A2D0C),
    amber = DefaultPalette.amber,
    amberDeep = DefaultPalette.amberDeep,
    plasma = DefaultPalette.plasma,
    toxic = DefaultPalette.toxic,
    prestige = DefaultPalette.prestige,
    textHi = DefaultPalette.textHi,
    textMid = DefaultPalette.textMid,
    textLow = DefaultPalette.textLow,
    locked = DefaultPalette.locked,
    silver = DefaultPalette.silver,
    bronze = DefaultPalette.bronze
)

/** Christmas: deep evergreen surfaces with a holly-red brand. */
val ChristmasPalette = DeadFestPalette(
    name = "Christmas",
    ink0 = Color(0xFF070D0A),
    ink1 = Color(0xFF0B1410),
    ink2 = Color(0xFF0F1C16),
    ink3 = Color(0xFF15271F),
    ink4 = Color(0xFF1C3229),
    inkLine = Color(0xFF22382F),
    inkGrid = Color(0xFF142019),
    ember = Color(0xFFD62828),
    emberDeep = Color(0xFF9B1C1C),
    emberGlow = Color(0xFFFF6B6B),
    emberDim = Color(0xFF4A1515),
    amber = DefaultPalette.amber,
    amberDeep = DefaultPalette.amberDeep,
    plasma = DefaultPalette.plasma,
    toxic = DefaultPalette.toxic,
    prestige = DefaultPalette.prestige,
    textHi = DefaultPalette.textHi,
    textMid = DefaultPalette.textMid,
    textLow = DefaultPalette.textLow,
    locked = DefaultPalette.locked,
    silver = DefaultPalette.silver,
    bronze = DefaultPalette.bronze
)

/** Toxic Storm: radioactive green-tinged ink with a lime brand. */
val ToxicStormPalette = DeadFestPalette(
    name = "Toxic Storm",
    ink0 = Color(0xFF080D08),
    ink1 = Color(0xFF0C130C),
    ink2 = Color(0xFF111B11),
    ink3 = Color(0xFF172517),
    ink4 = Color(0xFF1E2F1E),
    inkLine = Color(0xFF243424),
    inkGrid = Color(0xFF152015),
    ember = Color(0xFF84CC16),
    emberDeep = Color(0xFF4D7C0F),
    emberGlow = Color(0xFFBEF264),
    emberDim = Color(0xFF365314),
    amber = DefaultPalette.amber,
    amberDeep = DefaultPalette.amberDeep,
    plasma = DefaultPalette.plasma,
    toxic = DefaultPalette.toxic,
    prestige = DefaultPalette.prestige,
    textHi = DefaultPalette.textHi,
    textMid = DefaultPalette.textMid,
    textLow = DefaultPalette.textLow,
    locked = DefaultPalette.locked,
    silver = DefaultPalette.silver,
    bronze = DefaultPalette.bronze
)

/** Blood Moon: crimson-tinged ink with a dried-blood brand. */
val BloodMoonPalette = DeadFestPalette(
    name = "Blood Moon",
    ink0 = Color(0xFF0D0809),
    ink1 = Color(0xFF140C0D),
    ink2 = Color(0xFF1C1113),
    ink3 = Color(0xFF26171A),
    ink4 = Color(0xFF301D21),
    inkLine = Color(0xFF382428),
    inkGrid = Color(0xFF221518),
    // CC2222 rather than the original B91C1C: B91C1C fell just under 3:1 on Blood Moon cards (ContrastTest).
    ember = Color(0xFFCC2222),
    emberDeep = Color(0xFF7F1D1D),
    emberGlow = Color(0xFFEF4444),
    emberDim = Color(0xFF450A0A),
    amber = DefaultPalette.amber,
    amberDeep = DefaultPalette.amberDeep,
    plasma = DefaultPalette.plasma,
    toxic = DefaultPalette.toxic,
    prestige = DefaultPalette.prestige,
    textHi = DefaultPalette.textHi,
    textMid = DefaultPalette.textMid,
    textLow = DefaultPalette.textLow,
    locked = DefaultPalette.locked,
    silver = DefaultPalette.silver,
    bronze = DefaultPalette.bronze
)

/** Every theme the picker offers, in display order. */
val AppPalettes: List<DeadFestPalette> =
    listOf(DefaultPalette, HalloweenPalette, ChristmasPalette, ToxicStormPalette, BloodMoonPalette)

/** Theme names as stored in preferences and shown in the picker. */
val AppThemeNames: List<String> = AppPalettes.map { it.name }

/** The default palette's name, used when nothing has been stored yet. */
const val DEFAULT_THEME_NAME = "Default"

/** Looks a theme up by name, falling back to [DefaultPalette] for anything unrecognised. */
fun paletteFor(name: String?): DeadFestPalette =
    AppPalettes.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: DefaultPalette

/**
 * The palette currently in force, and the only way to change it.
 *
 * This is a process-level holder rather than a CompositionLocal on purpose: the whole app already
 * references the colour constants in [com.deadfest.ui.theme.Color] directly, and those are plain
 * properties. Routing them through this State means every one of those references — a thousand of
 * them, across 20 screens — subscribes to theme changes without a single screen being edited. The
 * getter reads inside the State, so any composable that touches a colour recomposes when the theme
 * changes, and non-composable call sites simply get the current value.
 */
object DeadFestTheme {
    private val _palette = mutableStateOf(DefaultPalette)

    /** The active palette. Reading this in a composable subscribes it to theme changes. */
    val palette: DeadFestPalette
        get() = _palette.value

    /** The active theme's display name, for the picker's selected state. */
    val currentName: String
        get() = _palette.value.name

    /** Switches the app to the theme called [name]. Unknown names fall back to the default. */
    fun select(name: String?) {
        val next = paletteFor(name)
        if (next.name != _palette.value.name) _palette.value = next
    }
}
