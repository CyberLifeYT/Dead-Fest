"""The PC's moderation logic, held to the same cases as the app's DeathModerationTest.kt."""

from dataclasses import replace

import pytest

from deadfest_detector import moderation as m


class Ledger:
    """One record and the players, updated all-or-nothing like a transaction."""

    def __init__(self, record: m.State, players: dict):
        self.record = record
        self.players = players      # uid -> {"coins": int, "deaths": {sector: int}}
        self.now = 1_000

    def run(self, action, fail_commit=False):
        result = m.plan(self.record, action, "admin", "Admin", self.now)
        self.now += 1
        if isinstance(result, tuple):
            return result
        counts = m.new_death_counts(result.counts, lambda u, s: self.players[u]["deaths"].get(s, 0))
        f = result.fields
        nxt = replace(self.record,
                      status=f.get("status", self.record.status), counted=f.get("counted", self.record.counted),
                      counted_sector=f.get("countedSector", self.record.counted_sector),
                      subject_uid=f.get("subjectUid", self.record.subject_uid), game_id=f.get("gameId", self.record.game_id),
                      event_type=f.get("eventType", self.record.event_type),
                      player_label=f.get("playerLabel", self.record.player_label), updated_at=f["updatedAt"])
        if fail_commit:
            raise RuntimeError("network dropped mid-commit")
        for (uid, sector), value in counts.items():
            self.players[uid]["deaths"][sector] = value
        self.record = nxt
        return result


SECTOR = "Dying Light 2"


@pytest.fixture
def ledger():
    return Ledger(
        m.State(status="pending", counted=False, counted_sector="", subject_uid="owner", submitter_uid="owner",
                game_id="dying_light_2", event_type="self", player_label="", updated_at=500),
        {"owner": {"coins": 777, "deaths": {SECTOR: 4}}, "mate": {"coins": 50, "deaths": {}}},
    )


def deaths(lg, uid="owner", sector=SECTOR):
    return lg.players[uid]["deaths"].get(sector, 0)


def test_verifying_counts_once_and_leaves_coins_unchanged(ledger):
    ledger.run(m.Verify(SECTOR))
    assert deaths(ledger) == 5 and ledger.players["owner"]["coins"] == 777
    assert ledger.record.status == "verified" and ledger.record.counted


def test_repeated_verify_cannot_double_count(ledger):
    ledger.run(m.Verify(SECTOR))
    for _ in range(5):
        assert ledger.run(m.Verify(SECTOR)) == ("no_change", "ALREADY_VERIFIED")
    assert deaths(ledger) == 5 and ledger.players["owner"]["coins"] == 777


def test_not_verified_takes_a_counted_death_off_without_touching_coins(ledger):
    ledger.run(m.Verify(SECTOR))
    ledger.run(m.Reject("Not a real death"))
    assert deaths(ledger) == 4 and ledger.players["owner"]["coins"] == 777 and not ledger.record.counted


def test_rejecting_pending_changes_only_the_status(ledger):
    ledger.run(m.Reject("loading screen"))
    assert deaths(ledger) == 4 and ledger.record.status == "rejected"


def test_remove_then_restore_counts_exactly_once(ledger):
    ledger.run(m.Verify(SECTOR))
    ledger.run(m.Remove("wrong player"))
    assert deaths(ledger) == 4
    ledger.run(m.Restore())
    for _ in range(3):
        ledger.run(m.Restore())
    assert deaths(ledger) == 5 and ledger.record.status == "verified" and ledger.players["owner"]["coins"] == 777


def test_restore_of_a_never_verified_record_goes_back_to_pending(ledger):
    ledger.run(m.Reject("blurry"))
    ledger.run(m.Restore())
    assert ledger.record.status == "pending" and deaths(ledger) == 4


def test_corrections_to_an_uncounted_record_cannot_make_restore_count_it(ledger):
    ledger.run(m.Correct(sector="Dead Island 2"))
    ledger.run(m.Remove("not a death"))
    ledger.run(m.Restore())
    assert ledger.record.status == "pending" and deaths(ledger) == 4


def test_moving_a_counted_death_is_minus_one_plus_one(ledger):
    ledger.run(m.Verify(SECTOR))
    p = ledger.run(m.Correct(game_id="dead_island_2", sector="Dead Island 2"))
    assert p.counts == [("owner", SECTOR, -1), ("owner", "Dead Island 2", +1)]
    assert deaths(ledger) == 4 and deaths(ledger, sector="Dead Island 2") == 1


def test_a_teammate_death_counts_for_the_linked_player_only(ledger):
    ledger.record = replace(ledger.record, event_type="teammate", subject_uid="", player_label="Mate")
    assert ledger.run(m.Verify(SECTOR)) == ("refused", "NEED_PLAYER")
    ledger.run(m.Verify(SECTOR, "mate"))
    assert deaths(ledger, "mate") == 1 and deaths(ledger) == 4
    assert ledger.players["mate"]["coins"] == 50 and ledger.players["owner"]["coins"] == 777


def test_counts_never_go_negative(ledger):
    ledger.run(m.Verify(SECTOR))
    ledger.players["owner"]["deaths"][SECTOR] = 0
    ledger.run(m.Reject("dup"))
    assert deaths(ledger) == 0


def test_a_failed_commit_changes_neither_record_nor_count(ledger):
    before = ledger.record
    with pytest.raises(RuntimeError):
        ledger.run(m.Verify(SECTOR), fail_commit=True)
    assert ledger.record == before and deaths(ledger) == 4


def test_plans_never_write_coins(ledger):
    p = ledger.run(m.Verify(SECTOR))
    assert not any("coin" in k.lower() for k in p.fields)
    assert all(c[2] in (1, -1) for c in p.counts)


def test_reasons_and_values_are_validated(ledger):
    assert ledger.run(m.Reject("  ")) == ("refused", "NEED_REASON")
    assert ledger.run(m.Correct(game_id="halo")) == ("refused", "BAD_GAME")
    assert ledger.run(m.Correct(event_type="murder")) == ("refused", "BAD_TYPE")
    assert ledger.run(m.Correct()) == ("no_change", "NOTHING_TO_CHANGE")


def test_each_decision_is_newer_than_the_record(ledger):
    ledger.now = 100
    p = ledger.run(m.Reject("x"))
    assert p.at > 500 and p.fields["updatedAt"] == p.at


def test_default_sector_prefers_the_configured_spelling():
    assert m.default_sector("dying_light_2", ["DYING LIGHT 2"]) == "DYING LIGHT 2"
    assert m.default_sector("dying_light_the_beast", ["Dying Light The Beast"]) == "Dying Light The Beast"
    assert m.default_sector("state_of_decay_2", []) == "State of Decay 2"


def test_field_path_segments_are_quoted():
    from deadfest_detector.firebase_rest import quote_segment

    assert quote_segment("deaths") == "deaths"
    assert quote_segment("Dying Light 2") == "`Dying Light 2`"
    assert quote_segment("Dying Light: The Beast") == "`Dying Light: The Beast`"
    assert quote_segment("we`ird") == "`we\\`ird`"
