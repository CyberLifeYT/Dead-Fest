import pytest

from conftest import banner_frame, death_frame, gameplay_frame, template_from_death_frame, windows_ocr_available
from deadfest_detector import games
from deadfest_detector.detect import Analyzer, Template, red_wash_score
from deadfest_detector.ocr import NullOcr, OcrProvider


class FakeOcr(OcrProvider):
    name = "fake"

    def __init__(self, text):
        self.text = text

    def read(self, frame):
        return self.text


def analyzer(text="", templates=None, **kw):
    profile = games.compile_profile(games.get(kw.pop("game", "dying_light_2")))
    return Analyzer(profile, FakeOcr(text), templates or [], **kw)


def test_you_died_is_a_self_death():
    (c,) = analyzer("YOU DIED").analyze(gameplay_frame(1))
    assert c.event_type == "self" and c.confidence >= 0.9


@pytest.mark.parametrize("text", ["You are dead", "YOU HAVE DIED", "you were killed"])
def test_other_self_wordings(text):
    assert [c.event_type for c in analyzer(text).analyze(gameplay_frame(1))] == ["self"]


def test_named_banner_is_a_teammate_with_a_player_label():
    (c,) = analyzer("Marco has died").analyze(gameplay_frame(1))
    assert (c.event_type, c.player_label) == ("teammate", "Marco")


def test_named_death_of_someone_outside_the_squad_is_other():
    (c,) = analyzer("Stranger was killed", teammates=["Marco"]).analyze(gameplay_frame(1))
    assert (c.event_type, c.player_label) == ("other", "Stranger")


def test_your_own_gamertag_in_a_banner_counts_as_you():
    (c,) = analyzer("DeadShot99 has died", self_names=["DeadShot99"]).analyze(gameplay_frame(1))
    assert c.event_type == "self"


def test_you_is_never_used_as_a_player_name():
    cands = analyzer("YOU DIED").analyze(gameplay_frame(1))
    assert all(c.player_label != "You" for c in cands)


def test_being_downed_is_not_a_death():
    assert analyzer("Marco is down - revive him", game="world_war_z").analyze(gameplay_frame(1)) == []


def test_no_cue_no_candidate():
    assert analyzer("AMMO 30 HP 100").analyze(gameplay_frame(1)) == []


def test_template_matches_the_real_screen_and_not_gameplay():
    img, ref = template_from_death_frame()
    a = Analyzer(games.compile_profile(games.get("dying_light")), NullOcr(), [Template("self", img, ref, "self_1.png")])
    hits = a.analyze(death_frame(5))
    assert len(hits) == 1 and hits[0].event_type == "self" and hits[0].confidence >= 0.8
    assert a.analyze(gameplay_frame(5)) == []


def test_template_and_text_together_are_more_confident():
    img, ref = template_from_death_frame()
    a = analyzer("YOU DIED", [Template("self", img, ref, "self_1.png")])
    alone = analyzer("YOU DIED").analyze(death_frame(5))[0].confidence
    assert a.analyze(death_frame(5))[0].confidence > alone


def test_red_wash_is_higher_on_a_death_screen():
    assert red_wash_score(death_frame(3)) > red_wash_score(gameplay_frame(3))


def test_colour_alone_never_creates_a_death():
    assert analyzer("").analyze(death_frame(3)) == []


@pytest.mark.skipif(not windows_ocr_available(), reason="Windows OCR not available")
def test_windows_ocr_reads_a_rendered_death_screen():
    from deadfest_detector.ocr import WindowsOcr

    a = Analyzer(games.compile_profile(games.get("dead_island_2")), WindowsOcr())
    cands = a.analyze(death_frame(2))
    assert [c.event_type for c in cands] == ["self"]
    teammates = a.analyze(banner_frame(2, "Marco has died"))
    assert [(c.event_type, c.player_label) for c in teammates] == [("teammate", "Marco")]


# ---------------------------------------------------------------- v2: tolerant and combined checks

@pytest.mark.parametrize("text", ["Y0U DlED", "YOU D1ED", "YOU ARE DAED", "YOU HAVE DlED"])
def test_ocr_slips_in_death_wording_still_match(text):
    assert [c.event_type for c in analyzer(text).analyze(gameplay_frame(1))] == ["self"]


def test_a_respawn_prompt_on_a_normal_screen_is_not_a_death():
    # Menus say "Retry" or "Respawn" too: on a bright, colourful screen it stays below the threshold.
    (c,) = analyzer("Press A to respawn").analyze(gameplay_frame(1))
    assert c.confidence < 0.8


def test_a_respawn_prompt_on_a_dark_death_screen_is_a_death():
    (c,) = analyzer("Press A to respawn").analyze(death_frame(1, text=""))
    assert c.event_type == "self" and c.confidence >= 0.8 and "dim_screen" in c.cues


def test_menu_words_inside_other_words_do_not_count():
    # "RETRYING" is not "RETRY": no candidate at all, even on a dark screen.
    assert analyzer("RETRYING CONNECTION... PRETRY").analyze(gameplay_frame(1)) == []
    assert analyzer("RETRYING CONNECTION").analyze(death_frame(1, text="")) == []
