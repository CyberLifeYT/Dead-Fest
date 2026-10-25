"""
The smooth live preview: frames captured on their own thread and streamed to the window as MJPEG.

  FrameHub        the newest frame, plus a counter so readers can wait for the next one
  ThreadedSource  reads a live source (Xbox app, capture card) on a dedicated thread at full rate and
                  publishes every frame to the hub; detection takes the newest frame when it's ready
                  for one, so slow OCR never makes the preview stutter
  PreviewServer   a tiny HTTP server on 127.0.0.1 only, with a random port and a secret token, that
                  streams the hub as MJPEG at the chosen frame rate; nothing is reachable from the
                  network
"""

from __future__ import annotations

import secrets
import threading
import time
from collections import deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable, Iterator
from urllib.parse import parse_qs, urlparse

import cv2
import numpy as np

from .sources import FrameSource

LIVE_SOURCES = {"xbox_app", "capture_card", "screen", "window"}


class FrameHub:
    def __init__(self):
        self._cond = threading.Condition()
        self._frame: np.ndarray | None = None
        self._t = 0.0
        self.seq = 0
        self._stamps: deque[float] = deque(maxlen=120)

    def publish(self, t: float, frame: np.ndarray) -> None:
        with self._cond:
            self._frame, self._t = frame, t
            self.seq += 1
            self._stamps.append(time.monotonic())
            self._cond.notify_all()

    def wait_next(self, after_seq: int, timeout: float = 1.0) -> tuple[int, float, np.ndarray] | None:
        with self._cond:
            # Wait while there's nothing new, including when there's no frame at all yet: returning
            # straight away then made the stream loop spin and starve the capture thread.
            if self.seq <= after_seq or self._frame is None:
                self._cond.wait(timeout)
            if self.seq <= after_seq or self._frame is None:
                return None
            return self.seq, self._t, self._frame

    def latest(self) -> np.ndarray | None:
        with self._cond:
            return None if self._frame is None else self._frame.copy()

    def fps(self) -> float:
        """Frames per second actually captured over the last ~2 seconds."""
        with self._cond:
            now = time.monotonic()
            recent = [s for s in self._stamps if now - s <= 2.0]
        if len(recent) < 2:
            return 0.0
        return (len(recent) - 1) / max(1e-6, recent[-1] - recent[0])

    def clear(self) -> None:
        with self._cond:
            self._frame = None
            self._stamps.clear()


class ThreadedSource(FrameSource):
    """A live source read on its own thread; consumers get the newest frame, never a backlog."""

    def __init__(self, source: FrameSource, hub: FrameHub, on_error: Callable[[Exception], None] | None = None):
        self.source = source
        self.hub = hub
        self.description = source.description
        self.on_error = on_error
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._closed = False
        self.error: Exception | None = None

    def start(self) -> "ThreadedSource":
        if self._thread is None:
            self._thread = threading.Thread(target=self._read, daemon=True)
            self._thread.start()
        return self

    def _read(self) -> None:
        frames = self.source.frames()
        try:
            for t, frame in frames:
                self.hub.publish(t, frame)
                if self._stop.is_set():
                    break
        except Exception as e:  # noqa: BLE001 - reported to whoever consumes the frames
            self.error = e
            if self.on_error:
                self.on_error(e)
        finally:
            try:
                frames.close()
            except Exception:  # noqa: BLE001
                pass
            self._stop.set()

    def frames(self) -> Iterator[tuple[float, np.ndarray]]:
        self.start()
        seq = self.hub.seq
        while not self._stop.is_set():
            got = self.hub.wait_next(seq, timeout=1.0)
            if got is None:
                continue
            seq, t, frame = got
            yield t, frame
        if self.error:
            raise self.error

    def close(self) -> None:
        self._stop.set()
        if self._thread and self._thread is not threading.current_thread():
            self._thread.join(timeout=3)
        if not self._closed:
            self._closed = True
            self.source.close()

    @property
    def running(self) -> bool:
        return self._thread is not None and not self._stop.is_set()


def encode_jpeg(frame: np.ndarray, max_width: int = 1280, quality: int = 72) -> bytes:
    h, w = frame.shape[:2]
    if w > max_width:
        frame = cv2.resize(frame, (max_width, int(h * max_width / w)), interpolation=cv2.INTER_AREA)
    ok, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, quality])
    return buf.tobytes() if ok else b""


class PreviewServer:
    """MJPEG over HTTP on 127.0.0.1, guarded by a per-run token."""

    def __init__(self, hub: FrameHub, on_viewer: Callable[[bool], None] | None = None):
        self.hub = hub
        self.token = secrets.token_urlsafe(16)
        self.on_viewer = on_viewer or (lambda active: None)
        self.viewers = 0
        self._lock = threading.Lock()
        server = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):   # keep the console quiet
                pass

            def do_GET(self):
                url = urlparse(self.path)
                q = parse_qs(url.query)
                if q.get("token", [""])[0] != server.token:
                    self.send_error(403)
                    return
                if url.path == "/stream":
                    fps = max(1.0, min(120.0, float(q.get("fps", ["30"])[0] or 30)))
                    width = max(320, min(1920, int(q.get("w", ["1280"])[0] or 1280)))
                    server._stream(self, fps, width)
                elif url.path == "/frame.jpg":
                    frame = server.hub.latest()
                    if frame is None:
                        self.send_error(404)
                        return
                    data = encode_jpeg(frame)
                    self.send_response(200)
                    self.send_header("Content-Type", "image/jpeg")
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
                else:
                    self.send_error(404)

        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.httpd.daemon_threads = True
        self.port = self.httpd.server_address[1]
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()

    def url(self, fps: float, width: int = 1280) -> str:
        return f"http://127.0.0.1:{self.port}/stream?token={self.token}&fps={fps:g}&w={width}"

    def _viewer(self, delta: int) -> None:
        with self._lock:
            self.viewers += delta
            active = self.viewers > 0
        self.on_viewer(active)

    def _stream(self, handler: BaseHTTPRequestHandler, fps: float, width: int) -> None:
        handler.send_response(200)
        handler.send_header("Content-Type", "multipart/x-mixed-replace; boundary=frame")
        handler.send_header("Cache-Control", "no-store")
        handler.end_headers()
        self._viewer(+1)
        period = 1.0 / fps
        seq = -1
        next_at = 0.0
        try:
            last_sent = 0.0
            while True:
                got = self.hub.wait_next(seq, timeout=1.0)
                if got is None:
                    # Nothing new (a still picture sends few frames): resend the current one now and
                    # then, because browsers may hold a stream's only frame back until the next one.
                    frame = self.hub.latest()
                    if frame is None or time.monotonic() - last_sent < 1.0:
                        continue
                else:
                    seq, _, frame = got
                now = time.monotonic()
                if now < next_at:
                    continue           # faster than the chosen rate: skip this frame
                next_at = max(next_at + period, now)
                data = encode_jpeg(frame, width)
                handler.wfile.write(b"--frame\r\nContent-Type: image/jpeg\r\nContent-Length: "
                                    + str(len(data)).encode() + b"\r\n\r\n" + data + b"\r\n")
                handler.wfile.flush()
                last_sent = time.monotonic()
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
            pass                       # the window stopped watching
        finally:
            self._viewer(-1)

    def close(self) -> None:
        self.httpd.shutdown()
