"""
Local evidence storage under D:\\XboxDeathDetector.

Layout (the game folders already exist on the PC; anything missing is created):

    D:\\XboxDeathDetector\\
        DyingLight2\\2026-10-25_01-32-10_self-death_1a2b3c4d.png
        metadata\\<event id>.json        <- the record; the source of truth, not the filename
        rejected\\DyingLight2\\...       <- images an admin rejected or removed

Rules this module enforces:
  * files are created exclusively (never overwritten), names carry the event id;
  * every path is resolved and checked to stay inside the evidence root;
  * if the root is unavailable (drive missing, no permission, disk full) a StorageUnavailable is
    raised. Nothing is ever written anywhere else instead.
"""

from __future__ import annotations

import errno
import json
import os
import re
import uuid
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path

from . import games

METADATA_DIR = "metadata"
REJECTED_DIR = "rejected"

SYNC_PRIVATE = "private"     # sharing off: stays on this PC
SYNC_PENDING = "pending"     # waiting to upload
SYNC_SYNCED = "synced"

_SAFE = re.compile(r"[^A-Za-z0-9_-]+")
EVENT_ID_RE = re.compile(r"^[A-Za-z0-9_-]{16,64}$")


class StorageUnavailable(Exception):
    """The evidence folder cannot be written. The message says why, in plain words."""


def new_event_id() -> str:
    return uuid.uuid4().hex


def sanitize(part: str, fallback: str = "x") -> str:
    cleaned = _SAFE.sub("-", part).strip("-")[:48]
    return cleaned or fallback


@dataclass
class EvidenceRecord:
    event_id: str
    game_id: str
    event_type: str
    player_label: str
    detected_at_ms: int                      # UTC epoch milliseconds
    detected_at_local: str                   # display copy in the PC's time zone
    confidence: float
    local_relative_path: str                 # e.g. "DyingLight2/2026-...png" (forward slashes)
    extra_frames: list[str] = field(default_factory=list)
    detection: dict = field(default_factory=dict)
    verification_status: str = "pending"    # mirrored from the app after sync
    review: dict = field(default_factory=dict)
    sync_state: str = SYNC_PRIVATE
    sync_attempts: int = 0
    sync_error: str = ""
    current_path: str = ""                   # where the image is now (moves to rejected/)
    created_at_ms: int = 0
    updated_at_ms: int = 0

    def to_json(self) -> dict:
        return asdict(self)

    @staticmethod
    def from_json(data: dict) -> "EvidenceRecord":
        known = {f for f in EvidenceRecord.__dataclass_fields__}
        return EvidenceRecord(**{k: v for k, v in data.items() if k in known})


def _now_ms() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def _explain(exc: OSError, root: Path) -> StorageUnavailable:
    if exc.errno == errno.ENOSPC:
        msg = f"The drive holding {root} is full. Free some space; the evidence is being held for retry."
    elif exc.errno in (errno.EACCES, errno.EPERM):
        msg = f"No permission to write to {root}. Check the folder's security settings."
    elif not Path(root.anchor or str(root)).exists():
        msg = f"Drive {root.anchor} is not available. Reconnect it; the evidence is being held for retry."
    else:
        msg = f"Could not write to {root}: {exc.strerror or exc}"
    return StorageUnavailable(msg)


