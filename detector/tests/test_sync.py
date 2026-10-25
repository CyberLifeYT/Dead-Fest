"""Upload and status mirroring against an in-memory Firestore double."""

import time
from datetime import datetime, timezone

import pytest

from conftest import death_frame
from deadfest_detector import imaging
from deadfest_detector.firebase_rest import FirebaseError, NetworkError, decode, encode
from deadfest_detector.sync import Syncer


class FakeFirestore:
    def __init__(self):
        self.docs = {}
        self.offline = False
        self.commits = 0

    def name(self, path):
        return path

    def create_write(self, path, fields):
        return {"path": path, "fields": fields}

    def commit(self, writes):
        if self.offline:
            raise NetworkError("no route to host")
        if any(w["path"] in self.docs for w in writes):
            raise FirebaseError(409, "ALREADY_EXISTS", "Document already exists")
        for w in writes:                       # all or nothing, like a real commit
            self.docs[w["path"]] = dict(w["fields"])
        self.commits += 1
        return {}

    def get(self, path):
        if self.offline:
            raise NetworkError("no route to host")
        return self.docs.get(path)


def saved(store, **kw):
    args = dict(game_id="world_war_z", event_type="self", player_label="", confidence=0.91,
                detected_at=datetime.now(timezone.utc), image_png=imaging.png(death_frame(1)), shared=True)
    args.update(kw)
    return store.save_evidence(**args)


def test_upload_writes_metadata_blob_and_audit_atomically(store):
    fs = FakeFirestore()
    fs.docs["users/me"] = {"username": "DeadShot"}
    r = saved(store)
    report = Syncer(store, fs, "me").push_pending()
    assert report.uploaded == 1
    ev = fs.docs[f"deathEvidence/{r.event_id}"]
    assert ev["status"] == "pending" and ev["counted"] is False and ev["uid"] == "me"
    assert ev["subjectUid"] == "me" and ev["submitterName"] == "DeadShot"
    assert ev["gameId"] == "world_war_z" and ev["localRelativePath"] == r.local_relative_path
    assert len(ev["thumb"]) < 100 * 1024
    assert len(fs.docs[f"deathEvidenceBlobs/{r.event_id}"]["data"]) < 900 * 1024
    audits = [k for k in fs.docs if k.startswith(f"deathEvidence/{r.event_id}/audit/")]
    assert len(audits) == 1 and fs.docs[audits[0]]["action"] == "submitted"
    assert store.load(r.event_id).sync_state == "synced"


def test_teammate_deaths_wait_for_an_admin_to_link_a_player(store):
    fs = FakeFirestore()
    r = saved(store, event_type="teammate", player_label="Marco")
    Syncer(store, fs, "me").push_pending()
    assert fs.docs[f"deathEvidence/{r.event_id}"]["subjectUid"] == ""


def test_offline_keeps_everything_pending_and_retries_later(store):
    fs = FakeFirestore()
    fs.offline = True
    r = saved(store)
    report = Syncer(store, fs, "me").push_pending()
    assert report.failed == 1 and report.uploaded == 0
    rec = store.load(r.event_id)
    assert rec.sync_state == "pending" and "no route" in rec.sync_error and rec.sync_attempts == 1
    fs.offline = False
    assert Syncer(store, fs, "me").push_pending().uploaded == 1


def test_retry_after_a_lost_response_does_not_duplicate(store):
    fs = FakeFirestore()
    r = saved(store)
    s = Syncer(store, fs, "me")
    s.upload(r)                      # landed, but pretend the reply was lost
    assert s.upload(r) == "already"
    assert fs.commits == 1


def test_private_evidence_is_never_uploaded(store):
    fs = FakeFirestore()
    saved(store, shared=False)
    report = Syncer(store, fs, "me").push_pending()
    assert report.skipped_private == 1 and fs.commits == 0


def test_admin_rejection_moves_the_image_and_restore_moves_it_back(store):
    fs = FakeFirestore()
    r = saved(store)
    s = Syncer(store, fs, "me")
    s.push_pending()
    path = f"deathEvidence/{r.event_id}"
    fs.docs[path].update(status="rejected", rejectionReason="Not a death, just a cutscene", reviewerName="Alice")
    assert s.pull_statuses().status_changes == 1
    rec = store.load(r.event_id)
    assert rec.verification_status == "rejected" and rec.current_path.startswith("rejected/")
    assert store.resolve(rec.current_path).exists()
    assert rec.review["reason"] == "Not a death, just a cutscene"

    fs.docs[path].update(status="verified", counted=True)
    s.pull_statuses()
    rec = store.load(r.event_id)
    assert rec.verification_status == "verified" and rec.current_path == r.local_relative_path


def test_value_encoding_round_trips():
    value = {"a": 1, "b": 2.5, "c": "x", "d": True, "e": b"\x00\x01", "f": [1, "y"], "g": None}
    assert decode(encode(value)) == value
