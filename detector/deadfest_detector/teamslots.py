"""
Teammate deaths from the HUD's name list, found automatically.

In the teammates area, every analysed frame:

  1. **Find the names** (rows.find_white_names): white text lines, from their shape. A line becomes a
     *slot* once it has stayed in the same place for a few frames: the HUD is still while the world
     moves, so bright scenery can't become a slot.
  2. **Who is it?** Now and then a slot's name is read (Windows OCR on the isolated letters) and
     matched to a Dead-Fest player. If it can't be read, and you're playing with exactly one teammate
     ("Playing with" in the app), it's them. Otherwise the death is saved for an admin to assign.
  3. **Did they die?** The cue the game uses:
       - "name_red" (Dying Light): the slot's own letter pixels, and the skull beside them, turn red.
         A downed teammate stays white, so being downed never counts;
       - "bar_empty": the bar under the name shrinks to nothing (compared with its longest length);
       - "either".
     The cue must hold for `hold` seconds, and the slot must have been seen alive shortly before.

Names leaving or moving just make slots disappear or appear; nobody's death is credited to a slot
someone else now occupies, because a slot is only judged on the letters it was learned from.

Needs a picture big enough to show names (about 600 pixels tall or more); smaller frames are refused
with `too_small` set, rather than guessed at.
"""

from __future__ import annotations

import re

from dataclasses import dataclass

import cv2
import numpy as np

from .detect import Candidate
from .games import TEAMMATE
from .rows import NameLine, find_white_names, judge, masks

MIN_FRAME_HEIGHT = 600
_PLAUSIBLE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9 _.\-]{2,19}$")


@dataclass
class _Slot:
    line: NameLine                 # in area pixels
    first_seen: float
    last_white: float
    hits: int = 1
    name: str = ""
    key: str = ""
    matched: bool = False          # the name read matches a Dead-Fest player
    name_read_at: float = float("-inf")
    colour: str = "white"
    red_since: float | None = None
    gone_since: float | None = None
    bar_len: int = 0
    bar_max: int = 0
    bar_thick: int = 0             # the bar's thickness when it was at its longest
    empty_since: float | None = None


def _same_place(a: NameLine, b: NameLine) -> bool:
    tol = max(2.0, 0.5 * max(a.h, b.h))
    return abs(a.cy - b.cy) <= tol and min(a.x1, b.x1) > max(a.x0, b.x0)


def _bar_under(area: np.ndarray, line: NameLine, min_thickness: int = 1) -> tuple[int, int]:
    """
    (length, thickness) of the health bar just under a name line, any colour.

    A bar is a long horizontal streak of one colour, so this measures, on each pixel row below the
    name, the longest run of neighbouring pixels with the same hue (or all white), and takes the
    longest. Scenery rarely forms long uniform streaks, and scenery touching the bar doesn't change
    the bar's own run. Thickness is how many pixel rows reach most of that length; runs thinner than
    min_thickness are ignored, so the 1-pixel outline some games leave when a bar is empty doesn't
    read as health.
    """
    H, W = area.shape[:2]
    y0 = min(H, line.y1)
    y1 = min(H, line.y1 + max(4, int(line.h * 1.3)))
    x0 = max(0, line.x0 - line.h * 2)
    x1 = min(W, line.x1 + line.h * 4)
    band = area[y0:y1, x0:x1]
    if band.size == 0 or band.shape[1] < 2:
        return 0, 0
    hsv = cv2.cvtColor(band, cv2.COLOR_BGR2HSV).astype(np.int16)
    h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    vivid = (s >= 80) & (v >= 80)
    white = (s <= 60) & (v >= 170)
    dh = np.abs(h[:, 1:] - h[:, :-1])
    dh = np.minimum(dh, 180 - dh)
    same = ((vivid[:, 1:] & vivid[:, :-1] & (dh <= 8)) | (white[:, 1:] & white[:, :-1]))
    runs = np.zeros(same.shape[0], int)
    for r in range(same.shape[0]):
        best = cur = 0
        for flag in same[r]:
            cur = cur + 1 if flag else 0
            if cur > best:
                best = cur
        runs[r] = best + 1 if best else 0
    longest = int(runs.max()) if len(runs) else 0
    if longest < max(8, line.h):
        return 0, 0
    thick = int((runs >= 0.8 * longest).sum())
    if thick < min_thickness:
        return 0, thick
    return longest, thick


