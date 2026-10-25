"""
Command line for the Dead-Fest Xbox Death Detector.

    python -m deadfest_detector app           open the desktop app (also: double-click run-detector.bat)
    python -m deadfest_detector doctor        check storage, OCR, capture source and login
    python -m deadfest_detector devices       list capture devices
    python -m deadfest_detector login         sign in with your Dead-Fest account
    python -m deadfest_detector config ...    change settings (game, source, sharing...)
    python -m deadfest_detector run           watch the feed and record deaths
    python -m deadfest_detector sync          upload waiting evidence, pull admin decisions
    python -m deadfest_detector list          show the evidence on this PC
    python -m deadfest_detector preview       save and open one frame as the detector sees it
    python -m deadfest_detector gallery       write and open the local gallery page
    python -m deadfest_detector calibrate ... save a death-screen template from a screenshot
"""

from __future__ import annotations

import argparse
import getpass
import json
import logging
import os
import signal
import sys
import threading
import time
from pathlib import Path

from . import __version__, config as configmod, games, gallery, ocr
from .detect import Analyzer, load_templates
from .firebase_rest import AuthClient, FirebaseError, FirestoreClient, NetworkError, Session
from .pipeline import Pipeline
from .sources import SourceError, first_frame, from_config, looks_blank, probe_devices
from .state import DeathStateMachine, Rules
from .storage import SYNC_PENDING, EvidenceStore, StorageUnavailable
from .sync import Syncer

log = logging.getLogger("deadfest.detector")
REPO_GOOGLE_SERVICES = Path(__file__).resolve().parents[2] / "app" / "google-services.json"


# ---------------------------------------------------------------------- helpers

def session_path() -> Path:
    return configmod.app_dir() / "session.json"


def templates_dir(game_id: str) -> Path:
    return configmod.app_dir() / "templates" / game_id


def load_session(cfg: dict) -> Session | None:
    p = session_path()
    if not p.exists():
        return None
    data = json.loads(p.read_text(encoding="utf-8"))
    auth = AuthClient(cfg["firebase"]["api_key"])
    return Session(auth, data["uid"], data["refresh_token"], data.get("email", ""), save=save_session)


def save_session(data: dict) -> None:
    p = session_path()
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_suffix(".tmp")
    tmp.write_text(json.dumps(data), encoding="utf-8")
    os.replace(tmp, p)


def make_syncer(cfg: dict, store: EvidenceStore) -> Syncer | None:
    session = load_session(cfg)
    if session is None:
        return None
    fs = FirestoreClient(cfg["firebase"]["project_id"], session.token)
    return Syncer(store, fs, session.uid)


def build_analyzer(cfg: dict, game_id: str) -> Analyzer:
    det = cfg["detection"]
    profile = games.compile_profile(games.get(game_id), cfg.get("game_overrides", {}).get(game_id))
    try:
        reader = ocr.create(det.get("ocr", "windows"), int(det.get("ocr_width", 1280)))
    except Exception as e:  # noqa: BLE001 - any OCR start-up failure falls back to templates only
        print(f"! Text recognition unavailable ({e}). Using death-screen templates only.")
        reader = ocr.NullOcr()
    templates = load_templates(templates_dir(game_id))
    if not templates and reader.name == "none":
        print("! No OCR and no templates for this game: nothing can be detected. Run 'calibrate' first.")
    player = cfg.get("player", {})
    return Analyzer(
        profile, reader, templates,
        template_threshold=float(det.get("template_threshold", 0.8)),
        color_boost=float(det.get("color_boost", 0.05)),
        self_names=[player.get("gamertag", "")] + list(player.get("aliases", [])),
        teammates=list(player.get("teammates", [])),
    )


def build_rules(cfg: dict) -> Rules:
    d = cfg["detection"]
    return Rules(
        threshold=float(d["threshold"]),
        confirm_frames=int(d["confirm_frames"]),
        confirm_seconds=float(d["confirm_seconds"]),
        max_misses=int(d["max_misses"]),
        respawn_clear_seconds=float(d["respawn_clear_seconds"]),
        cooldown_seconds=float(d["cooldown_seconds"]),
    )


