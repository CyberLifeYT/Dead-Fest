# Dead-Fest Xbox Death Detector

A small Windows program that watches your Xbox gameplay video, spots death screens, saves a
screenshot of each death to `D:\XboxDeathDetector`, and (if you choose) shares it with your
Dead-Fest group so admins can verify it in the app.

Supported games: Dying Light, Dying Light 2, Dying Light: The Beast, World War Z, Dead Island,
Dead Island Riptide, Dead Island 2, State of Decay 2.

## The desktop app

Double-click **`run-detector.bat`**. The first time, it sets itself up (a minute), then opens the
**Dead-Fest Detector** window:

| Tab | What it's for |
|---|---|
| **Live** | Start / stop, the picture the detector sees, the game, **whose deaths are yours**, sharing and counting switches, and a log of every death, upload and count |
| **Evidence** | Every death: this PC's and everyone's shared ones. **Count it** (choose the player), **Not verified**, **Restore**, open the screenshot |
| **Players** | All Dead-Fest players with level, coins and deaths per game. Pick who **"me"** is and enter each player's **Xbox gamertag** |
| **HUD setup** | Mark each player's name and health bar on screen, for games that never say "You died" |
| **Settings** | Sign in, video source (Xbox app or capture card), evidence folder |

### Counting deaths straight away

With **Share with the app** and **Count deaths straight away** on, and signed in with an **admin**
account, each death is counted the moment it's detected:

- **your own deaths** go to the player chosen under "Your deaths count for" (you);
- **a teammate's death** goes to the player whose gamertag (Players tab) matches the name on screen,
  even with small OCR slips like `Shad0wOnyx1l`;
- a name that matches nobody **stays pending** for you to assign on the Evidence tab.

It stays counted **until you press Not verified** (here or in the phone app's Admin > Deaths), which
takes it off the count again. Restore puts it back, exactly once. This is the same admin
verification as the phone app, checked by the same security rules: one death per record, and
**coins are never touched**.

### Teammate deaths (no "X is dead" on screen)

Co-op games show each teammate as a row on the HUD: an icon, their name and a health bar. In all of
these games an empty or red bar usually means **downed** (still revivable), not dead, so the detector
watches for each game's *death* sign instead:

- **Dying Light (all three): no setup.** The teammates list under your health (top left) is watched
  automatically. A downed teammate keeps a **white** name and skull; when they **die**, the name and
  skull turn **red**, and that is counted about 2 seconds later. Being downed never counts.
- **Other games:** in **HUD setup**, press **Capture frame**, drag the **Teammates area** over the list
  of names, choose **A teammate is dead when** (*their health bar empties* or *either*), and **Save**.

How it works:

1. Names are found by their **shape** (letters in a row), not by reading them, and trusted once they
   stay in place: the HUD is still while the world moves, so bright scenery never becomes a teammate.
   Leaving, rejoining and swapping places just make rows appear and disappear.
2. A dead name is judged on **exactly the letter pixels** it had while white, so a red roof behind
   the HUD, or a downed teammate's red health bar, can't fake a death.
3. **Who it was:** the name is read (Windows OCR) when it's big enough and matched to a Dead-Fest
   player. If it matches nobody, or can't be read, and you've ticked one teammate under
   **Playing with** on the Live tab, it's them, and the name seen on screen is saved as their
   gamertag so it matches by itself from then on. With several teammates and no readable name, the
   death is saved for an admin to assign.

**The Xbox app must be big enough.** Names are small: at 620 or 500 pixels wide they're a few pixels
tall and nothing can read or even find them. Teammate tracking pauses (with a red warning and a
**Maximise the Xbox app** button on the Live tab) whenever the picture is under about 600 pixels tall.
Maximised is best.

Press **Test** in HUD setup during co-op to see every teammate found, drawn on the picture, with their
state (alive/downed or DEAD) and who they are.

Your own deaths come from the death screen ("YOU ARE DEAD" in Dying Light). **My health bar** is
optional and never counts a death on its own, because the HUD also disappears in menus, the map and
cut-scenes.

### Live preview

The **What the detector sees** panel is a smooth live stream of the capture (15, 30 or 60 FPS, or a
custom rate, chosen next to it). Capture runs on its own thread, so detection never makes the picture
stutter, and the stream comes from a server that only this PC can reach (127.0.0.1, with a secret
per-run key). If there's no picture, the panel says why, for example that the Xbox app is minimised.

## What it can and can't do

- It reads **only the picture you see**: from the Xbox app's Remote Play stream on your PC, or from
  an HDMI capture card. It never touches the console, its memory, its security or its network
  traffic.
- It finds deaths from what the game shows, combining several checks:
  - the death screen's wording ("You died", "You are dead", "<name> has died"), read with Windows'
    own OCR and matched loosely so misreads like "Y0U DlED" still count;
  - **respawn prompts** ("Press A to respawn", "Load last save"), counted only when the screen is
    also dark or washed out, so menus don't trigger them;
  - **HUD health bars** that empty and stay empty, with the name next to them (HUD setup);
  - a **template** you capture once from a real death screen (the most reliable single cue).
- It **can't see every death**. If a game shows nothing when a teammate dies, that death can't be
  detected from the video, and the detector does not guess. Detection quality depends on the game,
  the resolution and what's on screen.
- A detected death is **evidence, not a verdict**. It arrives in the app as *pending*; an admin
  verifies or rejects it. Only a verified death changes anybody's death count, and moderation
  **never adds or deducts coins**.

## Setup

1. Install **Python 3.11 or newer** from python.org (tick "Add python.exe to PATH").
2. Double-click `run-detector.bat`. The first run creates a private environment in
   `detector\.venv` and installs the packages (OpenCV, NumPy, mss and the Windows OCR bindings),
   then shows a menu: start detecting, check setup, choose the game, choose the video source,
   sign in, sync, open the gallery. Press a number; results stay on screen until you press a key.
3. Pick **2 Check setup** first (or run `run-detector.bat doctor` in a terminal). It checks that `D:\XboxDeathDetector` can be written, that OCR works, that the video
   source gives frames, and whether you're signed in. Until a capture card is connected (or the
   source is set to the Xbox app window), "Start detecting" reports that the video device could
   not be opened.

