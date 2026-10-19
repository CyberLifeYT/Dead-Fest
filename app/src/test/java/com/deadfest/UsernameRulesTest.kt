package com.deadfest

import com.deadfest.domain.UsernameRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the handle rules.
 *
 * The failure this guards against is impersonation and confusion rather than a crash: a handle that
 * lets anyone call themselves "Admin", or one that accepts two spellings of the same name, would
 * undermine the player list, the death feed and the admin panel's audit trail at once.
 */
class UsernameRulesTest {

    // ------------------------------------------------------------ shape

    @Test fun acceptsAnOrdinaryHandle() {
        assertNull(UsernameRules.errorFor("Slayer"))
        assertNull(UsernameRules.errorFor("xX_Slayer_Xx"))
        assertNull(UsernameRules.errorFor("survivor_42"))
    }

    @Test fun rejectsEmptyAndBlank() {
        assertEquals("Enter a handle first.", UsernameRules.errorFor(""))
        assertEquals("Enter a handle first.", UsernameRules.errorFor("   "))
    }

    @Test fun enforcesLengthBounds() {
        assertEquals(
            "Handles need at least 3 characters.",
            UsernameRules.errorFor("ab")
        )
        assertEquals(
            "Handles can be at most 20 characters.",
            UsernameRules.errorFor("a".repeat(21))
        )
        // The bounds are inclusive: a 3-character and a 20-character handle are both fine.
        assertNull(UsernameRules.errorFor("abc"))
        assertNull(UsernameRules.errorFor("a".repeat(20)))
    }

    @Test fun mustStartWithALetter() {
        assertEquals(
            "Letters, numbers and underscores only — and it must start with a letter.",
            UsernameRules.errorFor("1Slayer")
        )
        assertEquals(
            "Letters, numbers and underscores only — and it must start with a letter.",
            UsernameRules.errorFor("_Slayer")
        )
    }

    @Test fun allowsOnlyLettersNumbersAndUnderscores() {
        val bad = listOf("has space", "dot.name", "dash-name", "emoji😀", "semi;colon", "star*name")
        bad.forEach { input ->
            assertEquals(
                "Letters, numbers and underscores only — and it must start with a letter.",
                UsernameRules.errorFor(input)
            )
        }
    }

    // ------------------------------------------------------------ reserved names

    @Test fun blocksEveryReservedName() {
        // Compared case-insensitively, so "ADMIN" and "Admin" are both refused.
        listOf("admin", "ADMIN", "Admin", "deadfest", "moderator", "dead_fest")
            .forEach { reserved ->
                assertEquals("That handle is reserved.", UsernameRules.errorFor(reserved))
            }
    }

    @Test fun allowsNamesThatMerelyContainAReservedWord() {
        // Only exact matches are reserved: "AdminHunter" is a legitimate handle.
        assertNull(UsernameRules.errorFor("AdminHunter"))
        assertNull(UsernameRules.errorFor("DeadfestFan"))
    }

    // ------------------------------------------------------------ uniqueness key

    @Test fun keyIgnoresCaseAndSurroundingSpace() {
        // The registry is keyed by this value, so two spellings must collapse to one key.
        assertEquals("slayer", UsernameRules.keyOf("Slayer"))
        assertEquals("slayer", UsernameRules.keyOf("  SLAYER  "))
        assertEquals("survivor_42", UsernameRules.keyOf("Survivor_42"))
    }

    @Test fun keyIsLowercase() {
        assertEquals("abc", UsernameRules.keyOf("AbC"))
    }
}
