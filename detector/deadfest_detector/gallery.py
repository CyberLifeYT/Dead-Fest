"""
A local, offline gallery of the evidence on this PC: D:\\XboxDeathDetector\\index.html.

It is a static page that links to the images by relative path and is opened straight from disk.
No web server runs and nothing is reachable from the network, which is how the PC's evidence
folder stays private while still being easy to browse.
"""

from __future__ import annotations

import html
import json
import os
from pathlib import Path

from . import games
from .storage import EvidenceStore

_PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Dead-Fest Death Evidence</title>
<style>
:root { --bg:#0b0b0e; --card:#16161c; --hi:#f2f2f5; --mid:#a7a7b3; --ember:#ff4b4b; --amber:#ffb020; --toxic:#5dff7a; }
body { margin:0; background:var(--bg); color:var(--hi); font:14px/1.4 system-ui, sans-serif; }
header { padding:16px; position:sticky; top:0; background:var(--bg); border-bottom:1px solid #26262e; }
h1 { margin:0 0 8px; font-size:18px; letter-spacing:.08em; }
select, button { background:var(--card); color:var(--hi); border:1px solid #33333d; border-radius:8px; padding:6px 10px; }
main { display:grid; grid-template-columns:repeat(auto-fill, minmax(240px, 1fr)); gap:12px; padding:16px; }
figure { margin:0; background:var(--card); border-radius:12px; overflow:hidden; }
figure img { width:100%; aspect-ratio:16/9; object-fit:cover; display:block; background:#000; }
figcaption { padding:8px 10px; }
.meta { color:var(--mid); font-size:12px; }
.pill { display:inline-block; font-size:11px; padding:1px 8px; border-radius:99px; border:1px solid; }
.missing { aspect-ratio:16/9; display:flex; align-items:center; justify-content:center; color:var(--ember); }
.empty { padding:32px; color:var(--mid); }
</style></head><body>
<header><h1>DEAD-FEST // DEATH EVIDENCE</h1>
<label>Game <select id="game"><option value="">All games</option>__OPTIONS__</select></label>
<label>Sort <select id="sort"><option value="new">Newest first</option><option value="old">Oldest first</option></select></label>
<span class="meta" id="count"></span></header>
<main id="grid"></main>
<script>
const items = __DATA__;
const colours = { pending:'var(--amber)', verified:'var(--toxic)', rejected:'var(--ember)', removed:'var(--ember)', deleted:'var(--ember)' };
function esc(s) { return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function render() {
  const g = document.getElementById('game').value, sort = document.getElementById('sort').value;
  const list = items.filter(i => !g || i.game === g).sort((a, b) => sort === 'new' ? b.t - a.t : a.t - b.t);
  document.getElementById('count').textContent = list.length + ' item(s)';
  document.getElementById('grid').innerHTML = list.length ? list.map(i => `
    <figure>${i.exists ? `<a href="${esc(i.src)}"><img loading="lazy" src="${esc(i.src)}" alt="${esc(i.gameName)} death"></a>` : '<div class="missing">Image file missing</div>'}
    <figcaption><b>${esc(i.gameName)}</b> <span class="pill" style="color:${colours[i.status] || 'var(--mid)'}">${esc(i.status)}</span>
    <div class="meta">${esc(i.when)} · ${esc(i.type)}${i.player ? ' · ' + esc(i.player) : ''} · ${Math.round(i.conf * 100)}%</div>
    <div class="meta">${esc(i.sync)} · ${esc(i.id)}</div></figcaption></figure>`).join('') : '<p class="empty">No death evidence yet.</p>';
}
document.getElementById('game').onchange = render; document.getElementById('sort').onchange = render; render();
</script></body></html>
"""


def write(store: EvidenceStore) -> Path:
    items = []
    for r in store.records():
        rel = r.current_path or r.local_relative_path
        try:
            store.resolve(rel)
        except ValueError:
            continue
        items.append({
            "id": r.event_id,
            "game": r.game_id,
            "gameName": games.get(r.game_id).name if r.game_id in games.ids() else r.game_id,
            "t": r.detected_at_ms,
            "when": r.detected_at_local,
            "type": r.event_type,
            "player": r.player_label,
            "conf": r.confidence,
            "status": r.verification_status,
            "sync": {"private": "Private (this PC only)", "pending": "Waiting to upload", "synced": "Shared"}.get(r.sync_state, r.sync_state),
            "src": rel,
            "exists": store.image_exists(r),
        })
    options = "".join(f'<option value="{html.escape(g.id)}">{html.escape(g.name)}</option>' for g in games.GAMES)
    # json.dumps escapes quotes; "</" is escaped so no value can close the script tag.
    data = json.dumps(items).replace("</", "<\\/")
    page = _PAGE.replace("__OPTIONS__", options).replace("__DATA__", data)
    out = store.root / "index.html"
    tmp = out.with_suffix(".tmp")
    tmp.write_text(page, encoding="utf-8")
    os.replace(tmp, out)
    return out