# ---------------------------------------------------------------------- commands

def cmd_doctor(cfg: dict, args) -> int:
    ok = True
    print(f"Dead-Fest Xbox Death Detector {__version__}")
    print(f"Config: {configmod.default_config_path()}")
    store = EvidenceStore(cfg["evidence_root"])
    try:
        store.ensure_layout()
        print(f"[ok]   Evidence folder {store.root} is writable")
    except StorageUnavailable as e:
        ok = False
        print(f"[FAIL] {e}")
    game = cfg["game"]
    print(f"[info] Game: {games.get(game).name} ({game}); sharing: {cfg['sharing']}")
    try:
        reader = ocr.create(cfg["detection"].get("ocr", "windows"))
        print(f"[ok]   Text recognition: {reader.name}")
    except Exception as e:  # noqa: BLE001
        print(f"[warn] Text recognition unavailable: {e}")
    n_templates = len(load_templates(templates_dir(game)))
    print(f"[{'ok' if n_templates else 'info'}]   {n_templates} death-screen template(s) for this game")
    try:
        src = from_config(cfg["source"])
        frame = first_frame(src)
        src.close()
        print(f"[ok]   Video source '{cfg['source']['type']}' gives {frame.shape[1]}x{frame.shape[0]} frames")
        if looks_blank(frame):
            print("[warn] The picture is almost completely black. If the game isn't on a black screen, "
                  "use 'preview' to see what is being captured.")
    except (SourceError, OSError) as e:
        ok = False
        print(f"[FAIL] Video source: {e}")
    if session_path().exists():
        print("[ok]   Signed in")
    else:
        print("[info] Not signed in (needed only for sharing): run 'login'")
    pending = sum(1 for r in store.records() if r.sync_state == SYNC_PENDING) if store.root.exists() else 0
    print(f"[info] {pending} record(s) waiting to upload")
    return 0 if ok else 1


def cmd_preview(cfg: dict, args) -> int:
    """Saves and opens one frame exactly as the detector sees it."""
    import cv2

    try:
        src = from_config(cfg["source"])
        frame = first_frame(src, timeout=20)
        src.close()
    except (SourceError, OSError) as e:
        print(f"! {e}")
        return 1
    out = configmod.app_dir() / "preview.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    ok, buf = cv2.imencode(".png", frame)
    out.write_bytes(buf.tobytes())
    print(f"Saved what the detector sees ({frame.shape[1]}x{frame.shape[0]}) to {out}")
    if looks_blank(frame):
        print("It is almost completely black. If your game isn't on a black screen right now, the stream "
              "isn't reaching the detector: make sure Remote Play is showing the game in the Xbox app.")
    if not args.no_open:
        os.startfile(out)  # type: ignore[attr-defined]  (Windows)
    return 0


def cmd_devices(cfg: dict, args) -> int:
    found = probe_devices()
    if not found:
        print("No video devices answered. Plug in the capture card, close OBS / vendor apps, and try again.")
        return 1
    for idx, w, h in found:
        print(f"  #{idx}: {w}x{h}")
    print("Pick one with: config --source capture_card --device <number>")
    return 0


