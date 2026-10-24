package com.deadfest.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class PlayerData(
    @get:PropertyName("coins") @set:PropertyName("coins") var coins: Long = 0,
    @get:PropertyName("luckyCoin") @set:PropertyName("luckyCoin") var luckyCoin: Boolean = false,
    @get:PropertyName("avatar") @set:PropertyName("avatar") var avatar: String? = "💀",
    @get:PropertyName("color") @set:PropertyName("color") var color: String? = "#536DFE",
    @get:PropertyName("bio") @set:PropertyName("bio") var bio: String? = "",
    /** 0 = none, 1 = Bankruptcy Level 1, 2 = Bankruptcy Level 2 (see domain.Bankruptcy). */
    @get:PropertyName("bankruptcyLevel") @set:PropertyName("bankruptcyLevel") var bankruptcyLevel: Int = 0,
    @get:PropertyName("games") @set:PropertyName("games") var games: Map<String, GameStats> = emptyMap(),
    @get:PropertyName("achievements") @set:PropertyName("achievements") var achievements: List<String> = emptyList(),
    @get:PropertyName("lastLogin") @set:PropertyName("lastLogin") var lastLogin: Long = 0,
    @get:PropertyName("ownedThemes") @set:PropertyName("ownedThemes") var ownedThemes: List<String> = emptyList(),
    @get:PropertyName("ownedTitles") @set:PropertyName("ownedTitles") var ownedTitles: List<String> = emptyList(),
    /** Forced Spin Protocol: penalty spins still to be executed. */
    @get:PropertyName("pendingSpins") @set:PropertyName("pendingSpins") var pendingSpins: Int = 0,
    @get:PropertyName("saveMessages") @set:PropertyName("saveMessages") var saveMessages: Boolean = true,
    @get:PropertyName("selectedTheme") @set:PropertyName("selectedTheme") var selectedTheme: String? = "default",
    @get:PropertyName("shield") @set:PropertyName("shield") var shield: Boolean = false,
    @get:PropertyName("title") @set:PropertyName("title") var title: String? = "Survivor",
    @get:PropertyName("isOnline") @set:PropertyName("isOnline") var isOnline: Boolean = false,

    // ---- v2.0.5 ----
    @get:PropertyName("dailyUpgradeOwned") @set:PropertyName("dailyUpgradeOwned") var dailyUpgradeOwned: Boolean = false,
    @get:PropertyName("lastDailyClaim") @set:PropertyName("lastDailyClaim") var lastDailyClaim: Long = 0,
    @get:PropertyName("lastDeathAt") @set:PropertyName("lastDeathAt") var lastDeathAt: Long = 0,
    @get:PropertyName("insurance") @set:PropertyName("insurance") var insurance: Boolean = false,
    /** 0 = none, 1..4 = Wealth Insulation tier. */
    @get:PropertyName("insulationTier") @set:PropertyName("insulationTier") var insulationTier: Int = 0,
    @get:PropertyName("xp") @set:PropertyName("xp") var xp: Long = 0,
    @get:PropertyName("level") @set:PropertyName("level") var level: Int = 1,
    @get:PropertyName("prestigeCount") @set:PropertyName("prestigeCount") var prestigeCount: Int = 0,
    @get:PropertyName("avatarFrame") @set:PropertyName("avatarFrame") var avatarFrame: String? = "none",

    // ---- v2.1.0 ----
    /** Consecutive ration claims with no death in between (reset by a recorded death). */
    @get:PropertyName("survivalStreak") @set:PropertyName("survivalStreak") var survivalStreak: Int = 0,
    /** Has ever been bankrupt: needed for the "Bankrupt and Back" achievement. */
    @get:PropertyName("everBankrupt") @set:PropertyName("everBankrupt") var everBankrupt: Boolean = false,
    /** Personal stats: coin balance sampled once a day, keyed "yyyy-MM-dd" (last 60 days). */
    @get:PropertyName("coinHistory") @set:PropertyName("coinHistory") var coinHistory: Map<String, Long> = emptyMap(),
    /** Personal stats: recorded deaths per day, keyed "yyyy-MM-dd" (last 60 days). */
    @get:PropertyName("deathsByDay") @set:PropertyName("deathsByDay") var deathsByDay: Map<String, Long> = emptyMap(),

    // ---- v2.1.1: weekly challenges ----
    /** ISO week the weekly stats belong to, e.g. "2026-W43". */
    @get:PropertyName("weekKey") @set:PropertyName("weekKey") var weekKey: String = "",
    /** This week's counters per ChallengeMetric key. */
    @get:PropertyName("weeklyStats") @set:PropertyName("weeklyStats") var weeklyStats: Map<String, Long> = emptyMap(),
    /** Challenge ids already claimed this week. */
    @get:PropertyName("weeklyClaimed") @set:PropertyName("weeklyClaimed") var weeklyClaimed: List<String> = emptyList(),

    /**
     * `playerData.lastSeen` is written as a server timestamp by the heartbeat. It's parsed by
     * hand in the repository (older data may hold a number), so it's excluded from mapping.
     */
    @get:Exclude @set:Exclude var lastSeenMs: Long = 0
)
