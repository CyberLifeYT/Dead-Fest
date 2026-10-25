/* Dead-Fest Detector window. Talks to Python through window.pywebview.api (see desktop.py Bridge). */
"use strict";

let api = null;
let state = {};
let players = [];
let evidence = [];
let tab = "live";
let hud = { image: null, w: 0, h: 0, boxes: {}, active: "self_bar" };

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const when = (ms) => (ms ? new Date(ms).toLocaleString() : "");

function toast(text, kind = "") {
  const t = $("toast");
  t.textContent = text;
  t.className = "toast " + kind;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => t.classList.add("hidden"), 4500);
}

function notice(id, text, kind = "") {
  const n = $(id);
  if (!text) { n.classList.add("hidden"); return; }
  n.textContent = text;
  n.className = "notice " + kind;
}

async function call(name, ...args) {
  try {
    const res = await api[name](...args);
    if (res && res.ok === false && res.error) toast(res.error, "bad");
    return res || {};
  } catch (e) {
    toast(String(e), "bad");
    return { ok: false, error: String(e) };
  }
}

/* ------------------------------------------------------------------ tabs */

document.querySelectorAll(".tab").forEach((b) =>
  b.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((x) => x.classList.toggle("active", x === b));
    tab = b.dataset.tab;
    document.querySelectorAll(".page").forEach((p) => p.classList.toggle("active", p.id === "tab-" + tab));
    if (tab === "live") ensureStream(); else stopStream();
    if (tab === "evidence") loadEvidence();
    if (tab === "players") loadPlayers();
    if (tab === "hud") renderHudSlots();
  })
);

/* ------------------------------------------------------------------ state */

function statusText(s) {
  const status = s.status && s.status !== "Stopped" ? s.status : "";
  return status;
}

function renderState() {
  const s = state;
  const pill = $("status-pill");
  pill.textContent = s.running ? "Watching" : "Stopped";
  pill.className = "pill " + (s.running ? "on" : "off");
  $("status-text").textContent = statusText(s);
  $("sync-text").textContent = s.lastSync ? "Last sync " + s.lastSync : "";
  const start = $("btn-start");
  start.textContent = s.running ? "Stop" : "Start detecting";
  start.classList.toggle("stop", !!s.running);
  $("stat-deaths").textContent = s.deaths || 0;
  $("stat-fps").textContent = s.measuredFps ? s.measuredFps.toFixed(0) : "0";
  // No frames flowing: say why (minimised Xbox app, closed window, unplugged card...).
  if (!s.measuredFps && s.captureStatus) {
    $("preview-empty").style.display = "block";
    $("preview-empty").textContent = s.captureStatus;
  } else if (s.measuredFps && $("preview-img").style.display === "block") {
    $("preview-empty").style.display = "none";
  }
  syncFps(s.previewFps);
  if (tab === "live") ensureStream();
  $("stat-frames").textContent = s.framesAnalyzed || 0;
  $("stat-held").textContent = s.held || 0;
  $("account-foot").textContent = s.signedIn ? (s.handle || s.email) + (s.isAdmin ? " · admin" : "") : "Not signed in";

  fillGames($("live-game"), s.game, false);
  if (!$("ev-game").dataset.filled) { fillGames($("ev-game"), "", true); $("ev-game").dataset.filled = "1"; }
  $("live-share").checked = !!s.sharing;
  $("live-count").checked = !!s.autoCount;
  $("live-count").disabled = !s.sharing;
  fillMe();

  const warn = [];
  if (s.storageError) warn.push(s.storageError);
  if (s.frameSize && s.frameSize.length && s.frameSize[0] < 960)
    warn.push(`The Xbox app window is small (${s.frameSize[0]}×${s.frameSize[1]}). Make it bigger or maximise it, so names and death text can be read reliably.`);
  if (!s.signedIn) warn.push("Not signed in: deaths are saved on this PC only. Sign in under Settings to share and count them.");
  else if (!s.sharing) warn.push("Sharing is off: deaths stay on this PC. Turn on \"Share with the app\" to upload and count them.");
  else if (s.autoCount && !s.isAdmin && s.handle) warn.push("Counting straight away needs an admin account; deaths upload as pending.");
  notice("live-warn", warn.join(" "), s.storageError ? "bad" : "");

  const log = $("log");
  log.innerHTML = (s.log || []).map((e) => `<li><span class="t">${esc(e.t)}</span><span class="${esc(e.kind)}">${esc(e.text)}</span></li>`).join("")
    || '<li class="muted">Nothing yet.</li>';

  // Settings
  $("acct-in").classList.toggle("hidden", !s.signedIn);
  $("acct-form").classList.toggle("hidden", !!s.signedIn);
  $("acct-email").textContent = s.email || "";
  $("acct-admin").classList.toggle("hidden", !s.isAdmin);
  const src = (s.source || {}).type;
  document.querySelectorAll('input[name=src]').forEach((r) => (r.checked = r.value === src));
  if (document.activeElement !== $("set-device")) $("set-device").value = (s.source || {}).device_index ?? 0;
  if (document.activeElement !== $("set-root")) $("set-root").value = s.evidenceRoot || "";
}

