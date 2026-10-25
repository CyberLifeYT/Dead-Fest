"""
Detector settings, stored as JSON in %LOCALAPPDATA%\\DeadFestDetector\\config.json.

Only settings live here. The evidence itself lives under `evidence_root` (D:\\XboxDeathDetector by
default), and the login session lives next to this file, never in the evidence folder, so the
evidence folder can be copied or shared without leaking a sign-in.
"""

from __future__ import annotations

import copy
import json
import os
from pathlib import Path

DEFAULT_EVIDENCE_ROOT = r"D:\XboxDeathDetector"

SHARING_PRIVATE = "private"
SHARING_SHARED = "shared"

DEFAULTS: dict = {
    "evidence_root": DEFAULT_EVIDENCE_ROOT,
    "game": "dying_light_2",
    # Captured evidence stays on this PC until you opt in to sharing it with your group.
    "sharing": SHARING_PRIVATE,
    "source": {
        # capture_card | screen | window | video | images
        "type": "capture_card",
        "device_index": 0,
        "width": 1920,
        "height": 1080,
        "monitor": 1,
        "region": None,          # [left, top, width, height] for "screen"
        "window_title": "Xbox",  # for "window": part of the window's title
        "path": "",              # for "video" / "images"
        "realtime": True,        # video files: play at real speed (False = as fast as possible)
    },
    "detection": {
        "analysis_fps": 4.0,
        "threshold": 0.8,
        "confirm_frames": 2,
        "confirm_seconds": 0.4,
        "max_misses": 1,
        "respawn_clear_seconds": 3.0,
        "cooldown_seconds": 8.0,
        "ocr": "windows",        # windows | none
        "ocr_width": 1280,
        "template_threshold": 0.8,
        "color_boost": 0.05,
    },
    "capture": {
        "save_pre_frame": True,
        "pre_seconds": 1.0,
        "save_post_frame": False,
        "post_seconds": 2.0,
    },
    "player": {
        "gamertag": "",
        "aliases": [],           # other names that are you (e.g. State of Decay 2 survivors)
        "teammates": [],         # your squad's names; other named deaths are classed "other"
    },
    "firebase": {
        "api_key": "",
        "project_id": "",
    },
    "game_overrides": {},        # { "<game id>": { "self_patterns": [...], ... } }
    "storage_retry_seconds": 15,
    "storage_queue_limit": 20,
}


def app_dir() -> Path:
    base = os.environ.get("DEADFEST_DETECTOR_HOME") or os.path.join(
        os.environ.get("LOCALAPPDATA", str(Path.home())), "DeadFestDetector"
    )
    return Path(base)


def default_config_path() -> Path:
    return app_dir() / "config.json"


def _merge(base: dict, extra: dict) -> dict:
    out = copy.deepcopy(base)
    for k, v in extra.items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = _merge(out[k], v)
        else:
            out[k] = v
    return out


def load(path: Path | None = None) -> dict:
    path = path or default_config_path()
    if not path.exists():
        return copy.deepcopy(DEFAULTS)
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    return _merge(DEFAULTS, data)


def save(cfg: dict, path: Path | None = None) -> Path:
    path = path or default_config_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2)
    os.replace(tmp, path)
    return path


def import_google_services(cfg: dict, google_services: Path) -> None:
    """Copies the Web API key and project id out of the app's google-services.json."""
    with open(google_services, encoding="utf-8") as f:
        gs = json.load(f)
    cfg["firebase"]["project_id"] = gs["project_info"]["project_id"]
    cfg["firebase"]["api_key"] = gs["client"][0]["api_key"][0]["current_key"]
