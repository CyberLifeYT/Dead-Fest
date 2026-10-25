"""Shared fixtures: synthetic gameplay and death-screen frames, rendered with OpenCV."""

from __future__ import annotations

import sys
from pathlib import Path

import cv2
import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

W, H = 1280, 720
DEATH_TEXT_ORIGIN = (380, 380)


def gameplay_frame(seed: int) -> np.ndarray:
    """A busy, changing 'gameplay' frame with a HUD, and no death wording anywhere."""
    rng = np.random.default_rng(seed)
    base = rng.integers(40, 200, size=(H // 8, W // 8, 3), dtype=np.uint8)
    frame = cv2.resize(base, (W, H), interpolation=cv2.INTER_CUBIC)
    cv2.rectangle(frame, (40, 640), (360, 690), (20, 20, 20), -1)
    cv2.putText(frame, f"AMMO {30 - seed % 30}  HP 100", (52, 675), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (230, 230, 230), 2)
    return frame


def death_frame(seed: int = 0, text: str = "YOU DIED", banner: str | None = None) -> np.ndarray:
    """A death screen: darkened, red-washed gameplay with the death wording on top."""
    frame = (gameplay_frame(seed) * 0.25).astype(np.uint8)
    frame[..., 2] = np.clip(frame[..., 2].astype(np.int16) + 70, 0, 255).astype(np.uint8)
    cv2.putText(frame, text, DEATH_TEXT_ORIGIN, cv2.FONT_HERSHEY_DUPLEX, 3.2, (40, 40, 230), 6, cv2.LINE_AA)
    if banner:
        cv2.putText(frame, banner, (60, 120), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (235, 235, 235), 2, cv2.LINE_AA)
    return frame


def banner_frame(seed: int, banner: str) -> np.ndarray:
    """Normal gameplay with a '<name> has died' banner (a teammate death while you live)."""
    frame = gameplay_frame(seed)
    cv2.rectangle(frame, (40, 80), (700, 140), (15, 15, 15), -1)
    cv2.putText(frame, banner, (60, 122), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (235, 235, 235), 2, cv2.LINE_AA)
    return frame


def template_from_death_frame() -> tuple[np.ndarray, int]:
    """The 'YOU DIED' crop, the way `calibrate` would save it."""
    gray = cv2.cvtColor(death_frame(99), cv2.COLOR_BGR2GRAY)
    x, y = DEATH_TEXT_ORIGIN
    return gray[y - 80:y + 20, x - 10:x + 520], W


def write_video(path: Path, segments: list[tuple[str, float]], fps: int = 10) -> Path:
    """segments: [("play"|"death", seconds), ...] rendered to an MJPG .avi."""
    writer = cv2.VideoWriter(str(path), cv2.VideoWriter_fourcc(*"MJPG"), fps, (W, H))
    assert writer.isOpened()
    i = 0
    for kind, seconds in segments:
        for _ in range(int(seconds * fps)):
            writer.write(death_frame(i) if kind == "death" else gameplay_frame(i))
            i += 1
    writer.release()
    return path


def windows_ocr_available() -> bool:
    try:
        from deadfest_detector.ocr import WindowsOcr

        WindowsOcr()
        return True
    except Exception:  # noqa: BLE001
        return False


@pytest.fixture
def store(tmp_path):
    from deadfest_detector.storage import EvidenceStore

    s = EvidenceStore(tmp_path / "XboxDeathDetector")
    s.ensure_layout()
    return s
