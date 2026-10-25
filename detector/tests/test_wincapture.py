"""The Xbox app window: finding it, cropping it, and capturing a real window with Windows Graphics Capture."""

import os
import threading
import time
import uuid

import pytest

from deadfest_detector import wincapture
from deadfest_detector.wincapture import WindowInfo, content_box, fit_16_9, is_xbox_app, pick_xbox_window


def win(exe, title="", rect=(0, 0, 1026, 834), minimized=False, cloaked=False, children=()):
    return WindowInfo(hwnd=hash((exe, title, rect)) & 0xFFFF, title=title, exe=exe, class_name="", visible=True,
                      minimized=minimized, cloaked=cloaked, rect=rect, children=list(children))


XBOX_CONTENT = win("XboxPcApp.exe", "XBOX", rect=(1, 33, 1025, 833))
XBOX_FRAME = win("ApplicationFrameHost.exe", "XBOX", children=[win("ApplicationFrameHost.exe"), XBOX_CONTENT])


def test_the_framed_store_app_is_recognised_by_its_owner_not_its_title():
    assert is_xbox_app(XBOX_FRAME)
    assert not is_xbox_app(win("XboxGameBarWidgets.exe", "Xbox"))
    assert not is_xbox_app(win("msedge.exe", "Xbox - Microsoft Edge"))
    assert not is_xbox_app(win("ApplicationFrameHost.exe", "Calculator", children=[win("CalculatorApp.exe")]))


def test_a_minimised_xbox_app_is_still_found_so_it_can_be_reported():
    minimised = win("ApplicationFrameHost.exe", "Xbox", minimized=True)
    assert pick_xbox_window([minimised]) is minimised


def test_a_visible_window_beats_a_minimised_one_and_cloaked_ones_are_ignored():
    hidden = win("ApplicationFrameHost.exe", "XBOX", minimized=True, rect=(0, 0, 3000, 2000), children=[XBOX_CONTENT])
    other_desktop = win("ApplicationFrameHost.exe", "XBOX", cloaked=True, children=[XBOX_CONTENT])
    assert pick_xbox_window([hidden, other_desktop, XBOX_FRAME]) is XBOX_FRAME
    assert pick_xbox_window([win("XboxGameBarWidgets.exe", "Xbox")]) is None


def test_content_box_drops_the_title_bar_the_frame_host_draws():
    assert content_box(XBOX_FRAME, 1026, 834) == (1, 33, 1025, 833)


def test_content_box_scales_with_the_captured_frame():
    # A capture at a different size than the window rect (e.g. a DPI mismatch) still lines up.
    x0, y0, x1, y1 = content_box(XBOX_FRAME, 2052, 1668)
    assert (x0, y0, x1, y1) == (2, 66, 2050, 1666)


@pytest.mark.parametrize("box,expected", [
    ((0, 0, 1920, 1080), (0, 0, 1920, 1080)),       # already 16:9
    ((1, 33, 1025, 833), (1, 145, 1025, 721)),     # taller window: bars top and bottom
    ((0, 0, 2560, 1080), (320, 0, 2240, 1080)),    # ultrawide: bars left and right
])
def test_fit_16_9_finds_the_letterboxed_stream(box, expected):
    x0, y0, x1, y1 = fit_16_9(box)
    assert (x0, y0, x1, y1) == expected
    assert abs((x1 - x0) / (y1 - y0) - 16 / 9) < 0.01


def _wgc_available() -> bool:
    if os.name != "nt":
        return False
    try:
        import windows_capture  # noqa: F401
        import tkinter  # noqa: F401
        return True
    except ImportError:
        return False


@pytest.mark.skipif(not _wgc_available() or bool(os.environ.get("CI")), reason="needs a Windows desktop session, Graphics Capture and Tk")
def test_graphics_capture_of_a_real_window_feeds_detection(tmp_path):
    import tkinter as tk

    import cv2

    from conftest import death_frame, windows_ocr_available
    from deadfest_detector.sources import WindowSource, first_frame, looks_blank

    image = tmp_path / "death.png"
    cv2.imwrite(str(image), cv2.resize(death_frame(4), (960, 540)))
    title = f"DeadFest capture test {uuid.uuid4().hex[:8]}"
    root = tk.Tk()
    root.title(title)
    photo = tk.PhotoImage(file=str(image))
    tk.Label(root, image=photo, borderwidth=0).pack()
    root.geometry("+40+40")
    root.update()
    try:
        source = WindowSource(title, fps=10)
        result = {}

        def grab():
            try:
                result["frame"] = first_frame(source, timeout=15)
            except Exception as e:  # noqa: BLE001
                result["error"] = e

        t = threading.Thread(target=grab, daemon=True)
        t.start()
        deadline = time.monotonic() + 20
        while t.is_alive() and time.monotonic() < deadline:
            root.update()          # keep the window painting while it is captured
            time.sleep(0.02)
        assert "frame" in result, result.get("error")
        frame = result["frame"]
        assert frame.shape[0] >= 500 and frame.shape[1] >= 900
        assert not looks_blank(frame)

        if windows_ocr_available():
            from deadfest_detector import games
            from deadfest_detector.detect import Analyzer
            from deadfest_detector.ocr import WindowsOcr

            cands = Analyzer(games.compile_profile(games.get("dying_light_2")), WindowsOcr()).analyze(frame)
            assert [c.event_type for c in cands] == ["self"]
    finally:
        root.destroy()


def test_dpi_awareness_is_safe_to_call_repeatedly():
    if os.name == "nt":
        wincapture.make_dpi_aware()
        wincapture.make_dpi_aware()