Every menu item is also a command for a terminal, as used below.

### Choose the game

```
run-detector.bat config --game dying_light_2
```

Ids: `dying_light`, `dying_light_2`, `dying_light_the_beast`, `world_war_z`, `dead_island`,
`dead_island_riptide`, `dead_island_2`, `state_of_decay_2`.

### Connect the video

**Xbox app Remote Play (the default, no extra hardware).** On the PC, open the **Xbox app**, pick
your console and start **Remote Play**, so your console's picture is on screen. That's all: the
detector finds the Xbox app by itself (menu **5 > 1**, or `run-detector.bat config --source xbox_app`).

- It captures the app with Windows Graphics Capture, the same way OBS's window capture works, so the
  Xbox app **can sit behind other windows**. It just can't be **minimised**; if it is, the detector
  says so and waits.
- It keeps only the game picture: the app's title bar and any black letterbox bars are cut off, so
  resizing the window doesn't matter.
- Menu **3 (preview)** saves and opens one frame exactly as the detector sees it. Check it shows your
  game before a session.
- Remote Play quality follows your network. A blurry or stuttering stream makes text harder to read,
  so a wired connection and a larger Xbox app window help.

**Capture card.** Plug the Xbox's HDMI into the card and the card into the PC. Close OBS or the
card vendor's app (only one program can use the card at a time), then menu **5 > 2**, or:

```
run-detector.bat devices
run-detector.bat config --source capture_card --device 0
```

If the picture is black, turn off **Allow HDCP** in the Xbox's *Settings > General > TV & display
options > Video modes*. Games don't need HDCP; streaming apps do.

**Recorded footage (for testing).** `run-detector.bat run --video D:\clips\session.mp4 --fast`

### Teach it a death screen (strongly recommended)

Get one screenshot of a real death screen for your game. The easiest: when you're on the death
screen, use menu **3 (preview)**. It saves `%LOCALAPPDATA%\DeadFestDetector\preview.png` at exactly the
detector's crop, so the template matches whatever size the Xbox app window is. A saved evidence image
works too. Note the pixel box around the part that never changes, usually the "YOU DIED" text, then:

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
