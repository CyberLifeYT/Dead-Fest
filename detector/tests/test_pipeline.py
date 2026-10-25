"""End to end on recorded footage: video file -> detection -> evidence on disk."""

from datetime import datetime, timezone

import pytest

from conftest import template_from_death_frame, windows_ocr_available, write_video
from deadfest_detector import games, imaging
from deadfest_detector.detect import Analyzer, Template
from deadfest_detector.ocr import NullOcr
from deadfest_detector.pipeline import Pipeline
from deadfest_detector.sources import VideoFileSource
from deadfest_detector.state import DeathStateMachine, Rules
from deadfest_detector.storage import EvidenceStore, StorageUnavailable

TWO_DEATHS = [("play", 3), ("death", 4), ("play", 5), ("death", 4), ("play", 2)]


def make_pipeline(store, analyzer, **kw):
    return Pipeline(
        game_id="dying_light_2", store=store, analyzer=analyzer,
        machine=DeathStateMachine(Rules()), shared=kw.pop("shared", True),
        analysis_fps=4, **kw,
    )


def template_analyzer():
    img, ref = template_from_death_frame()
    return Analyzer(games.compile_profile(games.get("dying_light_2")), NullOcr(), [Template("self", img, ref, "self_1.png")])


def test_two_deaths_in_a_video_give_exactly_two_records(tmp_path, store):
    video = write_video(tmp_path / "session.avi", TWO_DEATHS)
    seen = []
    p = make_pipeline(store, template_analyzer(), on_record=seen.append)
    stats = p.run(VideoFileSource(str(video), realtime=False))
    assert stats.deaths == 2 and len(seen) == 2 and len(store.records()) == 2
    for r in seen:
        img = imaging.decode(store.resolve(r.local_relative_path).read_bytes())
        assert img.shape == (720, 1280, 3)
        assert r.local_relative_path.startswith("DyingLight2/")
        assert r.event_type == "self" and r.confidence >= 0.8
        assert r.extra_frames and r.extra_frames[0].endswith("_pre.png")
        assert r.sync_state == "pending"
    # The saved frame is from the death screen, not the gameplay before it.
    assert seen[0].detection["cues"]["template_score"] >= 0.8


def test_gameplay_only_gives_no_records(tmp_path, store):
    video = write_video(tmp_path / "clean.avi", [("play", 8)])
    stats = make_pipeline(store, template_analyzer()).run(VideoFileSource(str(video), realtime=False))
    assert stats.deaths == 0 and store.records() == []


def test_only_evidence_frames_are_kept(tmp_path, store):
    video = write_video(tmp_path / "s.avi", [("play", 3), ("death", 4), ("play", 3)])
    make_pipeline(store, template_analyzer(), save_post_frame=True, post_seconds=1.0).run(
        VideoFileSource(str(video), realtime=False))
    files = sorted(p.name for p in (store.root / "DyingLight2").iterdir())
    assert len(files) == 3   # the death frame, the frame before it, one frame after it
    assert sum(f.endswith("_pre.png") for f in files) == 1 and sum(f.endswith("_post.png") for f in files) == 1


@pytest.mark.skipif(not windows_ocr_available(), reason="Windows OCR not available")
def test_windows_ocr_alone_finds_the_deaths(tmp_path, store):
    from deadfest_detector.ocr import WindowsOcr

    video = write_video(tmp_path / "ocr.avi", TWO_DEATHS)
    a = Analyzer(games.compile_profile(games.get("dying_light_2")), WindowsOcr())
    stats = make_pipeline(store, a).run(VideoFileSource(str(video), realtime=False))
    assert stats.deaths == 2


def test_storage_outage_holds_the_death_and_saves_it_when_back(tmp_path, monkeypatch):
    store = EvidenceStore(tmp_path / "XboxDeathDetector")
    real_save = store.save_evidence
    state = {"down": True}

    def flaky(**kw):
        if state["down"]:
            raise StorageUnavailable("Drive D:\\ is not available")
        return real_save(**kw)

    monkeypatch.setattr(store, "save_evidence", flaky)
    video = write_video(tmp_path / "s.avi", [("play", 2), ("death", 4), ("play", 2)])
    p = make_pipeline(store, template_analyzer())
    stats = p.run(VideoFileSource(str(video), realtime=False))
    assert stats.deaths == 1 and p.held == 1 and "not available" in stats.storage_error
    assert not (tmp_path / "XboxDeathDetector" / "metadata").exists() or store.records() == []

    state["down"] = False
    saved = p.retry_now()
    assert len(saved) == 1 and p.held == 0 and len(store.records()) == 1


def test_detection_time_is_when_the_death_appeared(tmp_path, store):
    clock = datetime(2026, 10, 25, 12, 0, 0, tzinfo=timezone.utc)
    video = write_video(tmp_path / "s.avi", [("play", 2), ("death", 4)])
    p = make_pipeline(store, template_analyzer(), wallclock=lambda: clock)
    p.run(VideoFileSource(str(video), realtime=False))
    (r,) = store.records()
    # Confirmation needs ~0.5 s of footage; the record is backdated to the first death frame.
    assert int(clock.timestamp() * 1000) - r.detected_at_ms in range(250, 1000)
