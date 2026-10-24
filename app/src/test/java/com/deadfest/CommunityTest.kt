package com.deadfest

import com.deadfest.data.model.GameStats
import com.deadfest.data.model.PlayerData
import com.deadfest.domain.*
import org.junit.Assert.*
import org.junit.Test

class CommunityTest {

    // ------------------------------------------------------------ sector overload

    @Test fun overloadOnlyAppliesToTheOverloadedSectorWhileLive() {
        assertEquals(3.0, OverloadRules.multiplierFor("Dying Light", "Dying Light", true, 3.0), 0.0)
        assertEquals(3.0, OverloadRules.multiplierFor("dying light ", "Dying Light", true, 3.0), 0.0)
        assertEquals(1.0, OverloadRules.multiplierFor("World War Z", "Dying Light", true, 3.0), 0.0)
        assertEquals(1.0, OverloadRules.multiplierFor("Dying Light", "Dying Light", false, 3.0), 0.0)
        assertEquals(1.0, OverloadRules.multiplierFor("Dying Light", "", true, 3.0), 0.0)
    }

    @Test fun overloadIsCappedAndIgnoresNonsense() {
        assertEquals(OverloadRules.MAX_MULTIPLIER, OverloadRules.multiplierFor("A", "A", true, 30.0), 0.0)
        assertEquals(1.0, OverloadRules.multiplierFor("A", "A", true, 0.5), 0.0)
        assertEquals(1.0, OverloadRules.multiplierFor("A", "A", true, Double.NaN), 0.0)
    }

    @Test fun boostScalesPayoutAndXp() {
        assertEquals(30, OverloadRules.boost(10, 3.0))
        assertEquals(25, OverloadRules.boost(10, 2.5))
        assertEquals(10, OverloadRules.boost(10, 1.0))
        assertEquals(0, OverloadRules.boost(0, 3.0))
        // A bankrupt survivor still gets half of the boosted reward.
        assertEquals(15, Bankruptcy.payoutFor(Bankruptcy.LEVEL_1, OverloadRules.boost(10, 3.0)))
    }

    // ------------------------------------------------------------ flash sales

    @Test fun flashSaleDiscountsWhileLive() {
        val sale = FlashSale(active = true, pct = 30, endsAt = 0)
        assertEquals(70, sale.apply(100))
        assertEquals(35, sale.apply(50))
        assertEquals(1, FlashSale(true, 90, 0).apply(1)) // never free
        assertEquals(0, sale.apply(0))                  // free stays free
    }

    @Test fun flashSaleStopsAtItsEndTime() {
        val sale = FlashSale(active = true, pct = 50, endsAt = 1_000)
        assertEquals(50, sale.apply(100, now = 999))
        assertEquals(100, sale.apply(100, now = 1_000))
        assertFalse(sale.isLive(now = 1_001))
    }

    @Test fun inactiveOrInvalidSaleChangesNothing() {
        assertEquals(100, FlashSale(active = false, pct = 50).apply(100))
        assertEquals(100, FlashSale(active = true, pct = 0).apply(100))
        assertEquals(100, FlashSale(active = true, pct = 95).apply(100)) // above the 90% cap
    }

    // ------------------------------------------------------------ achievements

    private fun ids(d: PlayerData, events: Set<AchievementEvent> = emptySet()) =
        Achievements.newlyEarned(d, events).map { it.id }.toSet()

    @Test fun freshSurvivorHasNothing() {
        assertTrue(ids(PlayerData(coins = 250)).isEmpty())
    }

    @Test fun firstBloodAndCenturion() {
        val one = PlayerData(games = mapOf("A" to GameStats(deaths = 1)))
        assertTrue("first_blood" in ids(one))
        assertFalse("centurion" in ids(one))
        val hundred = PlayerData(games = mapOf("A" to GameStats(deaths = 60), "B" to GameStats(deaths = 40)))
        assertTrue("centurion" in ids(hundred))
    }

    @Test fun bankruptAndBackNeedsARecovery() {
        assertFalse("bankrupt_back" in ids(PlayerData(everBankrupt = true, bankruptcyLevel = 1, coins = 0)))
        assertTrue("bankrupt_back" in ids(PlayerData(everBankrupt = true, bankruptcyLevel = 0, coins = 40)))
        assertFalse("bankrupt_back" in ids(PlayerData(everBankrupt = false, bankruptcyLevel = 0, coins = 40)))
    }

    @Test fun streakLevelPrestigeAndInsulation() {
        val d = PlayerData(survivalStreak = 7, level = 25, prestigeCount = 1, insulationTier = 4, coins = 10_000)
        assertTrue(ids(d).containsAll(listOf("survivor_7", "level_10", "level_25", "prestige", "ghost_economy", "high_roller")))
    }

    @Test fun eventBadgesOnlyFromTheirEvent() {
        val d = PlayerData()
        assertTrue(ids(d).none { it == "jackpot" })
        assertEquals(setOf("jackpot"), ids(d, setOf(AchievementEvent.JACKPOT)))
        assertEquals(setOf("deflector", "comms_online"), ids(d, setOf(AchievementEvent.SHIELD_BLOCKED, AchievementEvent.CHAT_MESSAGE)))
    }

    @Test fun ownedBadgesAreNeverGrantedTwice() {
        val d = PlayerData(games = mapOf("A" to GameStats(deaths = 1)), achievements = listOf("first_blood", "jackpot"))
        assertTrue(ids(d, setOf(AchievementEvent.JACKPOT)).isEmpty())
    }

    @Test fun everyBadgeIdIsUnique() {
        assertEquals(Achievements.ALL.size, Achievements.ALL.map { it.id }.toSet().size)
    }

    // ------------------------------------------------------------ stats history

    @Test fun historyKeepsTheNewestDays() {
        val keys = (1..65).map { "2026-08-%02d".format(it % 31 + 1) }.toSet() + listOf("2026-09-01", "2026-10-24")
        val prune = StatsHistory.keysToPrune(keys, keep = 60)
        assertEquals((keys.size - 60).coerceAtLeast(0), prune.size)
        assertFalse("2026-10-24" in prune)
        assertTrue(prune.all { it < "2026-10-24" })
    }

    @Test fun seriesIsChronological() {
        val s = StatsHistory.series(mapOf("2026-10-02" to 5L, "2026-09-30" to 1L, "2026-10-01" to 3L))
        assertEquals(listOf("2026-09-30", "2026-10-01", "2026-10-02"), s.map { it.first })
        assertEquals(listOf("2026-10-01", "2026-10-02"), StatsHistory.series(mapOf("2026-10-02" to 5L, "2026-09-30" to 1L, "2026-10-01" to 3L), lastDays = 2).map { it.first })
    }

    @Test fun dayKeyIsIsoDate() {
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(StatsHistory.dayKey(System.currentTimeMillis())))
    }
}
