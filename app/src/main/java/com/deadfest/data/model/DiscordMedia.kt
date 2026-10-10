package com.deadfest.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class DiscordMedia(
    @get:PropertyName("author") @set:PropertyName("author") var author: String = "",
    @get:PropertyName("caption") @set:PropertyName("caption") var caption: String = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Timestamp? = null,
    @get:PropertyName("tsRaw") @set:PropertyName("tsRaw") var tsRaw: Long = 0,
    @get:PropertyName("type") @set:PropertyName("type") var type: String = "",
    @get:PropertyName("url") @set:PropertyName("url") var url: String = ""
)
