package com.deadfest.data.model

import com.google.firebase.firestore.PropertyName

data class User(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("displayName") @set:PropertyName("displayName") var displayName: String? = "",
    @get:PropertyName("email") @set:PropertyName("email") var email: String? = "",
    @get:PropertyName("admin") @set:PropertyName("admin") var admin: Boolean = false,
    @get:PropertyName("playerData") @set:PropertyName("playerData") var playerData: PlayerData = PlayerData()
)
