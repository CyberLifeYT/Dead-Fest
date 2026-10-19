package com.deadfest.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * A game the community has asked to see as a sector (v2.0.8).
 *
 * Lifecycle: a survivor submits a request ([STATUS_PENDING]); an admin approves it
 * ([STATUS_APPROVED]) and it becomes a candidate sector, or removes it from the queue. Only admins
 * approve or remove — the request screen is read-only for everyone else.
 */
data class GameRequest(
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    /** Optional box art, fetched from the game database when the API is reachable. */
    @get:PropertyName("coverUrl") @set:PropertyName("coverUrl") var coverUrl: String = "",
    @get:PropertyName("requestedBy") @set:PropertyName("requestedBy") var requestedBy: String = "",
    /** Handle of whoever asked, stored so the queue is readable without a second lookup. */
    @get:PropertyName("requestedByName") @set:PropertyName("requestedByName") var requestedByName: String = "",
    @get:PropertyName("status") @set:PropertyName("status") var status: String = STATUS_PENDING,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = 0L,
    /** Handle of the admin who approved it, for the record. */
    @get:PropertyName("decidedByName") @set:PropertyName("decidedByName") var decidedByName: String = "",
    /** Firestore document id; never part of the document itself. */
    @get:Exclude @set:Exclude var id: String = ""
) {
    val isApproved: Boolean
        get() = status == STATUS_APPROVED

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
    }
}

/** Outcome of submitting a game request. */
sealed interface GameRequestResult {
    data object Ok : GameRequestResult
    data class Invalid(val reason: String) : GameRequestResult
}
