"""
The supported games and how each one announces a death.

The ids are shared with the Android app (domain/DeathGames.kt) and the Firestore rules, and the
folder names match the folders already created under D:\\XboxDeathDetector. Change neither.

The text patterns are a starting point, not a guarantee: they cover the common wording ("YOU
DIED", "<name> has died") but each game's real death screen should be checked against live
footage, and a template captured from a real death screen (`calibrate`) is the most reliable cue.
Every pattern can be overridden per game in the config file.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

SELF = "self"
TEAMMATE = "teammate"
OTHER = "other"
UNKNOWN = "unknown"
EVENT_TYPES = (SELF, TEAMMATE, OTHER, UNKNOWN)

# "<name> has died" style banners. The name group is what becomes the player label.
_NAMED_DEATH = r"(?P<name>[A-Z0-9][A-Z0-9_ .\-]{1,23}?)\s+(?:HAS DIED|DIED|WAS KILLED|IS DEAD|HAS BEEN KILLED|WAS SLAIN)\b"
_SELF_COMMON = (
    r"\bYOU (?:HAVE )?DIED\b",
    r"\bYOU ARE DEAD\b",
    r"\bYOU(?:'| A)?RE DEAD\b",
    r"\bYOU WERE KILLED\b",
)


@dataclass(frozen=True)
class GameProfile:
    id: str
    name: str
    folder: str
    self_patterns: tuple[str, ...] = _SELF_COMMON
    named_patterns: tuple[str, ...] = (_NAMED_DEATH,)
    # Words that look like deaths but are not (being downed is revivable, so it is not a death).
    ignore_patterns: tuple[str, ...] = (r"\bIS DOWN\b", r"\bDOWNED\b", r"\bREVIVE\b")
    notes: str = ""


GAMES: tuple[GameProfile, ...] = (
    GameProfile("dying_light", "Dying Light", "DyingLight"),
    GameProfile("dying_light_2", "Dying Light 2", "DyingLight2"),
    GameProfile("dying_light_the_beast", "Dying Light: The Beast", "DyingLightTheBeast"),
    GameProfile(
        "world_war_z", "World War Z", "WorldWarZ",
        notes="Being downed is not a death; only the final death screen or a death banner counts.",
    ),
    GameProfile("dead_island", "Dead Island", "DeadIsland"),
    GameProfile("dead_island_riptide", "Dead Island Riptide", "DeadIslandRiptide"),
    GameProfile("dead_island_2", "Dead Island 2", "DeadIsland2"),
    GameProfile(
        "state_of_decay_2", "State of Decay 2", "StateofDecay2",
        notes="A community survivor dying shows as '<name> has died'. Add your survivors' names "
              "to player.aliases so their deaths count as your own.",
    ),
)

_BY_ID = {g.id: g for g in GAMES}


def get(game_id: str) -> GameProfile:
    try:
        return _BY_ID[game_id]
    except KeyError:
        raise ValueError(f"Unknown game '{game_id}'. Choose one of: {', '.join(_BY_ID)}") from None


def ids() -> list[str]:
    return list(_BY_ID)


@dataclass
class CompiledProfile:
    """A profile with its patterns compiled, after any overrides from the config."""

    profile: GameProfile
    self_res: list[re.Pattern] = field(default_factory=list)
    named_res: list[re.Pattern] = field(default_factory=list)
    ignore_res: list[re.Pattern] = field(default_factory=list)


def compile_profile(profile: GameProfile, overrides: dict | None = None) -> CompiledProfile:
    overrides = overrides or {}

    def comp(key: str, default: tuple[str, ...]) -> list[re.Pattern]:
        return [re.compile(p, re.IGNORECASE) for p in overrides.get(key, default)]

    return CompiledProfile(
        profile=profile,
        self_res=comp("self_patterns", profile.self_patterns),
        named_res=comp("named_patterns", profile.named_patterns),
        ignore_res=comp("ignore_patterns", profile.ignore_patterns),
    )
