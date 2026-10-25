"""The live preview: capture on its own thread, MJPEG over a local-only, token-guarded server."""

import threading
import time
import urllib.error
import urllib.request

import numpy as np
import pytest

from deadfest_detector.live import FrameHub, PreviewServer, ThreadedSource
from deadfest_detector.sources import FrameSource


class FastSource(FrameSource):
    """A 'live' source producing numbered frames at a fixed rate."""
    description = "test source"

    def __init__(self, fps=120):
        self.fps = fps
        self.closed = False

    def frames(self):
        i = 0
        while not self.closed:
            frame = np.full((90, 160, 3), i % 256, np.uint8)
            yield time.monotonic(), frame
            i += 1
            time.sleep(1 / self.fps)

    def close(self):
        self.closed = True


def test_hub_hands_out_the_newest_frame():
    hub = FrameHub()
    assert hub.wait_next(0, timeout=0.05) is None
    hub.publish(1.0, np.zeros((2, 2, 3), np.uint8))
    hub.publish(2.0, np.ones((2, 2, 3), np.uint8))
    seq, t, frame = hub.wait_next(0)
    assert seq == 2 and t == 2.0 and frame.max() == 1


def test_a_slow_consumer_never_slows_capture():
    hub = FrameHub()
    src = ThreadedSource(FastSource(fps=120), hub)
    got = []
    start = time.monotonic()
    for t, frame in src.frames():
        got.append(frame[0, 0, 0])
        time.sleep(0.1)              # pretend OCR takes 100 ms per analysed frame
        if time.monotonic() - start > 1.2:
            break
    src.close()
    # The consumer saw ~10 frames, while capture kept running at full rate for the preview.
    assert 5 <= len(got) <= 14
    assert hub.seq > 60


def test_capture_rate_is_measured():
    hub = FrameHub()
    src = ThreadedSource(FastSource(fps=60), hub).start()
    time.sleep(1.5)
    fps = hub.fps()
    src.close()
    assert 35 < fps < 80


def read_mjpeg_frames(url, n, timeout=5):
    """The first n JPEG parts of an MJPEG stream, with the time each arrived."""
    resp = urllib.request.urlopen(url, timeout=timeout)
    assert resp.headers["Content-Type"].startswith("multipart/x-mixed-replace")
    frames, buf = [], b""
    while len(frames) < n:
        buf += resp.read1(65536)
        while True:
            start = buf.find(b"\xff\xd8")
            end = buf.find(b"\xff\xd9", start + 2)
            if start < 0 or end < 0:
                break
            frames.append((time.monotonic(), buf[start:end + 2]))
            buf = buf[end + 2:]
    resp.close()
    return frames


def test_preview_streams_jpegs_at_the_chosen_rate():
    hub = FrameHub()
    viewers = []
    server = PreviewServer(hub, on_viewer=viewers.append)
    src = ThreadedSource(FastSource(fps=120), hub).start()
    try:
        frames = read_mjpeg_frames(server.url(fps=20), 21)
        span = frames[-1][0] - frames[0][0]
        rate = (len(frames) - 1) / span
        assert 14 < rate < 26, rate            # capped near 20 even though capture runs at ~120
        assert all(f[1][:2] == b"\xff\xd8" for f in frames)
        frames60 = read_mjpeg_frames(server.url(fps=60), 61)
        rate60 = (len(frames60) - 1) / (frames60[-1][0] - frames60[0][0])
        assert rate60 > 40, rate60
        assert True in viewers                  # the app is told when someone starts watching
    finally:
        src.close()
        server.close()


def test_preview_needs_the_token_and_only_listens_locally():
    hub = FrameHub()
    server = PreviewServer(hub)
    try:
        assert server.httpd.server_address[0] == "127.0.0.1"
        bad = server.url(30).replace(server.token, "wrong")
        with pytest.raises(urllib.error.HTTPError) as e:
            urllib.request.urlopen(bad, timeout=3)
        assert e.value.code == 403
    finally:
        server.close()
