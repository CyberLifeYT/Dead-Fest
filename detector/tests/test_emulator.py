"""
End to end against the Firestore emulator and the real firestore.rules:

    video -> detection -> D:\\XboxDeathDetector-style folder -> upload as a signed-in survivor
    -> another survivor can see it but not moderate it -> an admin verifies one death and
    rejects another (balance checked before and after) -> the detector pulls the decisions back
    and moves the rejected image into rejected\\.

Runs only under the emulator:
    cd tools/rules-tests
    npx firebase emulators:exec --only firestore --config ../../firebase.json ^
        "..\\..\\detector\\.venv\\Scripts\\python -m pytest ..\\..\\detector\\tests\\test_emulator.py -q"
"""

import base64
import json
import os
import time
import urllib.request

import pytest

from conftest import template_from_death_frame, write_video
from deadfest_detector import games
from deadfest_detector.detect import Analyzer, Template
from deadfest_detector.firebase_rest import FirebaseError, FirestoreClient, encode
from deadfest_detector.ocr import NullOcr
from deadfest_detector.pipeline import Pipeline
from deadfest_detector.sources import VideoFileSource
from deadfest_detector.state import DeathStateMachine, Rules
from deadfest_detector.sync import Syncer

pytestmark = pytest.mark.skipif("FIRESTORE_EMULATOR_HOST" not in os.environ, reason="needs the Firestore emulator")

PROJECT = "dead-fest-b4fc2"
OWNER, OTHER, ADMIN = "e2eOwner", "e2eOther", "e2eAdmin"
SECTOR = "Dying Light 2"


def b64(d: dict) -> str:
    return base64.urlsafe_b64encode(json.dumps(d).encode()).decode().rstrip("=")


def token_for(uid: str) -> str:
    """An unsigned ID token, which the emulator accepts (the same trick the rules test SDK uses)."""
    now = int(time.time())
    payload = {"iss": f"https://securetoken.google.com/{PROJECT}", "aud": PROJECT, "iat": now, "exp": now + 3600,
               "auth_time": now, "sub": uid, "user_id": uid, "firebase": {"sign_in_provider": "custom", "identities": {}}}
    return f"{b64({'alg': 'none', 'kid': 'fakekid', 'typ': 'JWT'})}.{b64(payload)}."


def client(uid: str | None) -> FirestoreClient:
    return FirestoreClient(PROJECT, (lambda: "owner") if uid is None else (lambda: token_for(uid)))


def seed():
    admin_db = client(None)   # "owner" bypasses the rules, for fixtures only
    for uid, coins, is_admin, games_map in (
        (OWNER, 300, False, {SECTOR: {"deaths": 2}}),
        (OTHER, 120, False, {}),
        (ADMIN, 999, True, {}),
    ):
        fields = {"uid": uid, "displayName": uid, "username": uid, "admin": is_admin,
                  "playerData": {"coins": coins, "games": games_map}}
        admin_db.commit([{"update": {"name": admin_db.name(f"users/{uid}"), "fields": {k: encode(v) for k, v in fields.items()}}}])


def player(uid: str) -> dict:
    d = client(None).get(f"users/{uid}")["playerData"]
    return {"coins": d["coins"], "deaths": d.get("games", {}).get(SECTOR, {}).get("deaths", 0)}


def moderation_writes(db: FirestoreClient, event_id: str, fields: dict, counts=(), action="verified") -> list:
    """The same three writes the app makes in FirebaseRepository.moderateEvidence."""
    at = int(time.time() * 1000) + 5
    fields = {"reviewedBy": ADMIN, "reviewerName": "Admin", "reviewedAt": at, "updatedAt": at, **fields}
    writes = [{
        "update": {"name": db.name(f"deathEvidence/{event_id}"), "fields": {k: encode(v) for k, v in fields.items()}},
        "updateMask": {"fieldPaths": list(fields)},
        "currentDocument": {"exists": True},
    }]
    for uid, sector, value in counts:
        writes.append({
            "update": {"name": db.name(f"users/{uid}"),
                       "fields": {"playerData": encode({"games": {sector: {"deaths": value}}})}},
            "updateMask": {"fieldPaths": [f"playerData.games.`{sector}`.deaths"]},
            "currentDocument": {"exists": True},
        })
    writes.append(db.create_write(f"deathEvidence/{event_id}/audit/{at}",
                                  {"action": action, "actor": ADMIN, "actorName": "Admin", "at": at, "note": "", "changes": {}}))
    return writes