def cmd_login(cfg: dict, args) -> int:
    gs = Path(args.google_services) if args.google_services else REPO_GOOGLE_SERVICES
    if not cfg["firebase"]["api_key"] or args.google_services:
        if not gs.exists():
            print(f"Can't find google-services.json at {gs}. Pass --google-services <path>.")
            return 1
        configmod.import_google_services(cfg, gs)
    email = args.email or input("Dead-Fest email: ").strip()
    password = getpass.getpass("Password (not stored): ")
    try:
        data = AuthClient(cfg["firebase"]["api_key"]).sign_in(email, password)
    except (FirebaseError, NetworkError) as e:
        print(f"Sign-in failed: {e}")
        return 1
    save_session({"uid": data["uid"], "email": data["email"], "refresh_token": data["refresh_token"]})
    answer = input("Share captured death screenshots with your Dead-Fest group for admin review? [y/N] ").strip().lower()
    cfg["sharing"] = configmod.SHARING_SHARED if answer in ("y", "yes") else configmod.SHARING_PRIVATE
    configmod.save(cfg)
    print(f"Signed in as {data['email']}. Sharing is {cfg['sharing']}.")
    if cfg["sharing"] == configmod.SHARING_SHARED:
        store = EvidenceStore(cfg["evidence_root"])
        private = [r for r in store.records() if r.sync_state == "private"] if store.root.exists() else []
        if private and input(f"Also share the {len(private)} death(s) already saved on this PC? [y/N] ").strip().lower() in ("y", "yes"):
            args.event_ids = []
            return cmd_share(cfg, args)
    return 0


def cmd_logout(cfg: dict, args) -> int:
    session_path().unlink(missing_ok=True)
    print("Signed out. Evidence already on this PC is untouched.")
    return 0


def cmd_config(cfg: dict, args) -> int:
    changed = False
    if args.game:
        games.get(args.game)
        cfg["game"] = args.game
        changed = True
    if args.sharing:
        if args.sharing == configmod.SHARING_SHARED and not session_path().exists():
            print("Sign in first ('login') so shared evidence can be uploaded.")
            return 1
        cfg["sharing"] = args.sharing
        changed = True
    if args.source:
        cfg["source"]["type"] = args.source
        changed = True
    if args.device is not None:
        cfg["source"]["device_index"] = args.device
        changed = True
    if args.video:
        cfg["source"]["path"] = args.video
        changed = True
    if args.window_title:
        cfg["source"]["window_title"] = args.window_title
        changed = True
    if args.evidence_root:
        cfg["evidence_root"] = args.evidence_root
        changed = True
    if args.gamertag is not None:
        cfg["player"]["gamertag"] = args.gamertag
        changed = True
    if changed:
        path = configmod.save(cfg)
        print(f"Saved {path}")
    print(json.dumps({k: cfg[k] for k in ("evidence_root", "game", "sharing", "source", "player")}, indent=2))
    return 0


def cmd_run(cfg: dict, args) -> int:
    game_id = args.game or cfg["game"]
    games.get(game_id)
    if args.video:
        cfg["source"].update({"type": "video", "path": args.video, "realtime": not args.fast})
    store = EvidenceStore(cfg["evidence_root"])
    try:
        store.ensure_layout()
    except StorageUnavailable as e:
        print(f"! {e}\n  Detection will run and hold deaths in memory until the folder is back.")
    shared = cfg["sharing"] == configmod.SHARING_SHARED
    syncer = make_syncer(cfg, store) if shared and not args.no_sync else None
    if shared and syncer is None and not args.no_sync:
        print("! Sharing is on but you are not signed in: evidence will wait locally. Run 'login'.")

    try:
        source = from_config(cfg["source"])
    except SourceError as e:
        print(f"! {e}")
        return 1

    def on_record(r):
        where = "will upload" if r.sync_state == SYNC_PENDING else "kept private"
        label = f" ({r.player_label})" if r.player_label else ""
        print(f"DEATH  {r.detected_at_local}  {r.event_type}{label}  {r.confidence:.0%}  -> {r.local_relative_path}  [{where}]")

    det = cfg["detection"]
    cap = cfg["capture"]
    pipeline = Pipeline(
        game_id=game_id,
        store=store,
        analyzer=build_analyzer(cfg, game_id),
        machine=DeathStateMachine(build_rules(cfg)),
        shared=shared,
        analysis_fps=float(det["analysis_fps"]),
        save_pre_frame=bool(cap["save_pre_frame"]),
        pre_seconds=float(cap["pre_seconds"]),
        save_post_frame=bool(cap["save_post_frame"]),
        post_seconds=float(cap["post_seconds"]),
        retry_seconds=float(cfg["storage_retry_seconds"]),
        queue_limit=int(cfg["storage_queue_limit"]),
        on_record=on_record,
    )

    stop = threading.Event()
    signal.signal(signal.SIGINT, lambda *_: stop.set())

    def sync_loop():
        while not stop.wait(60):
            try:
                rep = syncer.push_pending()
                syncer.pull_statuses()
                if rep.uploaded:
                    print(f"SYNC   uploaded {rep.uploaded} record(s)")
                if rep.failed:
                    print(f"SYNC   {rep.failed} waiting (will retry): {rep.last_error}")
            except Exception as e:  # noqa: BLE001 - the sync thread must never take detection down
                print(f"SYNC   error, will retry: {e}")

    if syncer:
        threading.Thread(target=sync_loop, daemon=True).start()

    started = time.monotonic()
    print(f"Watching {source.description} for {games.get(game_id).name} deaths. Ctrl+C to stop.")
    stats = pipeline.run(
        source,
        should_stop=lambda: stop.is_set() or (args.max_seconds is not None and time.monotonic() - started > args.max_seconds),
    )
    stop.set()
    if pipeline.held:
        print(f"! {pipeline.held} death(s) could not be saved: {stats.storage_error}")
    if syncer:
        rep = syncer.push_pending()
        print(f"Final sync: {rep.uploaded} uploaded, {rep.failed} still waiting.")
    print(f"Done. {stats.frames_analyzed} frames analysed, {stats.deaths} death(s) recorded.")
    return 0 if not pipeline.held else 2