class TeamTracker:
    def __init__(self, area, death_cue: str = "name_red", hold: float = 2.0, ocr=None,
                 resolver=None, roster=None, read_every: float = 4.0, on_learn=None):
        self.area = area                         # (x, y, w, h) fractions of the frame
        self.cue = death_cue
        self.hold = hold
        self.ocr = ocr
        self.resolve = resolver or (lambda raw: (raw.upper(), raw))
        self.roster = roster or (lambda: [])     # teammates you're playing with (labels)
        self.read_every = read_every
        # on_learn(name): a name read on screen that matches nobody, while playing with one teammate,
        # is that teammate's gamertag; the app saves it so it matches from then on.
        self.on_learn = on_learn
        self.slots: list[_Slot] = []
        self.too_small = False

    # ------------------------------------------------------------------ helpers

    def _crop(self, frame: np.ndarray):
        fh, fw = frame.shape[:2]
        x, y, w, h = self.area
        x0, y0 = max(0, int(x * fw)), max(0, int(y * fh))
        x1, y1 = min(fw, int((x + w) * fw)), min(fh, int((y + h) * fh))
        return frame[y0:y1, x0:x1], (x0, y0)

    def _identity(self, raw: str) -> tuple[str, str]:
        res = self.resolve(raw)
        if isinstance(res, tuple):
            return res
        return (res or raw).upper(), res or raw

    def _read_name(self, area: np.ndarray, line: NameLine) -> str:
        """OCR of one name: the isolated white letters as black-on-white, enlarged; then the plain crop."""
        if not self.ocr:
            return ""
        pad = max(2, line.h // 3)
        H, W = area.shape[:2]
        crop = area[max(0, line.y0 - pad):min(H, line.y1 + pad), max(0, line.x0 - pad):min(W, line.x1 + pad)]
        if crop.size == 0:
            return ""
        scale = max(2.0, 32.0 / max(1, line.h))
        _, white = masks(crop)
        clean = np.where(white, 0, 255).astype(np.uint8)
        attempts = [cv2.cvtColor(clean, cv2.COLOR_GRAY2BGR), crop]
        for img in attempts:
            big = cv2.resize(img, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
            border = 255 if img is attempts[0] else 0
            big = cv2.copyMakeBorder(big, 30, 30, 30, 30, cv2.BORDER_CONSTANT, value=(border, border, border))
            for text, _ in self.ocr.read_lines(big):
                raw = "".join(ch for ch in text if ch.isalnum() or ch in " _-.").strip()
                if _PLAUSIBLE.match(raw):
                    return raw
        return ""

    def _label(self, slot: _Slot) -> str:
        """Who a slot is: a matched name, else the one teammate you're playing with, else the name read."""
        if slot.name and slot.matched:
            return slot.name
        roster = [r for r in self.roster() if r]
        if len(roster) == 1:
            return roster[0]
        return slot.name

    # ------------------------------------------------------------------ update

    def update(self, t: float, frame: np.ndarray) -> list[Candidate]:
        fh = frame.shape[0]
        self.too_small = fh < MIN_FRAME_HEIGHT
        if self.too_small:
            return []
        area, _ = self._crop(frame)
        if area.size == 0:
            return []
        lines = [ln for ln in find_white_names(area, fh) if (ln.x1 - ln.x0) >= frame.shape[1] * 0.035]
        matched: set[int] = set()
        for ln in lines:
            slot = next((s for s in self.slots if id(s) not in matched and _same_place(s.line, ln)), None)
            if slot is None:
                slot = _Slot(ln, t, t)
                self.slots.append(slot)
            else:
                slot.line, slot.last_white = ln, t
                slot.hits += 1
            slot.colour, slot.red_since, slot.gone_since = "white", None, None
            matched.add(id(slot))

        out: list[Candidate] = []
        read_one = False
        for slot in list(self.slots):
            if id(slot) not in matched:
                colour, _, _ = judge(area, slot.line)
                slot.colour = colour
                if colour == "red":
                    slot.gone_since = None
                    slot.red_since = slot.red_since if slot.red_since is not None else t
                elif colour == "white":
                    slot.last_white, slot.red_since, slot.gone_since = t, None, None
                else:
                    slot.gone_since = slot.gone_since if slot.gone_since is not None else t
                    # Gone for good (left, or a scenery shape that moved on): forget it.
                    if t - slot.gone_since > (90.0 if slot.red_since else 15.0):
                        self.slots.remove(slot)
                        continue
            confirmed = slot.hits >= 3
            # Who is it? One OCR read per frame at most, only on a confirmed, white slot.
            if (confirmed and slot.colour == "white" and not read_one and self.ocr
                    and t - slot.name_read_at >= self.read_every):
                raw = self._read_name(area, slot.line)
                slot.name_read_at = t
                read_one = True
                if raw:
                    slot.key, slot.name = self._identity(raw)
                    slot.matched = slot.key != raw.upper()
                    if not slot.matched and self.on_learn and len([r for r in self.roster() if r]) == 1:
                        self.on_learn(raw)
            if not confirmed:
                continue
            dead = False
            cue_text = ""
            if self.cue in ("name_red", "either") and slot.red_since is not None:
                if t - slot.red_since >= self.hold and slot.red_since - slot.last_white <= 120.0:
                    dead, cue_text = True, "name turned red"
            if not dead and self.cue in ("bar_empty", "either"):
                length, thick = _bar_under(area, slot.line, max(1, (slot.bar_thick + 1) // 2))
                slot.bar_len = length
                if length >= slot.bar_max:
                    slot.bar_max, slot.bar_thick = length, max(slot.bar_thick, thick)
                if slot.bar_max >= 10 and length <= 0.05 * slot.bar_max and slot.colour != "none":
                    slot.empty_since = slot.empty_since if slot.empty_since is not None else t
                    if t - slot.empty_since >= self.hold:
                        dead, cue_text = True, "health bar empty"
                else:
                    slot.empty_since = None
            if dead:
                label = self._label(slot)
                out.append(Candidate(TEAMMATE, label[:40], 0.9,
                                     {"hud": f"{label or 'a teammate'}: {cue_text}", "hud_slot": round(slot.line.cy)}))
        return out

    def readings(self, frame: np.ndarray) -> list[dict]:
        """For the HUD Test button: each slot, where it is (fractions of the frame), its state and name."""
        fh, fw = frame.shape[:2]
        _, (ox, oy) = self._crop(frame)
        out = []
        for s in self.slots:
            ln = s.line
            out.append({
                "slot": "Teammate", "name": self._label(s) or "(name not read yet)", "matched": bool(s.key and s.name),
                "colour": s.colour, "confirmed": s.hits >= 3,
                "fill": round(s.bar_len / s.bar_max, 3) if s.bar_max else (0.0 if s.colour == "red" else 1.0),
                "nameBox": [(ox + ln.x0) / fw, (oy + ln.y0) / fh, (ln.x1 - ln.x0) / fw, ln.h / fh],
                "bar": None,
            })
        return out
