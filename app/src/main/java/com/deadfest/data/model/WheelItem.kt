package com.deadfest.data.model

import com.google.firebase.firestore.Exclude

data class WheelItem(
    val id: String = "",
    val label: String = "",
    val value: String = "", // e.g. "100"
    val actionType: String = "give", // "give" or "take"
    val color: String = "#FF0000",
    val enabled: Boolean = true,
    val weight: Double = 1.0 // decimals allowed, e.g. 0.25 for ultra-rare loot
) {
    // @Exclude keeps these helpers out of Firestore when the admin saves wheel items.

    /** Coins this slice gives/takes, or null for a non-coin event slice. */
    @get:Exclude
    val coinAmount: Long? get() = value.trim().toLongOrNull()?.takeIf { it != 0L }

    @get:Exclude
    val isTake: Boolean get() = actionType.equals("take", ignoreCase = true)
}
