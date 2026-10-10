package com.deadfest

import com.deadfest.domain.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class EconomyTest {

    // ------------------------------------------------------------ tax tiers

    @Test fun tierBoundaries() {
        assertEquals(TaxTier.BASE, TaxTier.forBalance(500))
        assertEquals(TaxTier.MID, TaxTier.forBalance(501))
        assertEquals(TaxTier.MID, TaxTier.forBalance(1_000))
        assertEquals(TaxTier.HIGH, TaxTier.forBalance(1_001))
        assertEquals(TaxTier.HIGH, TaxTier.forBalance(5_000))
        assertEquals(TaxTier.ELITE, TaxTier.forBalance(5_001))
    }

    @Test fun eliteNoUpgrades() {
        val t = CasualtyTax.compute(10_000, 0, 0, false, 50)
        assertEquals(3_000, t.baseTax)
        assertEquals(3_000, t.finalTax)
        assertFalse(t.insuranceUsed)
    }

    @Test fun specExample_tierIII_plusInsurance_is750() {
        // Base 3,000 -> Tier III (-50%) 1,500 -> Insurance (50%) 750
        val t = CasualtyTax.compute(10_000, insulationTier = 3, insulationReductionPct = 50, insuranceActive = true, insurancePct = 50)
        assertEquals(3_000, t.baseTax)
        assertEquals(1_500, t.afterInsulation)
        assertEquals(1_500, t.insulationSaved)
        assertEquals(750, t.insuranceSaved)
        assertEquals(750, t.finalTax)
        assertTrue(t.insuranceUsed)
    }

    @Test fun tierII_elite_is22point5Percent() {
        val t = CasualtyTax.compute(10_000, 2, 25, false, 50)
        assertEquals(2_250, t.finalTax)
        assertEquals("22.5% of balance", CasualtyTax.effectiveRateLabel(10_000, 25))
    }

    @Test fun tierIV_zeroTax_andInsuranceNotConsumed() {
        val t = CasualtyTax.compute(10_000, 4, 100, true, 50)
        assertEquals(0, t.finalTax)
        assertFalse("nothing to absorb, so insurance stays armed", t.insuranceUsed)
    }

    @Test fun baseTierFlat25_neverBelowZero() {
        assertEquals(25, CasualtyTax.compute(400, 0, 0, false, 50).finalTax)
        assertEquals(10, CasualtyTax.compute(10, 0, 0, false, 50).finalTax)
        val broke = CasualtyTax.compute(0, 0, 0, true, 50)
        assertEquals(0, broke.finalTax)
        assertTrue("consumed even when it saves nothing", broke.insuranceUsed)
    }

    @Test fun insulationAppliesToFlatTierToo() {
        assertEquals(23, CasualtyTax.compute(300, 1, 10, false, 50).finalTax) // 25 * 0.9 = 22.5 -> 23
        assertEquals("flat 23 coins", CasualtyTax.effectiveRateLabel(300, 10))
    }

    // ------------------------------------------------------------ bankruptcy & forced spins

    @Test fun deathToZero_entersLevel1_withOneSpin() {
        val o = DeathRules.resolve(coins = 20, bankruptcyLevel = 0, pendingSpins = 0, insulationTier = 0,
            insulationReductionPct = 0, insuranceActive = false, insurancePct = 50)
        assertEquals(0, o.coinsAfter)
        assertEquals(Bankruptcy.LEVEL_1, o.levelAfter)
        assertEquals(1, o.spinsAdded)
        assertEquals(1, o.pendingAfter)
    }

    @Test fun deathWhileLevel1_escalatesToLevel2_withThreeSpins() {
        val o = DeathRules.resolve(0, Bankruptcy.LEVEL_1, 1, 0, 0, false, 50)
        assertEquals(Bankruptcy.LEVEL_2, o.levelAfter)
        assertEquals(3, o.spinsAdded)   // 1 for the death + 2 for Level 2
        assertEquals(4, o.pendingAfter)
    }

    @Test fun solventDeath_staysSolvent() {
        val o = DeathRules.resolve(2_000, 0, 0, 0, 0, false, 50)
        assertEquals(1_600, o.coinsAfter) // HIGH tier 20%
        assertEquals(Bankruptcy.NONE, o.levelAfter)
    }

    @Test fun level1_exitsAsSoonAsBalanceIsPositive() {
        assertEquals(Bankruptcy.NONE, Bankruptcy.afterBalanceChange(Bankruptcy.LEVEL_1, 5, pendingSpins = 2))
    }

    @Test fun level2_needsSpinsClearedAndPositiveBalance() {
        assertEquals(Bankruptcy.LEVEL_2, Bankruptcy.afterBalanceChange(Bankruptcy.LEVEL_2, 100, pendingSpins = 1))
        assertEquals(Bankruptcy.LEVEL_2, Bankruptcy.afterBalanceChange(Bankruptcy.LEVEL_2, 0, pendingSpins = 0))
        assertEquals(Bankruptcy.NONE, Bankruptcy.afterBalanceChange(Bankruptcy.LEVEL_2, 100, pendingSpins = 0))
    }

    @Test fun payoutHalvedWhileBankrupt() {
        assertEquals(10, Bankruptcy.payoutFor(Bankruptcy.NONE, 10))
        assertEquals(5, Bankruptcy.payoutFor(Bankruptcy.LEVEL_1, 10))
        assertEquals(5, Bankruptcy.payoutFor(Bankruptcy.LEVEL_2, 10))
    }

    // ------------------------------------------------------------ XP

    @Test fun defaultCurve() {
        val cfg = XpConfig()
        assertEquals(100, Leveling.xpForLevel(1, cfg))
        assertEquals(283, Leveling.xpForLevel(2, cfg))   // 100 * 2^1.5 = 282.8
        assertEquals(34_300, Leveling.xpForLevel(49, cfg))
    }

    @Test fun multipleLevelUpsInOneGain() {
        val r = Leveling.addXp(xp = 90, level = 1, amount = 300, cfg = XpConfig())
        // 390 total: -100 (L1->2) = 290, -283 (L2->3) = 7
        assertEquals(3, r.level)
        assertEquals(7, r.xp)
        assertEquals(listOf(2, 3), r.levelsGained)
    }

    @Test fun capsAtMaxLevel() {
        val cfg = XpConfig(maxLevel = 3)
        val r = Leveling.addXp(0, 3, 10_000, cfg)
        assertEquals(3, r.level)
        assertTrue(r.levelsGained.isEmpty())
        assertTrue(Leveling.canPrestige(3, cfg))
        assertEquals(1f, Leveling.progress(r.xp, r.level, cfg))
    }

    // ------------------------------------------------------------ weighted RNG

    @Test fun zeroWeightNeverWins() {
        val rnd = Random(42)
        repeat(5_000) { assertNotEquals(1, Weighted.pick(listOf(1.0, 0.0, 2.0), rnd)) }
    }

    @Test fun decimalWeightsAreRespected() {
        val rnd = Random(7)
        val weights = listOf(0.25, 1.35, 2.4)          // 6.25%, 33.75%, 60%
        val hits = IntArray(3)
        repeat(200_000) { hits[Weighted.pick(weights, rnd)]++ }
        assertEquals(0.0625, hits[0] / 200_000.0, 0.005)
        assertEquals(0.3375, hits[1] / 200_000.0, 0.005)
        assertEquals(0.60, hits[2] / 200_000.0, 0.005)
        assertEquals(6.25, Weighted.chances(weights)[0], 1e-9)
    }

    // ------------------------------------------------------------ daily

    @Test fun oneClaimPerCalendarDay() {
        val now = System.currentTimeMillis()
        val midnight = DailyClock.startOfDay(now)
        assertTrue(DailyClock.canClaim(0, now))
        assertTrue(DailyClock.canClaim(midnight - 1, now))   // claimed yesterday
        assertFalse(DailyClock.canClaim(midnight + 1, now))  // already claimed today
        assertTrue(DailyClock.nextReset(now) > now)
    }
}
