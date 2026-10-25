"""
Finding teammates' names on the HUD without reading them.

A co-op HUD lists each teammate as a row: an icon, their name and a health bar. This finds the name
lines from their shape alone, so it works even when the picture is too small for the names to be
read:

  * white pixels are grouped into letter-sized shapes, and letters that sit side by side on one
    baseline become a line of at least four letters;
  * long, solid, thin shapes are bars and big patches are background, never text.

Rows are found while names are **white** (alive or downed): there the letters are crisp and
separate. A dead teammate's red name is then judged on exactly those letter pixels, so red scenery
behind the HUD, scan lines or a downed teammate's red bar can't be mistaken for it.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import cv2
import numpy as np


def masks(bgr: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """(red, white) boolean masks of HUD text colours."""
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV).astype(np.int16)
    h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    red = ((h <= 10) | (h >= 170)) & (s >= 100) & (v >= 90)
    white = (s <= 70) & (v >= 165)
    return red, white


@dataclass
class NameLine:
    x0: int
    y0: int
    x1: int
    y1: int
    letters: np.ndarray = field(repr=False)     # the white pixels of the name, box-sized

    @property
    def cy(self) -> float:
        return (self.y0 + self.y1) / 2

    @property
    def h(self) -> int:
        return self.y1 - self.y0


def find_white_names(area_bgr: np.ndarray, frame_h: int, min_letters: int = 4) -> list[NameLine]:
    """White text lines (names) in the area, top to bottom. frame_h is the full frame's height."""
    if area_bgr.size == 0:
        return []
    _, white = masks(area_bgr)
    m = white.astype(np.uint8)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(m, 8)
    min_h = max(3, int(round(frame_h * 0.007)))
    max_h = max(min_h + 2, int(round(frame_h * 0.05)))
    letters = []
    for i in range(1, n):
        x, y, w, h, a = stats[i]
        if not (min_h <= h <= max_h) or a < 3:
            continue
        fill = a / float(w * h)
        if w <= 2.2 * h and fill < 0.9:
            letters.append((x, y, w, h))
    # Group letters into lines: same baseline region, small gaps.
    letters.sort(key=lambda b: b[0])
    lines: list[list[tuple]] = []
    for b in letters:
        cy = b[1] + b[3] / 2
        for line in lines:
            lx, ly, lw, lh = line[-1]
            lcy = ly + lh / 2
            if abs(cy - lcy) <= 0.45 * max(lh, b[3]) and 0 <= b[0] - (lx + lw) <= 1.8 * max(lh, b[3]):
                line.append(b)
                break
        else:
            lines.append([b])
    boxes = []
    for line in lines:
        if len(line) >= min_letters:
            x0 = min(b[0] for b in line)
            y0 = min(b[1] for b in line)
            x1 = max(b[0] + b[2] for b in line)
            y1 = max(b[1] + b[3] for b in line)
            if x1 - x0 >= 3 * (y1 - y0):
                boxes.append((x0, y0, x1, y1))
    out = [NameLine(x0, y0, x1, y1, white[y0:y1, x0:x1].copy()) for x0, y0, x1, y1 in boxes]
    out.sort(key=lambda r: r.y0)
    return out


def judge(area_bgr: np.ndarray, line: NameLine, icon_left: bool = True) -> tuple[str, float, float]:
    """
    ("red" | "white" | "none", red share, white share) for a name line's remembered letters, plus
    the icon to their left (Dying Light's skull turns red too). Looks only at those pixels.
    """
    red, white = masks(area_bgr)
    H, W = red.shape
    pad = max(1, line.h // 6)
    y0, y1 = max(0, line.y0 - pad), min(H, line.y1 + pad)
    letters = np.zeros((y1 - y0, line.x1 - line.x0), bool)
    ly = line.y0 - y0
    letters[ly:ly + line.h, :] = line.letters[: y1 - y0 - ly, :]
    letters = cv2.dilate(letters.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool)
    r_box, w_box = red[y0:y1, line.x0:line.x1], white[y0:y1, line.x0:line.x1]
    if r_box.shape != letters.shape:
        return "none", 0.0, 0.0
    n = float(letters.sum()) or 1.0
    r = float((r_box & letters).sum()) / n
    w = float((w_box & letters).sum()) / n
    if icon_left:
        ix0 = max(0, line.x0 - int(line.h * 1.8))
        icon_r = red[y0:y1, ix0:line.x0].mean() if line.x0 > ix0 else 0.0
        r = min(1.0, r + 0.5 * float(icon_r))
    if r >= 0.3 and r > w:
        return "red", r, w
    if w >= 0.3:
        return "white", r, w
    return "none", r, w
