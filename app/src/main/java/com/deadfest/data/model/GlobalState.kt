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
    /** Economy, shop and XP settings (parsed by hand from the global doc). */
    @get:Exclude @set:Exclude var economy: EconomyConfig = EconomyConfig()
) {
    /** Overload is live: switched on and, if it has a time limit, not yet expired. */
    fun overloadActive(now: Long = System.currentTimeMillis()): Boolean =
        sectorOverload && overloadedSector.isNotBlank() && (overloadEndsAt <= 0 || now < overloadEndsAt)
}
