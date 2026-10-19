package com.deadfest.domain

/**
 * Rules for a survivor's handle (v2.0.8).
 *
 * A handle is how the community sees someone: it shows on the player list, on the death feed and
 * in the game-request queue. It is deliberately stricter than a display name because it is a
 * public label other people read and search for — and because uniqueness is enforced against the
 * `usernames` registry in Firestore, so the name has to be one that can safely be a document key.
 */
object UsernameRules {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20

    /**
     * Must start with a letter; letters, digits and underscores only.
     *
     * Underscores are allowed (gamertags use them freely) but spaces and punctuation are not: the
     * handle is compared case-insensitively for uniqueness and shown next to other text, so
     * "xX_Slayer_Xx" has to be unambiguous.
     */
    private val PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]{2,19}$")

    /**
     * Names no survivor may take.
     *
     * Without these, anyone could call themselves "Admin" or "Dead-Fest" and the admin panel's
     * pill, the event log and the broadcast all use the handle — impersonation would be trivial.
     */
    private val RESERVED = setOf(
        "admin", "administrator", "deadfest", "dead_fest", "moderator", "mod",
        "support", "official", "root", "system", "null", "undefined"
    )

    /** An error message for an unacceptable handle, or null when it is fine. */
    fun errorFor(raw: String): String? {
        val name = raw.trim()
        if (name.isEmpty()) return "Enter a handle first."
        if (name.length < MIN_LENGTH) return "Handles need at least $MIN_LENGTH characters."
        if (name.length > MAX_LENGTH) return "Handles can be at most $MAX_LENGTH characters."
        if (!PATTERN.matches(name)) {
            return "Letters, numbers and underscores only — and it must start with a letter."
        }
        if (name.lowercase() in RESERVED) return "That handle is reserved."
        return null
    }

    /** Lowercase form used for the uniqueness registry and for case-insensitive comparisons. */
    fun keyOf(name: String): String = name.trim().lowercase()
}

/** Outcome of a handle change, so the UI can say exactly what happened. */
sealed interface UsernameChangeResult {
    /** The handle is now [name]. */
    data object Ok : UsernameChangeResult
    /** The requested handle is the one already set — nothing was written. */
    data object Same : UsernameChangeResult
    /** Another survivor already holds this handle (case-insensitively). */
    data object Taken : UsernameChangeResult
    /** The handle was rejected, with the reason to show. */
    data class Invalid(val reason: String) : UsernameChangeResult
}
