package com.deadfest.data.model

/**
 * A personal alert, stored at `users/{uid}/alerts/{id}`.
 *
 * Written by whoever caused it (the survivor who cursed you, the admin who removed your image),
 * read only by you. The app turns new ones into a phone notification while it's running and
 * keeps them in the Alerts inbox, so nothing is missed while the app was closed.
 */
data class AlertItem(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val fromUid: String,
    val fromName: String,
    val createdAt: Long,
    val read: Boolean
) {
    companion object {
        const val TYPE_CURSED = "cursed"
        const val TYPE_SHIELD_BLOCKED = "shield_blocked"
        const val TYPE_IMAGE_REMOVED = "image_removed"
        const val TYPE_ADMIN = "admin"
        const val TYPE_GAME_REQUEST = "game_request"
        const val TYPE_COMMENT = "comment"
    }
}

/**
 * One message in the group chat (`messages` collection, newest 100 shown).
 *
 * A photo is stored like a gallery image: a small thumbnail on the message itself and the full
 * image in `imageBlobs/{imageId}`, fetched only when someone opens it.
 */
data class ChatMessage(
    val id: String,
    val senderUid: String,
    val senderName: String,
    val senderAvatar: String,
    val senderColor: String,
    val text: String,
    val createdAt: Long,
    val imageId: String?,
    val thumb: ByteArray?,
    val imageWidth: Int,
    val imageHeight: Int
) {
    val hasImage: Boolean get() = imageId != null && thumb != null

    // ByteArray breaks data-class equality; messages are immutable, so the id is enough.
    override fun equals(other: Any?): Boolean = other is ChatMessage && other.id == id && other.text == text
    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val MAX_TEXT = 500
        const val MAX_MESSAGES = 100L
    }
}

/** A comment on a shared image (`images/{id}/comments`). */
data class ImageComment(
    val id: String,
    val uid: String,
    val name: String,
    val avatar: String,
    val text: String,
    val createdAt: Long
) {
    companion object {
        const val MAX_TEXT = 300
        /** The reactions anyone can leave on an image (must match firestore.rules). */
        val REACTIONS = listOf("🔥", "💀", "😂", "😮", "❤️", "👍")
    }
}
