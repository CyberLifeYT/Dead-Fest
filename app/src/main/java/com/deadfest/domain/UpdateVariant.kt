package com.deadfest.domain

/**
 * Which of the two published Dead-Fest builds this device asks to be updated to.
 *
 * Both builds come from the same source at the same version and carry the same signing key, so
 * either installs straight over the other: switching never costs a survivor their session, their
 * coins or their local settings. The only difference is packaging.
 *
 * [Standard] is the full build, debug metadata and all. It is the one to install when something is
 * misbehaving on a phone I cannot reach, because nothing has been rewritten underneath it.
 * [R8] is what R8 produces: unused code and unused resources stripped out, roughly a seventh of the
 * size and quicker to start, which is what actually matters on a low-end phone or a slow connection.
 *
 * The entry names are the values written to preferences. Renaming one would silently reset every
 * survivor's choice back to the default, so treat them as fixed.
 */
enum class UpdateVariant(
    /** Shown in Settings. Matches the file name published on the releases page. */
    val label: String,
    /** The trade-off in one line, so the choice is not a guess. */
    val blurb: String
) {
    /** Shrunk and optimised. Smallest download, fastest start. */
    R8(
        label = "Dead-Fest R8",
        blurb = "Smallest download and quickest to start. Best for most phones."
    ),

    /** Full build. Several times the size, but the safest thing to fall back to. */
    Standard(
        label = "Dead-Fest",
        blurb = "Full build. Much larger, but the one to try if the R8 build misbehaves."
    );

    companion object {
        /**
         * Reads a stored preference back, or null when nothing has been stored or the value is
         * unrecognised.
         *
         * Null deliberately means "never chose" rather than defaulting to a specific build: an
         * unchosen preference follows the build the app is actually running, so an update can never
         * quietly move someone onto a different kind of build than the one already in their hand.
         */
        fun fromStored(stored: String?): UpdateVariant? =
            UpdateVariant.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
    }
}
