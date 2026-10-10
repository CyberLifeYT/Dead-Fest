package com.deadfest.data.model

import com.deadfest.domain.EconomyConfig
import com.deadfest.domain.XpResult

enum class ShopCategory { COMBAT, ECONOMY, INSULATION }

/** Black Market catalog. `id` is the key written to logs, admin overrides and the repository. */
enum class ShopItem(
    val id: String,
    val displayName: String,
    val description: String,
    val price: Long,
    val category: ShopCategory,
    val logName: String,
    val curseDeaths: Long = 0,
    /** Permanent purchase: can only ever be bought once. */
    val oneTime: Boolean = false,
    /** Wealth Insulation tier this item grants (0 = not an insulation item). */
    val tier: Int = 0,
    /** Insulation tier that must already be owned before this one can be bought. */
    val requiresTier: Int = 0,
    /** Default tax reduction in percent (insulation tiers only). */
    val defaultReductionPct: Int = 0
) {
    SHIELD("shield", "SHIELD", "Blocks the next curse aimed at you", 50, ShopCategory.COMBAT, "a Shield"),
    CURSE("curse", "CURSE", "Adds 1 death to a target", 100, ShopCategory.COMBAT, "a Curse", curseDeaths = 1),
    DOUBLE_CURSE("double_curse", "DOUBLE CURSE", "Adds 2 deaths to a target", 200, ShopCategory.COMBAT, "a Double Curse", curseDeaths = 2),
    REVIVE("revive", "REVIVE", "Removes 1 death from you", 150, ShopCategory.COMBAT, "a Revive"),
    LUCKY_COIN("lucky_coin", "LUCKY COIN", "Doubles your next Wheel win", 80, ShopCategory.ECONOMY, "a Lucky Coin"),
    INSURANCE("insurance", "INSURANCE POLICY", "Absorbs part of your next casualty tax. One use.", 150, ShopCategory.ECONOMY, "an Insurance Policy"),
    RATION_UPGRADE("ration_upgrade", "RATION UPGRADE", "Permanently raises your daily rations", 750, ShopCategory.ECONOMY, "the Ration Upgrade", oneTime = true),

    INSULATION_1("insulation_1", "INSULATED LEDGER", "Wealth Insulation Tier I", 1_500, ShopCategory.INSULATION, "Insulated Ledger", oneTime = true, tier = 1, requiresTier = 0, defaultReductionPct = 10),
    INSULATION_2("insulation_2", "OFFSHORE TERMINAL", "Wealth Insulation Tier II", 5_000, ShopCategory.INSULATION, "Offshore Terminal", oneTime = true, tier = 2, requiresTier = 1, defaultReductionPct = 25),
    INSULATION_3("insulation_3", "BLACK VAULT PROTOCOL", "Wealth Insulation Tier III", 15_000, ShopCategory.INSULATION, "Black Vault Protocol", oneTime = true, tier = 3, requiresTier = 2, defaultReductionPct = 50),
    INSULATION_4("insulation_4", "GHOST ECONOMY", "Wealth Insulation Tier IV", 50_000, ShopCategory.INSULATION, "Ghost Economy", oneTime = true, tier = 4, requiresTier = 3, defaultReductionPct = 100);

    val isCurse: Boolean get() = curseDeaths > 0
    val isInsulation: Boolean get() = tier > 0

    fun priceIn(cfg: EconomyConfig): Long = cfg.shop[id]?.price?.coerceAtLeast(0) ?: price

    fun enabledIn(cfg: EconomyConfig): Boolean =
        (cfg.shop[id]?.enabled ?: true) && (!isInsulation || cfg.insulationEnabled)

    fun reductionIn(cfg: EconomyConfig): Int = cfg.shop[id]?.reductionPct?.coerceIn(0, 100) ?: defaultReductionPct

    companion object {
        val insulationTiers: List<ShopItem> = entries.filter { it.isInsulation }.sortedBy { it.tier }

        fun forInsulationTier(tier: Int): ShopItem? = insulationTiers.firstOrNull { it.tier == tier }

        /** Tax reduction for the tier a survivor currently owns (0 if none). */
        fun insulationReduction(tier: Int, cfg: EconomyConfig): Int =
            forInsulationTier(tier)?.reductionIn(cfg) ?: 0

        fun roman(tier: Int): String = when (tier) {
            1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"; else -> "-"
        }
    }
}

enum class ShopOutcome { APPLIED, BLOCKED_BY_SHIELD, INSUFFICIENT_FUNDS, ALREADY_ACTIVE, NO_DEATHS, TARGET_MISSING, DISABLED, TIER_LOCKED }

data class ShopResult(
    val outcome: ShopOutcome,
    /** Sector the death was added to / removed from, when relevant. */
    val sector: String? = null,
    /** Net deaths added (+) or removed (-), used to keep the global counter in sync. */
    val deathDelta: Long = 0,
    val xp: XpResult? = null
)

data class WheelPayout(
    /** Signed change actually applied to the player's coins. */
    val delta: Long,
    val luckyCoinUsed: Boolean,
    /** True if this spin cleared a Forced Spin Protocol penalty. */
    val penaltyCleared: Boolean = false,
    val pendingLeft: Int = 0,
    val bankruptcyLevel: Int = 0,
    val xp: XpResult? = null
)

