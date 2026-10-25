"""
Admin moderation of death evidence from the PC: verify ("count it"), reject ("not verified"),
remove, restore and correct.

A line-for-line port of the app's domain/DeathModeration.kt, executed the same way as the app's
FirebaseRepository.moderateEvidence: one Firestore transaction that re-reads the record and the
players, plans against that fresh state (so repeating an action is a no-op), and writes the record,
the death counts and the audit entry together. The security rules check the same invariants:

  * only an admin can moderate;
  * verified == counted, and the player's death count moves by exactly one in the same commit;
  * coins are never written, so a moderation commit can never change a balance;
  * every decision writes its audit entry.

Only `playerData.games.<sector>.deaths` is ever written on a player.
"""

from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Any

from .firebase_rest import FirebaseError, FirestoreClient

PENDING, VERIFIED, REJECTED, REMOVED = "pending", "verified", "rejected", "removed"
EVENT_TYPES = ("self", "teammate", "other", "unknown")
MAX_REASON, MAX_LABEL, MAX_SECTOR = 200, 40, 80

GAME_SECTORS = {
    "dying_light": "Dying Light",
    "dying_light_2": "Dying Light 2",
    "dying_light_the_beast": "Dying Light: The Beast",
    "world_war_z": "World War Z",
    "dead_island": "Dead Island",
    "dead_island_riptide": "Dead Island Riptide",
    "dead_island_2": "Dead Island 2",
    "state_of_decay_2": "State of Decay 2",
}


def _norm(s: str) -> str:
    return "".join(ch for ch in s.lower() if ch.isalnum())


def default_sector(game_id: str, sectors: list[str]) -> str:
    """The configured sector matching the game (ignoring case and punctuation), else the game's name."""
    name = GAME_SECTORS.get(game_id, game_id)
    key = _norm(name)
    return next((s for s in sectors if _norm(s) == key), name)


@dataclass
class State:
    status: str
    counted: bool
    counted_sector: str
    subject_uid: str
    submitter_uid: str
    game_id: str
    event_type: str
    player_label: str
    updated_at: int

    @staticmethod
    def from_doc(d: dict) -> "State":
        return State(
            status=d.get("status", PENDING), counted=bool(d.get("counted", False)),
            counted_sector=d.get("countedSector", ""), subject_uid=d.get("subjectUid", ""),
            submitter_uid=d.get("uid", ""), game_id=d.get("gameId", ""), event_type=d.get("eventType", "unknown"),
            player_label=d.get("playerLabel", ""), updated_at=int(d.get("updatedAt", 0)),
        )


@dataclass
class Plan:
    fields: dict[str, Any]
    counts: list[tuple[str, str, int]]          # (uid, sector, +1/-1)
    audit_action: str
    note: str
    changes: dict[str, list]
    at: int


@dataclass
class Outcome:
    kind: str                                   # "applied" | "no_change" | "refused"
    problem: str = ""
    action: str = ""
    deaths_delta: int = 0
    subject_uid: str = ""
    sector: str = ""


@dataclass
class Verify:
    sector: str
    subject_uid: str | None = None


@dataclass
class Reject:
    reason: str


@dataclass
class Remove:
    reason: str


@dataclass
class Restore:
    pass


@dataclass
class Correct:
    game_id: str | None = None
    event_type: str | None = None
    player_label: str | None = None
    subject_uid: str | None = None
    sector: str | None = None