def test_detect_upload_review_and_mirror_back(tmp_path, store):
    seed()
    img, ref = template_from_death_frame()
    analyzer = Analyzer(games.compile_profile(games.get("dying_light_2")), NullOcr(), [Template("self", img, ref, "self_1.png")])
    pipeline = Pipeline(game_id="dying_light_2", store=store, analyzer=analyzer,
                        machine=DeathStateMachine(Rules()), shared=True, analysis_fps=4)
    video = write_video(tmp_path / "session.avi", [("play", 2), ("death", 4), ("play", 5), ("death", 4), ("play", 1)])
    pipeline.run(VideoFileSource(str(video), realtime=False))
    first, second = sorted(store.records(), key=lambda r: r.detected_at_ms)

    # Upload as the survivor, through the real rules.
    syncer = Syncer(store, client(OWNER), OWNER)
    report = syncer.push_pending()
    assert report.uploaded == 2 and report.failed == 0, report.last_error
    assert syncer.upload(store.load(first.event_id)) == "already"   # a retry never duplicates

    # Every survivor can see the shared evidence...
    other = client(OTHER)
    ev = other.get(f"deathEvidence/{first.event_id}")
    assert ev["status"] == "pending" and ev["uid"] == OWNER and ev["subjectUid"] == OWNER
    assert other.get(f"deathEvidenceBlobs/{first.event_id}")["data"][:2] == b"\xff\xd8"   # a JPEG
    # ...but cannot moderate it.
    before = player(OWNER)
    with pytest.raises(FirebaseError) as denied:
        other.commit(moderation_writes(other, first.event_id,
                                       {"status": "verified", "counted": True, "countedSector": SECTOR, "reviewedBy": OTHER},
                                       counts=[(OWNER, SECTOR, before["deaths"] + 1)]))
    assert denied.value.status == 403
    assert player(OWNER) == before

    # The admin verifies the first death: counted once, coins untouched.
    admin = client(ADMIN)
    admin.commit(moderation_writes(admin, first.event_id, {"status": "verified", "counted": True, "countedSector": SECTOR},
                                   counts=[(OWNER, SECTOR, before["deaths"] + 1)]))
    after = player(OWNER)
    assert after == {"coins": before["coins"], "deaths": before["deaths"] + 1}

    # ...and rejects the second: no count change, coins untouched.
    admin.commit(moderation_writes(admin, second.event_id, {"status": "rejected", "rejectionReason": "Same death, seen twice"},
                                   action="rejected"))
    assert player(OWNER) == after

    # The detector mirrors the decisions back to the PC.
    pulled = syncer.pull_statuses()
    assert pulled.status_changes == 2
    verified, rejected = store.load(first.event_id), store.load(second.event_id)
    assert verified.verification_status == "verified" and verified.review["counted"] is True
    assert store.resolve(verified.current_path).exists() and not verified.current_path.startswith("rejected/")
    assert rejected.verification_status == "rejected" and rejected.current_path.startswith("rejected/")
    assert store.resolve(rejected.current_path).exists()
    assert rejected.review["reason"] == "Same death, seen twice"


def test_a_stranger_cannot_read_the_evidence():
    req = urllib.request.Request(
        f"http://{os.environ['FIRESTORE_EMULATOR_HOST']}/v1/projects/{PROJECT}/databases/(default)/documents/deathEvidence")
    with pytest.raises(urllib.error.HTTPError) as e:
        urllib.request.urlopen(req)
    assert e.value.code == 403
