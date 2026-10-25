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


def test_teammates_are_found_automatically_with_no_setup_for_dying_light(api, tmp_path):
    from test_teamslots import frame

    folder = tmp_path / "frames"
    folder.mkdir()
    cv2.imwrite(str(folder / "f.png"), frame([("AlphaWolf", "alive"), ("BravoSix", "downed")]))
    api.save_settings({"game": "dying_light", "sourceType": "images"})
    api.cfg["source"]["path"] = str(folder)
    assert api.state()["hudDefault"] is True               # nothing drawn: the built-in layout is used
    test = api.hud_test()
    assert test["ok"], test
    mates = [r for r in test["readings"] if r["slot"] == "Teammate"]
    assert len(mates) == 2 and all(r["colour"] == "white" and r["confirmed"] for r in mates), mates


def test_a_drawn_teammates_area_replaces_the_default_and_reset_restores_it(api):
    import numpy as np

    api.save_settings({"game": "world_war_z"})
    assert api.state()["hasHud"] is False                  # no built-in layout for this game
    api.hud_frame = np.zeros((720, 1280, 3), np.uint8)
    res = api.hud_save({"team_area": [0.7, 0.1, 0.28, 0.4], "death_cue": "bar_empty", "hold_seconds": 20})
    assert res["ok"] and api.cfg["hud"]["world_war_z"]["team"]["death_cue"] == "bar_empty"
    assert api.state()["hasHud"] is True
    assert api.hud_reset()["hud"] == {}


def test_playing_with_names_the_teammate_when_names_cant_be_read(api):
    from deadfest_detector.players import Player

    api.players_cache = [Player("me", "Me"), Player("u2", "Shadow"), Player("u3", "Bravo")]
    api.cfg["me_uid"] = "me"
    api.save_settings({"squad": ["me", "u2"]})
    assert api._roster() == ["Shadow"]


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


def test_detecting_a_recorded_co_op_session_saves_one_teammate_death(api, tmp_path):
    import time as _time

    from deadfest_detector.players import Player
    from test_teamslots import frame

    path = tmp_path / "coop.avi"
    writer = cv2.VideoWriter(str(path), cv2.VideoWriter_fourcc(*"MJPG"), 8, (1280, 720))
    t = 0.0
    for secs, state in [(4, "alive"), (6, "downed"), (6, "dead"), (3, "alive")]:
        for _ in range(int(secs * 8)):
            writer.write(frame([("AlphaWolf", state)], t))
            t += 1 / 8
    writer.release()
    api.players_cache = [Player("me", "Me"), Player("u2", "Shadow")]
    api.cfg["me_uid"] = "me"
    api.save_settings({"game": "dying_light", "sourceType": "video", "squad": ["u2"]})
    api.cfg["source"].update({"path": str(path), "realtime": False})
    assert api.start()["ok"]
    for _ in range(300):
        if not api.state()["running"]:
            break
        _time.sleep(0.1)
    items = api.evidence(include_remote=False)["items"]
    assert [(i["type"], i["player"]) for i in items] == [("teammate", "Shadow")], items
    # The name on screen matched nobody while playing with one teammate: learned as their gamertag.
    assert api.cfg["gamertags"].get("u2", "").lower().startswith("alpha")
