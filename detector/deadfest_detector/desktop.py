"""
Dead-Fest Detector: the desktop app (pywebview window around ui/index.html).

Everything the command line does, in one window:
  Live      start/stop, the picture the detector sees, a log of deaths and syncs
  Evidence  every death (this PC's and everyone's shared ones): count, mark not verified, restore
  Players   Dead-Fest players with their deaths, coins and level; who is "me"; Xbox gamertags
  HUD       mark each player's name and health bar on screen, for deaths read from the HUD
  Settings  sign in, sharing, counting, game, video source, evidence folder

The window calls the Api methods below; long work (capture, detection, uploads) runs on background
threads so the window never freezes.
"""

from __future__ import annotations

import base64
import os
import threading
import traceback
from collections import deque
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np

from . import __version__, config as configmod, games, imaging, moderation, ocr
from .cli import build_rules, load_session, save_session, session_path, templates_dir
from .counting import AutoCounter
from .detect import Analyzer, load_templates
from .firebase_rest import AuthClient, FirebaseError, FirestoreClient, NetworkError
from .hud import HudConfig, HudTracker, calibrate_team, crop, reference_colour
from .live import LIVE_SOURCES, FrameHub, PreviewServer, ThreadedSource
from .pipeline import Pipeline
from .players import Player, match_name
from .sources import SourceError, first_frame, from_config, looks_blank
from .state import DeathStateMachine
from .storage import SYNC_PENDING, SYNC_PRIVATE, SYNC_SYNCED, EvidenceStore, StorageUnavailable
from .sync import Syncer

UI_DIR = Path(__file__).resolve().parent / "ui"
REPO_GOOGLE_SERVICES = Path(__file__).resolve().parents[2] / "app" / "google-services.json"


def _b64_jpeg(frame: np.ndarray, max_dim: int = 960, quality: int = 75) -> str:
    h, w = frame.shape[:2]
    scale = min(1.0, max_dim / max(h, w))
    if scale < 1.0:
        frame = cv2.resize(frame, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)
    ok, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, quality])
    return "data:image/jpeg;base64," + base64.b64encode(buf.tobytes()).decode() if ok else ""


def _b64_bytes(data: bytes) -> str:
    return "data:image/jpeg;base64," + base64.b64encode(data).decode()


class _Published:
    """Recorded footage (not live): frames go to the pipeline in order and are also shown live."""

    def __init__(self, source, hub: FrameHub):
        self.source = source
        self.hub = hub
        self.description = source.description

    def frames(self):
        for t, frame in self.source.frames():
            self.hub.publish(t, frame)
            yield t, frame

    def close(self):
        self.source.close()


