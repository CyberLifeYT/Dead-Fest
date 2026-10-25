"""
Dead-Fest players as the detector sees them, and who a detected death belongs to.

Xbox gamertags aren't stored in Dead-Fest, so the desktop app keeps a local map of
`Dead-Fest uid -> gamertag` (config "gamertags"). A name read off the screen (a teammate banner, or
the name next to a health bar in the HUD) is matched against those gamertags and the players'
Dead-Fest handles, tolerating the small mistakes OCR makes (O/0, l/1, a missing character).
"""

from __future__ import annotations

import difflib
import re
from dataclasses import dataclass, field

_OCR_FIX = str.maketrans({"0": "o", "1": "l", "i": "l", "5": "s", "8": "b", "|": "l", "$": "s"})


def _key(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower()).translate(_OCR_FIX)


@dataclass
class Player:
    uid: str
    handle: str
    admin: bool = False
    coins: int = 0
    level: int = 1
    deaths_by_sector: dict[str, int] = field(default_factory=dict)
    avatar: str = ""

    @property
    def total_deaths(self) -> int:
        return sum(self.deaths_by_sector.values())

    @staticmethod
    def from_doc(uid: str, d: dict) -> "Player":
        pd = d.get("playerData") or {}
        handle = (d.get("username") or d.get("displayName") or "").strip() or f"SURVIVOR-{uid[:4].upper()}"
        games = pd.get("games") or {}
        return Player(
            uid=uid, handle=handle, admin=bool(d.get("admin", False)),
            coins=int(pd.get("coins", 0) or 0), level=int(pd.get("level", 1) or 1),
            deaths_by_sector={k: int((v or {}).get("deaths", 0) or 0) for k, v in games.items() if isinstance(v, dict)},
            avatar=str(pd.get("avatar") or ""),
        )

    def to_json(self) -> dict:
        return {"uid": self.uid, "handle": self.handle, "admin": self.admin, "coins": self.coins, "level": self.level,
                "deathsBySector": self.deaths_by_sector, "totalDeaths": self.total_deaths, "avatar": self.avatar}


def match_name(read: str, players: list[Player], gamertags: dict[str, str], cutoff: float = 0.8) -> Player | None:
    """The player a name read from the screen belongs to, or None if no confident match."""
    k = _key(read)
    if len(k) < 3:
        return None
    best, best_score = None, 0.0
    for p in players:
        for candidate in (gamertags.get(p.uid, ""), p.handle):
            ck = _key(candidate)
            if not ck:
                continue
            score = 1.0 if ck == k else difflib.SequenceMatcher(None, k, ck).ratio()
            if score > best_score:
                best, best_score = p, score
    return best if best_score >= cutoff else None


def resolve_subject(event_type: str, player_label: str, me_uid: str, players: list[Player],
                    gamertags: dict[str, str]) -> str:
    """Whose death count a detection goes to: you for your own deaths, a matched player otherwise, else ''."""
    if event_type == "self":
        return me_uid
    if player_label:
        p = match_name(player_label, players, gamertags)
        if p:
            return p.uid
    return ""
