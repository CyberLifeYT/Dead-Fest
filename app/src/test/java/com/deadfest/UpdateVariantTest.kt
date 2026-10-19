package com.deadfest

import com.deadfest.data.model.GlobalState
import com.deadfest.domain.UpdateVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two-build update rule.
 *
 * The failure this guards against is quiet rather than loud. If the fallback were wrong, a survivor
 * whose preference points at a build that has no URL yet would press Update, be told the terminal
 * is already current, and never learn that everyone else got the new version.
 */
class UpdateVariantTest {

    private val both = GlobalState(
        updateUrl = "https://example.test/full.apk",
        updateUrlR8 = "https://example.test/r8.apk"
    )

    // ------------------------------------------------------------ stored preference

    @Test fun storedValueRoundTrips() {
        // The entry names are what goes into preferences, so renaming one would silently reset
        // everyone's choice back to the default.
        for (variant in UpdateVariant.entries) {
            assertEquals(variant, UpdateVariant.fromStored(variant.name))
        }
    }

    @Test fun storedValueIsCaseInsensitive() {
        assertEquals(UpdateVariant.R8, UpdateVariant.fromStored("r8"))
        assertEquals(UpdateVariant.Standard, UpdateVariant.fromStored("STANDARD"))
    }

    @Test fun unrecognisedStoredValueMeansNeverChose() {
        // Null is meaningful: it is what makes the default follow the build actually running.
        assertNull(UpdateVariant.fromStored(null))
        assertNull(UpdateVariant.fromStored(""))
        assertNull(UpdateVariant.fromStored("Hammer"))
    }

    // ------------------------------------------------------------ url resolution

    @Test fun eachBuildPrefersItsOwnUrl() {
        assertEquals(both.updateUrlR8, both.updateUrlFor(UpdateVariant.R8))
        assertEquals(both.updateUrl, both.updateUrlFor(UpdateVariant.Standard))
    }

    @Test fun fallsBackWhenTheChosenBuildHasNoUrl() {
        // The live document carried a single URL for four releases, so on the day this ships the R8
        // URL is blank for everyone and a device asking for R8 has to be served the full build.
        val fullOnly = GlobalState(updateUrl = "https://example.test/full.apk", updateUrlR8 = "")
        assertEquals("https://example.test/full.apk", fullOnly.updateUrlFor(UpdateVariant.R8))
        assertEquals("https://example.test/full.apk", fullOnly.updateUrlFor(UpdateVariant.Standard))

        val r8Only = GlobalState(updateUrl = "", updateUrlR8 = "https://example.test/r8.apk")
        assertEquals("https://example.test/r8.apk", r8Only.updateUrlFor(UpdateVariant.R8))
        assertEquals("https://example.test/r8.apk", r8Only.updateUrlFor(UpdateVariant.Standard))
    }

    @Test fun noUrlAtAllResolvesToBlank() {
        // Blank is what makes checkUpdate report "up to date" rather than offering a dead download.
        assertEquals("", GlobalState().updateUrlFor(UpdateVariant.R8))
        assertEquals("", GlobalState().updateUrlFor(UpdateVariant.Standard))
    }

    @Test fun everyBuildIsPresentable() {
        // Both strings are rendered in Settings; a blank one would show as an empty row.
        for (variant in UpdateVariant.entries) {
            assertTrue("${variant.name} has no label", variant.label.isNotBlank())
            assertTrue("${variant.name} has no blurb", variant.blurb.isNotBlank())
        }
    }

    @Test fun theTwoBuildsAreDistinguishable() {
        // Two options a survivor cannot tell apart would be worse than no choice at all.
        val labels = UpdateVariant.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }
}
