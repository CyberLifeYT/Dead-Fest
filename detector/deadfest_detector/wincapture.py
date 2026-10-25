"""
Capturing the Xbox app's Remote Play stream (and other windows) on Windows 10/11.

How the Xbox app looks to Windows: a frame window titled "Xbox" that belongs to
ApplicationFrameHost.exe (the host Windows uses for Store apps), with the app's content in a child
window owned by XboxPcApp.exe. So the window is found by that owner, not by its title, which also
matches Game Bar widgets, browser tabs and anything else called "Xbox".

Store apps draw with DirectComposition, which the classic PrintWindow call sees as solid black.
Windows Graphics Capture (the API OBS's window capture uses) sees the real picture, and keeps
working while other windows cover it. It cannot capture a minimised window; that is reported
rather than producing black frames.

Every coordinate here is in physical pixels: the process is made per-monitor DPI aware first, so
display scaling (125 %, 150 %...) does not shift or shrink the captured area.
"""

from __future__ import annotations

import ctypes
import ntpath
import threading
import time
from ctypes import wintypes
from dataclasses import dataclass
from typing import Iterator

import numpy as np

XBOX_APP_EXE = "XboxPcApp.exe"
FRAME_HOST_EXE = "ApplicationFrameHost.exe"

_dpi_done = False


def make_dpi_aware() -> None:
    """Per-monitor DPI awareness, so window rectangles and captured pixels agree."""
    global _dpi_done
    if _dpi_done:
        return
    _dpi_done = True
    try:
        ctypes.windll.user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))  # PER_MONITOR_AWARE_V2
    except (AttributeError, OSError):
        try:
            ctypes.windll.shcore.SetProcessDpiAwareness(2)
        except (AttributeError, OSError):
            pass


@dataclass
class WindowInfo:
    hwnd: int
    title: str
    exe: str
    class_name: str
    visible: bool
    minimized: bool
    cloaked: bool
    rect: tuple[int, int, int, int]          # left, top, right, bottom (visible frame, physical px)
    children: list["WindowInfo"]


def _user32():
    return ctypes.windll.user32


def _exe_of(hwnd: int) -> str:
    pid = wintypes.DWORD()
    _user32().GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    k = ctypes.windll.kernel32
    handle = k.OpenProcess(0x1000, False, pid.value)   # PROCESS_QUERY_LIMITED_INFORMATION
    if not handle:
        return ""
    try:
        buf = ctypes.create_unicode_buffer(520)
        size = wintypes.DWORD(520)
        if k.QueryFullProcessImageNameW(handle, 0, buf, ctypes.byref(size)):
            return ntpath.basename(buf.value)
        return ""
    finally:
        k.CloseHandle(handle)


def _text(hwnd: int) -> str:
    buf = ctypes.create_unicode_buffer(256)
    _user32().GetWindowTextW(hwnd, buf, 256)
    return buf.value


def _class(hwnd: int) -> str:
    buf = ctypes.create_unicode_buffer(256)
    _user32().GetClassNameW(hwnd, buf, 256)
    return buf.value


def _frame_rect(hwnd: int) -> tuple[int, int, int, int]:
    """The visible frame (without the invisible resize border), which is what a capture shows."""
    rect = wintypes.RECT()
    if ctypes.windll.dwmapi.DwmGetWindowAttribute(hwnd, 9, ctypes.byref(rect), ctypes.sizeof(rect)) != 0:
        _user32().GetWindowRect(hwnd, ctypes.byref(rect))
    return rect.left, rect.top, rect.right, rect.bottom


def _cloaked(hwnd: int) -> bool:
    value = wintypes.DWORD()
    ctypes.windll.dwmapi.DwmGetWindowAttribute(hwnd, 14, ctypes.byref(value), ctypes.sizeof(value))
    return value.value != 0


