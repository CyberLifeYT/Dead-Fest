from deadfest_detector.players import Player, match_name, resolve_subject

PLAYERS = [
    Player("u1", "Shadow"), Player("u2", "Bentley"), Player("u3", "Zebra"),
]
TAGS = {"u1": "ShadowOnyx11", "u2": "BentleyPlays"}


def test_exact_gamertag():
    assert match_name("ShadowOnyx11", PLAYERS, TAGS).uid == "u1"


def test_ocr_mistakes_still_match():
    assert match_name("Shad0wOnyxl1", PLAYERS, TAGS).uid == "u1"
    assert match_name("BentIeyPlays", PLAYERS, TAGS).uid == "u2"


def test_dead_fest_handles_match_without_a_gamertag():
    assert match_name("ZEBRA", PLAYERS, TAGS).uid == "u3"


def test_unknown_names_do_not_match():
    assert match_name("RandomStranger", PLAYERS, TAGS) is None
    assert match_name("ab", PLAYERS, TAGS) is None


def test_whose_death_it_is():
    assert resolve_subject("self", "", "me-uid", PLAYERS, TAGS) == "me-uid"
    assert resolve_subject("teammate", "ShadowOnyx11", "me-uid", PLAYERS, TAGS) == "u1"
    assert resolve_subject("teammate", "Nobody", "me-uid", PLAYERS, TAGS) == ""


def test_player_from_a_user_document():
    p = Player.from_doc("u9", {"username": "Ghost", "admin": True,
                               "playerData": {"coins": 120, "level": 4, "games": {"Dying Light 2": {"deaths": 3}, "WWZ": {"deaths": 2}}}})
    assert (p.handle, p.admin, p.coins, p.level, p.total_deaths) == ("Ghost", True, 120, 4, 5)