function fillGames(sel, current, withAll) {
  const games = state.games || [];
  const opts = (withAll ? '<option value="">All games</option>' : "") +
    games.map((g) => `<option value="${esc(g.id)}">${esc(g.name)}</option>`).join("");
  if (sel.dataset.sig !== opts) { sel.innerHTML = opts; sel.dataset.sig = opts; }
  if (document.activeElement !== sel && current !== undefined && !withAll) sel.value = current;
}

function fillMe() {
  const sel = $("live-me");
  const opts = players.length
    ? players.map((p) => `<option value="${esc(p.uid)}">${esc(p.handle)}</option>`).join("")
    : `<option value="${esc(state.meUid || "")}">${esc(state.handle || "Me (sign in to choose)")}</option>`;
  if (sel.dataset.sig !== opts) { sel.innerHTML = opts; sel.dataset.sig = opts; }
  if (document.activeElement !== sel) sel.value = state.meUid || "";
}

async function poll() {
  state = await api.state();
  renderState();
}

/* ------------------------------------------------------------------ live */

/* The preview is an MJPEG stream from a local-only server (127.0.0.1, secret token). */
let streamUrl = "";
function ensureStream(force) {
  const img = $("preview-img");
  const url = state.previewUrl ? state.previewUrl + "&w=" + Math.min(1920, Math.max(640, Math.round(img.parentElement.clientWidth * (window.devicePixelRatio || 1)))) : "";
  if (!url || (url.split("&w=")[0] === streamUrl.split("&w=")[0] && !force)) return;
  streamUrl = url;
  img.onload = () => { img.style.display = "block"; $("preview-empty").style.display = "none"; $("fps-badge").classList.remove("hidden"); };
  img.onerror = () => { $("preview-empty").style.display = "block"; $("preview-empty").textContent = "No picture. Is the Xbox app open with Remote Play?"; };
  img.src = url + "&t=" + Date.now();
}
function stopStream() {
  streamUrl = "";
  $("preview-img").removeAttribute("src");
  $("preview-img").style.display = "none";
  $("fps-badge").classList.add("hidden");
  $("preview-empty").style.display = "block";
  $("preview-empty").textContent = "Connecting to the picture…";
}
setInterval(() => { if (state.measuredFps !== undefined) $("fps-badge").textContent = Math.round(state.measuredFps || 0) + " FPS"; }, 1000);

function syncFps(fps) {
  const sel = $("fps-select"), custom = $("fps-custom");
  if (document.activeElement === sel || document.activeElement === custom) return;
  const preset = ["15", "30", "60"].includes(String(fps)) ? String(fps) : "custom";
  sel.value = preset;
  custom.classList.toggle("hidden", preset !== "custom");
  if (preset === "custom") custom.value = fps;
}
$("fps-select").addEventListener("change", (e) => {
  const v = e.target.value;
  $("fps-custom").classList.toggle("hidden", v !== "custom");
  if (v === "custom") { $("fps-custom").focus(); return; }
  setFps(parseFloat(v));
});
$("fps-custom").addEventListener("change", (e) => setFps(Math.max(1, Math.min(120, parseFloat(e.target.value) || 30))));
async function setFps(fps) {
  await saveSetting({ previewFps: fps });
  stopStream();
  setTimeout(() => ensureStream(true), 300);
}

$("btn-start").addEventListener("click", async () => {
  const res = await call(state.running ? "stop" : "start");
  if (res.ok) poll();
});
$("btn-preview").addEventListener("click", () => { stopStream(); setTimeout(() => ensureStream(true), 200); });
$("btn-sync").addEventListener("click", async () => {
  const res = await call("sync_now");
  if (res.ok) toast(`Uploaded ${res.uploaded}, counted ${res.counted}` + (res.failed ? `, ${res.failed} waiting` : ""), "good");
  poll();
});
$("live-game").addEventListener("change", (e) => saveSetting({ game: e.target.value }));
$("live-me").addEventListener("change", (e) => saveSetting({ meUid: e.target.value }));
$("live-share").addEventListener("change", (e) => saveSetting({ sharing: e.target.checked }));
$("live-count").addEventListener("change", (e) => saveSetting({ autoCount: e.target.checked }));