def _info(hwnd: int, with_children: bool = False) -> WindowInfo:
    u = _user32()
    children: list[WindowInfo] = []
    if with_children:
        kids: list[int] = []

        @ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
        def each(child, _):
            kids.append(child)
            return True

        u.EnumChildWindows(hwnd, each, 0)
        children = [_info(c) for c in kids]
    return WindowInfo(
        hwnd=hwnd, title=_text(hwnd), exe=_exe_of(hwnd), class_name=_class(hwnd),
        visible=bool(u.IsWindowVisible(hwnd)), minimized=bool(u.IsIconic(hwnd)),
        cloaked=_cloaked(hwnd), rect=_frame_rect(hwnd), children=children,
    )


def top_level_windows() -> list[WindowInfo]:
    make_dpi_aware()
    handles: list[int] = []

    @ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    def each(hwnd, _):
        handles.append(hwnd)
        return True

    _user32().EnumWindows(each, 0)
    return [_info(h, with_children=True) for h in handles if _user32().IsWindowVisible(h)]


def is_xbox_app(w: WindowInfo) -> bool:
    """The Xbox app's main window: framed by ApplicationFrameHost around XboxPcApp content, or owned directly."""
    if w.exe == XBOX_APP_EXE and w.rect[2] - w.rect[0] > 200 and w.rect[3] - w.rect[1] > 150:
        return True
    if w.exe != FRAME_HOST_EXE:
        return False
    # Minimised Store apps can have their content detached from the frame, leaving just the title.
    return any(c.exe == XBOX_APP_EXE for c in w.children) or (w.minimized and w.title.lower() == "xbox")


def pick_xbox_window(windows: list[WindowInfo]) -> WindowInfo | None:
    """The best Xbox app window: shown windows before minimised ones, then the largest."""
    candidates = [w for w in windows if is_xbox_app(w) and not w.cloaked]
    if not candidates:
        return None
    return max(candidates, key=lambda w: (not w.minimized, (w.rect[2] - w.rect[0]) * (w.rect[3] - w.rect[1])))


def find_window_by_title(title_part: str) -> WindowInfo | None:
    matches = [w for w in top_level_windows() if title_part.lower() in w.title.lower() and not w.cloaked]
    exact = [w for w in matches if w.title.lower() == title_part.lower()]
    pool = exact or matches
    return max(pool, key=lambda w: (not w.minimized, (w.rect[2] - w.rect[0]) * (w.rect[3] - w.rect[1]))) if pool else None


# ---------------------------------------------------------------------- cropping

def content_box(w: WindowInfo, frame_w: int, frame_h: int) -> tuple[int, int, int, int]:
    """
    The app's own content inside a captured frame, i.e. without the title bar a frame host draws.
    For a framed Store app that is the XboxPcApp child window's rectangle; otherwise the whole frame.
    """
    left, top, right, bottom = w.rect
    content = next((c for c in w.children if c.exe == XBOX_APP_EXE and c.rect[2] > c.rect[0]), None)
    if content is None or right <= left or bottom <= top:
        return 0, 0, frame_w, frame_h
    sx, sy = frame_w / (right - left), frame_h / (bottom - top)
    cl, ct, cr, cb = content.rect
    x0 = max(0, int(round((cl - left) * sx)))
    y0 = max(0, int(round((ct - top) * sy)))
    x1 = min(frame_w, int(round((cr - left) * sx)))
    y1 = min(frame_h, int(round((cb - top) * sy)))
    if x1 - x0 < 64 or y1 - y0 < 64:
        return 0, 0, frame_w, frame_h
    return x0, y0, x1, y1