class EvidenceStore:
    def __init__(self, root: str | Path):
        self.root = Path(root)

    # ------------------------------------------------------------------ layout and paths

    def ensure_layout(self) -> None:
        """Creates the root and its subfolders if missing. Raises StorageUnavailable on failure."""
        if self.root.anchor and not Path(self.root.anchor).exists():
            raise StorageUnavailable(
                f"Drive {self.root.anchor} is not available, so {self.root} cannot be used. "
                "Evidence is not saved anywhere else."
            )
        try:
            self.root.mkdir(parents=True, exist_ok=True)
            for g in games.GAMES:
                (self.root / g.folder).mkdir(exist_ok=True)
            (self.root / METADATA_DIR).mkdir(exist_ok=True)
            (self.root / REJECTED_DIR).mkdir(exist_ok=True)
        except OSError as e:
            raise _explain(e, self.root) from e

    def resolve(self, relative: str) -> Path:
        """Turns a stored relative path into an absolute one, refusing anything outside the root."""
        if not relative or relative.startswith(("/", "\\")) or ":" in relative or ".." in relative.replace("\\", "/").split("/"):
            raise ValueError(f"Refusing unsafe evidence path: {relative!r}")
        # Pure path arithmetic (no filesystem calls), so listing thousands of records stays fast.
        root = os.path.abspath(self.root)
        target = os.path.abspath(os.path.join(root, relative))
        if os.path.commonpath([os.path.normcase(root), os.path.normcase(target)]) != os.path.normcase(root):
            raise ValueError(f"Refusing path outside the evidence folder: {relative!r}")
        return Path(target)

    def _metadata_path(self, event_id: str) -> Path:
        if not EVENT_ID_RE.match(event_id):
            raise ValueError(f"Bad event id {event_id!r}")
        return self.root / METADATA_DIR / f"{event_id}.json"

    # ------------------------------------------------------------------ writing

    def _write_exclusive(self, path: Path, data: bytes) -> None:
        try:
            with open(path, "xb") as f:   # "x": fail rather than overwrite
                f.write(data)
                f.flush()
                os.fsync(f.fileno())
        except FileExistsError:
            raise
        except OSError as e:
            try:
                path.unlink(missing_ok=True)
            except OSError:
                pass
            raise _explain(e, self.root) from e

    def save_evidence(
        self,
        *,
        game_id: str,
        event_type: str,
        player_label: str,
        detected_at: datetime,
        confidence: float,
        image_png: bytes,
        extra_frames: dict[str, bytes] | None = None,
        detection: dict | None = None,
        shared: bool,
        event_id: str | None = None,
    ) -> EvidenceRecord:
        """Writes the image(s) and the metadata record. Never overwrites an existing file."""
        profile = games.get(game_id)
        if event_type not in games.EVENT_TYPES:
            raise ValueError(f"Bad event type {event_type!r}")
        self.ensure_layout()
        event_id = event_id or new_event_id()
        local = detected_at.astimezone()
        stem = f"{local:%Y-%m-%d_%H-%M-%S}_{sanitize(event_type)}-death_{event_id[:8]}"
        folder = self.root / profile.folder

        # The event id makes collisions practically impossible; the loop is the belt to that braces.
        primary = folder / f"{stem}.png"
        n = 1
        while True:
            try:
                self._write_exclusive(primary, image_png)
                break
            except FileExistsError:
                n += 1
                primary = folder / f"{stem}_{n}.png"

        written = [primary]
        extras: list[str] = []
        try:
            for suffix, data in (extra_frames or {}).items():
                p = folder / f"{primary.stem}_{sanitize(suffix)}.png"
                self._write_exclusive(p, data)
                written.append(p)
                extras.append(f"{profile.folder}/{p.name}")

            rel = f"{profile.folder}/{primary.name}"
            now = _now_ms()
            record = EvidenceRecord(
                event_id=event_id,
                game_id=game_id,
                event_type=event_type,
                player_label=player_label[:40],
                detected_at_ms=int(detected_at.astimezone(timezone.utc).timestamp() * 1000),
                detected_at_local=local.strftime("%Y-%m-%d %H:%M:%S %Z").strip(),
                confidence=round(float(confidence), 3),
                local_relative_path=rel,
                extra_frames=extras,
                detection=detection or {},
                sync_state=SYNC_PENDING if shared else SYNC_PRIVATE,
                current_path=rel,
                created_at_ms=now,
                updated_at_ms=now,
            )
            self._write_exclusive(self._metadata_path(event_id), json.dumps(record.to_json(), indent=2).encode())
            return record
        except BaseException:
            # Leave no half-written evidence behind: an image without a record would be orphaned.
            for p in written:
                try:
                    p.unlink(missing_ok=True)
                except OSError:
                    pass
            raise

    def add_extra_frame(self, record: EvidenceRecord, suffix: str, image_png: bytes) -> str:
        """Adds a later frame (e.g. "post") to an existing record without touching its main image."""
        profile = games.get(record.game_id)
        base = Path(record.local_relative_path).stem
        path = self.root / profile.folder / f"{base}_{sanitize(suffix)}.png"
        self._write_exclusive(path, image_png)
        rel = f"{profile.folder}/{path.name}"
        record.extra_frames.append(rel)
        self.update(record)
        return rel

    def update(self, record: EvidenceRecord) -> None:
        """Rewrites a record atomically (temp file + replace of the record's own file only)."""
        record.updated_at_ms = _now_ms()
        path = self._metadata_path(record.event_id)
        tmp = path.with_suffix(".json.tmp")
        try:
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(record.to_json(), f, indent=2)
            os.replace(tmp, path)
        except OSError as e:
            raise _explain(e, self.root) from e

    # ------------------------------------------------------------------ reading

    def load(self, event_id: str) -> EvidenceRecord:
        with open(self._metadata_path(event_id), encoding="utf-8") as f:
            return EvidenceRecord.from_json(json.load(f))

    def records(self) -> list[EvidenceRecord]:
        meta = self.root / METADATA_DIR
        if not meta.exists():
            return []
        out = []
        for p in sorted(meta.glob("*.json")):
            try:
                with open(p, encoding="utf-8") as f:
                    out.append(EvidenceRecord.from_json(json.load(f)))
            except (OSError, ValueError, TypeError):
                continue  # a damaged record never stops the rest from loading
        out.sort(key=lambda r: r.detected_at_ms, reverse=True)
        return out

    def image_exists(self, record: EvidenceRecord) -> bool:
        try:
            return self.resolve(record.current_path or record.local_relative_path).is_file()
        except ValueError:
            return False

    # ------------------------------------------------------------------ moderation mirror

    def move_to_rejected(self, record: EvidenceRecord) -> None:
        """Moves the image(s) under rejected/ (kept, not deleted) and records the new location."""
        current = record.current_path or record.local_relative_path
        if current.startswith(REJECTED_DIR + "/"):
            return
        paths = [current] + record.extra_frames
        moved_primary = current
        for rel in paths:
            src = self.resolve(rel)
            if not src.exists():
                continue
            dst_rel = f"{REJECTED_DIR}/{rel}"
            dst = self.resolve(dst_rel)
            dst.parent.mkdir(parents=True, exist_ok=True)
            if dst.exists():
                raise StorageUnavailable(f"{dst} already exists; not overwriting it.")
            os.replace(src, dst)
            if rel == current:
                moved_primary = dst_rel
        record.current_path = moved_primary
        record.extra_frames = [
            f"{REJECTED_DIR}/{r}" if not r.startswith(REJECTED_DIR + "/") else r for r in record.extra_frames
        ]

    def restore_from_rejected(self, record: EvidenceRecord) -> None:
        current = record.current_path or record.local_relative_path
        if not current.startswith(REJECTED_DIR + "/"):
            return
        for rel in [current] + record.extra_frames:
            if not rel.startswith(REJECTED_DIR + "/"):
                continue
            src = self.resolve(rel)
            dst = self.resolve(rel[len(REJECTED_DIR) + 1:])
            if src.exists() and not dst.exists():
                os.replace(src, dst)
        record.current_path = current[len(REJECTED_DIR) + 1:]
        record.extra_frames = [r[len(REJECTED_DIR) + 1:] if r.startswith(REJECTED_DIR + "/") else r for r in record.extra_frames]
