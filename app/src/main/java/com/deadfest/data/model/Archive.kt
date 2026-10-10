package com.deadfest.data.model

import com.google.firebase.firestore.PropertyName

data class PlayerSnapshot(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("displayName") @set:PropertyName("displayName") var displayName: String? = "",
    @get:PropertyName("coins") @set:PropertyName("coins") var coins: Long = 0,
    @get:PropertyName("totalDeaths") @set:PropertyName("totalDeaths") var totalDeaths: Long = 0,
    @get:PropertyName("gameDeaths") @set:PropertyName("gameDeaths") var gameDeaths: Map<String, Long> = emptyMap()
)

data class Archive(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Long = 0L,
    @get:PropertyName("playerSnapshots") @set:PropertyName("playerSnapshots") var playerSnapshots: List<PlayerSnapshot> = emptyList()
)