def cmd_sync(cfg: dict, args) -> int:
    store = EvidenceStore(cfg["evidence_root"])
    syncer = make_syncer(cfg, store)
    if syncer is None:
        print("Not signed in. Run 'login' first.")
        return 1
    push = syncer.push_pending()
    pull = syncer.pull_statuses()
    print(f"Uploaded {push.uploaded}, already shared {push.already_there}, waiting {push.failed}, "
          f"private {push.skipped_private}; {pull.status_changes} admin decision(s) applied locally.")
    if push.last_error:
        print(f"Last error: {push.last_error}")
    return 0 if push.failed == 0 else 2


def cmd_share(cfg: dict, args) -> int:
    """Queues evidence that was saved while sharing was off, so the next sync uploads it."""
    from .storage import SYNC_PRIVATE

    store = EvidenceStore(cfg["evidence_root"])
    wanted = set(args.event_ids or [])
    queued = 0
    for r in store.records():
        if r.sync_state != SYNC_PRIVATE or (wanted and r.event_id not in wanted):
            continue
        r.sync_state = SYNC_PENDING
        store.update(r)
        queued += 1
    print(f"{queued} private record(s) queued for upload." if queued else "No private evidence to queue.")
    if make_syncer(cfg, store) is None:
        print("Sign in ('login') and they upload on the next sync.")
        return 0
    return cmd_sync(cfg, args)


def cmd_list(cfg: dict, args) -> int:
    store = EvidenceStore(cfg["evidence_root"])
    rows = [r for r in store.records() if not args.game or r.game_id == args.game]
    if not rows:
        print("No evidence yet.")
        return 0
    for r in rows:
        missing = "" if store.image_exists(r) else "  [IMAGE MISSING]"
        print(f"{r.detected_at_local:24} {games.get(r.game_id).name:24} {r.event_type:9} {r.player_label:16} "
              f"{r.confidence:4.0%} {r.verification_status:9} {r.sync_state:8} {r.event_id}{missing}")
    return 0


def cmd_gallery(cfg: dict, args) -> int:
    store = EvidenceStore(cfg["evidence_root"])
    try:
        store.ensure_layout()
    except StorageUnavailable as e:
        print(f"! {e}")
        return 1
    page = gallery.write(store)
    print(f"Gallery written to {page}")
    if not args.no_open:
        os.startfile(page)  # type: ignore[attr-defined]  (Windows)
    return 0


