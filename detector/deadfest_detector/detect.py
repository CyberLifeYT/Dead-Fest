"""
Per-frame analysis: does this frame show a death, whose, and how sure are we?

Three cues, strongest first:
  1. Templates: crops of a game's real death screen saved with `calibrate`. Matched with OpenCV's
     normalized cross-correlation, so a score near 1.0 means "this is that screen".
  2. OCR text: the game's death wording ("YOU DIED", "<name> has died").
  3. Colour: the heavy red/dark wash many death screens use. Weak on its own, so it can only add
     a small boost to a cue above; it never creates a detection by itself.

A frame produces zero or more candidates, each with a classification (self / teammate / other /
unknown) and a player label when the screen names one. Nothing is invented: if a game shows no
sign of another player's death, there is simply no candidate for it.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path

import cv2
import numpy as np

from . import games
from .games import OTHER, SELF, TEAMMATE, UNKNOWN
from .ocr import OcrProvider

_NOT_NAMES = {"YOU", "HE", "SHE", "THEY", "IT", "PLAYER", "SOMEONE"}


@dataclass
class Candidate:
    event_type: str
    player_label: str
    confidence: float
    cues: dict = field(default_factory=dict)

    @property
    def key(self) -> str:
        return f"{self.event_type}|{self.player_label.upper()}"


@dataclass
class Template:
    event_type: str
    image: np.ndarray        # grayscale crop
    ref_width: int           # frame width the crop was taken from
    name: str


def load_templates(folder: Path) -> list[Template]:
    """Loads <folder>/*.png. The filename prefix is the classification: self_*.png, teammate_*.png."""
    out: list[Template] = []
    if not folder.exists():
        return out
    for png in sorted(folder.glob("*.png")):
        event_type = png.stem.split("_", 1)[0].lower()
        if event_type not in games.EVENT_TYPES:
            event_type = SELF
        meta_path = png.with_suffix(".json")
        ref_width = 1920
        if meta_path.exists():
            try:
                ref_width = int(json.loads(meta_path.read_text(encoding="utf-8")).get("ref_width", 1920))
            except (ValueError, OSError):
                pass
        img = cv2.imdecode(np.fromfile(str(png), dtype=np.uint8), cv2.IMREAD_GRAYSCALE)
        if img is not None:
            out.append(Template(event_type, img, ref_width, png.name))
    return out


def normalize_text(text: str) -> str:
    text = text.upper().replace("’", "'")
    return re.sub(r"\s+", " ", text).strip()


def red_wash_score(frame_bgr: np.ndarray) -> float:
    """0..1: how much of the frame is a dark or red death wash."""
    small = cv2.resize(frame_bgr, (160, 90), interpolation=cv2.INTER_AREA).astype(np.int16)
    b, g, r = small[..., 0], small[..., 1], small[..., 2]
    red = (r > 90) & (r > g + 40) & (r > b + 40)
    dark = small.max(axis=2) < 40
    return float(np.clip(red.mean() * 1.5 + dark.mean() * 0.5, 0.0, 1.0))


class Analyzer:
    def __init__(
        self,
        profile: games.CompiledProfile,
        ocr: OcrProvider,
        templates: list[Template] | None = None,
        *,
        template_threshold: float = 0.8,
        color_boost: float = 0.05,
        self_names: list[str] | None = None,
        teammates: list[str] | None = None,
    ):
        self.profile = profile
        self.ocr = ocr
        self.templates = templates or []
        self.template_threshold = template_threshold
        self.color_boost = color_boost
        self.self_names = {n.strip().upper() for n in (self_names or []) if n.strip()}
        # With a squad list, a named death of anyone else is "other". Without one, a named banner
        # in these co-op games is taken to be a teammate.
        self.teammates = {n.strip().upper() for n in (teammates or []) if n.strip()}

    def _template_hits(self, frame_bgr: np.ndarray) -> list[Candidate]:
        hits: dict[str, Candidate] = {}
        if not self.templates:
            return []
        gray = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2GRAY)
        for t in self.templates:
            h, w = gray.shape
            scaled = gray if w == t.ref_width else cv2.resize(gray, (t.ref_width, int(h * t.ref_width / w)))
            if scaled.shape[0] < t.image.shape[0] or scaled.shape[1] < t.image.shape[1]:
                continue
            score = float(cv2.minMaxLoc(cv2.matchTemplate(scaled, t.image, cv2.TM_CCOEFF_NORMED))[1])
            if score >= self.template_threshold:
                c = Candidate(t.event_type, "", score, {"template": t.name, "template_score": round(score, 3)})
                if c.key not in hits or hits[c.key].confidence < score:
                    hits[c.key] = c
        return list(hits.values())

    def _text_hits(self, text: str) -> list[Candidate]:
        hits: list[Candidate] = []
        if not text:
            return hits
        norm = normalize_text(text)
        if any(r.search(norm) for r in self.profile.ignore_res) and not any(r.search(norm) for r in self.profile.self_res):
            # Only "downed"-style wording on screen: not a death.
            named_allowed = False
        else:
            named_allowed = True
        for r in self.profile.self_res:
            m = r.search(norm)
            if m:
                hits.append(Candidate(SELF, "", 0.9, {"text": m.group(0)}))
                break
        if named_allowed:
            # The self-death wording is not a name banner ("YOU HAVE DIED" must not yield "You Have").
            rest = norm
            for r in self.profile.self_res:
                rest = r.sub(" | ", rest)
            for r in self.profile.named_res:
                for m in r.finditer(rest):
                    name = (m.groupdict().get("name") or "").strip(" .-_")
                    if not name or name in _NOT_NAMES or name.split(" ")[0] in _NOT_NAMES:
                        continue
                    if name in self.self_names:
                        event_type = SELF
                    elif not self.teammates or name in self.teammates:
                        event_type = TEAMMATE
                    else:
                        event_type = OTHER
                    hits.append(Candidate(event_type, name.title()[:40], 0.85, {"text": m.group(0)}))
        return hits

    def analyze(self, frame_bgr: np.ndarray) -> list[Candidate]:
        candidates = self._template_hits(frame_bgr)
        text = self.ocr.read(frame_bgr) if self.ocr else ""
        for c in self._text_hits(text):
            existing = next((x for x in candidates if x.key == c.key or (x.event_type == c.event_type == SELF)), None)
            if existing:
                # Two independent cues agree: more confident than either alone.
                existing.confidence = min(1.0, max(existing.confidence, c.confidence) + 0.05)
                existing.cues.update(c.cues)
                if not existing.player_label:
                    existing.player_label = c.player_label
            else:
                candidates.append(c)
        if candidates and self.color_boost > 0:
            wash = red_wash_score(frame_bgr)
            for c in candidates:
                if c.event_type == SELF and wash > 0.25:
                    c.confidence = min(1.0, c.confidence + self.color_boost)
                    c.cues["red_wash"] = round(wash, 3)
        # Self deaths shown with a named banner of your own gamertag merge into one self candidate.
        merged: dict[str, Candidate] = {}
        for c in candidates:
            k = SELF if c.event_type == SELF else c.key
            if k not in merged or merged[k].confidence < c.confidence:
                if k in merged and not c.player_label:
                    c.player_label = merged[k].player_label
                merged[k] = c
        return list(merged.values())


__all__ = ["Analyzer", "Candidate", "Template", "load_templates", "normalize_text", "red_wash_score", "OTHER", "UNKNOWN"]
