# Dead-Fest Xbox Death Detector

A small Windows program that watches your Xbox gameplay video, spots death screens, saves a
screenshot of each death to `D:\XboxDeathDetector`, and (if you choose) shares it with your
Dead-Fest group so admins can verify it in the app.

Supported games: Dying Light, Dying Light 2, Dying Light: The Beast, World War Z, Dead Island,
Dead Island Riptide, Dead Island 2, State of Decay 2.

## What it can and can't do

- It reads **only the picture you see**: from an HDMI capture card, or from the Xbox app's Remote
  Play window on your PC. It never touches the console, its memory, its security or its network
  traffic.
- It finds deaths from what the game shows: the death screen's wording (read with Windows' own
  OCR) and, most reliably, a **template** you capture once from a real death screen.
- It **can't see every death**. If a game shows nothing when a teammate dies, that death can't be
  detected from the video, and the detector does not guess. Detection quality depends on the game,
  the resolution and what's on screen.
- A detected death is **evidence, not a verdict**. It arrives in the app as *pending*; an admin
  verifies or rejects it. Only a verified death changes anybody's death count, and moderation
  **never adds or deducts coins**.

## Setup

1. Install **Python 3.11 or newer** from python.org (tick "Add python.exe to PATH").
2. Double-click `run-detector.bat` once. The first run creates a private environment in
   `detector\.venv` and installs the packages (OpenCV, NumPy, mss and the Windows OCR bindings).
   Then close it with Ctrl+C.
3. Open a terminal in this folder and check everything:

   ```
   run-detector.bat doctor
   ```

   `doctor` checks that `D:\XboxDeathDetector` can be written, that OCR works, that the video
   source gives frames, and whether you're signed in.

### Choose the game

```
run-detector.bat config --game dying_light_2
```

Ids: `dying_light`, `dying_light_2`, `dying_light_the_beast`, `world_war_z`, `dead_island`,
`dead_island_riptide`, `dead_island_2`, `state_of_decay_2`.

### Connect the video

**Capture card (recommended).** Plug the Xbox's HDMI into the card and the card into the PC. Close
OBS or the card vendor's app (only one program can use the card at a time), then:

```
run-detector.bat devices
run-detector.bat config --source capture_card --device 0
```

If the picture is black, turn off **Allow HDCP** in the Xbox's *Settings > General > TV & display
options > Video modes*. Games don't need HDCP; streaming apps do.

**Xbox app Remote Play (no extra hardware).** Start Remote Play in the Xbox app on the PC, then:

```
run-detector.bat config --source window --window-title "Xbox"
```

The window must stay visible (not minimised). The Xbox app has no official way to hand its video to
other programs, so this captures the window's pixels. Run a short session and check the saved
screenshots: if they come out black, use a capture card instead.

**Recorded footage (for testing).** `run-detector.bat run --video D:\clips\session.mp4 --fast`

### Teach it a death screen (strongly recommended)

Take one screenshot of a real death screen for your game (from a saved evidence image, OBS, or
Win+Alt+PrtScn on the Remote Play window). Note the pixel box around the part that never changes,
usually the "YOU DIED" text, then:

```
run-detector.bat calibrate --image D:\shots\dl2-death.png --box 700,420,520,110 --type self
```

Templates are matched together with OCR, and a match on both raises the confidence. Add a template
per death screen variant if a game has several.

### Your name and squad (optional)

```
run-detector.bat config --gamertag "DeadShot99"
```

A "DeadShot99 has died" banner then counts as **your** death. In
`%LOCALAPPDATA%\DeadFestDetector\config.json` you can also list `player.aliases` (for example
your State of Decay 2 survivors) and `player.teammates` (named deaths of anyone else are then
classed as "other player").

## Sharing with your group

Evidence stays **private** on this PC until you opt in.

```
run-detector.bat login
```

Sign in with your Dead-Fest account. Your password is used once and never stored; only a sign-in
token is kept, in `%LOCALAPPDATA%\DeadFestDetector`, never in the evidence folder. You're then asked
whether to share. You can change it any time:

```
run-detector.bat config --sharing shared
run-detector.bat config --sharing private
```

When sharing is on, each death uploads a compressed copy (under 900 KB) and a thumbnail. The
original PNG never leaves your PC, and no video is ever uploaded. Uploads that fail (offline, server
down) wait and retry; nothing is lost. `run-detector.bat sync` retries by hand, and also brings
back the admins' decisions: rejected or removed images are moved to
`D:\XboxDeathDetector\rejected\` (kept, not deleted) and moved back if restored.

The detector signs in as you, so it can do exactly what you can do in the app and nothing more.
The Firestore security rules accept evidence only as *pending*, in your own name.

## Running

```
run-detector.bat
```

(the same as `run-detector.bat run`). Each death prints a line and saves:

```
D:\XboxDeathDetector\DyingLight2\2026-10-25_01-32-10_self-death_1a2b3c4d.png      the death
D:\XboxDeathDetector\DyingLight2\2026-10-25_01-32-10_self-death_1a2b3c4d_pre.png  just before
D:\XboxDeathDetector\metadata\1a2b3c4d....json                                    the record
```

The record (event id, game, UTC time and local time, classification, player, confidence, path,
verification status, review and sync state) is the source of truth, not the filename. Files are
never overwritten.

To look through everything on the PC: `run-detector.bat gallery` writes and opens
`D:\XboxDeathDetector\index.html`, a static page opened straight from disk (no server). Or
`run-detector.bat list`.

### How a death is counted once

```
ALIVE -> POSSIBLE_DEATH -> CONFIRMED_DEATH -> WAITING_FOR_RESPAWN -> ALIVE
```

A cue must hold for 2 analysed frames over at least 0.4 s before it counts (one flickering frame
never does). After that, the death screen can stay up as long as it likes: the detector waits for
it to be gone for 3 s, and won't count the same death again within 8 s. All of these are in
`config.json` under `detection`.

### If D: is unavailable

The detector never saves anywhere else. A death it can't save is held in memory (up to 20) and
retried every 15 seconds, with a clear message saying why: drive missing, folder not writable, or
disk full.

## Tests

```
.venv\Scripts\python -m pytest tests
```

They cover storage (no overwrites, no path escapes, outage handling), the de-duplication state
machine, detection with templates and with real Windows OCR, the full pipeline on generated
footage, uploads and status mirroring. `tests\test_emulator.py` runs the whole chain against the
Firestore emulator and the real security rules (see the file for the command).
