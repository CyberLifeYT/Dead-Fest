package com.deadfest

import androidx.compose.ui.graphics.Color
import com.deadfest.ui.theme.AppPalettes
import com.deadfest.ui.theme.AppThemeNames
import com.deadfest.ui.theme.DEFAULT_THEME_NAME
import com.deadfest.ui.theme.DefaultPalette
import com.deadfest.ui.theme.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the app themes.
 *
 * Two properties matter here. First, the default must be reachable by name — Settings shows the
 * stored name and highlights the matching palette, so a rename would leave nothing selected.
 * Second, the semantic accents must be identical across themes: amber is currency and green is
 * success in every one of them, or a "success" pill would start reading as a warning.
 */
class AppThemeTest {

    // ------------------------------------------------------------ lookup

    @Test fun everyThemeIsReachableByName() {
        for (name in AppThemeNames) {
            assertEquals(name, paletteFor(name).name)
        }
    }

    @Test fun themeLookupIsCaseInsensitive() {
        assertEquals("Default", paletteFor("default").name)
        assertEquals("Halloween", paletteFor("HALLOWEEN").name)
    }

    @Test fun unknownOrMissingNameFallsBackToDefault() {
        assertSame(DefaultPalette, paletteFor("Neon"))
        assertSame(DefaultPalette, paletteFor(null))
        assertSame(DefaultPalette, paletteFor(""))
        assertSame(DefaultPalette, paletteFor("   "))
    }

    @Test fun defaultThemeIsAlwaysFirst() {
        // The picker leads with Default, and the stored-default constant names it.
        assertEquals(DEFAULT_THEME_NAME, AppThemeNames.first())
        assertEquals(DefaultPalette.name, DEFAULT_THEME_NAME)
    }

    // ------------------------------------------------------------ invariants

    @Test fun everyThemeIsDistinguishable() {
        // Two themes with the same name would make the picker ambiguous.
        assertEquals(AppThemeNames.size, AppThemeNames.toSet().size)
        assertEquals(AppPalettes.size, AppPalettes.map { it.name }.toSet().size)
    }

    @Test fun everyThemeHasADifferentBrandColour() {
        // If two themes shared an ember they would be indistinguishable where it matters most.
        assertEquals(AppPalettes.size, AppPalettes.map { it.ember.value }.toSet().size)
    }

    @Test fun semanticAccentsAreTheSameInEveryTheme() {
        // Currency, success, tech and progression keep their meaning in every theme. The failure
        // mode this prevents is a Christmas-green "you succeeded" looking like a warning.
        for (palette in AppPalettes) {
            assertEquals("amber", DefaultPalette.amber.value, palette.amber.value)
            assertEquals("amberDeep", DefaultPalette.amberDeep.value, palette.amberDeep.value)
            assertEquals("toxic", DefaultPalette.toxic.value, palette.toxic.value)
            assertEquals("plasma", DefaultPalette.plasma.value, palette.plasma.value)
            assertEquals("prestige", DefaultPalette.prestige.value, palette.prestige.value)
            assertEquals("textHi", DefaultPalette.textHi.value, palette.textHi.value)
            assertEquals("textMid", DefaultPalette.textMid.value, palette.textMid.value)
            assertEquals("textLow", DefaultPalette.textLow.value, palette.textLow.value)
        }
    }

    @Test fun everyThemeKeepsTheDeadFestDarkSurfaces() {
        // A theme that went light would break the whole identity — every palette stays dark.
        for (palette in AppPalettes) {
            assertTrue("${palette.name} ink0 must be dark", palette.ink0.luminance() < 0.1f)
            assertTrue("${palette.name} ink4 must be dark", palette.ink4.luminance() < 0.2f)
            // Distinct surfaces, or cards would vanish into the background.
            assertNotEquals("${palette.name} surfaces must differ", palette.ink0.value, palette.ink2.value)
        }
    }

    @Test fun defaultPaletteIsUnchangedFromShippedValues() {
        // The Default theme is what v2.0.7.2 shipped; changing it would silently restyle everyone.
        // Compared as colours, not raw values: Color.value is a packed representation.
        assertEquals(Color(0xFFFF4B4B), DefaultPalette.ember)
        assertEquals(Color(0xFF0D0D11), DefaultPalette.ink1)
        assertEquals(Color(0xFF272730), DefaultPalette.inkLine)
        assertEquals(Color(0xFFFFD54F), DefaultPalette.amber)
    }

    /** Relative luminance (sRGB). Only used to assert a palette stays dark. */
    private fun androidx.compose.ui.graphics.Color.luminance(): Float {
        fun channel(c: Float) =
            if (c <= 0.03928f) c / 12.92f
            else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
    }
}
