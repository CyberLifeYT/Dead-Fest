package com.deadfest.data.model

import com.deadfest.domain.DeathEvidenceStatus
import com.deadfest.domain.EvidenceState

/**
 * One piece of Xbox death evidence (v2.1.3), captured by the Dead-Fest Xbox Death Detector on a PC.
 *
 * Stored like gallery images, so the gallery stays light on low-end phones:
 *  - `deathEvidence/{id}`: this metadata plus a small JPEG thumbnail;
 *  - `deathEvidenceBlobs/{id}`: the full screenshot, fetched only when someone opens it;
 *  - `deathEvidence/{id}/audit/{ms}`: the append-only history.
 *
 * [localRelativePath] is where the original lives on the submitter's PC, inside
 * D:\XboxDeathDetector. It is shown for reference only; the app never reads local files.
 */
data class DeathEvidence(
    val id: String,
    val gameId: String,
    val uid: String,
    val submitterName: String,
    val playerLabel: String,
    val eventType: String,
    val detectedAt: Long,
    val confidence: Double,
    val status: String,
    val localRelativePath: String,
    val hasImage: Boolean,
    val thumb: ByteArray?,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val counted: Boolean,
    val countedSector: String,
    val subjectUid: String,
    val reviewedBy: String,
    val reviewerName: String,
    val reviewedAt: Long,
    val rejectionReason: String
) {
    val isPending get() = status == DeathEvidenceStatus.PENDING
    val isVerified get() = status == DeathEvidenceStatus.VERIFIED
    val isRejected get() = status == DeathEvidenceStatus.REJECTED
    val isRemoved get() = status == DeathEvidenceStatus.REMOVED

    fun toState() = EvidenceState(
        status = status,
        counted = counted,
        countedSector = countedSector,
        subjectUid = subjectUid,
        submitterUid = uid,
        gameId = gameId,
        eventType = eventType,
        playerLabel = playerLabel,
        updatedAt = updatedAt
    )

    // ByteArray breaks data-class equality; a record is the same record at the same version.
    override fun equals(other: Any?): Boolean = other is DeathEvidence && other.id == id && other.updatedAt == updatedAt
    override fun hashCode(): Int = id.hashCode()
}

/** One line of a record's history. */
data class DeathEvidenceAudit(
    val id: String,
    val action: String,
    val actor: String,
    val actorName: String,
    val at: Long,
    val note: String
)
