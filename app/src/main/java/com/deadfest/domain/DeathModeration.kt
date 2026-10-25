package com.deadfest.domain

/**
 * Admin moderation of Xbox death evidence (v2.1.3), as pure logic.
 *
 * [DeathModeration.plan] turns a record's current state and an admin action into the exact
 * writes to make: the record's new fields, the change to each player's death count, and the audit
 * entry. The repository runs the plan inside a Firestore transaction against freshly read state,
 * and the security rules check the same invariants server-side.
 *
 * Two rules shape everything here:
 *  - **Exactly once.** A record is either counted (it is in a player's death total) or not.
 *    "Verified" means counted. Repeating an action re-plans against the current state and becomes
 *    a no-op, so a double tap or a retried request can never count a death twice.
 *  - **No coins.** A plan only ever touches `playerData.games.<sector>.deaths`. Verifying,
 *    rejecting, correcting, removing or restoring a death never deducts or pays coins, and never
 *    runs the casualty-tax pipeline; that stays the job of the existing death recording.
 */
object DeathEvidenceStatus {
    const val PENDING = "pending"
    const val VERIFIED = "verified"
    const val REJECTED = "rejected"
    const val REMOVED = "removed"
    val ALL = listOf(PENDING, VERIFIED, REJECTED, REMOVED)
    /** What survivors see in the gallery: removed records are soft-deleted. */
    val VISIBLE = listOf(PENDING, VERIFIED, REJECTED)
}

object DeathEventType {
    const val SELF = "self"
    const val TEAMMATE = "teammate"
    const val OTHER = "other"
    const val UNKNOWN = "unknown"
    val ALL = listOf(SELF, TEAMMATE, OTHER, UNKNOWN)
}

/** The parts of a record moderation reads. */
data class EvidenceState(
    val status: String,
    val counted: Boolean,
    val countedSector: String,
    val subjectUid: String,
    val submitterUid: String,
    val gameId: String,
    val eventType: String,
    val playerLabel: String,
    val updatedAt: Long
)

sealed interface ModerationAction {
    /** Accept the death and count it for [subjectUid] (default: the record's player) in [sector]. */
    data class Verify(val sector: String, val subjectUid: String? = null) : ModerationAction
    data class Reject(val reason: String) : ModerationAction
    /** Soft delete: out of the gallery and the count, evidence and history kept. */
    data class Remove(val reason: String) : ModerationAction
    /** Undo a reject/remove: back to counted if it had been verified, otherwise back to pending. */
    data object Restore : ModerationAction
    /** Fix fields. Null means "leave as is"; an empty [subjectUid] unlinks the player. */
    data class Correct(
        val gameId: String? = null,
        val eventType: String? = null,
        val playerLabel: String? = null,
        val subjectUid: String? = null,
        val sector: String? = null
    ) : ModerationAction
}

enum class ModerationProblem {
    ALREADY_VERIFIED, ALREADY_REJECTED, ALREADY_REMOVED, NOT_REMOVED_OR_REJECTED, NOT_PENDING,
    NEED_PLAYER, NEED_SECTOR, NEED_REASON, TOO_LONG, BAD_GAME, BAD_TYPE, NOTHING_TO_CHANGE,
    CANNOT_UNLINK_COUNTED, NOT_FOUND, PLAYER_MISSING
}

/** One player's death count moving by [delta] (+1 / -1) in [sector]. */
data class CountChange(val uid: String, val sector: String, val delta: Int)

sealed interface ModerationPlan {
    data class Apply(
        val fields: Map<String, Any>,
        val counts: List<CountChange>,
        val auditAction: String,
        val note: String,
        val changes: Map<String, List<Any>>,
        val at: Long
    ) : ModerationPlan

    data class NoOp(val problem: ModerationProblem) : ModerationPlan
    data class Invalid(val problem: ModerationProblem) : ModerationPlan
}

object DeathModeration {
    const val MAX_REASON = 200
    const val MAX_LABEL = 40
    const val MAX_SECTOR = 80

