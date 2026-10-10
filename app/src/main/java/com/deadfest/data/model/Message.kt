package com.deadfest.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Message(
    @get:PropertyName("id") @set:PropertyName("id") var id: String? = "",
    @get:PropertyName("senderUid") @set:PropertyName("senderUid") var senderUid: String? = "",
    @get:PropertyName("receiverUid") @set:PropertyName("receiverUid") var receiverUid: String? = "",
    @get:PropertyName("senderName") @set:PropertyName("senderName") var senderName: String? = "",
    @get:PropertyName("senderAvatar") @set:PropertyName("senderAvatar") var senderAvatar: String? = "",
    @get:PropertyName("text") @set:PropertyName("text") var text: String? = "",
    @get:PropertyName("timestamp") @set:PropertyName("timestamp") var timestamp: Timestamp? = Timestamp.now()
)
