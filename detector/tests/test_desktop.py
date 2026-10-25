"""The desktop app's actions, called the way the window calls them (no window needed)."""

import time

import cv2
import pytest

from conftest import write_video
from test_hud import MATE_BAR, MATE_NAME, SELF_BAR, hud_frame


@pytest.fixture
def api(tmp_path, monkeypatch):
    home = tmp_path / "home"
    monkeypatch.setenv("DEADFEST_DETECTOR_HOME", str(home))
    from deadfest_detector import config as configmod
    cfg = configmod.load()
    cfg["evidence_root"] = str(tmp_path / "XboxDeathDetector")
    cfg["detection"]["ocr"] = "windows"
    configmod.save(cfg)
    from deadfest_detector.desktop import Api
    return Api()


def test_state_before_signing_in(api):
    s = api.state()
    assert s["signedIn"] is False and s["running"] is False and len(s["games"]) == 8
    assert s["source"]["type"] == "xbox_app"


def test_sharing_needs_a_sign_in(api):
    res = api.save_settings({"sharing": True})
    assert res["ok"] is False and "Sign in" in res["error"]
    assert api.save_settings({"game": "dead_island_2", "gamertag": ["u1", "ShadowOnyx11"]})["ok"]
    assert api.state()["game"] == "dead_island_2" and api.cfg["gamertags"]["u1"] == "ShadowOnyx11"


def test_start_detects_deaths_and_lists_them_as_evidence(api, tmp_path):
    video = write_video(tmp_path / "s.avi", [("play", 2), ("death", 4), ("play", 2)])
    api.save_settings({"game": "dying_light_2", "sourceType": "video"})
    api.cfg["source"].update({"path": str(video), "realtime": False})
    assert api.start()["ok"]
    for _ in range(200):
        if not api.state()["running"]:
            break
        time.sleep(0.1)
    s = api.state()
    assert s["deaths"] == 1 and any(e["kind"] == "death" for e in s["log"])
    ev = api.evidence(include_remote=False)
    assert ev["ok"] and len(ev["items"]) == 1
    item = ev["items"][0]
    assert item["sync"] == "private" and item["thumb"].startswith("data:image/jpeg;base64,")
    assert api.evidence_image(item["id"])["image"].startswith("data:image/jpeg;base64,")
    # Sharing it later queues it for upload (it uploads once signed in).
    assert api.share([item["id"]])["queued"] == 1


def test_hud_setup_samples_bar_colours_and_reads_live_values(api, tmp_path):
    folder = tmp_path / "frames"
    folder.mkdir()
    cv2.imwrite(str(folder / "f.png"), hud_frame(mate_fill=0.5))
    api.save_settings({"sourceType": "images"})
    api.cfg["source"]["path"] = str(folder)
    api.hud_frame = hud_frame()          # "Capture frame" with everyone at full health
    res = api.hud_save({"self_bar": list(SELF_BAR), "teammates": [{"bar": list(MATE_BAR), "name_box": list(MATE_NAME)}],
                        "hold_seconds": 4})
    assert res["ok"], res
    saved = api.cfg["hud"]["dying_light_2"]
    assert saved["self_bar"]["ref"] and saved["teammates"][0]["bar"]["ref"] and saved["hold_seconds"] == 4
    test = api.hud_test()
    assert test["ok"], test
    mate = next(r for r in test["readings"] if r["slot"] == "Teammate 1")
    assert abs(mate["fill"] - 0.5) < 0.08
    you = next(r for r in test["readings"] if r["slot"] == "You")
    assert you["fill"] > 0.9


def test_moderation_needs_a_sign_in(api):
    assert api.moderate("x" * 32, "verify", {"subjectUid": "u1"})["ok"] is False


def test_the_bridge_exposes_actions_not_internals(api):
    from deadfest_detector.desktop import Bridge

    public = {n for n in dir(Bridge(api)) if not n.startswith("_")}
    assert {"state", "start", "stop", "moderate", "evidence", "hud_save", "players", "sign_in"} <= public
    assert not {"cfg", "lock", "log", "pipeline"} & public
