package com.deadfest.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class EventLog(
    @get:PropertyName("id") @set:PropertyName("id") var id: String? = "",
    @get:PropertyName("category") @set:PropertyName("category") var category: String? = "",
    @get:PropertyName("message") @set:PropertyName("message") var message: String? = "",
    @get:PropertyName("targetUid") @set:PropertyName("targetUid") var targetUid: String? = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Timestamp? = Timestamp.now(),
    @get:PropertyName("userUid") @set:PropertyName("userUid") var userUid: String? = null
) {
    fun isWheelSpin(): Boolean = category == "WHEEL_SPIN"
    fun isBlackMarket(): Boolean = category == "BLACK_MARKET"
    fun isWastelandAlert(): Boolean = category == "WASTELAND_ALERT" || category == "ADMIN_BROADCAST" || category == "FIRMWARE_UPDATE"
}