def cmd_calibrate(cfg: dict, args) -> int:
    import cv2
    import numpy as np

    game_id = args.game or cfg["game"]
    games.get(game_id)
    img = cv2.imdecode(np.fromfile(args.image, dtype=np.uint8), cv2.IMREAD_GRAYSCALE)
    if img is None:
        print(f"Can't read {args.image}")
        return 1
    x, y, w, h = (int(v) for v in args.box.split(","))
    if w < 16 or h < 8 or x < 0 or y < 0 or x + w > img.shape[1] or y + h > img.shape[0]:
        print(f"The box must fit inside the {img.shape[1]}x{img.shape[0]} image and be at least 16x8.")
        return 1
    folder = templates_dir(game_id)
    folder.mkdir(parents=True, exist_ok=True)
    n = len(list(folder.glob(f"{args.type}_*.png"))) + 1
    out = folder / f"{args.type}_{n}.png"
    ok, buf = cv2.imencode(".png", img[y:y + h, x:x + w])
    out.write_bytes(buf.tobytes())
    out.with_suffix(".json").write_text(json.dumps({"ref_width": img.shape[1], "box": [x, y, w, h]}), encoding="utf-8")
    print(f"Saved template {out}")
    return 0


# ---------------------------------------------------------------------- entry

def cmd_app(cfg: dict, args) -> int:
    from .desktop import main as desktop_main

    return desktop_main()


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(level=logging.WARNING, format="%(levelname)s %(message)s")
    p = argparse.ArgumentParser(prog="deadfest_detector", description="Dead-Fest Xbox Death Detector")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("app", help="open the desktop app")
    sub.add_parser("doctor")
    sub.add_parser("devices")
    lg = sub.add_parser("login")
    lg.add_argument("--email")
    lg.add_argument("--google-services")
    sub.add_parser("logout")
    c = sub.add_parser("config")
    c.add_argument("--game", choices=games.ids())
    c.add_argument("--sharing", choices=[configmod.SHARING_PRIVATE, configmod.SHARING_SHARED])
    c.add_argument("--source", choices=["xbox_app", "capture_card", "screen", "window", "video", "images"])
    c.add_argument("--device", type=int)
    c.add_argument("--video")
    c.add_argument("--window-title")
    c.add_argument("--evidence-root")
    c.add_argument("--gamertag")
    r = sub.add_parser("run")
    r.add_argument("--game", choices=games.ids())
    r.add_argument("--video", help="analyse a recorded file instead of the live source")
    r.add_argument("--fast", action="store_true", help="with --video: don't wait for real time")
    r.add_argument("--max-seconds", type=float)
    r.add_argument("--no-sync", action="store_true")
    sub.add_parser("sync")
    sh = sub.add_parser("share", help="upload evidence saved while sharing was off")
    sh.add_argument("event_ids", nargs="*", help="only these (default: all private evidence)")
    ls = sub.add_parser("list")
    ls.add_argument("--game", choices=games.ids())
    pv = sub.add_parser("preview", help="save and open one frame as the detector sees it")
    pv.add_argument("--no-open", action="store_true")
    gl = sub.add_parser("gallery")
    gl.add_argument("--no-open", action="store_true")
    cb = sub.add_parser("calibrate")
    cb.add_argument("--image", required=True, help="a screenshot of the death screen")
    cb.add_argument("--box", required=True, help="x,y,w,h of the part that identifies it (e.g. the 'YOU DIED' text)")
    cb.add_argument("--type", default="self", choices=list(games.EVENT_TYPES))
    cb.add_argument("--game", choices=games.ids())
    args = p.parse_args(argv)

    cfg = configmod.load()
    handler = {
        "app": cmd_app, "doctor": cmd_doctor, "devices": cmd_devices, "login": cmd_login, "logout": cmd_logout,
        "config": cmd_config, "run": cmd_run, "sync": cmd_sync, "share": cmd_share, "list": cmd_list,
        "gallery": cmd_gallery, "calibrate": cmd_calibrate, "preview": cmd_preview,
    }[args.cmd]
    try:
        return handler(cfg, args)
    except (ValueError, KeyError) as e:
        print(f"! {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
