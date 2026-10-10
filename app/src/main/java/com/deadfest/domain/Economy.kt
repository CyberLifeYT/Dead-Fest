package com.deadfest.domain

import java.util.Calendar
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

/*
 * Pure game rules: no Firebase, no Android UI. Everything that decides coins, tax,
 * bankruptcy and XP lives here so the repository transactions and the UI readouts
 * use exactly the same maths (and so it can be unit-tested).
 */

// ------------------------------------------------------------------ config

data class XpConfig(
    val death: Long = 10,
    val survivalDay: Long = 25,
    val purchase: Long = 5,
    val spin: Long = 3,
    val scratch: Long = 3,
    val penaltySpin: Long = 5,
    /** xpForLevel(n) = round(base * n ^ exponent) */
    val curveBase: Double = 100.0,
    val curveExponent: Double = 1.5,
    val maxLevel: Int = 50
)

/** Admin override for one Black Market item (null = use the catalog default). */
data class ShopOverride(
    val enabled: Boolean? = null,
    val price: Long? = null,
    /** Wealth Insulation tiers only: tax reduction in percent. */
    val reductionPct: Int? = null
)

data class EconomyConfig(
    val dailyBase: Long = 50,
    val dailyUpgraded: Long = 100,
    /** Mutual Survival Reward: coins every other survivor gets when someone dies. 0 = off. */
    val survivalReward: Long = 10,
    val insurancePct: Int = 50,
    val insulationEnabled: Boolean = true,
    val scratchEnabled: Boolean = true,
    val scratchCost: Long = 25,
    val shop: Map<String, ShopOverride> = emptyMap(),
    val xp: XpConfig = XpConfig()
)

// ------------------------------------------------------------------ weighted RNG

/** The one weighted picker, shared by the Wheel of Fate and Scratch Cards. Decimal weights welcome. */
object Weighted {
    fun pick(weights: List<Double>, random: Random = Random.Default): Int {
        require(weights.isNotEmpty()) { "Nothing to pick from" }
        val safe = weights.map { if (it.isFinite()) it.coerceAtLeast(0.0) else 0.0 }
        val total = safe.sum()
        if (total <= 0.0) return random.nextInt(safe.size)
        var roll = random.nextDouble(total)
        safe.forEachIndexed { i, w ->
            if (roll < w) return i
            roll -= w
        }
        return safe.lastIndex
    }

    /** Chance of each entry in percent (for odds readouts). */
    fun chances(weights: List<Double>): List<Double> {
        val safe = weights.map { it.coerceAtLeast(0.0) }
        val total = safe.sum()
        return if (total <= 0.0) safe.map { 0.0 } else safe.map { it / total * 100.0 }
    }
}

// ------------------------------------------------------------------ casualty tax

enum class TaxTier(val label: String, val rate: Double?, val flat: Long?) {
    ELITE("ELITE", 0.30, null),   // > 5,000
    HIGH("HIGH", 0.20, null),     // > 1,000
    MID("MID", 0.15, null),       // > 500
    BASE("BASE", null, 25);       // <= 500: flat 25

    companion object {
        fun forBalance(coins: Long): TaxTier = when {
            coins > 5_000 -> ELITE
            coins > 1_000 -> HIGH
            coins > 500 -> MID
            else -> BASE
        }
    }
}

data class TaxBreakdown(
    val tier: TaxTier,
    /** Tax before any upgrades. */
    val baseTax: Long,
    val insulationTier: Int,
    val insulationReductionPct: Int,
    val insulationSaved: Long,
    /** Tax after Wealth Insulation, before Insurance. */
    val afterInsulation: Long,
    val insuranceUsed: Boolean,
    val insurancePct: Int,
    val insuranceSaved: Long,
    /** What actually leaves the balance (never more than the balance). */
    val finalTax: Long
)

object CasualtyTax {
    /**
     * Order matters: tier tax -> Wealth Insulation (multiplicative) -> Insurance absorbs a % of
     * what's left -> clamp so the balance never goes negative. Insurance is consumed whenever a
     * tax would trigger, even if the balance is already 0 and it saves nothing.
     */
    fun compute(
        balance: Long,
        insulationTier: Int,
        insulationReductionPct: Int,
        insuranceActive: Boolean,
        insurancePct: Int
    ): TaxBreakdown {
        val coins = balance.coerceAtLeast(0)
        val tier = TaxTier.forBalance(coins)
        val baseTax = tier.rate?.let { (coins * it).roundToLong() } ?: tier.flat!!
        val reduction = if (insulationTier > 0) insulationReductionPct.coerceIn(0, 100) else 0
        val afterInsulation = (baseTax * (1 - reduction / 100.0)).roundToLong()
        val insuranceUsed = insuranceActive && afterInsulation > 0
        val pct = insurancePct.coerceIn(0, 100)
        val insuranceSaved = if (insuranceUsed) (afterInsulation * pct / 100.0).roundToLong() else 0
        val due = (afterInsulation - insuranceSaved).coerceAtLeast(0)
        return TaxBreakdown(
            tier = tier,
            baseTax = baseTax,
            insulationTier = insulationTier,
            insulationReductionPct = reduction,
            insulationSaved = baseTax - afterInsulation,
            afterInsulation = afterInsulation,
            insuranceUsed = insuranceUsed,
            insurancePct = if (insuranceUsed) pct else 0,
            insuranceSaved = insuranceSaved,
            finalTax = due.coerceAtMost(coins)
        )
    }