// ------------------------------------------------------------------ level rewards

/** One cosmetic unlocked at a given level. `type` is "color", "frame" or "title". */
data class LevelReward(
    var level: Int = 0,
    var rewardId: String = "",
    var type: String = "color",
    var label: String = "",
    var value: String = ""
)

/**
 * Built-in level -> reward table. A non-empty `levelRewards` Firestore collection
 * replaces it, so admins can redesign the track without an app update.
 */
object LevelRewardCatalog {
    val DEFAULTS = listOf(
        LevelReward(2, "color_toxic", "color", "Toxic Green badge", "#4CE07F"),
        LevelReward(3, "frame_rust", "frame", "Rust frame", "rust"),
        LevelReward(5, "title_scavenger", "title", "Title: SCAVENGER", "SCAVENGER"),
        LevelReward(7, "color_cyan", "color", "Neon Cyan badge", "#4DD0E1"),
        LevelReward(10, "frame_neon", "frame", "Neon frame", "neon"),
        LevelReward(12, "title_ranger", "title", "Title: WASTELAND RANGER", "WASTELAND RANGER"),
        LevelReward(15, "color_orange", "color", "Blood Orange badge", "#FF7043"),
        LevelReward(20, "frame_toxic", "frame", "Toxic frame", "toxic"),
        LevelReward(25, "title_defier", "title", "Title: DEATH DEFIER", "DEATH DEFIER"),
        LevelReward(30, "color_violet", "color", "Royal Violet badge", "#B388FF"),
        LevelReward(35, "frame_gold", "frame", "Gold frame", "gold"),
        LevelReward(40, "title_unkillable", "title", "Title: UNKILLABLE", "UNKILLABLE"),
        LevelReward(45, "color_gold", "color", "Molten Gold badge", "#FFD54F"),
        LevelReward(50, "frame_inferno", "frame", "Inferno frame", "inferno")
    )

    /** Prestige survivors keep everything they unlocked, plus the prestige frame. */
    fun isUnlocked(reward: LevelReward, level: Int, prestigeCount: Int): Boolean =
        prestigeCount > 0 || level >= reward.level
}

// ------------------------------------------------------------------ scratch cards

/** Scratch Card prize. Same shape as a wheel slice; `type` is "coins", "shield", "lucky_coin" or "none". */
data class ScratchPrize(
    val id: String = "",
    val label: String = "",
    val weight: Double = 1.0,
    val value: String = "",
    val type: String = TYPE_COINS,
    val color: String = "#4CE07F",
    val enabled: Boolean = true
) {
    val coinAmount: Long get() = value.trim().toLongOrNull()?.coerceAtLeast(0) ?: 0
    val isItem: Boolean get() = type == TYPE_SHIELD || type == TYPE_LUCKY

    fun toMap(): Map<String, Any> = mapOf(
        "label" to label, "weight" to weight, "value" to value,
        "type" to type, "color" to color, "enabled" to enabled
    )

    companion object {
        const val TYPE_COINS = "coins"
        const val TYPE_SHIELD = "shield"
        const val TYPE_LUCKY = "lucky_coin"
        const val TYPE_NONE = "none"

        val DEFAULT_POOL = listOf(
            ScratchPrize(label = "Dud", weight = 4.0, value = "0", type = TYPE_NONE, color = "#546E7A"),
            ScratchPrize(label = "Pocket Change", weight = 3.0, value = "15", type = TYPE_COINS, color = "#7CB342"),
            ScratchPrize(label = "Break Even", weight = 2.0, value = "25", type = TYPE_COINS, color = "#039BE5"),
            ScratchPrize(label = "Supply Cache", weight = 1.35, value = "60", type = TYPE_COINS, color = "#00897B"),
            ScratchPrize(label = "Free Shield", weight = 0.5, value = "", type = TYPE_SHIELD, color = "#4DD0E1"),
            ScratchPrize(label = "Lucky Coin", weight = 0.5, value = "", type = TYPE_LUCKY, color = "#FDD835"),
            ScratchPrize(label = "JACKPOT", weight = 0.25, value = "300", type = TYPE_COINS, color = "#FB8C00")
        )
    }
}

enum class ScratchOutcome { WIN, LOSS, ITEM }

data class ScratchResult(
    val prize: ScratchPrize,
    val outcome: ScratchOutcome,
    /** Coins won (item prizes that were already owned convert to their shop price). */
    val coinsWon: Long,
    val convertedItem: Boolean,
    val xp: XpResult?
)

// ------------------------------------------------------------------ death & daily results

data class DeathResult(
    val game: String,
    val outcome: com.deadfest.domain.DeathOutcome,
    val xp: XpResult?
)

data class DailyResult(
    val amount: Long,
    val newBalance: Long,
    val upgraded: Boolean,
    val survivalBonusXp: Long,
    val xp: XpResult?,
    /** False when today's rations were already claimed. */
    val claimed: Boolean = true
)
