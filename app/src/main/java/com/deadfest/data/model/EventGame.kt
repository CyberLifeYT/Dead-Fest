package com.deadfest.data.model

import com.google.firebase.Timestamp

data class EventGame(
    val id: String? = "",
    val title: String? = "",
    val type: String? = "",
    val status: String? = "pending",
    val timestamp: Timestamp? = Timestamp.now()
)

enum class GameType {
    FLAPPY,
    SHOOTER,
    WHEEL,
    OTHER
}
