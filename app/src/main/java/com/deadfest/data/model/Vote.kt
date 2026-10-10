package com.deadfest.data.model

import com.google.firebase.Timestamp

data class Vote(
    val id: String? = "",
    val title: String? = "",
    val options: List<String> = emptyList(),
    val results: Map<String, Int> = emptyMap(),
    val userVotes: Map<String, Int> = emptyMap(),
    val active: Boolean = true,
    val timestamp: Timestamp? = Timestamp.now()
)