async function saveSetting(obj) {
  const res = await call("save_settings", obj);
  if (res.ok && res.restartNeeded) toast("Saved. Stop and start detecting to use it.");
  else if (res.ok) toast("Saved.", "good");
  poll();
}

/* ------------------------------------------------------------------ evidence */

const STATUS = {
  verified: ["Counted", "on"], pending: ["Pending", "warn"], rejected: ["Not verified", "bad"],
  removed: ["Removed", "off"], deleted: ["Deleted", "off"],
};

function evStatus(it) {
  if (it.sync === "private") return ["Private", "off"];
  if (it.sync === "pending" && it.local && !it.reviewer) return ["Uploading", "warn"];
  return STATUS[it.status] || [it.status, "off"];
}

async function loadEvidence() {
  $("ev-grid").innerHTML = '<p class="muted">Loading…</p>';
  if (!players.length) await loadPlayers(true);
  const res = await call("evidence");
  evidence = res.items || [];
  notice("ev-note", res.remoteError ? "Couldn't load shared evidence: " + res.remoteError : "", "bad");
  renderEvidence();
}

function renderEvidence() {
  const g = $("ev-game").value, st = $("ev-status").value;
  const list = evidence.filter((it) => (!g || it.game === g) &&
    (!st || (st === "private" ? it.sync === "private" : it.status === st && it.sync !== "private")));
  const opts = players.map((p) => `<option value="${esc(p.uid)}">${esc(p.handle)}</option>`).join("");
  $("ev-grid").innerHTML = list.length ? list.map((it) => {
    const [label, kind] = evStatus(it);
    const who = it.type === "self" ? "Own death" : it.type === "teammate" ? "Teammate" : it.type === "other" ? "Other player" : "Unknown";
    const shared = it.sync !== "private";
    const suggested = it.subjectUid || (it.type === "self" ? state.meUid : "");
    const actions = [];
    if (shared && (it.status === "pending")) {
      actions.push(`<select data-who="${esc(it.id)}">${opts}</select><button class="primary" data-act="verify" data-id="${esc(it.id)}">Count it</button>`);
    }
    if (shared && (it.status === "pending" || it.status === "verified")) actions.push(`<button class="ghost" data-act="reject" data-id="${esc(it.id)}">Not verified</button>`);
    if (shared && (it.status === "rejected" || it.status === "removed")) actions.push(`<button class="ghost" data-act="restore" data-id="${esc(it.id)}">Restore</button>`);
    if (!shared) actions.push(`<button class="primary" data-act="share" data-id="${esc(it.id)}">Share</button>`);
    if (it.local) actions.push(`<button class="ghost" data-act="file" data-id="${esc(it.id)}">Open file</button>`);
    return `<div class="ev">
      <div class="thumb" data-zoom="${esc(it.id)}" style="background-image:url('${it.thumb || ""}')"><span class="badge pill ${kind}">${esc(label)}</span></div>
      <div class="body">
        <div class="title"><b>${esc(it.gameName)}</b><span class="muted small">${esc(Math.round((it.confidence || 0) * 100))}%</span></div>
        <div class="muted small">${esc(when(it.time))}</div>
        <div class="small">${esc(who)}${it.player ? " · " + esc(it.player) : ""}</div>
        ${it.subjectName && it.status === "verified" ? `<div class="small" style="color:var(--toxic)">Counted for ${esc(it.subjectName)}${it.sector ? " in " + esc(it.sector) : ""}</div>` : ""}
        ${it.reason ? `<div class="small" style="color:var(--ember)">${esc(it.reason)}</div>` : ""}
        ${it.note && it.status === "pending" ? `<div class="note">${esc(it.note)}</div>` : ""}
        <div class="muted small">From ${esc(it.submitter || "")}${it.imageOnPc === false && it.local ? " · image file missing" : ""}</div>
        <div class="actions">${actions.join("")}</div>
      </div></div>`;
  }).join("") : '<p class="muted">No deaths match.</p>';
  list.forEach((it) => {
    const sel = document.querySelector(`select[data-who="${CSS.escape(it.id)}"]`);
    if (sel) sel.value = it.subjectUid || (it.type === "self" ? state.meUid : (players[0] || {}).uid || "");
  });
}

