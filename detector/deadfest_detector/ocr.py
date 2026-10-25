"""
Text recognition for death banners and HUD names.

Uses the OCR engine built into Windows 10/11 (Windows.Media.Ocr), so nothing extra has to be
installed beyond the Python packages in requirements.txt. If it is unavailable (or the config sets
"ocr": "none") detection falls back to templates captured from real death screens.

read() gives the text; read_lines() also says where each line is, which is how HUD tracking finds
a player's health bar from where their name currently sits.
"""

from __future__ import annotations

import asyncio
import threading

import cv2
import numpy as np

Line = tuple[str, tuple[int, int, int, int]]   # text, (x, y, w, h) in the input image's pixels


class OcrProvider:
    name = "none"

    def read(self, frame_bgr: np.ndarray) -> str:
        return "\n".join(text for text, _ in self.read_lines(frame_bgr))

    def read_lines(self, frame_bgr: np.ndarray) -> list[Line]:
        return []


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
        self._lock = threading.Lock()   # one recognition at a time per engine

    def read_lines(self, frame_bgr: np.ndarray) -> list[Line]:
        from winrt.windows.graphics.imaging import BitmapAlphaMode, BitmapPixelFormat, SoftwareBitmap
        from winrt.windows.storage.streams import DataWriter

        h0, w0 = frame_bgr.shape[:2]
        scale = 1.0
        if w0 > self._width:
            scale = self._width / w0
            frame_bgr = cv2.resize(frame_bgr, (self._width, int(h0 * scale)), interpolation=cv2.INTER_AREA)
        h, w = frame_bgr.shape[:2]
        bgra = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2BGRA)
        writer = DataWriter()
        writer.write_bytes(bgra.tobytes())
        bitmap = SoftwareBitmap.create_copy_from_buffer(
            writer.detach_buffer(), BitmapPixelFormat.BGRA8, w, h, BitmapAlphaMode.PREMULTIPLIED
        )
        with self._lock:
            result = self._loop.run_until_complete(self._engine.recognize_async(bitmap))
        lines: list[Line] = []
        for line in result.lines:
            rects = [word.bounding_rect for word in line.words]
            if not rects:
                continue
            x0 = min(r.x for r in rects)
            y0 = min(r.y for r in rects)
            x1 = max(r.x + r.width for r in rects)
            y1 = max(r.y + r.height for r in rects)
            lines.append((line.text, (int(x0 / scale), int(y0 / scale), int((x1 - x0) / scale), int((y1 - y0) / scale))))
        return lines


def create(kind: str, width: int = 1280) -> OcrProvider:
    if kind == "windows":
        return WindowsOcr(width)
    return NullOcr()