def fit_16_9(box: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    """The largest centred 16:9 rectangle inside box: where the letterboxed stream is drawn."""
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    if w * 9 > h * 16:            # wider than 16:9: bars left and right
        nw = h * 16 // 9
        x0 += (w - nw) // 2
        return x0, y0, x0 + nw, y1
    nh = w * 9 // 16              # taller than 16:9: bars top and bottom
    y0 += (h - nh) // 2
    return x0, y0, x1, y0 + nh


# ---------------------------------------------------------------------- capture

class WindowCaptureError(Exception):
    pass


class GraphicsCapture:
    """Windows Graphics Capture of one window; keeps only the newest frame (BGR)."""

    def __init__(self, hwnd: int, fps: float = 10.0):
        from windows_capture import WindowsCapture   # Windows only; imported here for portability

        self.hwnd = hwnd
        self._lock = threading.Lock()
        self._frame: np.ndarray | None = None
        self._count = 0
        self.closed = threading.Event()
        interval = max(1, int(1000 / max(1.0, fps)))
        try:
            self._capture = WindowsCapture(cursor_capture=False, draw_border=False,
                                           minimum_update_interval=interval, window_hwnd=hwnd)
        except Exception:  # noqa: BLE001 - older Windows 10 builds can't hide the capture border
            self._capture = WindowsCapture(cursor_capture=False, minimum_update_interval=interval, window_hwnd=hwnd)

        @self._capture.event
        def on_frame_arrived(frame, control):
            img = np.array(frame.frame_buffer[:, :, :3])   # copy: the native buffer is reused
            with self._lock:
                self._frame = img
                self._count += 1

        @self._capture.event
        def on_closed():
            self.closed.set()

        try:
            self._control = self._capture.start_free_threaded()
        except Exception as e:  # noqa: BLE001
            raise WindowCaptureError(f"Windows Graphics Capture could not start: {e}") from e

    def latest(self) -> tuple[int, np.ndarray | None]:
        with self._lock:
            return self._count, self._frame

    def stop(self) -> None:
        try:
            self._control.stop()
        except Exception:  # noqa: BLE001
            pass


def capture_frames(
    find,
    *,
    fps: float = 10.0,
    crop_content: bool = True,
    crop_16_9: bool = True,
    describe_missing: str,
    on_status=print,
) -> Iterator[tuple[float, np.ndarray]]:
    """
    Yields (seconds, BGR frame) from the window find() returns, re-finding it if it closes or is
    reopened, and pausing (with a message) while it is minimised or missing.
    """
    make_dpi_aware()
    period = 1.0 / max(1.0, fps)
    session: GraphicsCapture | None = None
    window: WindowInfo | None = None
    last_find = 0.0
    last_count = -1
    last_status = ""
    last_status_at = 0.0

    def status(msg: str) -> None:
        nonlocal last_status, last_status_at
        now = time.monotonic()
        if msg != last_status or now - last_status_at > 30:
            on_status(msg)
            last_status, last_status_at = msg, now

    try:
        while True:
            now = time.monotonic()
            if session is None or session.closed.is_set() or now - last_find > 2.0:
                found = find()
                last_find = now
                if found is None:
                    if session:
                        session.stop()
                        session = None
                    status(describe_missing)
                    time.sleep(1.0)
                    continue
                if found.minimized:
                    status("The Xbox app is minimised. Restore it (it can sit behind other windows, just not minimised).")
                    time.sleep(1.0)
                    continue
                if session is None or session.closed.is_set() or found.hwnd != session.hwnd:
                    if session:
                        session.stop()
                    session = GraphicsCapture(found.hwnd, fps=fps)
                    last_count = -1
                    status(f"Capturing '{found.title or found.exe}' ({found.rect[2] - found.rect[0]}x{found.rect[3] - found.rect[1]}).")
                window = found

            count, frame = session.latest()
            if frame is None or count == last_count:
                time.sleep(period / 2)
                continue
            last_count = count
            h, w = frame.shape[:2]
            box = content_box(window, w, h) if crop_content else (0, 0, w, h)
            if crop_16_9:
                box = fit_16_9(box)
            x0, y0, x1, y1 = box
            yield time.monotonic(), frame[y0:y1, x0:x1]
            time.sleep(period)
    finally:
        if session:
            session.stop()