$("ev-grid").addEventListener("click", async (e) => {
  const z = e.target.closest("[data-zoom]");
  if (z) return openEvidence(z.dataset.zoom);
  const b = e.target.closest("button[data-act]");
  if (!b) return;
  const id = b.dataset.id, act = b.dataset.act;
  b.disabled = true;
  let res;
  if (act === "verify") {
    const who = document.querySelector(`select[data-who="${CSS.escape(id)}"]`).value;
    const it = evidence.find((x) => x.id === id) || {};
    res = await call("moderate", id, "verify", { subjectUid: who, game: it.game });
  } else if (act === "reject") {
    const reason = prompt("Why isn't this a real death? (shown to admins)", "Not a real death");
    if (reason === null) { b.disabled = false; return; }
    res = await call("moderate", id, "reject", { reason });
  } else if (act === "restore") {
    res = await call("moderate", id, "restore", {});
  } else if (act === "share") {
    res = await call("share", [id]);
  } else if (act === "file") {
    res = await call("open_file", id);
  }
  if (res && res.ok && res.message) toast(res.message, "good");
  if (act !== "file") loadEvidence();
  else b.disabled = false;
});
$("ev-game").addEventListener("change", renderEvidence);
$("ev-status").addEventListener("change", renderEvidence);
$("btn-ev-refresh").addEventListener("click", loadEvidence);
$("btn-folder").addEventListener("click", () => call("open_folder"));
$("btn-share-all").addEventListener("click", async () => {
  const res = await call("share", []);
  if (res.ok) toast(`${res.queued || 0} private death(s) queued` + (res.note ? ". " + res.note : ""), "good");
  loadEvidence();
});

async function openEvidence(id) {
  const it = evidence.find((x) => x.id === id) || {};
  $("modal-img").src = it.thumb || "";
  $("modal-info").innerHTML = `<b>${esc(it.gameName)}</b> · ${esc(when(it.time))} · <span class="muted">${esc(id)}</span>`;
  $("modal").classList.remove("hidden");
  const res = await call("evidence_image", id);
  if (res.ok) $("modal-img").src = res.image;
}
$("modal-close").addEventListener("click", () => $("modal").classList.add("hidden"));
$("modal").addEventListener("click", (e) => { if (e.target.id === "modal") $("modal").classList.add("hidden"); });

/* ------------------------------------------------------------------ players */

let selectedPlayer = "";
async function loadPlayers(quiet) {
  const res = quiet ? await api.players() : await call("players");
  if (!res.ok) { notice("pl-note", res.error || ""); return; }
  notice("pl-note", "");
  players = res.players;
  fillMe();
  renderPlayers();
}

function renderPlayers() {
  $("pl-table").querySelector("tbody").innerHTML = players.map((p) => `
    <tr data-uid="${esc(p.uid)}" class="${p.uid === selectedPlayer ? "sel" : ""}">
      <td><input type="radio" name="me" value="${esc(p.uid)}" ${p.isMe ? "checked" : ""}></td>
      <td>${esc(p.avatar)} ${esc(p.handle)}${p.admin ? '<span class="admin-tag">ADMIN</span>' : ""}</td>
      <td><input type="text" data-tag="${esc(p.uid)}" value="${esc(p.gamertag)}" placeholder="gamertag"></td>
      <td>${esc(p.level)}</td><td>${esc(p.coins)}</td><td>${esc(p.totalDeaths)}</td>
    </tr>`).join("");
  renderPlayerDetail();
}

function renderPlayerDetail() {
  const p = players.find((x) => x.uid === selectedPlayer);
  if (!p) { $("pl-detail").innerHTML = '<p class="muted">Select a player to see their deaths per game.</p>'; return; }
  const rows = Object.entries(p.deathsBySector || {}).sort((a, b) => b[1] - a[1])
    .map(([k, v]) => `<div><span>${esc(k)}</span><b>${esc(v)}</b></div>`).join("") || '<p class="muted">No deaths recorded.</p>';
  $("pl-detail").innerHTML = `<h3>${esc(p.handle)}</h3>
    <p class="muted small">Level ${esc(p.level)} · ${esc(p.coins)} coins · ${esc(p.totalDeaths)} deaths${p.gamertag ? " · Xbox: " + esc(p.gamertag) : ""}</p>
    <div class="bars">${rows}</div>`;
}

