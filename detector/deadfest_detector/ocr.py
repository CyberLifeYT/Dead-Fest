"""
Text recognition for death banners.

Uses the OCR engine built into Windows 10/11 (Windows.Media.Ocr), so nothing extra has to be
installed beyond the Python packages in requirements.txt. If it is unavailable (or the config sets
"ocr": "none") detection falls back to templates captured from real death screens.
"""

from __future__ import annotations

import asyncio

import cv2
import numpy as np


class OcrProvider:
    name = "none"

    def read(self, frame_bgr: np.ndarray) -> str:
        return ""


class NullOcr(OcrProvider):
    pass


class WindowsOcr(OcrProvider):
    name = "windows"

    def __init__(self, width: int = 1280):
        from winrt.windows.media.ocr import OcrEngine  # imported here so tests can run without it

        self._engine = OcrEngine.try_create_from_user_profile_languages()
        if self._engine is None:
            raise RuntimeError("Windows OCR has no recognizer for your display language. "
                               "Add English in Settings > Time & language > Language.")
        self._width = width
        self._loop = asyncio.new_event_loop()

    def read(self, frame_bgr: np.ndarray) -> str:
        from winrt.windows.graphics.imaging import BitmapAlphaMode, BitmapPixelFormat, SoftwareBitmap
        from winrt.windows.storage.streams import DataWriter

        h, w = frame_bgr.shape[:2]
        if w > self._width:
            frame_bgr = cv2.resize(frame_bgr, (self._width, int(h * self._width / w)), interpolation=cv2.INTER_AREA)
            h, w = frame_bgr.shape[:2]
        bgra = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2BGRA)
        writer = DataWriter()
        writer.write_bytes(bgra.tobytes())
        bitmap = SoftwareBitmap.create_copy_from_buffer(
            writer.detach_buffer(), BitmapPixelFormat.BGRA8, w, h, BitmapAlphaMode.PREMULTIPLIED
        )
        result = self._loop.run_until_complete(self._engine.recognize_async(bitmap))
        return "\n".join(line.text for line in result.lines)


def create(kind: str, width: int = 1280) -> OcrProvider:
    if kind == "windows":
        return WindowsOcr(width)
    return NullOcr()
