"""
Death de-duplication: one death on screen is counted once, however long the screen stays up.

Each distinct death (yours, or a named teammate's) gets its own tracker:

    ALIVE -> POSSIBLE_DEATH -> CONFIRMED_DEATH -> WAITING_FOR_RESPAWN -> ALIVE

  * POSSIBLE_DEATH: a cue above the threshold was seen; it must persist for `confirm_frames`
    analysed frames spanning at least `confirm_seconds` (a single flickering frame never counts).
  * CONFIRMED_DEATH: emitted exactly once, then the tracker waits.
  * WAITING_FOR_RESPAWN: the death screen can stay up for as long as it likes; the tracker only
    returns to ALIVE after the cue has been absent for `respawn_clear_seconds`.
  * `cooldown_seconds` after a confirmation, the same tracker cannot confirm again, which stops
    a screen that briefly disappears (menus, fades) from being counted twice.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum

from .detect import Candidate


class State(Enum):
    ALIVE = "ALIVE"
    POSSIBLE_DEATH = "POSSIBLE_DEATH"
    CONFIRMED_DEATH = "CONFIRMED_DEATH"
    WAITING_FOR_RESPAWN = "WAITING_FOR_RESPAWN"


@dataclass
class Rules:
    threshold: float = 0.8
    confirm_frames: int = 2
    confirm_seconds: float = 0.4
    max_misses: int = 1
    respawn_clear_seconds: float = 3.0
    cooldown_seconds: float = 8.0


@dataclass
class Confirmation:
    candidate: Candidate          # the strongest sighting
    first_seen: float             # time of the first frame showing the death
    first_frame_id: int           # id of that frame, so the pipeline saves the right image
    confirmed_at: float
    confidence: float


@dataclass
class Tracker:
    key: str
    state: State = State.ALIVE
    hits: int = 0
    misses: int = 0
    first_seen: float = 0.0
    first_frame_id: int = -1
    last_seen: float = 0.0
    last_confirmed: float = float("-inf")
    best: Candidate | None = None
    history: list[State] = field(default_factory=list)

    def _go(self, s: State) -> None:
        self.state = s
        self.history.append(s)


class DeathStateMachine:
    def __init__(self, rules: Rules):
        self.rules = rules
        self.trackers: dict[str, Tracker] = {}

    def update(self, t: float, frame_id: int, candidates: list[Candidate]) -> list[Confirmation]:
        """Feeds one analysed frame. Returns the deaths confirmed on this frame (usually none)."""
        r = self.rules
        seen = {c.key: c for c in candidates if c.confidence >= r.threshold}
        out: list[Confirmation] = []

        for key, c in seen.items():
            tr = self.trackers.setdefault(key, Tracker(key))
            tr.last_seen = t
            tr.misses = 0
            if tr.state == State.ALIVE:
                if t - tr.last_confirmed < r.cooldown_seconds:
                    continue
                tr._go(State.POSSIBLE_DEATH)
                tr.hits, tr.first_seen, tr.first_frame_id, tr.best = 1, t, frame_id, c
            elif tr.state == State.POSSIBLE_DEATH:
                tr.hits += 1
                if tr.best is None or c.confidence > tr.best.confidence:
                    tr.best = c
            if tr.state == State.POSSIBLE_DEATH and tr.hits >= r.confirm_frames and t - tr.first_seen >= r.confirm_seconds:
                tr._go(State.CONFIRMED_DEATH)
                tr.last_confirmed = t
                out.append(Confirmation(tr.best, tr.first_seen, tr.first_frame_id, t, tr.best.confidence))
                tr._go(State.WAITING_FOR_RESPAWN)

        for key, tr in self.trackers.items():
            if key in seen:
                continue
            if tr.state == State.POSSIBLE_DEATH:
                tr.misses += 1
                if tr.misses > r.max_misses:
                    tr._go(State.ALIVE)
                    tr.hits = 0
            elif tr.state == State.WAITING_FOR_RESPAWN and t - tr.last_seen >= r.respawn_clear_seconds:
                tr._go(State.ALIVE)
        return out

    def state_of(self, key: str) -> State:
        tr = self.trackers.get(key)
        return tr.state if tr else State.ALIVE
