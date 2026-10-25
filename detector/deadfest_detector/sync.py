"""
Sharing evidence with the group, and mirroring the admins' decisions back to this PC.

Upload (only for records whose sync_state is "pending", i.e. sharing is switched on):
  one atomic Firestore commit of
    deathEvidence/{eventId}             metadata + small thumbnail, status "pending"
    deathEvidenceBlobs/{eventId}        the full image (JPEG, < 900 KB)
    deathEvidence/{eventId}/audit/{ms}  the "submitted" audit entry
  Each write requires the document not to exist, so retrying after a lost response can never
  create a duplicate: an "already exists" answer just means the earlier attempt landed.

Status pull: an admin's verify / reject / remove in the app is copied into the local record; a
rejected or removed image is moved to D:\\XboxDeathDetector\\rejected\\ (kept, not deleted) and
moved back if the admin restores it.

Network trouble leaves the record pending for the next attempt. Nothing is lost.
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass

from . import __version__, imaging
from .firebase_rest import FirebaseError, FirestoreClient, NetworkError
from .storage import SYNC_PENDING, SYNC_SYNCED, EvidenceRecord, EvidenceStore, StorageUnavailable

log = logging.getLogger("deadfest.detector")

COLLECTION = "deathEvidence"
BLOBS = "deathEvidenceBlobs"


@dataclass
class SyncReport:
    uploaded: int = 0
    already_there: int = 0
    failed: int = 0
    skipped_private: int = 0
    status_changes: int = 0
    counted: int = 0
    last_error: str = ""
    messages: list = None

    def __post_init__(self):
        if self.messages is None:
            self.messages = []


class Syncer:
    def __init__(self, store: EvidenceStore, firestore: FirestoreClient, uid: str, counter=None):
        self.store = store
        self.fs = firestore
        self.uid = uid
        # Optional counting.AutoCounter: verify (count) each record right after it uploads.
        self.counter = counter
        self._display_name: str | None = None

    def display_name(self) -> str:
        if self._display_name is None:
            name = ""
            try:
                user = self.fs.get(f"users/{self.uid}") or {}
                name = (user.get("username") or user.get("displayName") or "").strip()
            except (FirebaseError, NetworkError):
                pass
            self._display_name = (name or f"SURVIVOR-{self.uid[:4].upper()}")[:40]
        return self._display_name

    # ------------------------------------------------------------------ upload

    def build_writes(self, record: EvidenceRecord, now_ms: int) -> list[dict]:
        image = imaging.decode(self.store.resolve(record.local_relative_path).read_bytes())
        thumb = imaging.thumbnail(image)
        full, width, height = imaging.full(image)
        evidence = {
            "eventId": record.event_id,
            "gameId": record.game_id,
            "uid": self.uid,
            "submitterName": self.display_name(),
            "playerLabel": record.player_label[:40],
            "eventType": record.event_type,
            "detectedAt": int(record.detected_at_ms),
            "confidence": float(record.confidence),
            "status": "pending",
            "localRelativePath": record.local_relative_path,
            "hasImage": True,
            "thumb": thumb,
            "width": int(width),
            "height": int(height),
            "sizeBytes": len(full),
            "createdAt": now_ms,
            "updatedAt": now_ms,
            "counted": False,
            "countedSector": "",
            # Your own death is about you; anyone else's waits for an admin to link a player.
            "subjectUid": self.uid if record.event_type == "self" else "",
            "reviewedBy": "",
            "reviewerName": "",
            "reviewedAt": 0,
            "rejectionReason": "",
            "source": "xbox-detector",
            "detectorVersion": __version__,
        }
        audit = {
            "action": "submitted",
            "actor": self.uid,
            "actorName": self.display_name(),
            "at": now_ms,
            "note": f"Detected on Xbox ({record.event_type}, confidence {record.confidence:.2f})",
            "changes": {},
        }
        return [
            self.fs.create_write(f"{COLLECTION}/{record.event_id}", evidence),
            self.fs.create_write(f"{BLOBS}/{record.event_id}", {"data": full, "uid": self.uid}),
            self.fs.create_write(f"{COLLECTION}/{record.event_id}/audit/{now_ms}", audit),
        ]

    def upload(self, record: EvidenceRecord) -> str:
        """Returns "uploaded", "already" or raises (NetworkError / FirebaseError / OSError)."""
        try:
            self.fs.commit(self.build_writes(record, int(time.time() * 1000)))
            return "uploaded"
        except FirebaseError:
            # A retry of an upload that already landed is refused: by the precondition (409) or,
            # because the rules are checked first, as permission denied (403). Either way, if the
            # record is there in our name, the earlier attempt succeeded.
            remote = self.fs.get(f"{COLLECTION}/{record.event_id}")
            if remote and remote.get("uid") == self.uid:
                return "already"
            raise

    def push_pending(self) -> SyncReport:
        report = SyncReport()
        for record in self.store.records():
            if record.sync_state != SYNC_PENDING:
                if record.sync_state != SYNC_SYNCED:
                    report.skipped_private += 1
                continue
            try:
                outcome = self.upload(record)
            except (NetworkError, FirebaseError, OSError, ValueError) as e:
                record.sync_attempts += 1
                record.sync_error = str(e)[:300]
                report.failed += 1
                report.last_error = record.sync_error
                self._save_quietly(record)
                if isinstance(e, NetworkError):
                    break    # offline: stop for now, every record stays pending
                continue
            record.sync_state = SYNC_SYNCED
            record.sync_error = ""
            record.sync_attempts += 1
            self._save_quietly(record)
            if outcome == "uploaded":
                report.uploaded += 1
            else:
                report.already_there += 1
            self._count(record, report)
        if self.counter:
            # Records that uploaded earlier but couldn't be counted then (offline, player unknown).
            for record in self.store.records():
                if (record.sync_state == SYNC_SYNCED and record.verification_status == "pending"
                        and (not record.review.get("count_tried") or record.review.get("count_retry"))):
                    self._count(record, report)
        return report

    def _count(self, record: EvidenceRecord, report: SyncReport) -> None:
        if not self.counter or record.verification_status != "pending":
            return
        result = self.counter.count(record)
        record.review = {**record.review, "count_tried": True, "count_message": result.message,
                         "count_retry": bool(getattr(result, "retry", False))}
        if result.counted:
            record.verification_status = "verified"
            record.review.update({"counted": True, "sector": result.sector, "subject_uid": result.subject_uid})
            report.counted += 1
        report.messages.append(f"{record.event_id[:8]}: {result.message}")
        self._save_quietly(record)

    # ------------------------------------------------------------------ status pull

    def pull_statuses(self) -> SyncReport:
        report = SyncReport()
        for record in self.store.records():
            if record.sync_state != SYNC_SYNCED:
                continue
            try:
                remote = self.fs.get(f"{COLLECTION}/{record.event_id}")
            except (NetworkError, FirebaseError) as e:
                report.failed += 1
                report.last_error = str(e)
                if isinstance(e, NetworkError):
                    break
                continue
            status = remote.get("status", "pending") if remote else "deleted"
            if status == record.verification_status:
                continue
            record.verification_status = status
            if remote:
                record.review = {**record.review,
                    "reviewed_by": remote.get("reviewerName", ""),
                    "reviewed_at_ms": remote.get("reviewedAt", 0),
                    "reason": remote.get("rejectionReason", ""),
                    "counted": remote.get("counted", False),
                }
            try:
                if status in ("rejected", "removed", "deleted"):
                    self.store.move_to_rejected(record)
                else:
                    self.store.restore_from_rejected(record)
            except (OSError, StorageUnavailable) as e:
                log.warning("Could not move the image for %s: %s", record.event_id, e)
            self._save_quietly(record)
            report.status_changes += 1
        return report

    def _save_quietly(self, record: EvidenceRecord) -> None:
        try:
            self.store.update(record)
        except (OSError, StorageUnavailable) as e:
            log.error("Could not update the local record %s: %s", record.event_id, e)