    fun plan(state: EvidenceState, action: ModerationAction, adminUid: String, adminName: String, now: Long): ModerationPlan {
        // The rules demand a strictly newer updatedAt, and the audit entry is keyed by it.
        val at = maxOf(now, state.updatedAt + 1)
        val review = mapOf<String, Any>(
            "reviewedBy" to adminUid,
            "reviewerName" to adminName.take(MAX_LABEL),
            "reviewedAt" to at,
            "updatedAt" to at
        )
        return when (action) {
            is ModerationAction.Verify -> verify(state, action, review, at)
            is ModerationAction.Reject -> takeOff(state, DeathEvidenceStatus.REJECTED, action.reason, "rejected", review, at)
            is ModerationAction.Remove -> takeOff(state, DeathEvidenceStatus.REMOVED, action.reason, "removed", review, at)
            ModerationAction.Restore -> restore(state, review, at)
            is ModerationAction.Correct -> correct(state, action, review, at)
        }
    }

    /**
     * The death values to write for a plan: each (player, sector) moved by its changes, never
     * below zero. [current] reads the committed value. Coins are not part of this, by design.
     */
    fun newDeathCounts(counts: List<CountChange>, current: (uid: String, sector: String) -> Long): Map<Pair<String, String>, Long> {
        val out = linkedMapOf<Pair<String, String>, Long>()
        counts.forEach { change ->
            val key = change.uid to change.sector
            out[key] = ((out[key] ?: current(change.uid, change.sector)) + change.delta).coerceAtLeast(0L)
        }
        return out
    }

    private fun verify(s: EvidenceState, a: ModerationAction.Verify, review: Map<String, Any>, at: Long): ModerationPlan {
        if (s.status == DeathEvidenceStatus.VERIFIED) return ModerationPlan.NoOp(ModerationProblem.ALREADY_VERIFIED)
        if (s.status != DeathEvidenceStatus.PENDING) return ModerationPlan.Invalid(ModerationProblem.NOT_PENDING)
        val subject = (a.subjectUid ?: s.subjectUid).trim()
        val sector = a.sector.trim()
        if (subject.isEmpty()) return ModerationPlan.Invalid(ModerationProblem.NEED_PLAYER)
        if (sector.isEmpty()) return ModerationPlan.Invalid(ModerationProblem.NEED_SECTOR)
        if (sector.length > MAX_SECTOR) return ModerationPlan.Invalid(ModerationProblem.TOO_LONG)
        return ModerationPlan.Apply(
            fields = review + mapOf(
                "status" to DeathEvidenceStatus.VERIFIED,
                "counted" to true,
                "countedSector" to sector,
                "subjectUid" to subject,
                "rejectionReason" to ""
            ),
            counts = listOf(CountChange(subject, sector, +1)),
            auditAction = "verified",
            note = sector,
            changes = changesOf(s, status = DeathEvidenceStatus.VERIFIED, subject = subject, sector = sector),
            at = at
        )
    }

    /** Reject or remove: out of the count if it was in it. The sector is kept so Restore can recount. */
    private fun takeOff(s: EvidenceState, target: String, reason: String, audit: String, review: Map<String, Any>, at: Long): ModerationPlan {
        if (s.status == target) {
            return ModerationPlan.NoOp(if (target == DeathEvidenceStatus.REJECTED) ModerationProblem.ALREADY_REJECTED else ModerationProblem.ALREADY_REMOVED)
        }
        if (target == DeathEvidenceStatus.REJECTED && s.status == DeathEvidenceStatus.REMOVED) {
            return ModerationPlan.Invalid(ModerationProblem.ALREADY_REMOVED)
        }
        val why = reason.trim()
        if (why.isEmpty()) return ModerationPlan.Invalid(ModerationProblem.NEED_REASON)
        if (why.length > MAX_REASON) return ModerationPlan.Invalid(ModerationProblem.TOO_LONG)
        return ModerationPlan.Apply(
            fields = review + mapOf("status" to target, "counted" to false, "rejectionReason" to why),
            counts = if (s.counted) listOf(CountChange(s.subjectUid, s.countedSector, -1)) else emptyList(),
            auditAction = audit,
            note = why,
            changes = changesOf(s, status = target),
            at = at
        )
    }

