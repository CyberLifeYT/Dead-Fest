package com.deadfest.domain

import com.deadfest.data.model.PlayerData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/*
 * v2.1.0 rules: Sector Overload payouts, flash sales, achievements and the personal stats
 * history. Pure Kotlin like the rest of the domain package, so the UI, the repository
 * transactions and the unit tests all share one definition.
 */

// ------------------------------------------------------------------ sector overload

object OverloadRules {
    /** Hard cap so a typo in the admin panel ("30" instead of "3") can't wreck the economy. */
    const val MAX_MULTIPLIER = 10.0

    /**
     * The multiplier a death in [game] earns: the admin's value while that sector is overloaded
     * and the overload is live, otherwise 1. Sector names compare case-insensitively.
     */
    fun multiplierFor(game: String, overloadedSector: String, overloadLive: Boolean, multiplier: Double): Double =
        if (overloadLive && overloadedSector.isNotBlank() && game.trim().equals(overloadedSector.trim(), ignoreCase = true) &&
            multiplier.isFinite() && multiplier > 1.0
        ) multiplier.coerceAtMost(MAX_MULTIPLIER) else 1.0

    /** Scales a coin or XP amount, rounding to the nearest whole unit. */
    fun boost(amount: Long, multiplier: Double): Long =
        if (multiplier <= 1.0 || amount <= 0) amount else (amount * multiplier).roundToLong()
}

// ------------------------------------------------------------------ flash sales

/** A timed Black Market discount, set by an admin. */
data class FlashSale(
    val active: Boolean = false,
    /** Discount in percent (1–90). */
    val pct: Int = 0,
    /** Epoch millis the sale ends; 0 = until an admin ends it. */
    val endsAt: Long = 0L
) {
    fun isLive(now: Long = System.currentTimeMillis()): Boolean =
        active && pct in 1..MAX_PCT && (endsAt <= 0L || now < endsAt)

    /** [price] after the discount (never below 1 for a paid item), or unchanged when no sale runs. */
    fun apply(price: Long, now: Long = System.currentTimeMillis()): Long {
        if (!isLive(now) || price <= 0) return price
        return (price * (100 - pct) / 100.0).roundToLong().coerceAtLeast(1)
    }

    companion object {
        const val MAX_PCT = 90
    }
}

// ------------------------------------------------------------------ achievements

/** Things that happen once and can't be read back off the profile later. */
enum class AchievementEvent { JACKPOT, CURSE_LANDED, SHIELD_BLOCKED, INSURANCE_CLAIMED, LUCKY_STRIKE, IMAGE_SHARED, CHAT_MESSAGE }

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    /** Set for event-driven badges; null for badges read off the profile. */
    val event: AchievementEvent? = null
)

object Achievements {
    const val JACKPOT_COINS = 250L
    const val STREAK_DAYS = 7
    const val CENTURION_DEATHS = 100L
    const val HIGH_ROLLER_COINS = 10_000L

    val ALL: List<Achievement> = listOf(
        Achievement("first_blood", "First Blood", "Record your first death"),
        Achievement("centurion", "Centurion", "Reach $CENTURION_DEATHS deaths"),
        Achievement("survivor_7", "Survived 7 Days", "Claim rations $STREAK_DAYS times in a row without dying"),
        Achievement("bankrupt_back", "Bankrupt and Back", "Climb out of bankruptcy"),
        Achievement("jackpot", "Jackpot", "Win $JACKPOT_COINS+ coins from one spin or scratch", AchievementEvent.JACKPOT),
        Achievement("high_roller", "High Roller", "Hold 10,000 coins at once"),
        Achievement("level_10", "Seasoned", "Reach level 10"),
        Achievement("level_25", "Veteran", "Reach level 25"),
        Achievement("prestige", "Legend", "Prestige for the first time"),
        Achievement("ghost_economy", "Ghost Economy", "Own Wealth Insulation Tier IV"),
        Achievement("hex_master", "Hex Master", "Land a curse on another survivor", AchievementEvent.CURSE_LANDED),
        Achievement("deflector", "Deflector", "Have your Shield block a curse", AchievementEvent.SHIELD_BLOCKED),
        Achievement("covered", "Covered", "Have Insurance absorb a casualty tax", AchievementEvent.INSURANCE_CLAIMED),
        Achievement("lucky_strike", "Lucky Strike", "Win on the Wheel with a Lucky Coin armed", AchievementEvent.LUCKY_STRIKE),
        Achievement("photographer", "Photographer", "Share an image", AchievementEvent.IMAGE_SHARED),
        Achievement("comms_online", "Comms Online", "Send your first chat message", AchievementEvent.CHAT_MESSAGE)
    )

    fun byId(id: String): Achievement? = ALL.firstOrNull { it.id == id }

    /** True when [a] is satisfied by the profile itself (profile-driven badges only). */
    private fun satisfiedBy(a: Achievement, d: PlayerData): Boolean {
        val deaths = d.games.values.sumOf { it.deaths }
        return when (a.id) {
            "first_blood" -> deaths >= 1
            "centurion" -> deaths >= CENTURION_DEATHS
            "survivor_7" -> d.survivalStreak >= STREAK_DAYS
            "bankrupt_back" -> d.everBankrupt && d.bankruptcyLevel == 0 && d.coins > 0
            "high_roller" -> d.coins >= HIGH_ROLLER_COINS
            "level_10" -> d.level >= 10 || d.prestigeCount > 0
            "level_25" -> d.level >= 25 || d.prestigeCount > 0
            "prestige" -> d.prestigeCount >= 1
            "ghost_economy" -> d.insulationTier >= 4
            else -> false
        }
    }

    /** Badges [d] has earned but doesn't hold yet, given any one-off [events] that just happened. */
    fun newlyEarned(d: PlayerData, events: Set<AchievementEvent> = emptySet()): List<Achievement> =
        ALL.filter { a ->
            a.id !in d.achievements && ((a.event != null && a.event in events) || (a.event == null && satisfiedBy(a, d)))
        }
}

// ------------------------------------------------------------------ personal stats history

object StatsHistory {
    /** Days of history kept on the profile (one small map entry each). */
    const val KEEP_DAYS = 60

    fun dayKey(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    /** Oldest keys to delete so at most [keep] remain (keys are ISO dates, so they sort by day). */
    fun keysToPrune(keys: Collection<String>, keep: Int = KEEP_DAYS): List<String> =
        keys.sorted().dropLast(keep)

    /** Chronological (day, value) points, optionally only the most recent [lastDays]. */
    fun series(map: Map<String, Long>, lastDays: Int? = null): List<Pair<String, Long>> {
        val sorted = map.entries.sortedBy { it.key }.map { it.key to it.value }
        return if (lastDays == null) sorted else sorted.takeLast(lastDays)
    }
}
