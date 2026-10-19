package com.deadfest

import com.deadfest.domain.Bankruptcy
import com.deadfest.domain.DeathRules
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the admin Forced Spin Protocol switch.
 *
 * The default (on) must reproduce the shipped behaviour exactly — every death adds a penalty spin,
 * and a second death while bankrupt adds two more. With it off, a death adds no spin at all while
 * bankruptcy escalation is untouched: a survivor who keeps dying while broke is a bankruptcy
 * problem, not a spin problem.
 */
class ForcedSpinProtocolTest {

    private val base = mapOf(
        "insulationTier" to 0,
        "insulationReductionPct" to 0,
        "insuranceActive" to false,
        "insurancePct" to 50
    )

    private fun resolve(
        coins: Long,
        level: Int,
        pending: Int,
        forceSpinProtocol: Boolean
    ) = DeathRules.resolve(
        coins = coins,
        bankruptcyLevel = level,
        pendingSpins = pending,
        insulationTier = base["insulationTier"] as Int,
        insulationReductionPct = base["insulationReductionPct"] as Int,
        insuranceActive = base["insuranceActive"] as Boolean,
        insurancePct = base["insurancePct"] as Int,
        forceSpinProtocol = forceSpinProtocol
    )

    // ------------------------------------------------------------ default (on)

    @Test fun defaultAddsOneSpinPerDeath() {
        // The switch defaults on, so a document written before it existed keeps the old behaviour.
        val on = resolve(coins = 20, level = 0, pending = 0, forceSpinProtocol = true)
        assertEquals(1, on.spinsAdded)
        assertEquals(1, on.pendingAfter)

        // And the omitted argument behaves the same way.
        val omitted = DeathRules.resolve(20, 0, 0, 0, 0, false, 50)
        assertEquals(on.spinsAdded, omitted.spinsAdded)
    }

    @Test fun defaultEscalatesBankruptcyAndAddsTwoMore() {
        val o = resolve(coins = 0, level = Bankruptcy.LEVEL_1, pending = 1, forceSpinProtocol = true)
        assertEquals(Bankruptcy.LEVEL_2, o.levelAfter)
        assertEquals(3, o.spinsAdded)   // 1 for the death + 2 for being bankrupt
        assertEquals(4, o.pendingAfter)
    }

    // ------------------------------------------------------------ switched off

    @Test fun offAddsNoSpin() {
        val o = resolve(coins = 20, level = 0, pending = 0, forceSpinProtocol = false)
        assertEquals(0, o.spinsAdded)
        assertEquals(0, o.pendingAfter)
    }

    @Test fun offStillEscalatesBankruptcy() {
        // Bankruptcy is a separate system from the spin protocol and must not be switched off
        // with it.
        val o = resolve(coins = 0, level = Bankruptcy.LEVEL_1, pending = 1, forceSpinProtocol = false)
        assertEquals(Bankruptcy.LEVEL_2, o.levelAfter)
        assertEquals(0, o.spinsAdded)
        assertEquals(1, o.pendingAfter)   // the survivor's existing spin is untouched
    }

    @Test fun offLeavesAnExistingSpinAlone() {
        // Turning the protocol off must not clear spins a survivor has already accrued.
        val o = resolve(coins = 500, level = 0, pending = 2, forceSpinProtocol = false)
        assertEquals(2, o.pendingAfter)
    }
}
