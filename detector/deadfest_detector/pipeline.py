"""
The detection pipeline: frames in, evidence records out.

    source -> sample at analysis_fps -> Analyzer -> DeathStateMachine -> save evidence -> on_record

Only the frames needed as evidence are kept: a short ring buffer (for the frame just before the
death) and, optionally, one later frame. Nothing else of the gameplay is stored.

If the evidence folder is unavailable, a confirmed death is held in memory (bounded) and the save
is retried; the event is reported as waiting, never silently dropped or written elsewhere.
"""

from __future__ import annotations

import logging
import time
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Callable

import numpy as np

from . import imaging
from .detect import Analyzer
from .sources import FrameSource
from .state import Confirmation, DeathStateMachine
from .storage import EvidenceRecord, EvidenceStore, StorageUnavailable

log = logging.getLogger("deadfest.detector")


@dataclass
class _PendingSave:
    kwargs: dict
    queued_at: float
    attempts: int = 0


@dataclass
class _PendingPost:
    due: float
    record: EvidenceRecord


@dataclass
class PipelineStats:
    frames_seen: int = 0
    frames_analyzed: int = 0
    deaths: int = 0
    held_for_retry: int = 0
    dropped: int = 0
    storage_error: str = ""
    records: list[EvidenceRecord] = field(default_factory=list)