$("pl-table").addEventListener("click", (e) => {
  const tr = e.target.closest("tr[data-uid]");
  if (!tr) return;
  if (e.target.name === "me") {
    saveSetting({ meUid: e.target.value }).then(() => loadPlayers(true));
    return;
  }
  if (e.target.matches("input[type=text]")) return;
  selectedPlayer = tr.dataset.uid;
  renderPlayers();
});
$("pl-table").addEventListener("change", (e) => {
  if (e.target.dataset.tag) saveSetting({ gamertag: [e.target.dataset.tag, e.target.value] });
});
$("btn-pl-refresh").addEventListener("click", () => loadPlayers(false));

/* ------------------------------------------------------------------ HUD setup */

const HUD_SLOTS = [
  ["self_bar", "My health bar", "#5dff7a"],
  ["team_area", "Teammates area", "#8c9bff"],
  ["example_name", "Example name", "#ffb020"],
  ["example_bar", "Example health bar", "#ff7ad9"],
];
let hudFound = [];   // boxes from the last Test: [{name, nameBox, bar, fill, matched}]

function renderHudSlots() {
  $("hud-slots").innerHTML = HUD_SLOTS.map(([k, label, colour]) =>
    `<button data-slot="${k}" class="${hud.active === k ? "active" : ""}" style="color:${colour}">${label}${hud.boxes[k] ? " ✓" : ""}</button>`).join("") +
    '<button data-slot="clear" style="color:var(--mid)">Clear selected</button>';
  drawHud();
}

$("hud-slots").addEventListener("click", (e) => {
  const b = e.target.closest("button[data-slot]");
  if (!b) return;
  if (b.dataset.slot === "clear") delete hud.boxes[hud.active];
  else hud.active = b.dataset.slot;
  renderHudSlots();
});

function hudFromSaved(saved) {
  hud.boxes = {};
  hudFound = [];
  if (!saved) return;
  if (saved.self_bar) hud.boxes.self_bar = saved.self_bar.box;
  if (saved.team) {
    hud.boxes.team_area = saved.team.area;
    if (saved.team.example_name) hud.boxes.example_name = saved.team.example_name;
    if (saved.team.example_bar) hud.boxes.example_bar = saved.team.example_bar;
    $("hud-anchor").value = saved.team.anchor || "left";
    $("hud-cue").value = saved.team.death_cue || "bar_empty";
  }
  if (saved.hold_seconds) $("hud-hold").value = saved.hold_seconds;
}

$("btn-hud-capture").addEventListener("click", async () => {
  notice("hud-note", "Capturing…");
  const res = await call("hud_capture");
  if (!res.ok) { notice("hud-note", res.error || "", "bad"); return; }
  notice("hud-note", "");
  hud.w = res.width; hud.h = res.height;
  const img = $("hud-img");
  img.onload = () => { $("hud-empty").style.display = "none"; img.style.display = "block"; drawHud(); };
  img.src = res.image;
  hudFromSaved(res.hud);
  if (!(res.hud && res.hud.team)) $("hud-cue").value = res.defaultCue || "bar_empty";
  renderHudSlots();
});

function drawHud() {
  const img = $("hud-img"), cv = $("hud-canvas");
  if (!img.src || img.style.display === "none") return;
  cv.width = img.clientWidth; cv.height = img.clientHeight;
  const ctx = cv.getContext("2d");
  ctx.clearRect(0, 0, cv.width, cv.height);
  const rect = (b, colour, width, label, dash) => {
    const [x, y, w, h] = [b[0] * cv.width, b[1] * cv.height, b[2] * cv.width, b[3] * cv.height];
    ctx.setLineDash(dash || []);
    ctx.strokeStyle = colour; ctx.lineWidth = width;
    ctx.strokeRect(x, y, w, h);
    if (label) { ctx.fillStyle = colour; ctx.font = "12px Segoe UI"; ctx.fillText(label, x, Math.max(12, y - 4)); }
  };
  HUD_SLOTS.forEach(([k, label, colour]) => {
    if (hud.boxes[k]) rect(hud.boxes[k], colour, k === hud.active ? 3 : 2, label, k === "team_area" ? [6, 4] : null);
  });
  hudFound.forEach((f) => {
    const colour = f.fill < 0.05 ? "#ff4b4b" : "#5dff7a";
    if (f.nameBox) rect(f.nameBox, "#ffffff", 1, `${f.name} ${Math.round(f.fill * 100)}%`);
    if (f.bar) rect(f.bar, colour, 2);
  });
  ctx.setLineDash([]);
}
window.addEventListener("resize", drawHud);

