import time
from datetime import datetime, timedelta, timezone

from deadfest_detector import gallery


def test_large_collection_writes_fast_and_escapes_names(store):
    start = datetime(2026, 1, 1, tzinfo=timezone.utc)
    for i in range(1500):
        store.save_evidence(
            game_id="dead_island_2", event_type="teammate", player_label='<script>alert(1)</script>' if i == 0 else f"P{i}",
            detected_at=start + timedelta(minutes=i), confidence=0.9, image_png=b"\x89PNG", shared=False,
        )
    t = time.perf_counter()
    page = gallery.write(store)
    elapsed = time.perf_counter() - t
    html = page.read_text(encoding="utf-8")
    # Almost all of this is opening 1,500 small files, which Windows Defender scans when they are
    # new; the page generation itself takes milliseconds. A sanity bound, not a benchmark.
    assert elapsed < 60
    assert "<script>alert(1)</script>" not in html          # cannot close the data script tag
    assert 'loading="lazy"' in html                          # images load as they scroll into view
    assert html.count('"id":') == 1500


def test_missing_images_are_marked(store):
    r = store.save_evidence(game_id="world_war_z", event_type="self", player_label="", confidence=0.9,
                            detected_at=datetime.now(timezone.utc), image_png=b"\x89PNG", shared=False)
    store.resolve(r.local_relative_path).unlink()
    assert '"exists": false' in gallery.write(store).read_text(encoding="utf-8")
