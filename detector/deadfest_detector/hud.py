"""
HUD tracking: deaths read from the health bars, for games that never print "You died".

In co-op the HUD shows each player's name next to their health bar. You mark those boxes once in
the desktop app (HUD setup), with everyone at full health. From then on, every analysed frame:

  * each bar's fill is measured (the share of the bar still in its "full" colour);
  * a bar that was alive and then sits at (near) zero for `hold_seconds` is a death;
  * the name box is read with OCR now and then and matched to a Dead-Fest player, so a teammate's
    death is labelled with who it was.

Boxes are stored as fractions of the frame (0..1), so they keep working when the Xbox app window
is resized. A slot whose name box is empty (nobody in that slot) never produces a death.

Being downed (revivable) also empties a bar in some games. `hold_seconds` is how long a bar must
stay empty to count; set it longer than a revive takes in games that have a downed state.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import cv2
import numpy as np

from .detect import Candidate
from .games import SELF, TEAMMATE
from .ocr import OcrProvider

Box = tuple[float, float, float, float]   # x, y, w, h as fractions of the frame


def crop(frame: np.ndarray, box: Box) -> np.ndarray:
    h, w = frame.shape[:2]
    x, y, bw, bh = box
    x0, y0 = int(round(x * w)), int(round(y * h))
    x1, y1 = int(round((x + bw) * w)), int(round((y + bh) * h))
    x0, y0 = max(0, min(w - 1, x0)), max(0, min(h - 1, y0))
    x1, y1 = max(x0 + 1, min(w, x1)), max(y0 + 1, min(h, y1))
    return frame[y0:y1, x0:x1]


def reference_colour(bar_bgr: np.ndarray) -> tuple[float, float, float] | None:
    """The bar's 'full' colour (HSV median of its vivid pixels), sampled while everyone is at full health."""
    hsv = cv2.cvtColor(bar_bgr, cv2.COLOR_BGR2HSV).reshape(-1, 3).astype(np.float32)
    vivid = hsv[(hsv[:, 1] > 60) & (hsv[:, 2] > 70)]
    bright = hsv[(hsv[:, 1] <= 60) & (hsv[:, 2] > 170)]   # white/grey bars
    # Whichever kind of pixel fills the bar is its colour; a few stray edge pixels don't decide it.
    group = vivid if len(vivid) >= len(bright) else bright
    if len(group) < max(8, len(hsv) // 5):
        return None
    return float(np.median(group[:, 0])), float(np.median(group[:, 1])), float(np.median(group[:, 2]))


def bar_fill(bar_bgr: np.ndarray, ref: tuple[float, float, float] | None) -> float:
    """0..1: the share of the bar's columns still showing its full colour."""
    if bar_bgr.size == 0:
        return 0.0
    hsv = cv2.cvtColor(bar_bgr, cv2.COLOR_BGR2HSV).astype(np.int16)
    h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    if ref is None:
        mask = ((s > 70) & (v > 80)) | (v > 200)
    elif ref[1] < 50:   # a white/grey bar: match on brightness
        mask = (v > max(120, ref[2] * 0.7)) & (s < 80)
    else:
        dh = np.abs(h - int(ref[0]))
        dh = np.minimum(dh, 180 - dh)
        mask = (dh < 14) & (s > ref[1] * 0.45) & (v > ref[2] * 0.45)
    columns = mask.mean(axis=0) > 0.35
    return float(columns.mean())


@dataclass
class BarSpec:
    box: Box
    ref: tuple[float, float, float] | None = None


@dataclass
class SlotSpec:
    """One player on the HUD. `name_box` None means the bar is yours."""
    bar: BarSpec
    name_box: Box | None = None
    fixed_label: str = ""


@dataclass
class HudConfig:
    self_bar: BarSpec | None = None
    teammates: list[SlotSpec] = field(default_factory=list)
    hold_seconds: float = 3.0
    empty_below: float = 0.04
    alive_above: float = 0.15
    name_every_seconds: float = 2.0

    @staticmethod
    def from_json(d: dict | None) -> "HudConfig | None":
        if not d:
            return None

        def bar(b):
            return BarSpec(tuple(b["box"]), tuple(b["ref"]) if b.get("ref") else None) if b else None

        return HudConfig(
            self_bar=bar(d.get("self_bar")),
            teammates=[SlotSpec(bar(t["bar"]), tuple(t["name_box"]) if t.get("name_box") else None, t.get("label", ""))
                       for t in d.get("teammates", []) if t.get("bar")],
            hold_seconds=float(d.get("hold_seconds", 3.0)),
        )


@dataclass
class _BarState:
    alive_at: float = float("-inf")
    empty_since: float | None = None
    fill: float = 0.0
    name: str = ""
    name_read_at: float = float("-inf")
    has_name: bool = True


class HudTracker:
    def __init__(self, config: HudConfig, ocr: OcrProvider | None = None, name_resolver=None):
        self.config = config
        self.ocr = ocr
        self.resolve = name_resolver or (lambda raw: raw)
        self.states: dict[str, _BarState] = {}

    def _read_name(self, frame: np.ndarray, box: Box) -> str:
        if not self.ocr:
            return ""
        img = crop(frame, box)
        if img.size == 0:
            return ""
        img = cv2.resize(img, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC)   # small HUD text reads better big
        text = " ".join(self.ocr.read(img).split())
        return text[:40]

    def _step(self, key: str, t: float, fill: float) -> _BarState:
        st = self.states.setdefault(key, _BarState())
        st.fill = fill
        c = self.config
        if fill >= c.alive_above:
            st.alive_at = t
            st.empty_since = None
        elif fill <= c.empty_below:
            if st.empty_since is None:
                st.empty_since = t
        return st

    def _dead(self, st: _BarState, t: float) -> bool:
        c = self.config
        # Empty for long enough, and alive shortly before it emptied (not an unused or broken slot).
        return (st.empty_since is not None and t - st.empty_since >= c.hold_seconds
                and st.empty_since - st.alive_at <= 30.0)

    def update(self, t: float, frame: np.ndarray) -> list[Candidate]:
        out: list[Candidate] = []
        c = self.config
        if c.self_bar:
            fill = bar_fill(crop(frame, c.self_bar.box), c.self_bar.ref)
            st = self._step("self", t, fill)
            if self._dead(st, t):
                out.append(Candidate(SELF, "", 0.85, {"hud": "own health bar empty", "hud_fill": round(fill, 3)}))
        for i, slot in enumerate(c.teammates):
            key = f"mate{i}"
            fill = bar_fill(crop(frame, slot.bar.box), slot.bar.ref)
            st = self._step(key, t, fill)
            if slot.name_box and t - st.name_read_at >= c.name_every_seconds and fill >= c.alive_above:
                # Read names while the player is alive: dead HUD names are often greyed out.
                raw = self._read_name(frame, slot.name_box)
                st.name_read_at = t
                st.has_name = bool(raw)
                if raw:
                    st.name = self.resolve(raw) or raw
            label = slot.fixed_label or st.name
            if self._dead(st, t) and (slot.fixed_label or st.has_name):
                out.append(Candidate(TEAMMATE, label[:40], 0.85,
                                     {"hud": f"teammate {i + 1} health bar empty", "hud_fill": round(fill, 3)}))
        return out

    def readings(self) -> dict[str, dict]:
        return {k: {"fill": round(v.fill, 3), "name": v.name, "empty_for": None if v.empty_since is None else True}
                for k, v in self.states.items()}