(function hudDrag() {
  const cv = $("hud-canvas");
  let start = null;
  const pos = (e) => { const r = cv.getBoundingClientRect(); return [(e.clientX - r.left) / r.width, (e.clientY - r.top) / r.height]; };
  cv.addEventListener("mousedown", (e) => { start = pos(e); });
  cv.addEventListener("mousemove", (e) => {
    if (!start) return;
    const [x, y] = pos(e);
    hud.boxes[hud.active] = [Math.min(start[0], x), Math.min(start[1], y), Math.abs(x - start[0]), Math.abs(y - start[1])];
    drawHud();
  });
  window.addEventListener("mouseup", () => {
    if (!start) return;
    start = null;
    const b = hud.boxes[hud.active];
    if (b && (b[2] < 0.005 || b[3] < 0.003)) delete hud.boxes[hud.active];
    renderHudSlots();
  });
})();

$("btn-hud-save").addEventListener("click", async () => {
  const res = await call("hud_save", {
    self_bar: hud.boxes.self_bar || null, team_area: hud.boxes.team_area || null,
    example_name: hud.boxes.example_name || null, example_bar: hud.boxes.example_bar || null,
    anchor: $("hud-anchor").value, death_cue: $("hud-cue").value, hold_seconds: parseFloat($("hud-hold").value) || 3,
  });
  if (res.ok) notice("hud-note", res.message + (res.restartNeeded ? " Stop and start detecting to use it." : ""), "good");
  poll();
});

$("btn-hud-test").addEventListener("click", async () => {
  $("hud-readings").textContent = "Reading the HUD…";
  const res = await call("hud_test");
  if (!res.ok) { $("hud-readings").textContent = res.error || ""; return; }
  // Show the frame that was tested, with every teammate found by name drawn on it.
  const img = $("hud-img");
  img.onload = () => { $("hud-empty").style.display = "none"; img.style.display = "block"; drawHud(); };
  img.src = res.image;
  hudFound = res.readings.filter((r) => r.slot === "Teammate");
  const rows = res.readings.map((r) => `
    <div class="reading"><span class="who">${r.slot === "You" ? "You" : esc(r.name)}
      ${r.slot === "Teammate" ? (r.matched ? '<span class="tag-ok">Dead-Fest player</span>' : '<span class="tag-no">not a Dead-Fest player</span>') : ""}</span>
    <span class="meter"><i style="width:${Math.round(r.fill * 100)}%;background:${r.fill < 0.05 ? "var(--ember)" : "var(--toxic)"}"></i></span>
    <b>${Math.round(r.fill * 100)}%</b>${r.colour && r.colour !== "none" ? ` <span class="${r.colour === "red" ? "tag-no" : "tag-ok"}">name ${esc(r.colour)}</span>` : ""}</div>`).join("");
  const none = hudFound.length ? "" : '<p class="small" style="color:var(--amber)">No teammate names found in the teammates area. Check the box covers their names, and that players are signed in (Players tab) so names can be recognised.</p>';
  $("hud-readings").innerHTML = rows + none +
    `<p class="small muted">${res.knownPlayers} Dead-Fest players known for name matching. A bar at 0% for ${esc(res.holdSeconds)} s counts as a death.</p>`;
  drawHud();
});

/* ------------------------------------------------------------------ settings */

$("acct-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const res = await call("sign_in", $("acct-user").value, $("acct-pass").value);
  $("acct-pass").value = "";
  if (res.ok) { toast("Signed in.", "good"); loadPlayers(true); }
  poll();
});
$("btn-signout").addEventListener("click", async () => { await call("sign_out"); players = []; poll(); });
document.querySelectorAll("input[name=src]").forEach((r) => r.addEventListener("change", () => saveSetting({ sourceType: r.value })));
$("set-device").addEventListener("change", (e) => saveSetting({ deviceIndex: parseInt(e.target.value, 10) || 0 }));
$("btn-root").addEventListener("click", () => saveSetting({ evidenceRoot: $("set-root").value }));

/* ------------------------------------------------------------------ start */

window.addEventListener("pywebviewready", async () => {
  api = window.pywebview.api;
  await poll();
  if (state.signedIn) loadPlayers(true);
  setInterval(poll, 1500);
  const start = location.hash.slice(1);
  const b = start && document.querySelector(`.tab[data-tab="${start}"]`);
  if (b) b.click();
});
