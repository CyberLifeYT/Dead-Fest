"""Teammates tracked by name: positions can change (leave, rejoin, swap) without mixing people up."""

import cv2
import numpy as np
import pytest

from conftest import windows_ocr_available
from deadfest_detector.hud import HudConfig, HudTracker, TeamSpec, calibrate_team, crop, reference_colour
from deadfest_detector.ocr import OcrProvider

W, H = 1280, 720
AREA = (0.70, 0.05, 0.28, 0.40)          # where the squad list is drawn
ROW_Y0, ROW_STEP = 60, 70                 # pixels
NAME_X = 910
BAR_DX, BAR_DY, BAR_W, BAR_H = 0, 26, 200, 10
GREEN = (80, 200, 60)


def squad_frame(rows, seed=0):
    """rows: [(name, fill), ...] top to bottom. Returns the frame and each row's name box in pixels."""
    rng = np.random.default_rng(seed)
    frame = cv2.resize(rng.integers(30, 110, size=(H // 8, W // 8, 3), dtype=np.uint8), (W, H), interpolation=cv2.INTER_CUBIC)
    boxes = {}
    for i, (name, fill) in enumerate(rows):
        y = ROW_Y0 + i * ROW_STEP
        cv2.rectangle(frame, (NAME_X - 6, y - 4), (NAME_X + 230, y + 44), (12, 12, 12), -1)
        cv2.putText(frame, name, (NAME_X, y + 16), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (240, 240, 240), 1, cv2.LINE_AA)
        (tw, th), _ = cv2.getTextSize(name, cv2.FONT_HERSHEY_SIMPLEX, 0.6, 1)
        boxes[name] = (NAME_X, y + 16 - th, tw, th)
        bx, by = NAME_X + BAR_DX, y + BAR_DY
        cv2.rectangle(frame, (bx, by), (bx + BAR_W, by + BAR_H), (30, 30, 30), -1)
        if fill > 0:
            cv2.rectangle(frame, (bx, by), (bx + int(BAR_W * fill), by + BAR_H), GREEN, -1)
    return frame, boxes


class LayoutOcr(OcrProvider):
    """Reports the names exactly where they're drawn (in the crop's scaled coordinates)."""
    name = "fake"

    def __init__(self):
        self.boxes = {}

    def read_lines(self, img):
        aw = round(AREA[2] * W)
        # The tracker reads the area at 1x with a 24 px margin and at 2x with a 48 px margin.
        scale, pad = (1.0, 24) if abs(img.shape[1] - (aw + 48)) <= 3 else (2.0, 48)
        ax, ay = AREA[0] * W, AREA[1] * H
        return [(name, (int((x - ax) * scale + pad), int((y - ay) * scale + pad), int(w * scale), int(h * scale)))
                for name, (x, y, w, h) in self.boxes.items()]


def team_spec(full_frame, example_box):
    nx, ny, nw, nh = example_box
    name = (nx / W, ny / H, nw / W, nh / H)
    bar = ((NAME_X + BAR_DX) / W, (ROW_Y0 + BAR_DY) / H, BAR_W / W, BAR_H / H)
    return TeamSpec(AREA, calibrate_team(AREA, name, bar), reference_colour(crop(full_frame, bar)))


PLAYERS = {"ALPHAWOLF": "Alpha", "BRAVOSIX": "Bravo", "CHARLIEX": "Charlie"}


def resolver(raw):
    key = raw.replace(" ", "").upper()
    return (key, PLAYERS[key]) if key in PLAYERS else (key, raw)


def make(ocr=None, hold=3.0):
    full, boxes = squad_frame([("AlphaWolf", 1), ("BravoSix", 1), ("CharlieX", 1)])
    cfg = HudConfig(team=team_spec(full, boxes["AlphaWolf"]), hold_seconds=hold)
    return HudTracker(cfg, ocr or LayoutOcr(), resolver, known_names=lambda: list(PLAYERS.values()))


def run(tracker, timeline, step=0.25):
    """timeline: [(seconds, rows)], each rows list shown for that long."""
    out, t = [], 0.0
    for seconds, rows in timeline:
        frame, boxes = squad_frame(rows)
        if isinstance(tracker.ocr, LayoutOcr):
            tracker.ocr.boxes = boxes
        for _ in range(int(seconds / step)):
            out += [(t, c) for c in tracker.update(t, frame)]
            t += step
    return out


def test_a_death_is_credited_to_the_player_by_name():
    tr = make()
    out = run(tr, [(3, [("AlphaWolf", 1), ("BravoSix", 1), ("CharlieX", 1)]),
                   (5, [("AlphaWolf", 1), ("BravoSix", 0), ("CharlieX", 1)])])
    labels = {c.player_label for _, c in out}
    assert labels == {"Bravo"} and all(c.event_type == "teammate" for _, c in out)
    assert out[0][0] >= 3 + 3.0 - 0.01          # only after the hold


def test_someone_leaving_and_everyone_shifting_is_not_a_death_and_nobody_is_mixed_up():
    tr = make()
    out = run(tr, [
        (3, [("AlphaWolf", 1), ("BravoSix", 1), ("CharlieX", 1)]),
        (6, [("BravoSix", 1), ("CharlieX", 1)]),                       # Alpha left; rows move up
        (5, [("BravoSix", 1), ("CharlieX", 0)]),                       # then Charlie dies
    ])
    labels = [c.player_label for _, c in out]
    assert labels and set(labels) == {"Charlie"}                      # not Alpha, not Bravo


def test_rejoining_in_a_different_place_keeps_tracking_them():
    tr = make()
    out = run(tr, [
        (3, [("AlphaWolf", 1), ("BravoSix", 1), ("CharlieX", 1)]),
        (3, [("BravoSix", 1), ("CharlieX", 1)]),
        (3, [("BravoSix", 1), ("CharlieX", 1), ("AlphaWolf", 1)]),     # Alpha back, now last
        (5, [("BravoSix", 1), ("CharlieX", 1), ("AlphaWolf", 0)]),
    ])
    assert {c.player_label for _, c in out} == {"Alpha"}


def test_a_player_whose_row_vanishes_is_not_dead():
    tr = make()
    out = run(tr, [(3, [("AlphaWolf", 1), ("BravoSix", 1)]), (20, [("AlphaWolf", 1)])])
    assert out == []


def test_text_that_is_not_a_known_player_is_ignored():
    tr = make()
    run(tr, [(2, [("AlphaWolf", 1), ("AMMO 30", 0)])])
    assert all(st.label != "AMMO 30" for st in tr.states.values())


@pytest.mark.skipif(not windows_ocr_available(), reason="Windows OCR not available")
def test_real_ocr_finds_names_and_credits_the_right_player():
    from deadfest_detector.ocr import WindowsOcr

    tr = make(ocr=WindowsOcr())
    out = run(tr, [(3, [("AlphaWolf", 1), ("BravoSix", 1), ("CharlieX", 1)]),
                   (2, [("BravoSix", 1), ("CharlieX", 1)]),
                   (5, [("BravoSix", 0), ("CharlieX", 1)])])
    assert out and {c.player_label for _, c in out} == {"Bravo"}