    private fun restore(s: EvidenceState, review: Map<String, Any>, at: Long): ModerationPlan {
        if (s.status != DeathEvidenceStatus.REJECTED && s.status != DeathEvidenceStatus.REMOVED) {
            return ModerationPlan.NoOp(ModerationProblem.NOT_REMOVED_OR_REJECTED)
        }
        // It had been verified (a sector and a player were chosen): put it back in the count.
        val recount = s.countedSector.isNotBlank() && s.subjectUid.isNotBlank()
        val status = if (recount) DeathEvidenceStatus.VERIFIED else DeathEvidenceStatus.PENDING
        return ModerationPlan.Apply(
            fields = review + mapOf("status" to status, "counted" to recount, "rejectionReason" to ""),
            counts = if (recount) listOf(CountChange(s.subjectUid, s.countedSector, +1)) else emptyList(),
            auditAction = "restored",
            note = status,
            changes = changesOf(s, status = status),
            at = at
        )
    }

    private fun correct(s: EvidenceState, a: ModerationAction.Correct, review: Map<String, Any>, at: Long): ModerationPlan {
        val game = a.gameId?.trim() ?: s.gameId
        val type = a.eventType?.trim() ?: s.eventType
        val label = a.playerLabel?.trim() ?: s.playerLabel
        var subject = a.subjectUid?.trim() ?: s.subjectUid
        // countedSector is only ever set by a verification, which is how Restore knows a record
        // was once counted, so an uncounted record keeps its (empty) sector.
        val sector = if (s.counted) a.sector?.trim() ?: s.countedSector else s.countedSector
        if (!DeathGames.isKnown(game)) return ModerationPlan.Invalid(ModerationProblem.BAD_GAME)
        if (type !in DeathEventType.ALL) return ModerationPlan.Invalid(ModerationProblem.BAD_TYPE)
        if (label.length > MAX_LABEL || sector.length > MAX_SECTOR) return ModerationPlan.Invalid(ModerationProblem.TOO_LONG)
        // Reclassified as the submitter's own death with nobody linked: it is theirs.
        if (type == DeathEventType.SELF && subject.isEmpty() && a.subjectUid == null) subject = s.submitterUid
        if (s.counted && subject.isEmpty()) return ModerationPlan.Invalid(ModerationProblem.CANNOT_UNLINK_COUNTED)
        if (s.counted && sector.isEmpty()) return ModerationPlan.Invalid(ModerationProblem.NEED_SECTOR)

        val fields = mutableMapOf<String, Any>()
        if (game != s.gameId) fields["gameId"] = game
        if (type != s.eventType) fields["eventType"] = type
        if (label != s.playerLabel) fields["playerLabel"] = label
        if (subject != s.subjectUid) fields["subjectUid"] = subject
        if (sector != s.countedSector) fields["countedSector"] = sector
        if (fields.isEmpty()) return ModerationPlan.NoOp(ModerationProblem.NOTHING_TO_CHANGE)

        // A counted death that changes player or sector moves: -1 where it was, +1 where it goes.
        val moves = s.counted && (subject != s.subjectUid || sector != s.countedSector)
        val counts = if (moves) {
            listOf(CountChange(s.subjectUid, s.countedSector, -1), CountChange(subject, sector, +1))
        } else emptyList()
        val changes = fields.mapValues { (k, v) ->
            listOf(
                when (k) {
                    "gameId" -> s.gameId
                    "eventType" -> s.eventType
                    "playerLabel" -> s.playerLabel
                    "subjectUid" -> s.subjectUid
                    else -> s.countedSector
                },
                v
            )
        }
        return ModerationPlan.Apply(
            fields = review + fields,
            counts = counts,
            auditAction = "corrected",
            note = fields.keys.joinToString(", "),
            changes = changes,
            at = at
        )
    }

    private fun changesOf(s: EvidenceState, status: String, subject: String = s.subjectUid, sector: String = s.countedSector): Map<String, List<Any>> {
        val out = mutableMapOf<String, List<Any>>("status" to listOf(s.status, status))
        if (subject != s.subjectUid) out["subjectUid"] = listOf(s.subjectUid, subject)
        if (sector != s.countedSector) out["countedSector"] = listOf(s.countedSector, sector)
        return out
    }
}
