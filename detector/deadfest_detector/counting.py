"""
Counting deaths as soon as they're detected ("count until I say it's not verified").

After a record uploads, an admin's detector verifies it straight away: your own deaths count for the
player you chose as "me", a teammate's for the player their on-screen name matches. It stays counted
until someone marks it not verified (a reject), in the PC app or the phone app.

This is the same verification an admin does in Admin -> Deaths, through moderation.Moderator, so the
security rules apply unchanged: admin only, exactly one death per record, never any coins. A death
whose player can't be identified stays pending for a person to decide.
"""

from __future__ import annotations

from dataclasses import dataclass

from . import moderation
from .firebase_rest import FirebaseError, FirestoreClient, NetworkError
from .players import Player, resolve_subject
from .storage import EvidenceRecord


@dataclass
class CountResult:
    counted: bool
    message: str
    subject_uid: str = ""
    sector: str = ""
    retry: bool = False          # worth trying again on the next sync (refused, offline)


class AutoCounter:
    def __init__(self, fs: FirestoreClient, admin_uid: str, admin_name: str, me_uid: str,
                 players: list[Player], gamertags: dict[str, str], sectors: list[str]):
        self.moderator = moderation.Moderator(fs, admin_uid, admin_name)
        self.me_uid = me_uid or admin_uid
        self.players = players
        self.gamertags = gamertags
        self.sectors = sectors

    @staticmethod
    def load(fs: FirestoreClient, uid: str, me_uid: str, gamertags: dict[str, str]) -> "AutoCounter | None":
        """None if the signed-in account isn't an admin (only admins may count deaths)."""
        players = [Player.from_doc(pid, d) for pid, d in fs.list_documents("users")]
        me = next((p for p in players if p.uid == uid), None)
        if me is None or not me.admin:
            return None
        global_state = fs.get("gameState/global") or {}
        sectors = [s for s in global_state.get("games", []) if isinstance(s, str)]
        return AutoCounter(fs, uid, me.handle, me_uid, players, gamertags, sectors)

    def subject_for(self, record: EvidenceRecord) -> str:
        return resolve_subject(record.event_type, record.player_label, self.me_uid, self.players, self.gamertags)

    def count(self, record: EvidenceRecord) -> CountResult:
        subject = self.subject_for(record)
        if not subject:
            return CountResult(False, "left pending: no Dead-Fest player matches "
                                      f"'{record.player_label or record.event_type}'")
        sector = moderation.default_sector(record.game_id, self.sectors)
        try:
            out = self.moderator.apply(record.event_id, moderation.Verify(sector, subject))
        except FirebaseError as e:
            if e.status == 403:
                return CountResult(False, "not counted yet: the live Firestore rules refused it. Publish the "
                                          "v2.1.3 rules (Firebase console > Firestore > Rules), then Sync now.", retry=True)
            return CountResult(False, f"not counted yet: {e}", retry=True)
        except NetworkError as e:
            return CountResult(False, f"not counted yet (offline): {e}", retry=True)
        who = next((p.handle for p in self.players if p.uid == subject), subject[:8])
        if out.kind == "applied":
            return CountResult(True, f"counted for {who} in {sector}", subject, sector)
        if out.problem == "ALREADY_VERIFIED":
            return CountResult(True, "already counted", subject, sector)
        return CountResult(False, moderation.PROBLEM_TEXT.get(out.problem, out.problem))
