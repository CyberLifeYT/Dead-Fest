package com.deadfest

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.deadfest.ui.theme.AppPalettes
import com.deadfest.ui.theme.DeadFestPalette
import com.deadfest.ui.theme.readableOn
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2.1 contrast checks for every app theme.
 *
 * - Primary and secondary text (textHi, textMid) must reach 4.5:1 (AA body text) on every
 *   surface a card or screen can use.
 * - Tertiary text (textLow) is used for timestamps, captions and hints; it must reach 3:1 (AA
 *   large text / non-text UI) on every surface.
 * - The semantic accents (ember, amber, toxic, plasma, prestige) carry state through icons,
 *   pills and chart marks, so they must reach 3:1 (AA non-text contrast) on card surfaces.
 *
 * A new theme that fails any of these fails the build instead of shipping unreadable text.
 */
class ContrastTest {

    private fun luminance(c: Color): Double {
        val argb = c.toArgb()
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
    }

    private fun ratio(a: Color, b: Color): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun surfaces(p: DeadFestPalette) = listOf("ink0" to p.ink0, "ink1" to p.ink1, "ink2" to p.ink2, "ink3" to p.ink3)

    private fun check(p: DeadFestPalette, fgName: String, fg: Color, min: Double, onSurfaces: List<Pair<String, Color>>) {
        onSurfaces.forEach { (bgName, bg) ->
            val r = ratio(fg, bg)
            assertTrue(
                "${p.name}: $fgName on $bgName is ${"%.2f".format(r)}:1, needs ${min}:1",
                r >= min
            )
        }
    }

    @Test fun textIsReadableOnEverySurfaceInEveryTheme() {
        AppPalettes.forEach { p ->
            check(p, "textHi", p.textHi, 4.5, surfaces(p))
            check(p, "textMid", p.textMid, 4.5, surfaces(p))
            check(p, "textLow", p.textLow, 3.0, surfaces(p))
        }
    }

    @Test fun accentsAreVisibleOnCardsInEveryTheme() {
        AppPalettes.forEach { p ->
            val cards = surfaces(p).filter { it.first in setOf("ink1", "ink2", "ink3") }
            listOf("ember" to p.ember, "amber" to p.amber, "toxic" to p.toxic, "plasma" to p.plasma, "prestige" to p.prestige)
                .forEach { (name, c) -> check(p, name, c, 3.0, cards) }
        }
    }

    @Test fun buttonLabelsOnEmberAreReadable() {
        // PrimaryAction and Material buttons label the ember fill with readableOn(ember).
        AppPalettes.forEach { p ->
            val label = readableOn(p.ember)
            val r = ratio(label, p.ember)
            assertTrue("${p.name}: button label on ember is ${"%.2f".format(r)}:1, needs 3:1 (bold label)", r >= 3.0)
        }
    }
}