class Pipeline:
    def __init__(
        self,
        *,
        game_id: str,
        store: EvidenceStore,
        analyzer: Analyzer,
        machine: DeathStateMachine,
        shared: bool,
        analysis_fps: float = 4.0,
        save_pre_frame: bool = True,
        pre_seconds: float = 1.0,
        save_post_frame: bool = False,
        post_seconds: float = 2.0,
        retry_seconds: float = 15.0,
        queue_limit: int = 20,
        on_record: Callable[[EvidenceRecord], None] | None = None,
        wallclock: Callable[[], datetime] = lambda: datetime.now(timezone.utc),
        monotonic: Callable[[], float] = time.monotonic,
    ):
        self.game_id = game_id
        self.store = store
        self.analyzer = analyzer
        self.machine = machine
        self.shared = shared
        self.interval = 1.0 / max(0.5, analysis_fps)
        self.save_pre_frame = save_pre_frame
        self.pre_seconds = pre_seconds
        self.save_post_frame = save_post_frame
        self.post_seconds = post_seconds
        self.retry_seconds = retry_seconds
        self.queue_limit = queue_limit
        self.on_record = on_record
        self.wallclock = wallclock
        self.monotonic = monotonic
        # Enough analysed frames to reach back past a slow confirmation plus the pre-death frame.
        self._buffer: deque[tuple[int, float, np.ndarray]] = deque(maxlen=int((pre_seconds + 6.0) * max(0.5, analysis_fps)) + 4)
        self._next_t: float | None = None
        self._frame_id = 0
        self._pending_saves: deque[_PendingSave] = deque()
        self._pending_posts: list[_PendingPost] = []
        self._last_retry = float("-inf")
        self.stats = PipelineStats()

    # ------------------------------------------------------------------ per frame

    def process(self, t: float, frame: np.ndarray) -> list[EvidenceRecord]:
        """Feeds one source frame (t in source seconds). Returns records saved on this frame."""
        self.stats.frames_seen += 1
        saved: list[EvidenceRecord] = []
        self._retry_saves(saved)
        self._capture_posts(t, frame)
        if self._next_t is not None and t < self._next_t:
            return saved
        self._next_t = t + self.interval
        self._frame_id += 1
        fid = self._frame_id
        self._buffer.append((fid, t, frame))
        self.stats.frames_analyzed += 1

        candidates = self.analyzer.analyze(frame)
        for conf in self.machine.update(t, fid, candidates):
            record = self._save(conf, t)
            if record:
                saved.append(record)
        return saved

    def _frame_by_id(self, fid: int) -> tuple[float, np.ndarray] | None:
        for f_id, ft, img in self._buffer:
            if f_id == fid:
                return ft, img
        return None

    def _frame_before(self, t: float) -> np.ndarray | None:
        best = None
        for _, ft, img in self._buffer:
            if ft <= t:
                best = img
        return best

    def _save(self, conf: Confirmation, now_t: float) -> EvidenceRecord | None:
        self.stats.deaths += 1
        first = self._frame_by_id(conf.first_frame_id)
        evidence_t, evidence = first if first else (now_t, self._buffer[-1][2])
        extras: dict[str, bytes] = {}
        if self.save_pre_frame:
            pre = self._frame_before(evidence_t - self.pre_seconds)
            if pre is not None and pre is not evidence:
                extras["pre"] = imaging.png(pre)
        detected_at = self.wallclock() - timedelta(seconds=max(0.0, now_t - evidence_t))
        c = conf.candidate
        kwargs = dict(
            game_id=self.game_id,
            event_type=c.event_type,
            player_label=c.player_label,
            detected_at=detected_at,
            confidence=conf.confidence,
            image_png=imaging.png(evidence),
            extra_frames=extras,
            detection={
                "cues": c.cues,
                "confirmed_after_s": round(conf.confirmed_at - conf.first_seen, 2),
                "ocr": getattr(self.analyzer.ocr, "name", "none"),
            },
            shared=self.shared,
        )
        record = self._try_save(kwargs)
        if record is None:
            return None
        if self.save_post_frame:
            self._pending_posts.append(_PendingPost(evidence_t + self.post_seconds, record))
        return record

    def _try_save(self, kwargs: dict) -> EvidenceRecord | None:
        try:
            record = self.store.save_evidence(**kwargs)
        except StorageUnavailable as e:
            self.stats.storage_error = str(e)
            log.error("Evidence not saved yet: %s", e)
            if len(self._pending_saves) >= self.queue_limit:
                self.stats.dropped += 1
                log.error("Retry queue full (%d). The oldest held death was dropped.", self.queue_limit)
                self._pending_saves.popleft()
            self._pending_saves.append(_PendingSave(kwargs, self.monotonic()))
            self.stats.held_for_retry = len(self._pending_saves)
            return None
        self.stats.storage_error = ""
        self.stats.records.append(record)
        if self.on_record:
            self.on_record(record)
        return record

    def _retry_saves(self, saved: list[EvidenceRecord]) -> None:
        if not self._pending_saves:
            return
        now = self.monotonic()
        if now - self._last_retry < self.retry_seconds:
            return
        self._last_retry = now
        while self._pending_saves:
            item = self._pending_saves[0]
            item.attempts += 1
            try:
                record = self.store.save_evidence(**item.kwargs)
            except StorageUnavailable as e:
                self.stats.storage_error = str(e)
                break
            self._pending_saves.popleft()
            self.stats.storage_error = ""
            self.stats.records.append(record)
            saved.append(record)
            if self.on_record:
                self.on_record(record)
        self.stats.held_for_retry = len(self._pending_saves)

    def retry_now(self) -> list[EvidenceRecord]:
        self._last_retry = float("-inf")
        saved: list[EvidenceRecord] = []
        self._retry_saves(saved)
        return saved

    def _capture_posts(self, t: float, frame: np.ndarray) -> None:
        for post in list(self._pending_posts):
            if t >= post.due:
                self._pending_posts.remove(post)
                try:
                    self.store.add_extra_frame(post.record, "post", imaging.png(frame))
                except (StorageUnavailable, FileExistsError, OSError) as e:
                    log.warning("Post-death frame not saved: %s", e)

    # ------------------------------------------------------------------ loop

    def run(self, source: FrameSource, should_stop: Callable[[], bool] = lambda: False) -> PipelineStats:
        try:
            for t, frame in source.frames():
                self.process(t, frame)
                if should_stop():
                    break
        finally:
            source.close()
        return self.stats

    @property
    def held(self) -> int:
        return len(self._pending_saves)