class Api:
    def __init__(self):
        self.cfg = configmod.load()
        self.log: deque[dict] = deque(maxlen=200)
        self.lock = threading.RLock()
        self.running = False
        self.stop_flag = threading.Event()
        self.sync_wake = threading.Event()
        self.pipeline: Pipeline | None = None
        self.source = None
        self.status_line = "Stopped"
        self.players_cache: list[Player] = []
        self.thumb_cache: dict[str, str] = {}
        self.hud_frame: np.ndarray | None = None
        self.last_sync = ""
        self.cfg.setdefault("gamertags", {})
        self.cfg.setdefault("hud", {})
        self.cfg.setdefault("auto_count", True)
        self.cfg.setdefault("preview_fps", 30)
        # Live preview: frames captured on their own thread, streamed to the window as MJPEG.
        self.hub = FrameHub()
        self.preview_source: ThreadedSource | None = None
        self.preview_lock = threading.Lock()
        self.preview_server = PreviewServer(self.hub, on_viewer=self._on_viewer)
        self.capture_status = ""
        threading.Thread(target=self._sync_loop, daemon=True).start()

    # ------------------------------------------------------------------ helpers

    def _event(self, kind: str, text: str) -> None:
        self.log.appendleft({"t": datetime.now().strftime("%H:%M:%S"), "kind": kind, "text": text})

    def _save_cfg(self) -> None:
        configmod.save(self.cfg)

    def _store(self) -> EvidenceStore:
        return EvidenceStore(self.cfg["evidence_root"])

    def _session(self):
        if not session_path().exists() or not self.cfg["firebase"]["api_key"]:
            return None
        try:
            return load_session(self.cfg)
        except (OSError, ValueError, KeyError):
            return None

    def _fs(self) -> tuple[FirestoreClient, str] | None:
        s = self._session()
        if s is None:
            return None
        return FirestoreClient(self.cfg["firebase"]["project_id"], s.token), s.uid

    def _players(self, refresh: bool = False) -> list[Player]:
        if refresh or not self.players_cache:
            conn = self._fs()
            if conn:
                fs, _ = conn
                self.players_cache = sorted((Player.from_doc(uid, d) for uid, d in fs.list_documents("users")),
                                            key=lambda p: p.handle.lower())
        return self.players_cache

    def _resolve_name(self, raw: str) -> str:
        p = match_name(raw, self.players_cache, self.cfg.get("gamertags", {}))
        return p.handle if p else raw

    def _hud_identity(self, raw: str) -> tuple[str, str]:
        """(key, label) for a name read off the HUD: the Dead-Fest player it is, else the raw name."""
        p = match_name(raw, self.players_cache, self.cfg.get("gamertags", {}))
        return (p.uid, p.handle) if p else (raw.upper(), raw)

    def _known_names(self) -> list[str]:
        return [p.handle for p in self.players_cache] + [t for t in self.cfg.get("gamertags", {}).values() if t]

    def _open_source(self, src_cfg: dict):
        """from_config, with the source's status messages shown in the window."""
        raw = from_config(src_cfg)
        self.capture_status = ""

        def status(msg: str) -> None:
            self.capture_status = "" if msg.startswith("Capturing") else msg
            self._event("info" if msg.startswith("Capturing") else "warn", msg)

        raw.on_status = status
        return raw

    def _source_cfg(self) -> dict:
        """The source settings, capturing live sources at the preview frame rate."""
        src = dict(self.cfg["source"])
        src["capture_fps"] = max(float(self.cfg.get("preview_fps", 30)), float(self.cfg["detection"]["analysis_fps"]))
        return src

    # ------------------------------------------------------------------ live preview

    def _on_viewer(self, active: bool) -> None:
        if active:
            self._start_preview_capture()
        else:
            threading.Timer(3.0, self._maybe_stop_preview).start()

    def _start_preview_capture(self) -> None:
        """Capture just for the preview while detection is stopped (detection feeds it otherwise)."""
        with self.preview_lock:
            if self.running or (self.preview_source and self.preview_source.running):
                return
            src_cfg = self._source_cfg()
            if src_cfg.get("type") not in LIVE_SOURCES:
                return
            try:
                raw = self._open_source(src_cfg)
            except SourceError as e:
                self.capture_status = str(e)
                self._event("warn", f"Preview: {e}")
                return
            self.preview_source = ThreadedSource(raw, self.hub).start()

    def _stop_preview_capture(self) -> None:
        with self.preview_lock:
            if self.preview_source:
                self.preview_source.close()
                self.preview_source = None

    def _maybe_stop_preview(self) -> None:
        if self.preview_server.viewers == 0:
            self._stop_preview_capture()

    def _current_frame(self, timeout: float = 15.0) -> np.ndarray:
        """The newest live frame if one is flowing, else a fresh one-off capture."""
        if self.running or (self.preview_source and self.preview_source.running):
            got = self.hub.wait_next(self.hub.seq - 1, timeout=2.0)
            if got is not None:
                return got[2].copy()
        src = from_config(self._source_cfg())
        try:
            return first_frame(src, timeout=timeout)
        finally:
            src.close()

    @staticmethod
    def _safe(fn):
        try:
            return fn()
        except (FirebaseError, NetworkError) as e:
            return {"ok": False, "error": str(e)}
        except Exception as e:  # noqa: BLE001 - every error reaches the window as text, never a hang
            traceback.print_exc()
            return {"ok": False, "error": f"{type(e).__name__}: {e}"}

    # ------------------------------------------------------------------ state

    def state(self) -> dict:
        s = self._session()
        me = next((p for p in self.players_cache if s and p.uid == s.uid), None)
        stats = self.pipeline.stats if self.pipeline else None
        return {
            "version": __version__,
            "running": self.running,
            "status": self.status_line,
            "signedIn": s is not None,
            "email": s.email if s else "",
            "uid": s.uid if s else "",
            "isAdmin": bool(me and me.admin),
            "handle": me.handle if me else "",
            "game": self.cfg["game"],
            "games": [{"id": g.id, "name": g.name} for g in games.GAMES],
            "sharing": self.cfg["sharing"] == configmod.SHARING_SHARED,
            "autoCount": bool(self.cfg.get("auto_count", True)),
            "meUid": self.cfg.get("me_uid") or (s.uid if s else ""),
            "source": self.cfg["source"],
            "evidenceRoot": self.cfg["evidence_root"],
            "deaths": stats.deaths if stats else 0,
            "framesAnalyzed": stats.frames_analyzed if stats else 0,
            "storageError": stats.storage_error if stats else "",
            "held": self.pipeline.held if self.pipeline else 0,
            "lastSync": self.last_sync,
            "log": list(self.log)[:60],
            "hasHud": bool(self.cfg["hud"].get(self.cfg["game"])),
            "previewFps": float(self.cfg.get("preview_fps", 30)),
            "previewUrl": self.preview_server.url(float(self.cfg.get("preview_fps", 30))),
            "measuredFps": round(self.hub.fps(), 1),
            "captureStatus": self.capture_status,
        }

    # ------------------------------------------------------------------ account and settings

    def sign_in(self, email: str, password: str) -> dict:
        def go():
            if not self.cfg["firebase"]["api_key"]:
                if not REPO_GOOGLE_SERVICES.exists():
                    return {"ok": False, "error": f"google-services.json not found at {REPO_GOOGLE_SERVICES}"}
                configmod.import_google_services(self.cfg, REPO_GOOGLE_SERVICES)
                self._save_cfg()
            data = AuthClient(self.cfg["firebase"]["api_key"]).sign_in(email.strip(), password)
            save_session({"uid": data["uid"], "email": data["email"], "refresh_token": data["refresh_token"]})
            if not self.cfg.get("me_uid"):
                self.cfg["me_uid"] = data["uid"]
                self._save_cfg()
            self._players(refresh=True)
            self._event("account", f"Signed in as {data['email']}")
            return {"ok": True}
        return self._safe(go)

    def sign_out(self) -> dict:
        session_path().unlink(missing_ok=True)
        self.players_cache = []
        self._event("account", "Signed out. Evidence on this PC is untouched.")
        return {"ok": True}

    def save_settings(self, s: dict) -> dict:
        with self.lock:
            if "game" in s:
                games.get(s["game"])
                self.cfg["game"] = s["game"]
            if "sharing" in s:
                if s["sharing"] and not self._session():
                    return {"ok": False, "error": "Sign in first so shared deaths can be uploaded."}
                self.cfg["sharing"] = configmod.SHARING_SHARED if s["sharing"] else configmod.SHARING_PRIVATE
            if "autoCount" in s:
                self.cfg["auto_count"] = bool(s["autoCount"])
            if "meUid" in s:
                self.cfg["me_uid"] = s["meUid"]
            if "sourceType" in s:
                self.cfg["source"]["type"] = s["sourceType"]
            if "deviceIndex" in s:
                self.cfg["source"]["device_index"] = int(s["deviceIndex"])
            if "evidenceRoot" in s and s["evidenceRoot"].strip():
                self.cfg["evidence_root"] = s["evidenceRoot"].strip()
            if "gamertag" in s:
                uid, tag = s["gamertag"]
                self.cfg["gamertags"][uid] = tag.strip()[:40]
            if "previewFps" in s:
                self.cfg["preview_fps"] = max(1.0, min(120.0, float(s["previewFps"])))
            self._save_cfg()
        if "previewFps" in s or "sourceType" in s or "deviceIndex" in s:
            # Restart the preview-only capture at the new rate / source (detection restarts on Start).
            if self.preview_source:
                self._stop_preview_capture()
                if self.preview_server.viewers:
                    self._start_preview_capture()
        if self.cfg["sharing"] == configmod.SHARING_SHARED:
            self.sync_wake.set()
        return {"ok": True, "restartNeeded": self.running and any(k in s for k in ("game", "sourceType", "deviceIndex", "evidenceRoot", "previewFps"))}

    # ------------------------------------------------------------------ detection

    def _build_analyzer(self, game_id: str) -> Analyzer:
        det = self.cfg["detection"]
        profile = games.compile_profile(games.get(game_id), self.cfg.get("game_overrides", {}).get(game_id))
        try:
            reader = ocr.create(det.get("ocr", "windows"), int(det.get("ocr_width", 1280)))
        except Exception as e:  # noqa: BLE001
            self._event("warn", f"Text recognition unavailable ({e}); templates and HUD only.")
            reader = ocr.NullOcr()
        player = self.cfg.get("player", {})
        names = [player.get("gamertag", "")] + list(player.get("aliases", []))
        me = self.cfg.get("me_uid")
        if me and self.cfg["gamertags"].get(me):
            names.append(self.cfg["gamertags"][me])
        hud_cfg = HudConfig.from_json(self.cfg["hud"].get(game_id))
        hud = HudTracker(hud_cfg, reader, self._hud_identity, self._known_names) if hud_cfg else None
        return Analyzer(profile, reader, load_templates(templates_dir(game_id)),
                        template_threshold=float(det.get("template_threshold", 0.8)),
                        color_boost=float(det.get("color_boost", 0.05)),
                        self_names=names, teammates=list(player.get("teammates", [])), hud=hud)

    def start(self) -> dict:
        def go():
            with self.lock:
                if self.running:
                    return {"ok": True}
                self._stop_preview_capture()      # detection takes over the camera / window
                src_cfg = self._source_cfg()
                try:
                    raw = self._open_source(src_cfg)
                except SourceError as e:
                    return {"ok": False, "error": str(e)}
                game_id = self.cfg["game"]
                store = self._store()
                try:
                    store.ensure_layout()
                except StorageUnavailable as e:
                    self._event("warn", f"{e} Deaths will be held until it's back.")
                if self.cfg["sharing"] == configmod.SHARING_SHARED:
                    self._players()
                if src_cfg.get("type") in LIVE_SOURCES:
                    # Captured on its own thread at the preview rate; detection takes the newest frame.
                    self.source = ThreadedSource(raw, self.hub)
                else:
                    self.source = _Published(raw, self.hub)
                det, cap = self.cfg["detection"], self.cfg["capture"]

                def on_record(r):
                    label = f" ({r.player_label})" if r.player_label else ""
                    self._event("death", f"{games.get(r.game_id).name}: {r.event_type}{label}, {r.confidence:.0%}")
                    self.thumb_cache.pop(r.event_id, None)
                    self.sync_wake.set()

                self.pipeline = Pipeline(
                    game_id=game_id, store=store, analyzer=self._build_analyzer(game_id),
                    machine=DeathStateMachine(build_rules(self.cfg)),
                    shared=self.cfg["sharing"] == configmod.SHARING_SHARED,
                    analysis_fps=float(det["analysis_fps"]), save_pre_frame=bool(cap["save_pre_frame"]),
                    pre_seconds=float(cap["pre_seconds"]), save_post_frame=bool(cap["save_post_frame"]),
                    post_seconds=float(cap["post_seconds"]), retry_seconds=float(self.cfg["storage_retry_seconds"]),
                    queue_limit=int(self.cfg["storage_queue_limit"]), on_record=on_record,
                )
                self.stop_flag.clear()
                self.running = True
                self.status_line = f"Watching {self.source.description} for {games.get(game_id).name} deaths"
                self._event("info", self.status_line)
                threading.Thread(target=self._run, daemon=True).start()
                return {"ok": True}
        return self._safe(go)

    def _run(self) -> None:
        try:
            self.pipeline.run(self.source, should_stop=self.stop_flag.is_set)
        except SourceError as e:
            self._event("warn", str(e))
        except Exception as e:  # noqa: BLE001
            traceback.print_exc()
            self._event("warn", f"Detection stopped: {e}")
        finally:
            self.running = False
            self.status_line = "Stopped"
            if self.preview_server.viewers:
                self._start_preview_capture()   # keep the preview going after detection stops

    def stop(self) -> dict:
        self.stop_flag.set()
        if isinstance(self.source, ThreadedSource):
            self.source.close()                 # ends the frame stream even if no frames arrive
        self._event("info", "Stopping.")
        return {"ok": True}

    def preview(self) -> dict:
        def go():
            frame = self._current_frame()
            return {"ok": True, "image": _b64_jpeg(frame), "width": frame.shape[1], "height": frame.shape[0],
                    "blank": looks_blank(frame)}
        return self._safe(go)

    # ------------------------------------------------------------------ syncing and counting

    def _sync_loop(self) -> None:
        while True:
            self.sync_wake.wait(30)
            self.sync_wake.clear()
            if self.cfg["sharing"] != configmod.SHARING_SHARED:
                continue
            try:
                self.sync_now()
            except Exception as e:  # noqa: BLE001
                self._event("warn", f"Sync failed, will retry: {e}")

    def _syncer(self) -> Syncer | None:
        conn = self._fs()
        if conn is None:
            return None
        fs, uid = conn
        counter = None
        if self.cfg.get("auto_count", True):
            counter = AutoCounter.load(fs, uid, self.cfg.get("me_uid") or uid, self.cfg.get("gamertags", {}))
            if counter is None:
                self._event("warn", "Automatic counting needs an admin account; deaths upload as pending.")
            else:
                self.players_cache = sorted(counter.players, key=lambda p: p.handle.lower())
        return Syncer(self._store(), fs, uid, counter)

    def sync_now(self) -> dict:
        def go():
            syncer = self._syncer()
            if syncer is None:
                return {"ok": False, "error": "Sign in first."}
            push = syncer.push_pending()
            pull = syncer.pull_statuses()
            for m in push.messages:
                self._event("count", m)
            if push.uploaded or push.counted or pull.status_changes:
                self._event("sync", f"Uploaded {push.uploaded}, counted {push.counted}, "
                                    f"{pull.status_changes} decision(s) from the app")
            if push.failed:
                self._event("warn", f"{push.failed} waiting to upload: {push.last_error}")
            self.last_sync = datetime.now().strftime("%H:%M:%S")
            return {"ok": True, "uploaded": push.uploaded, "counted": push.counted, "failed": push.failed}
        return self._safe(go)

    def share(self, event_ids: list[str] | None = None) -> dict:
        store = self._store()
        wanted = set(event_ids or [])
        n = 0
        for r in store.records():
            if r.sync_state == SYNC_PRIVATE and (not wanted or r.event_id in wanted):
                r.sync_state = SYNC_PENDING
                store.update(r)
                n += 1
        if not self._session():
            return {"ok": True, "queued": n, "note": "Sign in to upload them."}
        res = self.sync_now()
        return {**res, "queued": n}

    # ------------------------------------------------------------------ evidence

    def _local_thumb(self, store: EvidenceStore, r) -> str:
        if r.event_id in self.thumb_cache:
            return self.thumb_cache[r.event_id]
        thumb = ""
        try:
            img = imaging.decode(store.resolve(r.current_path or r.local_relative_path).read_bytes())
            thumb = _b64_jpeg(img, 360, 70)
        except (OSError, ValueError):
            pass
        self.thumb_cache[r.event_id] = thumb
        return thumb

    def evidence(self, include_remote: bool = True) -> dict:
        def go():
            store = self._store()
            local = {r.event_id: r for r in store.records()} if store.root.exists() else {}
            items: dict[str, dict] = {}
            for r in local.values():
                items[r.event_id] = {
                    "id": r.event_id, "game": r.game_id, "gameName": games.get(r.game_id).name,
                    "time": r.detected_at_ms, "type": r.event_type, "player": r.player_label,
                    "confidence": r.confidence, "status": r.verification_status, "sync": r.sync_state,
                    "local": True, "imageOnPc": store.image_exists(r), "submitter": "this PC",
                    "subjectUid": r.review.get("subject_uid", ""), "sector": r.review.get("sector", ""),
                    "note": r.review.get("count_message", "") or r.sync_error,
                    "thumb": self._local_thumb(store, r),
                }
            remote_error = ""
            conn = self._fs() if include_remote else None
            if conn:
                fs, _ = conn
                try:
                    for doc_id, d in fs.run_query("deathEvidence", "detectedAt", True, 80):
                        it = items.get(doc_id, {})
                        it.update({
                            "id": doc_id, "game": d.get("gameId", ""), "gameName": moderation.GAME_SECTORS.get(d.get("gameId", ""), d.get("gameId", "")),
                            "time": d.get("detectedAt", 0), "type": d.get("eventType", "unknown"),
                            "player": d.get("playerLabel", ""), "confidence": d.get("confidence", 0.0),
                            "status": d.get("status", "pending"), "sync": it.get("sync", SYNC_SYNCED),
                            "local": it.get("local", False), "imageOnPc": it.get("imageOnPc", False),
                            "submitter": d.get("submitterName", ""), "subjectUid": d.get("subjectUid", ""),
                            "sector": d.get("countedSector", ""), "counted": d.get("counted", False),
                            "reason": d.get("rejectionReason", ""), "reviewer": d.get("reviewerName", ""),
                            "note": it.get("note", ""),
                        })
                        if not it.get("thumb") and isinstance(d.get("thumb"), (bytes, bytearray)):
                            it["thumb"] = _b64_bytes(d["thumb"])
                        items[doc_id] = it
                except (FirebaseError, NetworkError) as e:
                    remote_error = str(e)
            out = sorted(items.values(), key=lambda x: x.get("time", 0), reverse=True)
            names = {p.uid: p.handle for p in self.players_cache}
            for it in out:
                it["subjectName"] = names.get(it.get("subjectUid", ""), "")
            return {"ok": True, "items": out, "remoteError": remote_error}
        return self._safe(go)

    def evidence_image(self, event_id: str) -> dict:
        def go():
            store = self._store()
            try:
                r = store.load(event_id)
                img = imaging.decode(store.resolve(r.current_path or r.local_relative_path).read_bytes())
                return {"ok": True, "image": _b64_jpeg(img, 1600, 85)}
            except (OSError, ValueError):
                pass
            conn = self._fs()
            if conn:
                blob = conn[0].get(f"deathEvidenceBlobs/{event_id}")
                if blob and isinstance(blob.get("data"), (bytes, bytearray)):
                    return {"ok": True, "image": _b64_bytes(blob["data"])}
            return {"ok": False, "error": "The image file is missing (the record itself is intact)."}
        return self._safe(go)

    def moderate(self, event_id: str, action: str, params: dict | None = None) -> dict:
        def go():
            params_ = params or {}
            conn = self._fs()
            if conn is None:
                return {"ok": False, "error": "Sign in first."}
            fs, uid = conn
            players = self._players(refresh=True)
            me = next((p for p in players if p.uid == uid), None)
            if not me or not me.admin:
                return {"ok": False, "error": "Only admins can verify or reject deaths."}
            if action == "verify":
                subject = params_.get("subjectUid") or ""
                if not subject:
                    return {"ok": False, "error": "Choose whose death it is."}
                game = params_.get("game") or self.cfg["game"]
                sectors = [s for s in (fs.get("gameState/global") or {}).get("games", []) if isinstance(s, str)]
                act = moderation.Verify(params_.get("sector") or moderation.default_sector(game, sectors), subject)
            elif action == "reject":
                act = moderation.Reject(params_.get("reason") or "Marked not verified")
            elif action == "remove":
                act = moderation.Remove(params_.get("reason") or "Removed")
            elif action == "restore":
                act = moderation.Restore()
            else:
                return {"ok": False, "error": f"Unknown action {action}"}
            out = moderation.Moderator(fs, uid, me.handle).apply(event_id, act)
            if out.kind != "applied":
                return {"ok": out.problem in ("ALREADY_VERIFIED", "ALREADY_REJECTED"),
                        "error": moderation.PROBLEM_TEXT.get(out.problem, out.problem)}
            who = next((p.handle for p in players if p.uid == out.subject_uid), "")
            text = {
                "verified": f"Counted for {who} in {out.sector}. Coins unchanged.",
                "rejected": "Marked not verified." + (f" Taken off {who}'s count. Coins unchanged." if out.deaths_delta < 0 else ""),
                "removed": "Removed." + (f" Taken off {who}'s count. Coins unchanged." if out.deaths_delta < 0 else ""),
                "restored": f"Restored and counted for {who} in {out.sector}." if out.deaths_delta > 0 else "Restored to pending.",
            }.get(out.action, "Done.")
            self._event("count", text)
            Syncer(self._store(), fs, uid).pull_statuses()   # mirror it into the PC's record
            self._players(refresh=True)
            return {"ok": True, "message": text}
        return self._safe(go)

    def open_folder(self) -> dict:
        root = self._store().root
        if root.exists():
            os.startfile(root)  # type: ignore[attr-defined]
            return {"ok": True}
        return {"ok": False, "error": f"{root} isn't available."}

    def open_file(self, event_id: str) -> dict:
        store = self._store()
        try:
            r = store.load(event_id)
            os.startfile(store.resolve(r.current_path or r.local_relative_path))  # type: ignore[attr-defined]
            return {"ok": True}
        except (OSError, ValueError) as e:
            return {"ok": False, "error": str(e)}

    # ------------------------------------------------------------------ players

    def players(self) -> dict:
        def go():
            ps = self._players(refresh=True)
            if not ps:
                return {"ok": False, "error": "Sign in to see the Dead-Fest players."}
            tags = self.cfg.get("gamertags", {})
            s = self._session()
            me = self.cfg.get("me_uid") or (s.uid if s else "")
            return {"ok": True, "me": me,
                    "players": [{**p.to_json(), "gamertag": tags.get(p.uid, ""), "isMe": p.uid == me} for p in ps]}
        return self._safe(go)

    # ------------------------------------------------------------------ HUD setup

    def hud_capture(self) -> dict:
        def go():
            frame = self._current_frame()
            self.hud_frame = frame
            return {"ok": True, "image": _b64_jpeg(frame, 1280, 85), "width": frame.shape[1], "height": frame.shape[0],
                    "hud": self.cfg["hud"].get(self.cfg["game"]) or {}}
        return self._safe(go)

    def _text_box(self, frame: np.ndarray, box) -> tuple | None:
        """The exact box of the text inside a drawn box (OCR), in fractions of the frame."""
        try:
            reader = ocr.create(self.cfg["detection"].get("ocr", "windows"))
        except Exception:  # noqa: BLE001
            return None
        h, w = frame.shape[:2]
        img = crop(frame, tuple(box))
        if img.size == 0:
            return None
        lines = reader.read_lines(cv2.resize(img, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC))
        if not lines:
            return None
        _, (x, y, bw, bh) = lines[0]
        return box[0] + x / 3 / w, box[1] + y / 3 / h, bw / 3 / w, bh / 3 / h

    def hud_save(self, hud: dict) -> dict:
        """
        Saves the HUD for the current game from the captured frame: your bar, the teammates area,
        and one example teammate (name + bar) that teaches where a bar sits relative to a name.
        """
        def go():
            frame = self.hud_frame
            if frame is None:
                return {"ok": False, "error": "Capture a frame first (everyone at full health)."}
            out: dict = {"hold_seconds": float(hud.get("hold_seconds", 3.0))}
            notes = []
            if hud.get("self_bar"):
                ref = reference_colour(crop(frame, tuple(hud["self_bar"])))
                out["self_bar"] = {"box": list(hud["self_bar"]), "ref": list(ref) if ref else None}
                if not ref:
                    notes.append("couldn't find a clear colour in your bar")
            area, name, bar = hud.get("team_area"), hud.get("example_name"), hud.get("example_bar")
            if area or name or bar:
                if not (area and name and bar):
                    return {"ok": False, "error": "For teammates, draw the teammates area, one example name and that player's health bar."}
                anchor = hud.get("anchor", "left")
                name_box = self._text_box(frame, name) or tuple(name)
                ref = reference_colour(crop(frame, tuple(bar)))
                out["team"] = {"area": list(area), "bar_offset": list(calibrate_team(tuple(area), name_box, tuple(bar), anchor)),
                               "ref": list(ref) if ref else None, "anchor": anchor, "example_name": list(name),
                               "example_bar": list(bar)}
                if not ref:
                    notes.append("couldn't find a clear colour in the example bar")
            if "self_bar" not in out and "team" not in out:
                self.cfg["hud"].pop(self.cfg["game"], None)
            else:
                self.cfg["hud"][self.cfg["game"]] = out
            self._save_cfg()
            note = (" Note: " + "; ".join(notes) + ". Capture again with everyone at full health.") if notes else ""
            return {"ok": True, "message": "HUD saved for " + games.get(self.cfg["game"]).name + "." + note,
                    "restartNeeded": self.running}
        return self._safe(go)

    def hud_test(self) -> dict:
        """What HUD tracking sees right now: every teammate found by name, their bar and its fill."""
        def go():
            cfg = HudConfig.from_json(self.cfg["hud"].get(self.cfg["game"]))
            if not cfg:
                return {"ok": False, "error": "No HUD saved for this game yet."}
            frame = self._current_frame()
            try:
                reader = ocr.create(self.cfg["detection"].get("ocr", "windows"))
            except Exception:  # noqa: BLE001
                reader = ocr.NullOcr()
            if self.cfg.get("sharing") == configmod.SHARING_SHARED or self._session():
                self._players()
            tracker = HudTracker(cfg, reader, self._hud_identity, self._known_names)
            tracker.update(0.0, frame)
            readings = []
            for r in tracker.readings():
                if r["slot"] == "Teammate":
                    key = next((k for k, st in tracker.states.items() if st.label == r["name"]), "")
                    matched = any(p.uid == key[5:] for p in self.players_cache) if key.startswith("team:") else False
                    readings.append({**r, "matched": matched})
                else:
                    readings.append(r)
            return {"ok": True, "readings": readings, "holdSeconds": cfg.hold_seconds,
                    "image": _b64_jpeg(frame, 1280, 85), "knownPlayers": len(self.players_cache)}
        return self._safe(go)