    /** Effective rate for the readout: percent of balance, or a flat coin amount for BASE. */
    fun effectiveRateLabel(balance: Long, insulationReductionPct: Int): String {
        val tier = TaxTier.forBalance(balance)
        val factor = 1 - insulationReductionPct.coerceIn(0, 100) / 100.0
        return if (tier.rate != null) {
            val pct = tier.rate * 100 * factor
            "${formatPct(pct)}% of balance"
        } else {
            "flat ${(tier.flat!! * factor).roundToLong()} coins"
        }
    }

    fun formatPct(p: Double): String =
        if (p % 1.0 == 0.0) p.toLong().toString() else String.format(java.util.Locale.US, "%.1f", p)
}

// ------------------------------------------------------------------ bankruptcy

object Bankruptcy {
    const val NONE = 0
    const val LEVEL_1 = 1
    const val LEVEL_2 = 2

    /**
     * Applies the balance-driven transitions after any coin change:
     *  NONE -> LEVEL_1 when the balance hits 0;
     *  LEVEL_1 -> NONE as soon as the balance is above 0;
     *  LEVEL_2 -> NONE once every pending spin is cleared AND the balance is above 0.
     * (LEVEL_1 -> LEVEL_2 is death-driven: see [DeathRules].)
     */
    fun afterBalanceChange(level: Int, coins: Long, pendingSpins: Int): Int = when {
        level <= NONE && coins <= 0 -> LEVEL_1
        level == LEVEL_1 && coins > 0 -> NONE
        level >= LEVEL_2 && coins > 0 && pendingSpins <= 0 -> NONE
        else -> level.coerceIn(NONE, LEVEL_2)
    }

    /** Mutual Survival Reward income is halved for anyone in bankruptcy. */
    fun payoutFor(level: Int, reward: Long): Long = if (level >= LEVEL_1) reward / 2 else reward
}

data class DeathOutcome(
    val tax: TaxBreakdown,
    val coinsBefore: Long,
    val coinsAfter: Long,
    val levelBefore: Int,
    val levelAfter: Int,
    val pendingBefore: Int,
    val pendingAfter: Int,
    val spinsAdded: Int
)

object DeathRules {
    /**
     * Forced Spin Protocol + bankruptcy escalation + casualty tax, all from the live values
     * read inside the same transaction.
     */
    fun resolve(
        coins: Long,
        bankruptcyLevel: Int,
        pendingSpins: Int,
        insulationTier: Int,
        insulationReductionPct: Int,
        insuranceActive: Boolean,
        insurancePct: Int
    ): DeathOutcome {
        val wasBankrupt = bankruptcyLevel >= Bankruptcy.LEVEL_1
        var spinsAdded = 1                       // every death: +1 pending spin
        var level = bankruptcyLevel.coerceIn(Bankruptcy.NONE, Bankruptcy.LEVEL_2)
        if (wasBankrupt) {                        // any further death while bankrupt
            level = Bankruptcy.LEVEL_2
            spinsAdded += 2                       // Level 2 adds 2 more on top
        }
        val pending = pendingSpins.coerceAtLeast(0) + spinsAdded
        val tax = CasualtyTax.compute(coins, insulationTier, insulationReductionPct, insuranceActive, insurancePct)
        val after = (coins - tax.finalTax).coerceAtLeast(0)
        level = Bankruptcy.afterBalanceChange(level, after, pending)
        return DeathOutcome(
            tax = tax,
            coinsBefore = coins,
            coinsAfter = after,
            levelBefore = bankruptcyLevel,
            levelAfter = level,
            pendingBefore = pendingSpins,
            pendingAfter = pending,
            spinsAdded = spinsAdded
        )
    }
}

// ------------------------------------------------------------------ XP & levels

data class XpResult(
    val gained: Long,
    val xp: Long,
    val level: Int,
    val levelsGained: List<Int>
)

object Leveling {
    /** XP needed to go from level [n] to n + 1. */
    fun xpForLevel(n: Int, cfg: XpConfig): Long =
        (cfg.curveBase * n.coerceAtLeast(1).toDouble().pow(cfg.curveExponent)).roundToLong().coerceAtLeast(1)

    fun addXp(xp: Long, level: Int, amount: Long, cfg: XpConfig): XpResult {
        var curXp = xp.coerceAtLeast(0) + amount.coerceAtLeast(0)
        var curLevel = level.coerceIn(1, cfg.maxLevel.coerceAtLeast(1))
        val gained = mutableListOf<Int>()
        while (curLevel < cfg.maxLevel) {
            val need = xpForLevel(curLevel, cfg)
            if (curXp < need) break
            curXp -= need
            curLevel++
            gained += curLevel
        }
        return XpResult(amount.coerceAtLeast(0), curXp, curLevel, gained)
    }

    /** 0..1 progress towards the next level (1 at max level). */
    fun progress(xp: Long, level: Int, cfg: XpConfig): Float =
        if (level >= cfg.maxLevel) 1f
        else (xp.toFloat() / xpForLevel(level, cfg)).coerceIn(0f, 1f)

    fun canPrestige(level: Int, cfg: XpConfig): Boolean = level >= cfg.maxLevel
}

// ------------------------------------------------------------------ daily rations

object DailyClock {
    /** Midnight (local time) of the day containing [millis]. */
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun nextReset(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(now)
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    /** One claim per calendar day: the last claim must be before today's midnight. */
    fun canClaim(lastClaim: Long, now: Long): Boolean = lastClaim <= 0 || lastClaim < startOfDay(now)

    fun countdown(now: Long): String {
        val left = ((nextReset(now) - now) / 1000).coerceAtLeast(0)
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", left / 3600, (left % 3600) / 60, left % 60)
    }
}