def plan(s: State, action, admin_uid: str, admin_name: str, now: int) -> Plan | tuple[str, str]:
    """A Plan, or ("no_change"|"refused", problem)."""
    at = max(now, s.updated_at + 1)
    review = {"reviewedBy": admin_uid, "reviewerName": admin_name[:MAX_LABEL], "reviewedAt": at, "updatedAt": at}

    def changes(status, subject=None, sector=None):
        subject = s.subject_uid if subject is None else subject
        sector = s.counted_sector if sector is None else sector
        out = {"status": [s.status, status]}
        if subject != s.subject_uid:
            out["subjectUid"] = [s.subject_uid, subject]
        if sector != s.counted_sector:
            out["countedSector"] = [s.counted_sector, sector]
        return out

    if isinstance(action, Verify):
        if s.status == VERIFIED:
            return "no_change", "ALREADY_VERIFIED"
        if s.status != PENDING:
            return "refused", "NOT_PENDING"
        subject = (action.subject_uid if action.subject_uid is not None else s.subject_uid).strip()
        sector = action.sector.strip()
        if not subject:
            return "refused", "NEED_PLAYER"
        if not sector:
            return "refused", "NEED_SECTOR"
        if len(sector) > MAX_SECTOR:
            return "refused", "TOO_LONG"
        return Plan({**review, "status": VERIFIED, "counted": True, "countedSector": sector, "subjectUid": subject,
                     "rejectionReason": ""}, [(subject, sector, +1)], "verified", sector,
                    changes(VERIFIED, subject, sector), at)

    if isinstance(action, (Reject, Remove)):
        target = REJECTED if isinstance(action, Reject) else REMOVED
        if s.status == target:
            return "no_change", "ALREADY_REJECTED" if target == REJECTED else "ALREADY_REMOVED"
        if target == REJECTED and s.status == REMOVED:
            return "refused", "ALREADY_REMOVED"
        why = action.reason.strip()
        if not why:
            return "refused", "NEED_REASON"
        if len(why) > MAX_REASON:
            return "refused", "TOO_LONG"
        counts = [(s.subject_uid, s.counted_sector, -1)] if s.counted else []
        return Plan({**review, "status": target, "counted": False, "rejectionReason": why}, counts,
                    "rejected" if target == REJECTED else "removed", why, changes(target), at)

    if isinstance(action, Restore):
        if s.status not in (REJECTED, REMOVED):
            return "no_change", "NOT_REMOVED_OR_REJECTED"
        recount = bool(s.counted_sector.strip()) and bool(s.subject_uid.strip())
        status = VERIFIED if recount else PENDING
        counts = [(s.subject_uid, s.counted_sector, +1)] if recount else []
        return Plan({**review, "status": status, "counted": recount, "rejectionReason": ""}, counts,
                    "restored", status, changes(status), at)

    if isinstance(action, Correct):
        game = action.game_id.strip() if action.game_id is not None else s.game_id
        etype = action.event_type.strip() if action.event_type is not None else s.event_type
        label = action.player_label.strip() if action.player_label is not None else s.player_label
        subject = action.subject_uid.strip() if action.subject_uid is not None else s.subject_uid
        sector = (action.sector.strip() if action.sector is not None else s.counted_sector) if s.counted else s.counted_sector
        if game not in GAME_SECTORS:
            return "refused", "BAD_GAME"
        if etype not in EVENT_TYPES:
            return "refused", "BAD_TYPE"
        if len(label) > MAX_LABEL or len(sector) > MAX_SECTOR:
            return "refused", "TOO_LONG"
        if etype == "self" and not subject and action.subject_uid is None:
            subject = s.submitter_uid
        if s.counted and not subject:
            return "refused", "CANNOT_UNLINK_COUNTED"
        if s.counted and not sector:
            return "refused", "NEED_SECTOR"
        new = {}
        old = {"gameId": s.game_id, "eventType": s.event_type, "playerLabel": s.player_label,
               "subjectUid": s.subject_uid, "countedSector": s.counted_sector}
        for k, v in (("gameId", game), ("eventType", etype), ("playerLabel", label), ("subjectUid", subject),
                     ("countedSector", sector)):
            if v != old[k]:
                new[k] = v
        if not new:
            return "no_change", "NOTHING_TO_CHANGE"
        moves = s.counted and (subject != s.subject_uid or sector != s.counted_sector)
        counts = [(s.subject_uid, s.counted_sector, -1), (subject, sector, +1)] if moves else []
        return Plan({**review, **new}, counts, "corrected", ", ".join(new),
                    {k: [old[k], v] for k, v in new.items()}, at)

    raise TypeError(f"Unknown action {action!r}")


