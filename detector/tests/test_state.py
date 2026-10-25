from deadfest_detector.detect import Candidate
from deadfest_detector.state import DeathStateMachine, Rules, State

FPS = 4
DT = 1 / FPS


def run(frames, rules=None):
    """frames: list of candidate lists, one per analysed frame at 4 fps."""
    sm = DeathStateMachine(rules or Rules())
    out = []
    for i, cands in enumerate(frames):
        out += sm.update(i * DT, i, cands)
    return sm, out


def you(conf=0.9):
    return [Candidate("self", "", conf)]


def test_a_death_screen_that_stays_up_counts_once():
    sm, out = run([[]] * 4 + [you()] * 60 + [[]] * 20)
    assert len(out) == 1
    assert sm.state_of("self|") == State.ALIVE
    history = sm.trackers["self|"].history
    assert history[:3] == [State.POSSIBLE_DEATH, State.CONFIRMED_DEATH, State.WAITING_FOR_RESPAWN]
    assert history[-1] == State.ALIVE


def test_the_first_death_frame_is_the_evidence_frame():
    _, out = run([[]] * 4 + [you()] * 10)
    assert out[0].first_frame_id == 4


def test_a_single_flicker_frame_is_not_a_death():
    _, out = run([[], you(), [], [], you(), [], []])
    assert out == []


def test_low_confidence_is_ignored():
    _, out = run([you(0.5)] * 20)
    assert out == []


def test_respawn_then_a_new_death_counts_again():
    frames = [you()] * 10 + [[]] * 60 + [you()] * 10
    _, out = run(frames)
    assert len(out) == 2


def test_screen_blinking_off_briefly_is_not_a_second_death():
    # The screen fades out for 1 s (less than respawn_clear_seconds) then comes back.
    frames = [you()] * 10 + [[]] * 4 + [you()] * 10 + [[]] * 20
    _, out = run(frames)
    assert len(out) == 1


def test_cooldown_blocks_an_immediate_recount():
    rules = Rules(respawn_clear_seconds=0.5, cooldown_seconds=10)
    frames = [you()] * 4 + [[]] * 4 + [you()] * 8
    _, out = run(frames, rules)
    assert len(out) == 1


def test_teammate_and_self_deaths_are_tracked_separately():
    marco = Candidate("teammate", "Marco", 0.85)
    frames = [[marco]] * 6 + [[marco] + you()] * 6 + [[]] * 20
    _, out = run(frames)
    kinds = sorted((c.candidate.event_type, c.candidate.player_label) for c in out)
    assert kinds == [("self", ""), ("teammate", "Marco")]


def test_one_miss_is_tolerated_while_confirming():
    _, out = run([you(), [], you(), you()])
    assert len(out) == 1
