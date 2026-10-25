"""
Where frames come from.

  capture_card  An HDMI capture card (Elgato, AVerMedia, generic UVC dongles...). These appear in
                Windows as a webcam-style video device, which OpenCV reads through DirectShow.
                This is the dependable way to get Xbox video onto a PC.
  xbox_app      The Xbox app's Remote Play stream on this PC (Windows Graphics Capture). Found by
                its process, follows the window, keeps working while other windows cover it.
  screen        A region of a monitor (mss).
  window        Any other window, by part of its title (Windows Graphics Capture).
  video         A recorded gameplay file. For testing and tuning on real footage.
  images        A folder of still frames, in name order. For tests.

The Xbox app has no public API for its video frames, so Remote Play is captured the way OBS's window
capture does it: Windows Graphics Capture of the app's window. The app does not block capture
(its display affinity is "none"), so this sees exactly what you see.
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
            time.sleep(0.1)

    def close(self) -> None:
        self._mss.close()


class XboxAppSource(FrameSource):
    """The Xbox app's Remote Play stream, via Windows Graphics Capture (works while covered)."""

    def __init__(self, fps: float = 10.0, crop_16_9: bool = True):
        from . import wincapture

        self._wc = wincapture
        self.fps = fps
        self.crop_16_9 = crop_16_9
        self.description = "the Xbox app (Remote Play)"
        if wincapture.pick_xbox_window(wincapture.top_level_windows()) is None:
            raise SourceError(
                "The Xbox app isn't open. Open it, choose your console and start Remote Play, "
                "then try again. (It can be behind other windows, but not minimised.)"
            )

    def frames(self) -> Iterator[Frame]:
        wc = self._wc
        return wc.capture_frames(
            lambda: wc.pick_xbox_window(wc.top_level_windows()),
            fps=self.fps,
            crop_content=True,
            crop_16_9=self.crop_16_9,
            describe_missing="Waiting for the Xbox app window (was it closed?).",
        )


class WindowSource(FrameSource):
    """Any window by part of its title, via Windows Graphics Capture."""

    def __init__(self, title_part: str, fps: float = 10.0, crop_16_9: bool = False):
        from . import wincapture

        self._wc = wincapture
        self.title_part = title_part
        self.fps = fps
        self.crop_16_9 = crop_16_9
        self.description = f"window '{title_part}'"
        if wincapture.find_window_by_title(title_part) is None:
            raise SourceError(f"No open window has '{title_part}' in its title.")

    def frames(self) -> Iterator[Frame]:
        wc = self._wc
        return wc.capture_frames(
            lambda: wc.find_window_by_title(self.title_part),
            fps=self.fps,
            crop_content=False,
            crop_16_9=self.crop_16_9,
            describe_missing=f"Waiting for a window titled '{self.title_part}'.",
        )


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


def first_frame(source: FrameSource, timeout: float = 15.0) -> np.ndarray:
    """One frame from a source, giving up after timeout seconds (a minimised app never sends one)."""
    import threading

    result: dict = {}

    def grab():
        frames = source.frames()
        try:
            result["frame"] = next(frames)[1]
        except Exception as e:  # noqa: BLE001 - reported to the caller below
            result["error"] = e
        finally:
            try:
                frames.close()
            except Exception:  # noqa: BLE001
                pass

    t = threading.Thread(target=grab, daemon=True)
    t.start()
    t.join(timeout)
    if "frame" in result:
        return result["frame"]
    if "error" in result:
        err = result["error"]
        raise SourceError(str(err) or "The video source stopped.") from err
    raise SourceError(f"No picture from {source.description} within {timeout:.0f} seconds.")


def looks_blank(frame: np.ndarray) -> bool:
    """Almost uniformly black: nothing is being drawn, or the source is blocked."""
    small = cv2.resize(frame, (160, 90), interpolation=cv2.INTER_AREA)
    return float(small.mean()) < 8 and float(small.std()) < 6


def from_config(src: dict) -> FrameSource:
    kind = src.get("type", "capture_card")
    if kind == "capture_card":
        return CaptureCardSource(int(src.get("device_index", 0)), int(src.get("width", 1920)), int(src.get("height", 1080)))
    if kind == "screen":
        return ScreenSource(int(src.get("monitor", 1)), src.get("region"))
    if kind == "xbox_app":
        return XboxAppSource(float(src.get("capture_fps", 10)), bool(src.get("crop_16_9", True)))
    if kind == "window":
        return WindowSource(src.get("window_title") or "Xbox", float(src.get("capture_fps", 10)), bool(src.get("crop_16_9", False)))
    if kind == "video":
        return VideoFileSource(src.get("path", ""), bool(src.get("realtime", True)))
    if kind == "images":
        return ImageFolderSource(src.get("path", ""))
    raise SourceError(f"Unknown source type '{kind}'")
