"""
HUD tracking: deaths read from the health bars, for games that never print "You died".

You mark the HUD once in the desktop app (HUD setup), in co-op with everyone at full health:

  * **your health bar** (it never moves);
  * the **teammates area**: the part of the screen where teammates' names and bars are listed;
  * **one example teammate**: their name and their health bar. That teaches the layout: where a
    bar sits relative to its name, and the bar's "full" colour.

From then on teammates are tracked **by name, not by position**. About once a second the names in
the teammates area are read (Windows OCR) and matched to Dead-Fest players (whose usernames are
their Xbox gamertags; the Players tab can map any that differ). Each player's bar is found from
where *their* name currently is, so people leaving, rejoining or swapping places never mixes them
up. Every analysed frame, each known player's bar fill is measured:

  * a bar that was alive and then sits at (near) zero for `hold_seconds` is that player's death;
  * the player's name must still be on screen in the same place after their bar emptied, because
    a player who *leaves* also makes their bar vanish, and that is not a death.

Boxes are stored as fractions of the frame (0..1), so resizing the Xbox app window is fine.

Being downed (revivable) also empties a bar in some games: set `hold_seconds` longer than a
revive takes there. The older fixed-slot setup (one box per slot) still works if it's configured.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

import cv2
import numpy as np

from .detect import Candidate
from .games import SELF, TEAMMATE
from .ocr import OcrProvider

Box = tuple[float, float, float, float]   # x, y, w, h as fractions of the frame

_PLAUSIBLE_NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9 _.\-]{1,19}$")


def crop(frame: np.ndarray, box: Box) -> np.ndarray:
    h, w = frame.shape[:2]
    x, y, bw, bh = box
    x0, y0 = int(round(x * w)), int(round(y * h))
    x1, y1 = int(round((x + bw) * w)), int(round((y + bh) * h))
    x0, y0 = max(0, min(w - 1, x0)), max(0, min(h - 1, y0))
    x1, y1 = max(x0 + 1, min(w, x1)), max(y0 + 1, min(h, y1))
    return frame[y0:y1, x0:x1]


def reference_colour(bar_bgr: np.ndarray) -> tuple[float, float, float] | None:
    """The bar's 'full' colour (HSV median of its vivid or bright pixels), sampled at full health."""
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
    """Fixed-slot mode: one teammate position (older setups)."""
    bar: BarSpec
    name_box: Box | None = None
    fixed_label: str = ""


@dataclass
class TeamSpec:
    """Name-tracking mode: where teammates are listed and how a bar sits relative to its name."""
    area: Box
    bar_offset: tuple[float, float, float, float]   # dx, dy from the name's anchor corner; w, h of the bar
    ref: tuple[float, float, float] | None = None
    anchor: str = "left"                             # names line up on their "left" or "right" edge
    read_every: float = 1.0

    def bar_for(self, name_box: Box) -> Box:
        nx, ny, nw, _ = name_box
        ax = nx if self.anchor == "left" else nx + nw
        dx, dy, bw, bh = self.bar_offset
        return ax + dx, ny + dy, bw, bh


def calibrate_team(area: Box, example_name: Box, example_bar: Box, anchor: str = "left") -> tuple:
    """bar_offset from one example teammate (name box and bar box, fractions of the frame)."""
    nx, ny, nw, _ = example_name
    bx, by, bw, bh = example_bar
    ax = nx if anchor == "left" else nx + nw
    return bx - ax, by - ny, bw, bh