def new_death_counts(counts: list[tuple[str, str, int]], current) -> dict[tuple[str, str], int]:
    """Each (player, sector) moved by its changes, never below zero. current(uid, sector) -> int."""
    out: dict[tuple[str, str], int] = {}
    for uid, sector, delta in counts:
        key = (uid, sector)
        out[key] = max(0, out.get(key, current(uid, sector)) + delta)
    return out


def deaths_of(user_doc: dict, sector: str) -> int:
    return int(((user_doc.get("playerData") or {}).get("games") or {}).get(sector, {}).get("deaths", 0) or 0)


class Moderator:
    def __init__(self, fs: FirestoreClient, admin_uid: str, admin_name: str):
        self.fs = fs
        self.admin_uid = admin_uid
        self.admin_name = admin_name

    def apply(self, event_id: str, action, attempts: int = 3) -> Outcome:
        """Runs one decision in a transaction; retries if a concurrent change aborts it."""
        last: Exception | None = None
        for _ in range(attempts):
            try:
                return self._apply_once(event_id, action)
            except FirebaseError as e:
                # ABORTED / FAILED_PRECONDITION: someone changed the record mid-way; re-read and re-plan.
                if e.status in (409,) or e.code in ("ABORTED",):
                    last = e
                    continue
                raise
        raise last  # type: ignore[misc]

    def _apply_once(self, event_id: str, action) -> Outcome:
        fs = self.fs
        tx = fs.begin_transaction()
        doc = fs.get_in(f"deathEvidence/{event_id}", tx)
        if doc is None:
            return Outcome("refused", "NOT_FOUND")
        result = plan(State.from_doc(doc), action, self.admin_uid, self.admin_name, int(time.time() * 1000))
        if isinstance(result, tuple):
            return Outcome(result[0], result[1])
        players = {}
        for uid in dict.fromkeys(c[0] for c in result.counts):
            players[uid] = fs.get_in(f"users/{uid}", tx)
            if players[uid] is None:
                return Outcome("refused", "PLAYER_MISSING")
        counts = new_death_counts(result.counts, lambda u, sec: deaths_of(players[u], sec))
        writes = [fs.update_write(f"deathEvidence/{event_id}", result.fields)]
        for (uid, sector), value in counts.items():
            writes.append(fs.update_write(f"users/{uid}", {("playerData", "games", sector, "deaths"): value}))
        writes.append(fs.create_write(f"deathEvidence/{event_id}/audit/{result.at}", {
            "action": result.audit_action, "actor": self.admin_uid, "actorName": self.admin_name[:MAX_LABEL],
            "at": result.at, "note": result.note[:300], "changes": result.changes,
        }))
        fs.commit_in(writes, tx)
        net = sum(c[2] for c in result.counts)
        if net:
            try:   # the global eliminations counter, best effort, after the commit (like the app)
                fs.commit([fs.increment_write("gameState/global", "grandTotal", net)])
            except Exception:  # noqa: BLE001
                pass
        target = result.counts[-1] if result.counts else None
        return Outcome("applied", action=result.audit_action, deaths_delta=net,
                       subject_uid=target[0] if target else "", sector=target[1] if target else "")


PROBLEM_TEXT = {
    "ALREADY_VERIFIED": "Already counted.",
    "ALREADY_REJECTED": "Already marked not verified.",
    "ALREADY_REMOVED": "Already removed. Restore it first.",
    "NOT_REMOVED_OR_REJECTED": "Only rejected or removed records can be restored.",
    "NOT_PENDING": "Only pending evidence can be verified. Restore it first.",
    "NEED_PLAYER": "Choose whose death it is first.",
    "NEED_SECTOR": "Choose a sector to count it in.",
    "NEED_REASON": "Give a reason.",
    "TOO_LONG": "That text is too long.",
    "BAD_GAME": "That game isn't supported.",
    "BAD_TYPE": "That classification isn't valid.",
    "NOTHING_TO_CHANGE": "Nothing to change.",
    "CANNOT_UNLINK_COUNTED": "A counted death needs a player. Mark it not verified instead.",
    "NOT_FOUND": "This record isn't on the server (not shared yet, or deleted).",
    "PLAYER_MISSING": "That player's account no longer exists.",
}
