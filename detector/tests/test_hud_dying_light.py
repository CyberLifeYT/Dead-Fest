"""
Dying Light teammates: downed keeps a white name (not a death); dead turns the name and skull red.
Synthetic frames copy the look of real screenshots; the real screenshots themselves are only used
when present in tests/fixtures/local (kept out of git because they show gamertags).
"""

import json
import os
from pathlib import Path

import cv2
import numpy as np
import pytest

from conftest import windows_ocr_available
from deadfest_detector import games
from deadfest_detector.detect import Analyzer
from deadfest_detector.hud import BarSpec, HudConfig, HudTracker, TeamSpec, calibrate_team, name_colour
from deadfest_detector.ocr import NullOcr, OcrProvider
from deadfest_detector.state import DeathStateMachine, Rules

W, H = 1280, 720
NAME = "ShadowOnyx11"
NAME_ORG = (150, 250)                 # text baseline origin
AREA = (0.03, 0.25, 0.30, 0.20)
LOCAL = Path(__file__).parent / "fixtures" / "local"
RED = (40, 30, 225)
WHITE = (235, 235, 235)


def dl_frame(state="alive", roof=False, hud=True, seed=1):
    rng = np.random.default_rng(seed)
    frame = cv2.resize(rng.integers(40, 120, size=(H // 8, W // 8, 3), dtype=np.uint8), (W, H), interpolation=cv2.INTER_CUBIC)
    if roof:   # a red-orange tin roof right behind the name, the hard case
        frame[200:300, 80:500] = (60, 80, 200)
        for x in range(80, 500, 18):
            cv2.line(frame, (x, 200), (x, 300), (40, 55, 150), 4)
    if not hud:
        return frame
    # own health: heart + grey bar + number (top left)
    cv2.rectangle(frame, (60, 40), (300, 56), (170, 170, 170), -1)
    cv2.putText(frame, "275", (250, 54), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (90, 90, 90), 1, cv2.LINE_AA)
    colour = RED if state == "dead" else WHITE
    # skull icon left of the name
    cv2.circle(frame, (NAME_ORG[0] - 22, NAME_ORG[1] - 8), 10, colour, -1)
    cv2.putText(frame, NAME, NAME_ORG, cv2.FONT_HERSHEY_SIMPLEX, 0.7, colour, 2, cv2.LINE_AA)
    bx, by = NAME_ORG[0], NAME_ORG[1] + 10
    if state == "alive":
        cv2.rectangle(frame, (bx, by), (bx + 200, by + 5), WHITE, -1)
    elif state == "downed":
        cv2.rectangle(frame, (bx, by), (bx + 200, by + 1), WHITE, 1)
        cv2.rectangle(frame, (bx, by), (bx + 34, by + 5), RED, -1)
    else:   # dead: an empty outline
        cv2.rectangle(frame, (bx, by), (bx + 200, by + 1), WHITE, 1)
    return frame


def name_box_px():
    (tw, th), _ = cv2.getTextSize(NAME, cv2.FONT_HERSHEY_SIMPLEX, 0.7, 2)
    return NAME_ORG[0], NAME_ORG[1] - th, tw, th


class NameOcr(OcrProvider):
    """Reads the name where it's drawn, but only while it's white (red names are unreadable in game)."""
    name = "fake"

    def __init__(self):
        self.readable = True

    def read_lines(self, img):
        if not self.readable:
            return []
        aw = round(AREA[2] * W)
        scale, pad = (1.0, 24) if abs(img.shape[1] - (aw + 48)) <= 3 else (2.0, 48)
        x, y, w, h = name_box_px()
        ax, ay = AREA[0] * W, AREA[1] * H
        return [("shad6wOnyx11", (int((x - ax) * scale + pad), int((y - ay) * scale + pad), int(w * scale), int(h * scale)))]


def tracker(ocr=None, cue="name_red"):
    x, y, w, h = name_box_px()
    name = (x / W, y / H, w / W, h / H)
    bar = (x / W, (y + h + 12) / H, 200 / W, 6 / H)
    team = TeamSpec(AREA, calibrate_team(AREA, name, bar), (0, 0, 235), death_cue=cue)
    resolver = (lambda raw: ("u1", "ShadowOnyx11") if "onyx" in raw.lower().replace("6", "o") else (raw.upper(), raw))
    return HudTracker(HudConfig(team=team, hold_seconds=3.0), ocr or NameOcr(), resolver,
                      known_names=lambda: ["ShadowOnyx11"])


def run(tr, timeline, step=0.25):
    out, t = [], 0.0
    for seconds, kw in timeline:
        frame = dl_frame(**kw)
        if isinstance(tr.ocr, NameOcr):
            tr.ocr.readable = kw.get("state", "alive") != "dead" and kw.get("hud", True)
        for _ in range(int(seconds / step)):
            out += [(t, c) for c in tr.update(t, frame)]
            t += step
    return out


def test_name_colour_tells_white_from_red():
    x, y, w, h = name_box_px()
    region = lambda f: f[y - 4:y + h + 4, x - 36:x + w]     # noqa: E731
    assert name_colour(region(dl_frame("alive"))) == "white"
    assert name_colour(region(dl_frame("downed"))) == "white"
    assert name_colour(region(dl_frame("dead"))) == "red"
    assert name_colour(region(dl_frame(hud=False))) == "none"


def test_downed_is_not_a_death_even_for_a_long_time():
    out = run(tracker(), [(3, {"state": "alive"}), (25, {"state": "downed"})])
    assert out == []


def test_dead_is_a_death_credited_by_name():
    out = run(tracker(), [(3, {"state": "alive"}), (4, {"state": "downed"}), (5, {"state": "dead"})])
    assert out and {c.player_label for _, c in out} == {"ShadowOnyx11"}
    first = out[0][0]
    assert 7.0 + 2.0 - 0.01 <= first <= 7.0 + 3.0       # counted ~2 s after the name turned red


def test_a_red_roof_behind_the_name_is_neither_a_false_death_nor_hides_a_real_one():
    assert run(tracker(), [(3, {"state": "alive", "roof": True}), (20, {"state": "downed", "roof": True})]) == []
    out = run(tracker(), [(3, {"state": "alive", "roof": True}), (5, {"state": "dead", "roof": True})])
    assert {c.player_label for _, c in out} == {"ShadowOnyx11"}


def test_menus_hiding_the_hud_are_not_deaths():
    out = run(tracker(), [(3, {"state": "alive"}), (15, {"hud": False}), (3, {"state": "alive"}), (15, {"hud": False})])
    assert out == []


def test_bar_empty_cue_would_wrongly_count_downed_which_is_why_dying_light_uses_name_red():
    # Documents the trap: with the bar cue, a downed teammate (red sliver, not the full colour) reads as empty.
    out = run(tracker(cue="bar_empty"), [(3, {"state": "alive"}), (10, {"state": "downed"})])
    assert out, "if this ever stops counting, the bar cue became safe for Dying Light"


def test_the_whole_pipeline_counts_one_death_per_red_name_and_resets_on_respawn():
    analyzer = Analyzer(games.compile_profile(games.get("dying_light")), NullOcr(), hud=tracker())
    sm = DeathStateMachine(Rules())
    timeline = [("alive", 3), ("downed", 6), ("dead", 8), ("alive", 6), ("dead", 6), ("alive", 3)]
    confirmed, t = [], 0.0
    for state, secs in timeline:
        frame = dl_frame(state)
        analyzer.hud.ocr.readable = state != "dead"
        for _ in range(int(secs * 4)):
            confirmed += sm.update(t, int(t * 4), analyzer.analyze(frame, t))
            t += 0.25
    assert [c.candidate.player_label for c in confirmed] == ["ShadowOnyx11", "ShadowOnyx11"]


def test_own_health_bar_alone_can_never_confirm_a_death():
    # The setup that caused false captures: a big box over the heart and number, no bar colour found.
    cfg = HudConfig(self_bar=BarSpec((0.015, 0.009, 0.34, 0.13), None), hold_seconds=3.0)
    analyzer = Analyzer(games.compile_profile(games.get("dying_light")), NullOcr(), hud=HudTracker(cfg))
    sm = DeathStateMachine(Rules())
    confirmed, t = [], 0.0
    for frame in [dl_frame(hud=False)] * 40 + [dl_frame("alive")] * 20 + [dl_frame(hud=False)] * 40:
        confirmed += sm.update(t, int(t * 4), analyzer.analyze(frame, t))
        t += 0.25
    assert confirmed == []
    # And with a sampled colour it's still only a supporting cue, below the threshold.
    cfg2 = HudConfig(self_bar=BarSpec((60 / W, 40 / H, 240 / W, 16 / H), (0, 0, 170)), hold_seconds=1.0)
    tr = HudTracker(cfg2)
    cands = []
    for i in range(20):
        cands += tr.update(i * 0.25, dl_frame("alive") if i < 4 else dl_frame(hud=False))
    assert cands and all(c.confidence < 0.8 for c in cands)


@pytest.mark.skipif(not windows_ocr_available(), reason="Windows OCR not available")
def test_real_ocr_learns_the_name_while_white_then_the_red_name_counts():
    from deadfest_detector.ocr import WindowsOcr

    out = run(tracker(ocr=WindowsOcr()), [(3, {"state": "alive"}), (4, {"state": "downed"}), (5, {"state": "dead"})])
    assert out and {c.player_label for _, c in out} == {"ShadowOnyx11"}


# ---------------------------------------------------------------- the real screenshots (local only)

needs_local = pytest.mark.skipif(not (LOCAL / "dl1_teammate_dead.png").exists(), reason="real screenshots not present")


@needs_local
def test_real_screenshots_downed_is_white_dead_is_red():
    downed = cv2.imread(str(LOCAL / "dl1_teammate_downed.png"))
    dead = cv2.imread(str(LOCAL / "dl1_teammate_dead.png"))
    assert name_colour(downed[122:147, 62:192]) == "white"      # skull + name, as OCR located it
    assert name_colour(dead[115:136, 33:160]) == "red"


@needs_local
def test_real_false_captures_no_longer_count():
    """The frames that were wrongly saved as deaths, replayed with the exact HUD setup that caused them."""
    cfg_file = Path(os.environ.get("LOCALAPPDATA", "")) / "DeadFestDetector" / "config.json"
    box = (0.015, 0.009, 0.342, 0.132)
    if cfg_file.exists():
        saved = json.loads(cfg_file.read_text(encoding="utf-8")).get("hud", {}).get("dying_light", {}).get("self_bar")
        if saved:
            box = tuple(saved["box"])
    frames = [cv2.imread(str(LOCAL / n)) for n in ("false_dark_room.png", "false_rooftops.png", "false_other.png")]
    cfg = HudConfig(self_bar=BarSpec(box, None), hold_seconds=3.0)
    analyzer = Analyzer(games.compile_profile(games.get("dying_light")), NullOcr(), hud=HudTracker(cfg))
    sm = DeathStateMachine(Rules())
    confirmed, t = [], 0.0
    for frame in frames:
        for _ in range(40):
            confirmed += sm.update(t, int(t * 4), analyzer.analyze(frame, t))
            t += 0.25
    assert confirmed == []