@dataclass
class HudConfig:
    self_bar: BarSpec | None = None
    teammates: list[SlotSpec] = field(default_factory=list)
    team: TeamSpec | None = None
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

        team = None
        if d.get("team"):
            t = d["team"]
            team = TeamSpec(tuple(t["area"]), tuple(t["bar_offset"]), tuple(t["ref"]) if t.get("ref") else None,
                            t.get("anchor", "left"), float(t.get("read_every", 1.0)))
        return HudConfig(
            self_bar=bar(d.get("self_bar")),
            teammates=[SlotSpec(bar(t["bar"]), tuple(t["name_box"]) if t.get("name_box") else None, t.get("label", ""))
                       for t in d.get("teammates", []) if t.get("bar")],
            team=team,
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
    # name-tracking mode
    label: str = ""
    bar_box: Box | None = None
    name_box: Box | None = None
    seen_at: float = float("-inf")       # last OCR pass that found this player's name


def _near(a: Box | None, b: Box | None, tol: float = 0.02) -> bool:
    return a is not None and b is not None and abs(a[0] - b[0]) < tol and abs(a[1] - b[1]) < tol


class HudTracker:
    def __init__(self, config: HudConfig, ocr: OcrProvider | None = None, name_resolver=None,
                 known_names=None):
        """
        name_resolver(raw) -> (key, label) or label: who a name read off the screen is.
        known_names() -> list of names to accept in name-tracking mode (empty: any plausible name).
        """
        self.config = config
        self.ocr = ocr
        self.resolve = name_resolver or (lambda raw: raw)
        self.known_names = known_names or (lambda: [])
        self.states: dict[str, _BarState] = {}
        self._team_read_at = float("-inf")

    # ------------------------------------------------------------------ common

    def _identity(self, raw: str) -> tuple[str, str]:
        res = self.resolve(raw)
        if isinstance(res, tuple):
            return res
        label = res or raw
        return label.upper(), label

    def _read_name(self, frame: np.ndarray, box: Box) -> str:
        if not self.ocr:
            return ""
        img = crop(frame, box)
        if img.size == 0:
            return ""
        img = cv2.resize(img, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC)   # small HUD text reads better big
        return " ".join(self.ocr.read(img).split())[:40]

    def _step(self, st: _BarState, t: float, fill: float) -> None:
        st.fill = fill
        c = self.config
        if fill >= c.alive_above:
            st.alive_at = t
            st.empty_since = None
        elif fill <= c.empty_below:
            if st.empty_since is None:
                st.empty_since = t

    def _dead(self, st: _BarState, t: float) -> bool:
        c = self.config
        # Empty for long enough, and alive shortly before it emptied (not an unused or broken slot).
        return (st.empty_since is not None and t - st.empty_since >= c.hold_seconds
                and st.empty_since - st.alive_at <= 30.0)

    # ------------------------------------------------------------------ name-tracking mode

    def _area_lines(self, area: np.ndarray) -> list[tuple[str, tuple[float, float, float, float]]]:
        """
        Text lines in the teammates area, positions in the area's own pixels. Windows OCR is picky
        about scale (the same names can read at 1x and vanish at 2x), so it reads the area twice,
        at native size and enlarged with a margin, and keeps every distinct line it found.
        """
        out: list[tuple[str, tuple[float, float, float, float]]] = []
        seen: set[str] = set()
        for scale, pad in ((1.0, 24), (2.0, 48)):
            img = area if scale == 1.0 else cv2.resize(area, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
            img = cv2.copyMakeBorder(img, pad, pad, pad, pad, cv2.BORDER_CONSTANT, value=(0, 0, 0))
            for text, (x, y, w, h) in self.ocr.read_lines(img):
                key = "".join(text.split()).upper()
                if not key or key in seen:
                    continue
                seen.add(key)
                out.append((text, ((x - pad) / scale, (y - pad) / scale, w / scale, h / scale)))
        return out

    def _read_team(self, t: float, frame: np.ndarray) -> None:
        team = self.config.team
        area = crop(frame, team.area)
        if area.size == 0 or not self.ocr:
            return
        fh, fw = frame.shape[:2]
        ax, ay = team.area[0], team.area[1]
        known = [n for n in self.known_names() if n]
        taken: set[str] = set()
        for text, (x, y, w, h) in self._area_lines(area):
            raw = " ".join(text.split()).strip(" .-_")
            if not _PLAUSIBLE_NAME.match(raw):
                continue
            key, label = self._identity(raw)
            if known and label not in known and key not in {k.upper() for k in known}:
                continue          # a known-players list exists and this isn't one of them
            if key in taken:
                continue
            taken.add(key)
            name_box = (ax + x / fw, ay + y / fh, w / fw, h / fh)
            st = self.states.setdefault("team:" + key, _BarState())
            if not _near(st.name_box, name_box):
                # They moved (someone left or joined): their bar history belongs to the old position.
                st.empty_since = None if st.name_box is not None else st.empty_since
            st.label, st.name_box, st.bar_box, st.seen_at = label, name_box, team.bar_for(name_box), t

    def _update_team(self, t: float, frame: np.ndarray) -> list[Candidate]:
        team = self.config.team
        if t - self._team_read_at >= team.read_every:
            self._read_team(t, frame)
            self._team_read_at = t
        out = []
        forget = self.config.hold_seconds + 10
        for key, st in list(self.states.items()):
            if not key.startswith("team:") or st.bar_box is None:
                continue
            if t - st.seen_at > forget:
                del self.states[key]          # gone from the HUD: left the game
                continue
            fill = bar_fill(crop(frame, st.bar_box), team.ref)
            self._step(st, t, fill)
            # Still listed, in the same place, after the bar emptied: dead, not departed.
            still_here = st.empty_since is not None and st.seen_at >= st.empty_since
            if self._dead(st, t) and still_here:
                out.append(Candidate(TEAMMATE, st.label[:40], 0.85,
                                     {"hud": f"{st.label}'s health bar empty", "hud_fill": round(fill, 3)}))
        return out

    # ------------------------------------------------------------------ update

    def update(self, t: float, frame: np.ndarray) -> list[Candidate]:
        out: list[Candidate] = []
        c = self.config
        if c.self_bar:
            st = self.states.setdefault("self", _BarState())
            fill = bar_fill(crop(frame, c.self_bar.box), c.self_bar.ref)
            self._step(st, t, fill)
            if self._dead(st, t):
                out.append(Candidate(SELF, "", 0.85, {"hud": "own health bar empty", "hud_fill": round(fill, 3)}))
        if c.team:
            out += self._update_team(t, frame)
        for i, slot in enumerate(c.teammates):
            st = self.states.setdefault(f"mate{i}", _BarState())
            fill = bar_fill(crop(frame, slot.bar.box), slot.bar.ref)
            self._step(st, t, fill)
            if slot.name_box and t - st.name_read_at >= c.name_every_seconds and fill >= c.alive_above:
                # Read names while the player is alive: dead HUD names are often greyed out.
                raw = self._read_name(frame, slot.name_box)
                st.name_read_at = t
                st.has_name = bool(raw)
                if raw:
                    st.name = self._identity(raw)[1]
            label = slot.fixed_label or st.name
            if self._dead(st, t) and (slot.fixed_label or st.has_name):
                out.append(Candidate(TEAMMATE, label[:40], 0.85,
                                     {"hud": f"teammate {i + 1} health bar empty", "hud_fill": round(fill, 3)}))
        return out

    def readings(self) -> list[dict]:
        out = []
        for key, st in self.states.items():
            if key == "self":
                out.append({"slot": "You", "name": "", "fill": round(st.fill, 3)})
            elif key.startswith("team:"):
                out.append({"slot": "Teammate", "name": st.label, "fill": round(st.fill, 3),
                            "bar": st.bar_box, "nameBox": st.name_box})
            else:
                out.append({"slot": f"Slot {key[4:]}", "name": st.name, "fill": round(st.fill, 3)})
        return out
