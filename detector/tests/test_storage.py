import errno
import os
import json
import string
from datetime import datetime, timezone
from pathlib import Path

import pytest

from deadfest_detector import games
from deadfest_detector.storage import (
    SYNC_PENDING, SYNC_PRIVATE, EvidenceStore, StorageUnavailable, _explain,
)

PNG = b"\x89PNG\r\n\x1a\nfake-image-bytes"
WHEN = datetime(2026, 10, 25, 1, 32, 10, tzinfo=timezone.utc)


def save(store, **kw):
    args = dict(game_id="dying_light_2", event_type="self", player_label="", detected_at=WHEN,
                confidence=0.93, image_png=PNG, shared=False)
    args.update(kw)
    return store.save_evidence(**args)


def test_layout_has_every_game_folder_plus_metadata_and_rejected(store):
    for g in games.GAMES:
        assert (store.root / g.folder).is_dir()
    assert (store.root / "metadata").is_dir()
    assert (store.root / "rejected").is_dir()


def test_folder_names_match_the_existing_pc_folders():
    assert [g.folder for g in games.GAMES] == [
        "DyingLight", "DyingLight2", "DyingLightTheBeast", "WorldWarZ",
        "DeadIsland", "DeadIslandRiptide", "DeadIsland2", "StateofDecay2",
    ]


def test_save_writes_image_and_record_with_all_fields(store):
    r = save(store, extra_frames={"pre": b"pre-bytes"}, shared=True)
    image = store.resolve(r.local_relative_path)
    assert image.read_bytes() == PNG
    assert r.local_relative_path.startswith("DyingLight2/")
    assert "_self-death_" in image.name and image.name.endswith(".png")
    assert image.name.startswith(WHEN.astimezone().strftime("%Y-%m-%d_%H-%M-%S"))
    assert r.event_id[:8] in image.name
    assert r.detected_at_ms == int(WHEN.timestamp() * 1000)
    assert r.sync_state == SYNC_PENDING
    assert store.resolve(r.extra_frames[0]).read_bytes() == b"pre-bytes"
    meta = json.loads((store.root / "metadata" / f"{r.event_id}.json").read_text())
    for key in ("event_id", "game_id", "detected_at_ms", "detected_at_local", "player_label", "event_type",
                "confidence", "local_relative_path", "verification_status", "created_at_ms", "updated_at_ms"):
        assert key in meta
    assert store.load(r.event_id) == r


def test_private_records_are_not_queued_for_upload(store):
    assert save(store).sync_state == SYNC_PRIVATE


def test_never_overwrites_an_existing_file(store):
    first = save(store, event_id="a" * 32)
    clash = store.resolve(first.local_relative_path)
    original = clash.read_bytes()
    # Same second, same id prefix: the name collides and must get a new suffix instead.
    second = save(store, event_id="a" * 8 + "b" * 24, image_png=b"\x89PNGother")
    assert clash.read_bytes() == original
    assert second.local_relative_path != first.local_relative_path
    assert store.resolve(second.local_relative_path).read_bytes() == b"\x89PNGother"


def test_metadata_for_an_existing_event_id_is_never_overwritten(store):
    save(store, event_id="c" * 32)
    with pytest.raises(FileExistsError):
        save(store, event_id="c" * 32)


@pytest.mark.parametrize("bad", ["../outside.png", "..\\outside.png", "C:\\Windows\\win.ini", "/etc/passwd",
                                 "\\\\server\\share\\x.png", "DyingLight2/../../x.png", ""])
def test_paths_cannot_escape_the_evidence_folder(store, bad):
    with pytest.raises(ValueError):
        store.resolve(bad)


def test_bad_event_ids_are_refused(store):
    with pytest.raises(ValueError):
        store.load("../../secrets")


def test_unknown_game_or_type_is_refused(store):
    with pytest.raises(ValueError):
        save(store, game_id="halo")
    with pytest.raises(ValueError):
        save(store, event_type="murder")


def _missing_drive() -> str:
    for letter in reversed(string.ascii_uppercase):
        if not Path(f"{letter}:\\").exists():
            return letter
    pytest.skip("every drive letter is in use")


@pytest.mark.skipif(os.name != "nt", reason="drive letters are a Windows concept")
def test_missing_drive_raises_and_writes_nowhere_else(tmp_path):
    store = EvidenceStore(f"{_missing_drive()}:\\XboxDeathDetector")
    with pytest.raises(StorageUnavailable, match="not available"):
        save(store)
    assert not any(tmp_path.iterdir())


def test_disk_full_and_permission_errors_are_explained(tmp_path):
    full = _explain(OSError(errno.ENOSPC, "No space"), tmp_path)
    assert "full" in str(full)
    denied = _explain(OSError(errno.EACCES, "Denied"), tmp_path)
    assert "permission" in str(denied)


def test_failed_record_write_leaves_no_orphan_images(store, monkeypatch):
    real = store._write_exclusive

    def failing(path, data):
        if path.parent.name == "metadata":
            raise StorageUnavailable("disk full")
        real(path, data)

    monkeypatch.setattr(store, "_write_exclusive", failing)
    with pytest.raises(StorageUnavailable):
        save(store, extra_frames={"pre": b"pre"})
    assert not any((store.root / "DyingLight2").iterdir())


def test_reject_moves_into_rejected_and_restore_moves_back(store):
    r = save(store, extra_frames={"pre": b"pre"})
    original = r.local_relative_path
    store.move_to_rejected(r)
    assert r.current_path == f"rejected/{original}"
    assert store.resolve(r.current_path).exists() and not store.resolve(original).exists()
    assert all(p.startswith("rejected/") for p in r.extra_frames)
    store.restore_from_rejected(r)
    assert r.current_path == original and store.resolve(original).exists()


def test_records_skip_damaged_files_and_sort_newest_first(store):
    older = save(store, detected_at=datetime(2026, 1, 1, tzinfo=timezone.utc))
    newer = save(store)
    (store.root / "metadata" / "garbage.json").write_text("{not json")
    assert [r.event_id for r in store.records()] == [newer.event_id, older.event_id]


def test_missing_image_is_reported_not_fatal(store):
    r = save(store)
    store.resolve(r.local_relative_path).unlink()
    assert store.image_exists(r) is False
