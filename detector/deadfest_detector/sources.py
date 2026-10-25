"""
Where frames come from.

  capture_card  An HDMI capture card (Elgato, AVerMedia, generic UVC dongles...). These appear in
                Windows as a webcam-style video device, which OpenCV reads through DirectShow.
                This is the dependable way to get Xbox video onto a PC.
  screen        A region of a monitor (mss). Use it to watch the Xbox app's Remote Play window.
  window        Like "screen", but finds the window by part of its title and follows it.
  video         A recorded gameplay file. For testing and tuning on real footage.
  images        A folder of still frames, in name order. For tests.

The Xbox app has no public API for its video frames, so Remote Play is watched the same way you
see it: by capturing the screen area it is drawn in. Whether the stream can be captured depends on
the Xbox app; if a capture comes out black, use a capture card.
"""

from __future__ import annotations

import time
from pathlib import Path
from typing import Iterator

import cv2
import numpy as np

Frame = tuple[float, np.ndarray]   # (seconds, BGR image)


class SourceError(Exception):
    pass


class FrameSource:
    description = "source"

    def frames(self) -> Iterator[Frame]:
        raise NotImplementedError

    def close(self) -> None:
        pass


class CaptureCardSource(FrameSource):
    def __init__(self, device_index: int = 0, width: int = 1920, height: int = 1080):
        self.description = f"capture device #{device_index}"
        self.cap = cv2.VideoCapture(device_index, cv2.CAP_DSHOW)
        if not self.cap.isOpened():
            self.cap.release()
            raise SourceError(
                f"Video device #{device_index} could not be opened. Check the capture card is "
                "plugged in and not in use by OBS or the vendor's app, or try another device index "
                "(run the 'devices' command)."
            )
        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, width)
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, height)

    def frames(self) -> Iterator[Frame]:
        failures = 0
        while True:
            ok, frame = self.cap.read()
            if not ok or frame is None:
                failures += 1
                if failures > 50:
                    raise SourceError("The capture device stopped sending video.")
                time.sleep(0.1)
                continue
            failures = 0
            yield time.monotonic(), frame

    def close(self) -> None:
        self.cap.release()


def probe_devices(max_index: int = 6) -> list[tuple[int, int, int]]:
    """(index, width, height) for each video device that opens and returns a frame."""
    found = []
    for i in range(max_index):
        cap = cv2.VideoCapture(i, cv2.CAP_DSHOW)
        try:
            if cap.isOpened():
                ok, frame = cap.read()
                if ok and frame is not None:
                    found.append((i, frame.shape[1], frame.shape[0]))
        finally:
            cap.release()
    return found


class ScreenSource(FrameSource):
    def __init__(self, monitor: int = 1, region: list[int] | None = None):
        import mss

        self._mss = mss.mss()
        if region:
            left, top, width, height = region
            self.area = {"left": left, "top": top, "width": width, "height": height}
        else:
            try:
                self.area = self._mss.monitors[monitor]
            except IndexError:
                raise SourceError(f"There is no monitor {monitor}.") from None
        self.description = f"screen area {self.area}"

    def _grab(self) -> np.ndarray:
        shot = self._mss.grab(self.area)
        return cv2.cvtColor(np.asarray(shot), cv2.COLOR_BGRA2BGR)

    def frames(self) -> Iterator[Frame]:
        while True:
            yield time.monotonic(), self._grab()

    def close(self) -> None:
        self._mss.close()


def find_window(title_part: str) -> tuple[int, int, int, int] | None:
    """Screen rectangle (left, top, width, height) of the first visible window whose title contains title_part."""
    import ctypes
    from ctypes import wintypes

    user32 = ctypes.windll.user32
    found: list[int] = []

    @ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    def enum(hwnd, _):
        if user32.IsWindowVisible(hwnd):
            n = user32.GetWindowTextLengthW(hwnd)
            if n:
                buf = ctypes.create_unicode_buffer(n + 1)
                user32.GetWindowTextW(hwnd, buf, n + 1)
                if title_part.lower() in buf.value.lower():
                    found.append(hwnd)
                    return False
        return True

    user32.EnumWindows(enum, 0)
    if not found:
        return None
    rect = wintypes.RECT()
    user32.GetWindowRect(found[0], ctypes.byref(rect))
    w, h = rect.right - rect.left, rect.bottom - rect.top
    if w <= 0 or h <= 0:
        return None
    return rect.left, rect.top, w, h


class WindowSource(ScreenSource):
    def __init__(self, title_part: str):
        rect = find_window(title_part)
        if rect is None:
            raise SourceError(f"No open window has '{title_part}' in its title. Open the Xbox app and start Remote Play first.")
        super().__init__(region=list(rect))
        self.title_part = title_part
        self.description = f"window '{title_part}'"

    def frames(self) -> Iterator[Frame]:
        last_check = 0.0
        while True:
            now = time.monotonic()
            if now - last_check > 2.0:   # follow the window if it is moved or resized
                rect = find_window(self.title_part)
                if rect:
                    left, top, width, height = rect
                    self.area = {"left": left, "top": top, "width": width, "height": height}
                last_check = now
            yield now, self._grab()


class VideoFileSource(FrameSource):
    def __init__(self, path: str, realtime: bool = True):
        if not Path(path).is_file():
            raise SourceError(f"Video file not found: {path}")
        self.cap = cv2.VideoCapture(path)
        if not self.cap.isOpened():
            raise SourceError(f"OpenCV cannot read {path}")
        self.fps = self.cap.get(cv2.CAP_PROP_FPS) or 30.0
        self.realtime = realtime
        self.description = f"video {Path(path).name}"

    def frames(self) -> Iterator[Frame]:
        start = time.monotonic()
        i = 0
        while True:
            ok, frame = self.cap.read()
            if not ok:
                return
            t = i / self.fps            # video time, so results do not depend on machine speed
            if self.realtime:
                delay = start + t - time.monotonic()
                if delay > 0:
                    time.sleep(delay)
            i += 1
            yield t, frame

    def close(self) -> None:
        self.cap.release()


class ImageFolderSource(FrameSource):
    def __init__(self, folder: str, fps: float = 4.0):
        self.files = sorted(p for p in Path(folder).iterdir() if p.suffix.lower() in (".png", ".jpg", ".jpeg"))
        if not self.files:
            raise SourceError(f"No images in {folder}")
        self.fps = fps
        self.description = f"images in {folder}"

    def frames(self) -> Iterator[Frame]:
        for i, p in enumerate(self.files):
            img = cv2.imdecode(np.fromfile(str(p), dtype=np.uint8), cv2.IMREAD_COLOR)
            if img is not None:
                yield i / self.fps, img


def from_config(src: dict) -> FrameSource:
    kind = src.get("type", "capture_card")
    if kind == "capture_card":
        return CaptureCardSource(int(src.get("device_index", 0)), int(src.get("width", 1920)), int(src.get("height", 1080)))
    if kind == "screen":
        return ScreenSource(int(src.get("monitor", 1)), src.get("region"))
    if kind == "window":
        return WindowSource(src.get("window_title") or "Xbox")
    if kind == "video":
        return VideoFileSource(src.get("path", ""), bool(src.get("realtime", True)))
    if kind == "images":
        return ImageFolderSource(src.get("path", ""))
    raise SourceError(f"Unknown source type '{kind}'")
