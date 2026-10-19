package com.deadfest.data.model

import com.deadfest.domain.EconomyConfig
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class GlobalState(
    @get:PropertyName("marketEnabled") @set:PropertyName("marketEnabled") var marketEnabled: Boolean = true,
    @get:PropertyName("wheelEnabled") @set:PropertyName("wheelEnabled") var wheelEnabled: Boolean = true,
    @get:PropertyName("reviveEnabled") @set:PropertyName("reviveEnabled") var reviveEnabled: Boolean = true,
    @get:PropertyName("multiplier") @set:PropertyName("multiplier") var multiplier: Double = 1.0,
    @get:PropertyName("sectorOverload") @set:PropertyName("sectorOverload") var sectorOverload: Boolean = false,
    @get:PropertyName("flashSaleActive") @set:PropertyName("flashSaleActive") var flashSaleActive: Boolean = false,
    @get:PropertyName("featuredVideoUrl") @set:PropertyName("featuredVideoUrl") var featuredVideoUrl: String = "",
    @get:PropertyName("showFeaturedVideo") @set:PropertyName("showFeaturedVideo") var showFeaturedVideo: Boolean = false,
    @get:PropertyName("lockdownActive") @set:PropertyName("lockdownActive") var lockdownActive: Boolean = false,
    @get:PropertyName("grandTotal") @set:PropertyName("grandTotal") var grandTotal: Long = 0,
    @get:PropertyName("onlineCount") @set:PropertyName("onlineCount") var onlineCount: Int = 0,
    @get:PropertyName("latestVersionName") @set:PropertyName("latestVersionName") var latestVersionName: String = "1.5.9",
    @get:PropertyName("latestVersionCode") @set:PropertyName("latestVersionCode") var latestVersionCode: Int = 1,
    @get:PropertyName("updateUrl") @set:PropertyName("updateUrl") var updateUrl: String = "",
    @get:PropertyName("updateNotes") @set:PropertyName("updateNotes") var updateNotes: String = "",
    @get:PropertyName("forceUpdate") @set:PropertyName("forceUpdate") var forceUpdate: Boolean = false,
    @get:PropertyName("games") @set:PropertyName("games") var games: List<String> = emptyList(),
    @get:PropertyName("lockedGames") @set:PropertyName("lockedGames") var lockedGames: List<String> = emptyList(),
    @get:PropertyName("wheelItems") var wheelItems: List<WheelItem> = emptyList(),
    @get:PropertyName("overloadedSector") @set:PropertyName("overloadedSector") var overloadedSector: String = "",
    /** Epoch millis when the overload ends; 0 = no time limit. */
    @get:PropertyName("overloadEndsAt") @set:PropertyName("overloadEndsAt") var overloadEndsAt: Long = 0,

    // ---- v2.0.7: Dead-Fest seasons ------------------------------------------
    /** Name of the season that is running right now, e.g. "Dead-Fest Season 1". */
    @get:PropertyName("currentSeasonName") @set:PropertyName("currentSeasonName") var currentSeasonName: String = "Dead-Fest Season 1",
    /** Master switch for the "Next Dead-Fest" panel. */
    @get:PropertyName("nextEventEnabled") @set:PropertyName("nextEventEnabled") var nextEventEnabled: Boolean = false,
    /** Name of the upcoming season, e.g. "Dead-Fest Season 2". */
    @get:PropertyName("nextEventName") @set:PropertyName("nextEventName") var nextEventName: String = "",
    /** Epoch millis when the next Dead-Fest begins; 0 = date still to be announced. */
    @get:PropertyName("nextEventStartsAt") @set:PropertyName("nextEventStartsAt") var nextEventStartsAt: Long = 0L,
    /** Games lined up for the next Dead-Fest, in play order. */
    @get:PropertyName("nextEventGames") @set:PropertyName("nextEventGames") var nextEventGames: List<String> = emptyList(),
    /** Optional admin blurb shown under the countdown. */
    @get:PropertyName("nextEventNotes") @set:PropertyName("nextEventNotes") var nextEventNotes: String = "",

    /** Economy, shop and XP settings (parsed by hand from the global doc). */
    @get:Exclude @set:Exclude var economy: EconomyConfig = EconomyConfig()
) {
    /** Overload is live: switched on and, if it has a time limit, not yet expired. */
    fun overloadActive(now: Long = System.currentTimeMillis()): Boolean =
        sectorOverload && overloadedSector.isNotBlank() && (overloadEndsAt <= 0 || now < overloadEndsAt)

    /** There is something worth showing in the Next Dead-Fest panel. */
    fun hasNextEvent(): Boolean =
        nextEventEnabled && (nextEventName.isNotBlank() || nextEventGames.isNotEmpty() || nextEventStartsAt > 0L)

    /** Epoch millis the next Dead-Fest starts, or null when no date is set. */
    fun nextEventStartOrNull(): Long? = nextEventStartsAt.takeIf { it > 0L }

    /** True when the scheduled start time has already passed. */
    fun nextEventStarted(now: Long = System.currentTimeMillis()): Boolean =
        nextEventStartsAt > 0L && now >= nextEventStartsAt

    /** The season name to show for the next event, falling back to a sensible label. */
    fun nextEventDisplayName(): String =
        nextEventName.takeIf { it.isNotBlank() } ?: "NEXT DEAD-FEST"
}
