"""Deaths read from HUD health bars, and names next to them."""

import cv2
import numpy as np
import pytest

from conftest import windows_ocr_available
from deadfest_detector.hud import BarSpec, HudConfig, HudTracker, SlotSpec, bar_fill, crop, reference_colour
from deadfest_detector.ocr import OcrProvider

W, H = 1280, 720
SELF_BAR = (0.05, 0.90, 0.25, 0.02)
MATE_BAR = (0.80, 0.14, 0.15, 0.015)
MATE_NAME = (0.80, 0.08, 0.15, 0.05)


def hud_frame(self_fill=1.0, mate_fill=1.0, mate_name="ShadowOnyx11", seed=0, mate_colour=(80, 200, 60)):
    rng = np.random.default_rng(seed)
    frame = cv2.resize(rng.integers(30, 120, size=(H // 8, W // 8, 3), dtype=np.uint8), (W, H), interpolation=cv2.INTER_CUBIC)

    def bar(box, fill, colour):
        x, y, w, h = int(box[0] * W), int(box[1] * H), int(box[2] * W), int(box[3] * H)
        cv2.rectangle(frame, (x, y), (x + w, y + h), (25, 25, 25), -1)
        if fill > 0:
            cv2.rectangle(frame, (x, y), (x + int(w * fill), y + h), colour, -1)

    bar(SELF_BAR, self_fill, (40, 40, 220))
    bar(MATE_BAR, mate_fill, mate_colour)
    nx, ny, nw, nh = int(MATE_NAME[0] * W), int(MATE_NAME[1] * H), int(MATE_NAME[2] * W), int(MATE_NAME[3] * H)
    cv2.rectangle(frame, (nx, ny), (nx + nw, ny + nh), (15, 15, 15), -1)
    if mate_name:
        cv2.putText(frame, mate_name, (nx + 4, ny + nh - 9), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (240, 240, 240), 1, cv2.LINE_AA)
    return frame


class NameOcr(OcrProvider):
    name = "fake"

    def __init__(self, text):
        self.text = text

    def read(self, frame):
        return self.text


def tracker(hold=3.0, ocr=None, resolver=None):
    full = hud_frame()
    cfg = HudConfig(
        self_bar=BarSpec(SELF_BAR, reference_colour(crop(full, SELF_BAR))),
        teammates=[SlotSpec(BarSpec(MATE_BAR, reference_colour(crop(full, MATE_BAR))), MATE_NAME)],
        hold_seconds=hold,
    )
    return HudTracker(cfg, ocr or NameOcr("ShadowOnyx11"), resolver)


@pytest.mark.parametrize("fill", [1.0, 0.75, 0.5, 0.2, 0.0])
def test_bar_fill_tracks_the_bar(fill):
    full = hud_frame()
    ref = reference_colour(crop(full, MATE_BAR))
    measured = bar_fill(crop(hud_frame(mate_fill=fill), MATE_BAR), ref)
    assert abs(measured - fill) < 0.06


def test_white_bars_work_too():
    white = (235, 235, 235)
    ref = reference_colour(crop(hud_frame(mate_colour=white), MATE_BAR))
    assert ref is not None and ref[1] < 50
    assert bar_fill(crop(hud_frame(mate_fill=0.5, mate_colour=white), MATE_BAR), ref) == pytest.approx(0.5, abs=0.06)


def feed(tr, frames):
    out = []
    for t, frame in frames:
        out += tr.update(t, frame)
    return out


def test_a_teammate_whose_bar_empties_and_stays_empty_dies_with_their_name():
    tr = tracker()
    frames = [(i * 0.25, hud_frame(mate_fill=1.0 - i * 0.1)) for i in range(8)]
    frames += [(2.0 + i * 0.25, hud_frame(mate_fill=0.0)) for i in range(16)]
    cands = feed(tr, frames)
    assert cands and all(c.event_type == "teammate" for c in cands)
    assert cands[0].player_label == "ShadowOnyx11"
    first_t = next(t for t, f in frames if tr.update(t, f))   # sanity: deaths only after the hold
    assert first_t >= 2.0 + 3.0 - 0.01


def test_a_short_drop_to_zero_is_not_a_death():
    tr = tracker(hold=3.0)
    frames = [(i * 0.25, hud_frame()) for i in range(4)]
    frames += [(1 + i * 0.25, hud_frame(mate_fill=0.0)) for i in range(6)]       # 1.5 s at zero (downed, revived)
    frames += [(2.5 + i * 0.25, hud_frame(mate_fill=0.4)) for i in range(8)]
    assert feed(tr, frames) == []


def test_an_empty_slot_never_dies():
    tr = tracker(ocr=NameOcr(""))
    frames = [(i * 0.25, hud_frame(mate_fill=0.0, mate_name="")) for i in range(40)]
    assert feed(tr, frames) == []


def test_your_own_bar_is_a_self_death():
    tr = tracker()
    frames = [(i * 0.25, hud_frame()) for i in range(4)]
    frames += [(1 + i * 0.25, hud_frame(self_fill=0.0)) for i in range(16)]
    cands = feed(tr, frames)
    assert any(c.event_type == "self" for c in cands)


def test_names_are_resolved_to_dead_fest_players():
    tr = tracker(ocr=NameOcr("Shad0wOnyx1l"), resolver=lambda raw: "Shadow" if "onyx" in raw.lower() else raw)
    frames = [(i * 0.25, hud_frame()) for i in range(4)] + [(1 + i * 0.25, hud_frame(mate_fill=0.0)) for i in range(16)]
    cands = feed(tr, frames)
    assert cands[0].player_label == "Shadow"


@pytest.mark.skipif(not windows_ocr_available(), reason="Windows OCR not available")
def test_real_ocr_reads_the_hud_name():
    from deadfest_detector.ocr import WindowsOcr

    tr = tracker(ocr=WindowsOcr())
    frames = [(i * 0.25, hud_frame()) for i in range(4)] + [(1 + i * 0.25, hud_frame(mate_fill=0.0)) for i in range(16)]
    cands = feed(tr, frames)
    assert cands and cands[0].player_label.replace("0", "O").lower().startswith("shadowonyx")


def test_the_pipeline_counts_a_hud_death_once():
    from deadfest_detector import games
    from deadfest_detector.detect import Analyzer
    from deadfest_detector.ocr import NullOcr
    from deadfest_detector.state import DeathStateMachine, Rules

    analyzer = Analyzer(games.compile_profile(games.get("dying_light_2")), NullOcr(), hud=tracker())
    sm = DeathStateMachine(Rules())
    confirmed = []
    t = 0.0
    for fill in [1.0] * 8 + [0.0] * 40 + [1.0] * 30:     # dies, stays dead 10 s, respawns
        confirmed += sm.update(t, int(t * 4), analyzer.analyze(hud_frame(mate_fill=fill), t))
        t += 0.25
    assert len(confirmed) == 1 and confirmed[0].candidate.player_label == "ShadowOnyx11"
