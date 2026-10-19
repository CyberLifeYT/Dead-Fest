package com.deadfest.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * One survivor frozen at the moment a season was archived.
 *
 * Fields added in v2.0.7 (level, xp, title, ...) default to neutral values, so archives
 * written by older builds still load: they simply report the extra stats as "not recorded".
 */
data class PlayerSnapshot(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("displayName") @set:PropertyName("displayName") var displayName: String? = "",
    @get:PropertyName("coins") @set:PropertyName("coins") var coins: Long = 0,
    @get:PropertyName("totalDeaths") @set:PropertyName("totalDeaths") var totalDeaths: Long = 0,
    @get:PropertyName("gameDeaths") @set:PropertyName("gameDeaths") var gameDeaths: Map<String, Long> = emptyMap(),

    // ---- v2.0.7: fuller season snapshot ----
    @get:PropertyName("level") @set:PropertyName("level") var level: Int = 0,
    @get:PropertyName("xp") @set:PropertyName("xp") var xp: Long = 0,
    @get:PropertyName("prestigeCount") @set:PropertyName("prestigeCount") var prestigeCount: Int = 0,
    @get:PropertyName("title") @set:PropertyName("title") var title: String? = "",
    @get:PropertyName("insulationTier") @set:PropertyName("insulationTier") var insulationTier: Int = 0,
    @get:PropertyName("bankruptcyLevel") @set:PropertyName("bankruptcyLevel") var bankruptcyLevel: Int = 0,
    @get:PropertyName("pendingSpins") @set:PropertyName("pendingSpins") var pendingSpins: Int = 0,
    @get:PropertyName("avatar") @set:PropertyName("avatar") var avatar: String? = "💀",
    @get:PropertyName("avatarFrame") @set:PropertyName("avatarFrame") var avatarFrame: String? = "none",
    @get:PropertyName("color") @set:PropertyName("color") var color: String? = "#536DFE"
) {
    /** True when this snapshot carries the richer v2.0.7 stat set. */
    @get:Exclude val hasDetailedStats: Boolean get() = level > 0
}

data class Archive(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Long = 0L,
    @get:PropertyName("playerSnapshots") @set:PropertyName("playerSnapshots") var playerSnapshots: List<PlayerSnapshot> = emptyList(),

    // ---- v2.0.7: season summary ----
    /** Snapshot format. 1 = v2.0.6 and older (coins + deaths only), 2 = v2.0.7+. */
    @get:PropertyName("schemaVersion") @set:PropertyName("schemaVersion") var schemaVersion: Int = 1,
    /** The season's opening time, when known. */
    @get:PropertyName("startedAt") @set:PropertyName("startedAt") var startedAt: Long = 0L,
    /** Sector roster at the time the season closed, in display order. */
    @get:PropertyName("sectorRoster") @set:PropertyName("sectorRoster") var sectorRoster: List<String> = emptyList(),
    /** Global eliminations counter at the time the season closed. */
    @get:PropertyName("grandTotalAtEnd") @set:PropertyName("grandTotalAtEnd") var grandTotalAtEnd: Long = 0L,
    /** Admin blurb describing the season. */
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = ""
) {
    // Derived values. Excluded from Firestore mapping so they are never written to the
    // document and never expected as fields when a season is read back.
    @get:Exclude val survivorCount: Int get() = playerSnapshots.size
    @get:Exclude val totalCoins: Long get() = playerSnapshots.sumOf { it.coins }
    @get:Exclude val totalDeaths: Long get() = playerSnapshots.sumOf { it.totalDeaths }
    @get:Exclude val champion: PlayerSnapshot? get() = playerSnapshots.maxByOrNull { it.coins }

    /** Combined deaths for every sector across all survivors, biggest first. */
    fun deathsPerSector(): List<Pair<String, Long>> =
        playerSnapshots
            .flatMap { it.gameDeaths.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, v) -> v.sum() }
            .toList()
            .sortedByDescending { it.second }
}