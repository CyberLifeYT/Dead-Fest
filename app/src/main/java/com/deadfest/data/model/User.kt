package com.deadfest.data.model

import com.google.firebase.firestore.PropertyName

data class User(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("displayName") @set:PropertyName("displayName") var displayName: String? = "",
    @get:PropertyName("email") @set:PropertyName("email") var email: String? = "",
    @get:PropertyName("admin") @set:PropertyName("admin") var admin: Boolean = false,
    @get:PropertyName("playerData") @set:PropertyName("playerData") var playerData: PlayerData = PlayerData(),
    /**
     * The survivor's public handle (v2.0.8). Null/blank on every account created before this
     * field existed — which is exactly why [handle] falls back rather than assuming it is set.
     *
     * Uniqueness is enforced by the `usernames` registry collection: `usernames/{lowercase}`
     * points at the uid that holds it, so two survivors can never end up with the same handle
     * whatever casing they typed.
     */
    @get:PropertyName("username") @set:PropertyName("username") var username: String? = ""
) {
    /**
     * The name every screen should show for this survivor.
     *
     * Chosen handle first, then the auth display name, then a stable placeholder derived from the
     * uid so a row is never blank. Computed rather than stored, so it needs no migration and is
     * picked up by every existing Firestore listener the moment a handle is saved — which is what
     * makes a rename appear in real time.
     */
    val handle: String
        get() = username?.takeIf { it.isNotBlank() }
            ?: displayName?.takeIf { it.isNotBlank() }
            ?: "SURVIVOR-${uid.take(4).uppercase()}"
}
