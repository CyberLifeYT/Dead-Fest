package com.deadfest.data.model

import com.google.firebase.firestore.PropertyName

data class GameStats(
    @get:PropertyName("deaths") @set:PropertyName("deaths") var deaths: Long = 0,
    @get:PropertyName("kd") @set:PropertyName("kd") var kd: Double = 0.0,
    @get:PropertyName("kills") @set:PropertyName("kills") var kills: Long = 0,
    @get:PropertyName("losses") @set:PropertyName("losses") var losses: Long = 0
)