class Bridge:
    """What the window may call: only these methods, never the app's internal state."""

    def __init__(self, api: Api):
        self._api = api

    def state(self): return self._api.state()
    def sign_in(self, email, password): return self._api.sign_in(email, password)
    def sign_out(self): return self._api.sign_out()
    def save_settings(self, s): return self._api.save_settings(s)
    def start(self): return self._api.start()
    def stop(self): return self._api.stop()
    def preview(self): return self._api.preview()
    def sync_now(self): return self._api.sync_now()
    def share(self, ids=None): return self._api.share(ids)
    def evidence(self): return self._api.evidence()
    def evidence_image(self, event_id): return self._api.evidence_image(event_id)
    def moderate(self, event_id, action, params=None): return self._api.moderate(event_id, action, params)
    def open_folder(self): return self._api.open_folder()
    def open_file(self, event_id): return self._api.open_file(event_id)
    def players(self): return self._api.players()
    def hud_capture(self): return self._api.hud_capture()
    def hud_save(self, hud): return self._api.hud_save(hud)
    def hud_test(self): return self._api.hud_test()


def main() -> int:
    import webview

    api = Api()
    page = (UI_DIR / "index.html").as_uri()
    start_tab = os.environ.get("DEADFEST_UI_TAB", "")
    webview.create_window("Dead-Fest Detector", page + (f"#{start_tab}" if start_tab else ""), js_api=Bridge(api),
                          width=1280, height=860, min_size=(980, 680), background_color="#0B0B0E")
    webview.start(debug=bool(os.environ.get("DEADFEST_UI_DEBUG")))
    api.stop_flag.set()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
