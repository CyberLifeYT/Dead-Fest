"""Automatic teammate tracking on Dying Light-style HUD frames, with moving scenery."""

import cv2
import numpy as np
import pytest

from conftest import windows_ocr_available
from deadfest_detector.hud import DEFAULT_TEAM_AREA, HudConfig, HudTracker, default_config
from deadfest_detector.ocr import OcrProvider
from deadfest_detector.state import DeathStateMachine, Rules
from deadfest_detector.teamslots import TeamTracker

W, H = 1280, 720
AREA = DEFAULT_TEAM_AREA["dying_light"]
WHITE, RED, TEAL = (235, 235, 235), (40, 30, 225), (170, 190, 60)
ROW0, PITCH = 112, 46


def frame(rows, t=0, roof=False, hud=True, w=W, h=H):
    """rows: [(name, state)] with state alive / downed / dead. The background moves with t."""
    rng = np.random.default_rng(int(t * 4) + 7)
    bg = cv2.resize(rng.integers(40, 120, size=(h // 8, w // 8, 3), dtype=np.uint8), (w, h), interpolation=cv2.INTER_CUBIC)
    # Bright 'pavement' shapes drifting through the teammates area: must never become teammates.
    for k in range(5):
        x = int((k * 97 + t * 160) % (w * 0.3))
        y = int(h * 0.15 + (k * 53 + t * 40) % (h * 0.25))
        cv2.rectangle(bg, (x, y), (x + 60, y + 9), (215, 215, 210), -1)
        cv2.rectangle(bg, (x + 70, y), (x + 76, y + 9), (215, 215, 210), -1)
    if roof:
        bg[int(h * 0.12):int(h * 0.42), 0:int(w * 0.33)] = (60, 80, 200)
    if not hud:
        return bg
    s = h / 720
    cv2.rectangle(bg, (int(60 * s), int(40 * s)), (int(300 * s), int(54 * s)), (170, 170, 170), -1)   # own health
    for i, (name, state) in enumerate(rows):
        y = int((ROW0 + i * PITCH) * s)
        colour = RED if state == "dead" else WHITE
        cv2.circle(bg, (int(70 * s), y - int(7 * s)), int(9 * s), colour, -1)
        cv2.putText(bg, name, (int(88 * s), y), cv2.FONT_HERSHEY_SIMPLEX, 0.55 * s, colour, max(1, int(round(1.4 * s))), cv2.LINE_AA)
        bx, by, bw = int(88 * s), y + int(8 * s), int(150 * s)
        if state == "alive":
            cv2.rectangle(bg, (bx, by), (bx + bw, by + max(2, int(4 * s))), TEAL, -1)
        elif state == "downed":
            cv2.rectangle(bg, (bx, by), (bx + int(bw * 0.15), by + max(2, int(4 * s))), RED, -1)
        else:
            cv2.rectangle(bg, (bx, by), (bx + bw, by), WHITE, 1)
    return bg


class NoOcr(OcrProvider):
    name = "none"


def tracker(ocr=None, roster=(), cue="name_red"):
    resolver = lambda raw: (raw.upper(), raw)   # noqa: E731
    return TeamTracker(AREA, cue, 2.0, ocr or NoOcr(), resolver, lambda: list(roster))


def run(tr, timeline, step=0.25, **kw):
    """Deaths as the app counts them: tracker cues through the same de-duplicating state machine."""
    sm = DeathStateMachine(Rules())
    out, t = [], 0.0
    for secs, rows in timeline:
        for _ in range(int(secs / step)):
            for conf in sm.update(t, int(t / step), tr.update(t, frame(rows, t, **kw))):
                out.append((t, conf.candidate))
            t += step
    return out


A, B = "AlphaWolf", "BravoSix"


def test_moving_bright_scenery_never_becomes_a_teammate():
    tr = tracker()
    run(tr, [(10, [])])
    assert [s for s in tr.slots if s.hits >= 3] == []


def test_downed_is_never_a_death():
    out = run(tracker(roster=["Bravo"]), [(3, [(A, "alive"), (B, "alive")]), (30, [(A, "alive"), (B, "downed")])])
    assert out == []


def test_dead_counts_once_for_the_right_row():
    tr = tracker(roster=["Bravo"])
    out = run(tr, [(3, [(A, "alive"), (B, "alive")]), (5, [(A, "alive"), (B, "downed")]), (6, [(A, "alive"), (B, "dead")])])
    assert len(out) == 1
    t, c = out[0]
    # Name red at 8 s; the tracker needs it red for 2 s, then the state machine confirms (~0.5 s).
    assert c.event_type == "teammate" and 10.0 - 0.01 <= t <= 11.5
    assert "name turned red" in c.cues["hud"]


def test_with_one_teammate_and_unreadable_names_the_death_is_theirs():
    out = run(tracker(roster=["Shadow"]), [(3, [(A, "alive")]), (5, [(A, "dead")])])
    assert [c.player_label for _, c in out] == ["Shadow"]


def test_with_several_teammates_and_unreadable_names_it_stays_unassigned():
    out = run(tracker(roster=["Shadow", "Bravo"]), [(3, [(A, "alive")]), (5, [(A, "dead")])])
    assert [c.player_label for _, c in out] == [""]


def test_a_red_roof_behind_the_list_is_not_a_death_and_doesnt_hide_one():
    assert run(tracker(), [(3, [(A, "alive")]), (20, [(A, "downed")])], roof=True) == []
    assert len(run(tracker(), [(3, [(A, "alive")]), (5, [(A, "dead")])], roof=True)) == 1


def test_menus_hiding_the_hud_are_not_deaths():
    out = run(tracker(), [(3, [(A, "alive"), (B, "alive")]), (12, None or []), (3, [(A, "alive"), (B, "alive")])],
              hud=True)
    assert out == []
    out = run(tracker(), [(3, [(A, "alive")])]) + run(tracker(), [(12, [])], hud=False)
    assert out == []


def test_leaving_and_rows_shifting_doesnt_create_deaths_and_a_later_death_still_counts():
    out = run(tracker(roster=["x"]), [
        (3, [(A, "alive"), (B, "alive")]),
        (6, [(B, "alive")]),                  # Alpha left: Bravo moves up to row 1
        (6, [(B, "dead")]),
    ])
    assert len(out) == 1


def test_respawn_then_dying_again_counts_again():
    out = run(tracker(), [(3, [(A, "alive")]), (5, [(A, "dead")]), (32, [(A, "alive")]), (5, [(A, "dead")])])
    assert len(out) == 2


def test_too_small_a_picture_is_refused_not_guessed():
    tr = tracker()
    out = run(tr, [(3, [(A, "alive")]), (6, [(A, "dead")])], w=620, h=348)
    assert out == [] and tr.too_small


def test_bar_empty_cue_counts_when_the_bar_runs_out():
    out = run(tracker(cue="bar_empty"), [(3, [(A, "alive")]), (5, [(A, "dead")])])
    assert len(out) == 1 and "health bar empty" in out[0][1].cues["hud"]


def test_dying_light_works_with_no_setup_at_all():
    cfg = HudConfig.from_json(default_config("dying_light"))
    tr = HudTracker(cfg, NoOcr(), roster=lambda: ["Shadow"])
    out = run(tr, [(3, [(A, "alive")]), (5, [(A, "dead")])])
    assert [c.player_label for _, c in out] == ["Shadow"]
    assert default_config("world_war_z") is None


@pytest.mark.skipif(not windows_ocr_available(), reason="Windows OCR not available")
def test_real_ocr_reads_who_it_was_at_1080p():
    from deadfest_detector.ocr import WindowsOcr

    tr = TeamTracker(AREA, "name_red", 2.0, WindowsOcr(),
                     lambda raw: ("U2", "Bravo") if "bravo" in raw.lower() else (raw.upper(), raw), lambda: ["a", "b"])
    out = run(tr, [(6, [(A, "alive"), (B, "alive")]), (5, [(A, "alive"), (B, "dead")])], w=1920, h=1080)
    assert [c.player_label for _, c in out] == ["Bravo"]
