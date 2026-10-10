package com.deadfest.data.model

/**
 * One image in the shared Images gallery.
 *
 * Stored in two Firestore documents so the gallery stays light on low-end phones:
 *  - `images/{id}`: this metadata plus a small JPEG thumbnail (~20 KB), streamed live to everyone;
 *  - `imageBlobs/{id}`: the full image (≤ ~700 KB), fetched only when someone opens it.
 */
data class SharedImage(
    val id: String,
    val uploaderUid: String,
    val uploaderName: String,
    val caption: String,
    val createdAt: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val thumb: ByteArray
) {
    // ByteArray breaks data-class equality; compare by identity fields instead.
    override fun equals(other: Any?): Boolean =
        other is SharedImage && other.id == id && other.caption == caption && other.createdAt == createdAt
    override fun hashCode(): Int = id.hashCode()
}
