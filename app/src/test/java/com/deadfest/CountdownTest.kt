package com.deadfest

import com.deadfest.ui.components.countdownParts
import com.deadfest.ui.components.formatCountdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the arithmetic behind the Next Dead-Fest countdown.
 *
 * These are the numbers a survivor actually reads off the More tab, so an off-by-one in the slice
 * boundaries would silently show a wrong time. Pure functions, no Android framework needed.
 */
class CountdownTest {

    // ------------------------------------------------------------ tile breakdown

    @Test fun breakdownOfExactUnits() {
        assertEquals(listOf("DAYS" to 0L, "HRS" to 0L, "MIN" to 0L, "SEC" to 0L), countdownParts(0, true))
        assertEquals(listOf("DAYS" to 0L, "HRS" to 0L, "MIN" to 0L, "SEC" to 1L), countdownParts(1, true))
        assertEquals(listOf("DAYS" to 0L, "HRS" to 0L, "MIN" to 1L, "SEC" to 0L), countdownParts(60, true))
        assertEquals(listOf("DAYS" to 0L, "HRS" to 1L, "MIN" to 0L, "SEC" to 0L), countdownParts(3_600, true))
        assertEquals(listOf("DAYS" to 1L, "HRS" to 0L, "MIN" to 0L, "SEC" to 0L), countdownParts(86_400, true))
    }

    @Test fun breakdownOfMixedUnits() {
        // 1d 1h 1m 1s
        assertEquals(
            listOf("DAYS" to 1L, "HRS" to 1L, "MIN" to 1L, "SEC" to 1L),
            countdownParts(90_061, true)
        )
        // Just under a day must not roll over into 1 day.
        assertEquals(
            listOf("DAYS" to 0L, "HRS" to 23L, "MIN" to 59L, "SEC" to 59L),
            countdownParts(86_399, true)
        )
        // Just under an hour.
        assertEquals(
            listOf("DAYS" to 0L, "HRS" to 0L, "MIN" to 59L, "SEC" to 59L),
            countdownParts(3_599, true)
        )
    }

    @Test fun partsAlwaysReconstructTheInput() {
        // The four tiles must add back up to the total, for any value.
        val samples = listOf(0L, 1L, 59L, 60L, 61L, 3_599L, 3_600L, 86_399L, 86_400L, 90_061L, 604_800L, 31_536_000L)
        for (total in samples) {
            val parts = countdownParts(total, true).associate { it.first to it.second }
            val rebuilt = parts.getValue("DAYS") * 86_400 +
                parts.getValue("HRS") * 3_600 +
                parts.getValue("MIN") * 60 +
                parts.getValue("SEC")
            assertEquals("breakdown lost time at $total", total, rebuilt)
        }
    }

    @Test fun hourAndMinuteTilesStayInRange() {
        // A tile that can exceed its unit is how countdowns end up reading "HRS 27".
        for (total in 0L..200_000L step 137) {
            val parts = countdownParts(total, true).associate { it.first to it.second }
            assertTrue("MIN out of range at $total", parts.getValue("MIN") in 0..59)
            assertTrue("SEC out of range at $total", parts.getValue("SEC") in 0..59)
            assertTrue("HRS out of range at $total", parts.getValue("HRS") in 0..23)
        }
    }

    // ------------------------------------------------------------ performance mode

    @Test fun reducedPrecisionDropsSecondsTile() {
        val full = countdownParts(90_061, withSeconds = true)
        val reduced = countdownParts(90_061, withSeconds = false)

        assertEquals(4, full.size)
        assertEquals(3, reduced.size)

        // Everything except the seconds tile must be identical, otherwise turning Performance Mode
        // on would change the day/hour/minute readout and look like a wrong countdown.
        assertEquals(full.dropLast(1), reduced)
        assertTrue(reduced.none { it.first == "SEC" })
    }

    // ------------------------------------------------------------ compact label

    @Test fun compactLabelPicksTheLargestUnit() {
        assertEquals("LIVE NOW", formatCountdown(0))
        assertEquals("LIVE NOW", formatCountdown(-1))
        assertEquals("5S", formatCountdown(5_000))
        assertEquals("1M 1S", formatCountdown(61_000))
        assertEquals("1H 1M 1S", formatCountdown(3_661_000))
        assertEquals("1D 1H 1M", formatCountdown(90_061_000))
    }

    @Test fun compactLabelNeverShowsNegativeNumbers() {
        for (ms in listOf(1L, 999L, 1_000L, 60_000L, 3_600_000L, 86_400_000L)) {
            val label = formatCountdown(ms)
            assertTrue("negative component in '$label'", !label.contains("-"))
        }
    }
}
