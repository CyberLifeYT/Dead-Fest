"""The desktop app's actions, called the way the window calls them (no window needed)."""

import time

import cv2
import pytest

from conftest import write_video


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


def test_hud_setup_learns_the_layout_and_finds_teammates_by_name(api, tmp_path):
    from conftest import windows_ocr_available
    from test_hud_names import AREA, BAR_DY, BAR_H, BAR_W, H, NAME_X, ROW_Y0, W, squad_frame

    folder = tmp_path / "frames"
    folder.mkdir()
    live, _ = squad_frame([("BravoSix", 1), ("AlphaWolf", 0.5)])     # positions swapped since setup
    cv2.imwrite(str(folder / "f.png"), live)
    api.save_settings({"sourceType": "images"})
    api.cfg["source"]["path"] = str(folder)
    full, boxes = squad_frame([("AlphaWolf", 1), ("BravoSix", 1)])
    api.hud_frame = full                                              # "Capture frame" at full health
    nx, ny, nw, nh = boxes["AlphaWolf"]
    res = api.hud_save({
        "self_bar": None, "team_area": list(AREA),
        "example_name": [(nx - 4) / W, (ny - 4) / H, (nw + 30) / W, (nh + 8) / H],    # drawn loosely
        "example_bar": [NAME_X / W, (ROW_Y0 + BAR_DY) / H, BAR_W / W, BAR_H / H],
        "anchor": "left", "hold_seconds": 4,
    })
    assert res["ok"], res
    team = api.cfg["hud"]["dying_light_2"]["team"]
    assert team["ref"] and team["anchor"] == "left"
    if not windows_ocr_available():
        return
    test = api.hud_test()
    assert test["ok"], test
    found = {r["name"]: r["fill"] for r in test["readings"] if r["slot"] == "Teammate"}
    assert found.get("BravoSix", 0) > 0.9 and abs(found.get("AlphaWolf", 0) - 0.5) < 0.1, found


def test_preview_fps_is_a_setting_and_the_stream_url_is_local(api):
    s = api.state()
    assert s["previewFps"] == 30 and s["previewUrl"].startswith("http://127.0.0.1:")
    assert api.save_settings({"previewFps": 60})["ok"]
    assert api.state()["previewFps"] == 60 and "fps=60" in api.state()["previewUrl"]
    api.save_settings({"previewFps": 500})
    assert api.state()["previewFps"] == 120          # clamped
    assert api._source_cfg()["capture_fps"] == 120


def test_moderation_needs_a_sign_in(api):
    assert api.moderate("x" * 32, "verify", {"subjectUid": "u1"})["ok"] is False


def test_the_bridge_exposes_actions_not_internals(api):
    from deadfest_detector.desktop import Bridge

    public = {n for n in dir(Bridge(api)) if not n.startswith("_")}
    assert {"state", "start", "stop", "moderate", "evidence", "hud_save", "players", "sign_in"} <= public
    assert not {"cfg", "lock", "log", "pipeline"} & public


def test_dying_light_defaults_to_the_red_name_cue_and_a_colourless_bar_is_not_saved(api):
    import numpy as np
    from deadfest_detector.desktop import default_death_cue

    assert default_death_cue("dying_light") == "name_red" and default_death_cue("world_war_z") == "bar_empty"
    api.hud_frame = np.full((720, 1280, 3), 60, np.uint8)       # a grey screen: no bar colour anywhere
    res = api.hud_save({"self_bar": [0.01, 0.01, 0.3, 0.1], "hold_seconds": 3})
    assert res["ok"] and "wasn't saved" in res["message"]
    assert "self_bar" not in (api.cfg["hud"].get("dying_light_2") or {})
