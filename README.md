# DeadFest 🎮💀

> ⚠️ **THE OFFICIAL DEAD-FEST SERVER IS PRIVATE – FRIENDS & FAMILY ONLY**  
> The official app and its database are strictly for my personal circle of friends and family.  
> **Do not try to join, sign in to, or install the official build.** Unauthorized access will result in an **immediate and permanent IP ban**.  
> The **source code** is published so it can never be lost again – and so you can [build and host **your own** private copy](#-host-your-own-dead-fest-full-guide) with your own Firebase project.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase)](https://firebase.google.com)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-3DDC84?logo=android)](https://developer.android.com)
[![Version](https://img.shields.io/badge/Version-2.1.1-FF4B4B)](https://github.com/CyberLifeYT/Dead-Fest/releases)
[![APK](https://img.shields.io/badge/APK-4.0%20MB%20(R8)-555)](https://github.com/CyberLifeYT/Dead-Fest/releases)

**DeadFest** is a real‑time multiplayer survival companion built for a small, private community.  
Record your deaths, survive the casualty tax, spin the Wheel of Fate, scratch for prizes, level up, and see who's online — all synced live for every survivor.

---

## ✅ PROJECT STATUS – REBUILD COMPLETE

**Survivors, the terminal has risen.**

The original source was lost to a failed hard drive, so DeadFest was **rebuilt from scratch** (v2.0.0) while the event kept running. The rebuild is now complete and receiving regular updates.

- ✅ **All data survived** – coins, deaths, titles and history were never affected.
- ✅ **The event never stopped** – the current run stays live.
- ✅ **Updates are back** – new versions arrive in‑app (update prompt / Settings → System Updates).
- 🪶 **Built for every phone** – small APK, Performance Mode, and live data that disconnects in the background to save battery.

> 🖥️ **The desktop version has been discontinued.** The Windows client (`Dead-Fest-Desktop-*.exe`) will receive no further updates, and new features (group chat, alerts, achievements, flash sales...) are not coming to it. **The Android app is where Dead-Fest lives and it will keep getting updates.** Existing desktop builds still connect to the same game for now, but they may stop working as the mobile app moves on. Its source stays in [`desktop/`](desktop/) for reference.

---

## 🔄 What's New

### v2.1.3 – Xbox death evidence *(Latest)*

- **🎮 Xbox Death Detector** – A companion program for the PC next to the Xbox ([`detector/`](detector/README.md)). It watches the gameplay video (HDMI capture card or the Xbox app's Remote Play window), spots death screens in Dying Light 1/2/The Beast, World War Z, Dead Island/Riptide/2 and State of Decay 2, and saves each death's screenshot to `D:\XboxDeathDetector`. Each death is counted once however long the death screen stays up. Evidence stays private until you choose to share it.
- **📸 More → Deaths Images** – Every survivor can browse the shared death screenshots: filter by game and status, sort, zoom, with clear empty, error and missing-image states.
- **✅ Admin → Deaths** – Verify, reject, correct, remove, restore or permanently delete evidence, with search, filters, a missing-image check and a full audit history. Verifying adds exactly one death to the player's count, enforced by the security rules.
- **🪙 Coins are never touched by death moderation** – The security rules refuse any moderation commit that changes a balance, and every case is tested against the Firestore emulator with real before/after balances.
- **🛠️ R8 build fixed** – The small "R8" APK of 2.1.2 crashed on launch (R8 stripped a constructor the background check-ins need). Fixed, and the release build now checks for it automatically.
- **Admins:** deploy the updated rules **and indexes** (`firebase deploy --only firestore:rules,firestore:indexes`). Full notes: [release/v2.1.3](release/v2.1.3/RELEASE_NOTES.md).

### v2.1.2 – Stay in the loop

- **🔔 Real Android notifications** – Six categories you can mute separately (alerts, chat, levels & achievements, broadcasts, Black Market, updates), the Dead-Fest skull in the status bar, tap to open the right screen, quick Reply from chat notifications, and grouping so bursts don't flood the tray.
- **⏱️ Background check-ins** – About every 15 minutes, Android's scheduler checks for new alerts, chat, broadcasts, flash sales and updates while the app is closed. No server or paid plan needed; can be switched off in Settings.
- **🎯 Weekly challenges** – Four challenges every week ("Spin the Wheel 10 times", "Survive 3 days"...) paying coins and XP. More → Challenges.
- **💬 Gallery reactions & comments** – React with 🔥 💀 😂 😮 ❤️ 👍 and comment on shared images; uploaders get an alert.
- **🌍 Fully translatable** – All the older screens' text moved into `strings.xml` ([docs/TRANSLATING.md](docs/TRANSLATING.md)).
- **Admins:** deploy the updated [`firestore.rules`](firestore.rules) for reactions and comments. Full notes: [release/v2.1.2](release/v2.1.2/RELEASE_NOTES.md).

### v2.1.1 – The community update

- **💬 Group chat** – One chat for every survivor (newest 100 messages, live), with photo attachments. Delete your own messages; admins can delete any.
- **🔔 Personal alerts** – "You were cursed by X", "Your Shield blocked a curse", "Your image was removed", admin changes to your coins/deaths, approved game requests. Phone notifications while the app is open, plus an Alerts inbox (bell on the Dashboard).
- **🏅 Achievements** – 16 badges (First Blood, Survived 7 Days, Bankrupt and Back, Jackpot, Hex Master, Deflector...) in Progression, with an unlock pop-up.
- **📊 My Stats** – Your deaths by sector, coins over time and deaths per day, charted.
- **🙂 Edit Profile** – Emoji avatar, bio and badge colour (more colours unlock with levels).
- **⚡ Flash sales** – Admins set a discount and timer; the shop shows a countdown banner and crossed-out prices.
- **🔥 Sector Overloads pay out** – Deaths in the overloaded sector now pay every survivor the boosted survival reward and earn boosted XP (capped at x10).
- **♿ Accessibility & translations** – Every theme now passes WCAG contrast checks (enforced by a test), Halloween/Toxic Storm buttons use readable dark labels, new screens are fully labelled for screen readers, and new text lives in `strings.xml` ready for translation ([docs/TRANSLATING.md](docs/TRANSLATING.md)).
- **🖥️ Desktop discontinued** – The Windows client gets no further updates; the Android app continues.
- **Admins:** deploy the updated [`firestore.rules`](firestore.rules) before handing out this build. Full notes: [release/v2.1.1](release/v2.1.1/RELEASE_NOTES.md).

### Desktop 2.1.0 – The desktop console, at full parity *(final desktop release)*

- **🖥️ Every screen, every action** – The Windows client is now the phone app, not a tour of it: command center, survivor network, black market (all five tabs), Wheel of Fate, scratch cards, progression, utility hub, team progress, archives, image board, survival intel, game requests, every settings page, and the full 12-module admin console. Same layout, same wording, same rules as the Compose screen it replaces.
- **⚙️ The same transactions** – Buying, cursing, upgrading insulation, buying and equipping titles, spinning, scratching, recording a death (tax, penalty spin, bankruptcy, XP, mutual reward), claiming rations, prestiging, equipping reward frames, editing profiles, posting images and requesting games all call the same Firestore operations as the phone. The formulas live in `js/economy.js` and the repository port in `js/data.js`; there is no desktop-only shortcut and no separate database.
- **🧪 Tested screen by screen** – A render harness walks every screen, tab and dialog, stubs the data layer and asserts 31 actions (spin timing and readout, scratch reveal, purchase confirmation, the death pipeline, handle validation, themes, admin edits). It ships in the repo at `desktop/tools/smoke.js`.
- **📦 Still one file** – `Dead-Fest-Desktop-2.1.0.exe`, no installer. It reports itself as 2.1.0 (build 15), so it will not offer you the phone's 2.0.9 update.
- **📱 The APKs are unchanged** – Both 2.0.9 builds are attached again for convenience. Android stays on 2.0.9 (build 14); only the desktop moved.

### v2.0.9 – The admin side, enforced by the database

- **🔐 Firestore security rules** – The rules are now part of the project, in [`firestore.rules`](firestore.rules), and **tested on every change** — 45 cases decided against the real rules file on the Firestore emulator. This was not a formality: **the two features added in 2.0.8 — changing your handle and asking for a game — were being refused by the server**, because nothing had told Firestore those collections were allowed to exist. Now only an **admin** can change the global document (which holds the update link every survivor is told to download, the economy, the sectors and every switch), nobody can promote themselves, only an admin can approve or remove a game request or edit the shop, a handle can only be claimed once, and the global death counter can only move one step at a time. See [docs/ADMIN.md](docs/ADMIN.md).
- **🖥️ The desktop console caught up** – The Windows client can now **inject a death** (the full pipeline: tax, penalty spin, bankruptcy, XP) and **grant or revoke admin rights** — the two player controls the phone app has always had. It also refuses to render the console for anybody who is not an admin.
- **🩹 One quiet fix** – The rules read the admin flag from your player document, so a survivor whose document was missing could not write anything. The app now repairs that document the moment it notices it is gone.
- **Honest limit** – The **economy is still client-trusted**: a survivor can write their own coins and XP, because those are the same writes the app makes when you spin. Rules cannot tell the two apart. Moving the economy into Cloud Functions is the real fix and needs the Blaze plan; the rules close every door that can be closed without it.

### v2.0.8 – Handles, Themes, Game Requests & the desktop client

- **🖥️ Dead-Fest for Windows** – The same app as a **desktop program**: `Dead-Fest-Desktop-2.0.8.exe` on the releases page is a single self-contained file — no installer, no uninstaller, run it from anywhere. It talks to the **same Firebase project**, so it is the *same accounts and the same live game* — your coins, deaths, level, handle and the whole survivor network are the ones you already have, and anything that changes on a phone appears on the desktop immediately. Same ember-on-ink design system, same cards, same type, same themes — the phone app stood up for a desktop. Source is in [`desktop/`](desktop/).
- **🪪 Your handle, your call** – Change the name the community sees from **Settings → General → Identity**. It updates everywhere the moment you save it (player list, death feed, game requests) because every screen reads the same live field. Handles are unique across the whole app — `Slayer` and `slayer` are the same name — 3–20 characters of letters, numbers and underscores, with `Admin`/`Dead-Fest`/`Moderator` reserved. Coins, level, stats, titles and history are untouched.
- **🎨 App themes** – **Settings → General → Themes** offers Default, Halloween, Christmas, Toxic Storm and Blood Moon, each previewing with its own real colours before you pick. The change is instant across the whole app. Themes restyle the surfaces and the brand colour and deliberately leave the semantic accents alone (amber is always currency, green always success), so the app stays Dead-Fest in every season and a success pill never starts looking like a warning.
- **🎮 Request a game** – **More → Request Game** opens the community queue. **Request Game** looks the title up in a game database and shows real box art; **Pending games** shows what is waiting for an admin; **Approved games** is out in the open. Admins get a **Requests** module (approve / remove) — only admins can decide. If the game database is unreachable you can enter the game by hand, so the feature works regardless.
- **🛑 Forced Spin Protocol kill switch** – Admins can turn it off from **Admin → Global → System switches**. While off, a death adds no penalty spin; existing spins and bankruptcy escalation are untouched. It is read live, so it takes effect on the next death rather than after a restart, and it defaults to on so existing behaviour is preserved.
- **🧪 More tests** – 75 unit tests now, covering the handle rules, the theme system, the death-pipeline toggle and the game-database parsing.

### v2.0.7.2 – Two Builds, Splash & Screen Readers

- **📦 Pick your build** – Every release is now published twice: **Dead-Fest.R8.apk** (~3.9 MB, shrunk by R8) and **Dead-Fest.apk** (~26 MB, the full build). Same version, same signing key, so either installs over the other whenever you change your mind. **Settings → Update** shows both and remembers your choice, starting you on the same kind of build you're already running. If only one has been uploaded, everyone is served that one rather than being told there's nothing to install.
- **💀 A real splash screen** – Cold start shows the Dead-Fest skull on ink on **every** Android version from 7.0 up, not just on Android 12+ where the system invents one from the launcher icon. It also waits for your saved session to come back before handing over, so a returning survivor can't glimpse an empty dashboard or the login screen for a frame — capped, so a broken session can never strand you behind a logo.
- **♿ Screen-reader support** – The countdown is now **one** TalkBack stop spoken as "Starts in 2 days 5 hours 30 minutes" instead of eight loose numbers, deliberately rounded to the minute so it holds still (the tiles still tick every second). Section titles are headings, progress bars report their real value, label/value rows read as one fact, selected options announce that they're selected, small controls got proper touch targets, and icons that only repeat the text beside them are no longer read twice.
- **🔐 Signed releases + CI** – `assembleRelease` and `assembleDebug` sign from a keystore read out of `local.properties` or the environment, and a GitHub Actions workflow builds, tests, signs, **verifies both certificates against the expected fingerprint** and publishes on a tag. The signature is still the one every earlier build carried — a different key would make Android refuse the update and force an uninstall. See [docs/SIGNING.md](docs/SIGNING.md).
- **📐 Not measured yet: baseline profile** – The runtime half (`profileinstaller`) is in, but the profile itself has to come from a macrobenchmark on a real device and none was available. Writing one by hand would be inventing measurements. See [docs/BASELINE_PROFILE.md](docs/BASELINE_PROFILE.md).

### v2.0.7.1 – Interface Rebuild & Live Countdown

- **⏱️ The countdown now ticks** – The Next Dead-Fest panel updates **live every second** in DAYS / HRS / MIN / SEC. Pause the app for an hour and it snaps straight to the correct value on return. In Performance Mode it updates every 30 seconds and the SEC tile is hidden, because a seconds digit that only changed every 30 s would be lying to you.
- **🔋 Timer bug fixed** – Daily Rations used to run a one-second timer that never stopped, even once the reward was claimable and even in the background. It now stops the moment there's nothing left to wait for and never runs while the app is closed.
- **🎨 Every screen rebuilt** – Command Center, Login, Utility Hub, Black Market, Survivor Network, Terminal Config, Wheel of Fate, Scratch Cards, Progression, Archives, Team Progress, Images, Survival Intel and the Admin console all sit on one design system now: one app-wide background, one card, one type scale, one spacing scale. No screen was left on the old styling.
- **🧭 New navigation bar** – A floating bar with a gradient scrim so content dissolves behind it, and an ember indicator above the active tab. Admins' seven-tab compact mode still fits on a small phone.
- **💀 Proper app icon** – The green Android Studio robot is gone. The icon is now a hand-drawn vector skull with an ember glow on deep ink, supplied as an adaptive icon with themed-monochrome support, so it looks right in circle, squircle and Android 13+ themed modes. The skull is also the brand mark in every screen header.
- **🛒 Custom shop artwork** – Black Market items each carry a hex-plate emblem in their own hue: cyan for defensive gear, purple and red for curses, amber for economy, green for a second chance.
- **📱 Leaner** – No new dependencies, ~173 KB of unused stock icon art deleted, animations are draw-layer only, and Performance Mode now also skips the background grid and every entrance animation.
- **🔐 Nothing is reset** – No storage key, session or Firestore path changed, so upgrading keeps you signed in with your coins, level, titles and history untouched.

### v2.0.7 – Seasons & Team Progress

- **📅 Next Dead-Fest** (More → top of the tab) – The upcoming season by name (e.g. **"Dead-Fest Season 2"**), a live **countdown** in DAYS / HRS / MIN / SEC, the **games lined up** in play order, the exact start time and an optional blurb.
- **📊 Team Progress** (More → Team Progress) – The season running right now in numbers: survivors, total deaths, coins in play, average and top level, prestiges, bankruptcies, pending spins and insulated survivors, plus eliminations by sector and the current deadliest/richest.
- **🕰️ Every Past Dead-Fest** – Browse every archived season ("Dead-Fest Season X"), each with its summary, eliminations by sector and full final standings. Tap back through your history.
- **🛠️ Admin → Event** – Rename the current season, switch the Next Dead-Fest panel on/off, set its name, start date & time and blurb, and build the game line-up (add from your sectors, reorder, remove).
- **🗂️ Richer Archives** – Season snapshots now also store level, XP, prestige, title, avatar, frame, colour, insulation, bankruptcy and pending spins, plus the sector roster and global eliminations at close. Old archives still load and simply say the extra stats weren't recorded.
- **🎨 Layout Pass** – The More tab is now grouped into clear sections with larger, described tiles; the bottom bar no longer clips labels on small phones (unselected tabs collapse to their icon); admins get a one-tap "Set up Next Dead-Fest" prompt until it's configured.

### v2.0.6 – Presence, Images & Battery

- **🟢 Live Online/Offline Status** – You're **online** while the app is open and **offline** the moment you leave. Crashed or lost signal? You drop offline within ~2.5 minutes. Shown on Players ("last seen 5m ago"), the Dashboard, the Admin Live Dashboard and the home‑screen widget.
- **🖼️ Images Gallery** (More → Images) – Share photos with every survivor. Auto‑rotated, compressed, thumbnails for fast scrolling, full‑screen pinch‑to‑zoom, uploader/admin delete, offline uploads queued.
- **🔋 Battery Optimisation Setting** – One‑time first‑launch prompt + Settings → System → Background Activity, so alerts and updates aren't delayed by battery savers.

### v2.0.5 – The Economy Update

- **💰 Daily Rations** – Claim free coins once a day (50, or 100 with the permanent **Ration Upgrade**). Survive a full day for bonus XP.
- **💀 Casualty Tax** – Every death costs coins: 30% (>5,000), 20% (>1,000), 15% (>500), or a flat 25.
- **📉 Bankruptcy Levels 1 & 2** – Hit 0 coins and payouts are halved; die again while broke and you must clear penalty spins to escape.
- **🎡 Forced Spin Protocol** – Every death adds a pending penalty spin on the Wheel.
- **🤝 Mutual Survival Reward** – When someone dies, everyone else gets paid.
- **🎟️ Scratch Cards** – Instant‑win mini‑game with coins, Shields and Lucky Coins.
- **🛡️ Insurance Policy** – Absorbs 50% of your next casualty tax.
- **🏦 Wealth Insulation** – Four permanent tiers cutting your tax by up to 100%.
- **⭐ Levels, XP & Prestige** – Earn XP from everything; unlock badge colours, avatar frames and titles; prestige at level 50.
- **📡 Admin Live Event Dashboard** – Real-time online count, death rate, overloads, pending spins, wealth distribution and bankruptcy tracker.

### v2.0.4 – Smoother & Stronger

- Faster, smaller (3.7 MB) and battery‑friendly; Performance Mode for low‑end phones.
- Shop items and Wheel payouts fully working (Shield, Curse, Revive, Lucky Coin).
- Dozens of crash and logic fixes; new consistent design across every screen.

*(Full release notes for every version are on the [Releases page](https://github.com/CyberLifeYT/Dead-Fest/releases).)*

---

## 📖 Table of Contents

- [Features](#-features)
  - [🎮 Gameplay & Economy](#-gameplay--economy)
  - [👥 Player & Social Features](#-player--social-features)
  - [🗳️ Community & Competition](#️-community--competition)
  - [🛡️ Admin Override Suite](#️-admin-override-suite)
  - [📡 Infrastructure](#-infrastructure)
- [Tech Stack](#-tech-stack)
- [Host Your Own Dead-Fest (Full Guide)](#-host-your-own-dead-fest-full-guide)
  - [1. What You Need](#1-what-you-need)
  - [2. Download the Source](#2-download-the-source)
  - [3. Create Your Firebase Project](#3-create-your-firebase-project)
  - [4. Get google-services.json](#4-get-google-servicesjson)
  - [5. Firestore Security Rules](#5-firestore-security-rules)
  - [6. Build & Run](#6-build--run)
  - [7. Make Yourself Admin & Set Up the Game](#7-make-yourself-admin--set-up-the-game)
  - [8. Sign & Distribute (Deploy)](#8-sign--distribute-deploy)
  - [9. Publishing Updates (OTA)](#9-publishing-updates-ota)
  - [10. Editing the App](#10-editing-the-app)
  - [11. Costs & Limits](#11-costs--limits)
  - [12. Troubleshooting](#12-troubleshooting)
- [Contributing](#-contributing)
- [License](#-license)

---

## ✨ Features

### 🎮 Gameplay & Economy

- **Sectors System** – Game zones where every death is recorded to a specific sector, shown on a tactical dashboard.
- **Casualty Tax** – Wealth‑based death penalty calculated from your live balance.
- **Bankruptcy & Forced Spins** – Economic pressure with escalating penalties and a live pending‑spin counter. Admins can toggle the Forced Spin Protocol off entirely with a kill switch.
- **Wheel of Fate** – Weighted spins (decimal odds for ultra‑rare loot) that really give or take coins; penalty spins when you owe the Wheel.
- **Scratch Cards** – Instant prizes using the same odds engine as the Wheel.
- **Black Market** – Shield, Curse, Double Curse, Revive, Lucky Coin, Insurance, Ration Upgrade, Wealth Insulation tiers and Titles.
- **Daily Rations** – Free daily coins with a live countdown.
- **Sector Overloads** – Admin‑triggered events with multipliers and optional timers.
- **Runs (Seasons)** – Archive a season and reset coins, deaths and penalties; levels and permanent upgrades carry over. Track every season afterwards in **Team Progress**.
- **Forced Spin Protocol Kill Switch** – Admins can disable penalty spins entirely from Admin → Global → System switches. Existing spins are untouched, and bankruptcy escalation still applies separately.

### 👥 Player & Social Features

- **Survivor Identity** – A handle you change yourself in Settings → General → Identity (unique across the app, updates everywhere in real time), plus avatar, badge colour, avatar frame and earnable **Titles**.
<- **App Themes** – Choose from Default, Halloween, Christmas, Toxic Storm and Blood Moon in Settings → General → Themes. Each previews with real colours before you pick, and the change applies instantly everywhere. Themes restyle surfaces and the brand colour while keeping semantic accents (amber = currency, green = success) consistent.
- **Desktop Client (Windows)** – *Discontinued.* The last build is Desktop 2.1.0; no further updates. Source kept in [`desktop/`](desktop/).
- **Levels, XP & Prestige** – A long‑term progression track with cosmetic rewards and a permanent prestige badge.
- **Live Presence** – See who's in the app right now and when others were last seen.
- **Images Gallery** – A shared photo wall for the whole community.
- **Deaths Images** – Xbox death screenshots captured by the [Xbox Death Detector](detector/README.md), with their verification status.
- **Survivor Dossiers** – Tap any survivor for deaths per sector, coins, level, gear, bankruptcy and status.
- **Feed & Alert Filters** – Choose which events show in your live ticker and push notifications.

### 🗳️ Community & Competition

- **Leaderboards** – **Wealth** (most coins) and **Survival Prowess** (fewest deaths).
- **Wasteland Archives** – Past seasons with champions and full standings.
- **Team Progress** – Live season aggregate (survivors, deaths, coins, levels, prestiges, bankruptcies, spins, insulation) plus eliminations by sector and every past Dead-Fest, browsable in full.
- **Next Dead-Fest Countdown** – The upcoming season by name, with a live countdown, the game line-up in play order and the exact start time.
- **Game Requests** – A community queue where survivors can request games (with real box art lookup from a game database), see pending requests, and browse approved games. Admins review and decide from the Requests module.
- **Live Ticker** – Deaths, purchases, spins, scratches, level‑ups and image shares as they happen.

### 🛡️ Admin Override Suite

- **Live Event Dashboard** – Online count, deaths/minute, overloads, pending spins, wealth chart, bankruptcy and insulation tracker.
- **Shop & Economy Control** – Enable/disable and price every item, tune insurance, rations, survival reward and insulation tiers.
- **Scratch & Wheel Editors** – Prize pools with decimal weights and live odds.
- **XP & Levels** – Configure every XP source and the level curve.
- **Player Control** – Coins, death injection (full tax pipeline), admin rights, clear penalties.
- **Season & Event Control** – Rename the running season, schedule the next Dead-Fest (name, start time, blurb) and queue its games.
- **Death Verification** – Review Xbox death evidence: verify (adds one death, never touches coins), reject, correct, remove, restore, with an audit trail.
- **Game Requests Module** – Review the community queue: approve pending requests (making a game a candidate sector) or remove them. Only admins can decide.
- **System Switches** – Market, Wheel, Revives, Lockdown, **Forced Spin Protocol kill switch** (stop deaths from adding penalty spins), firmware publishing (with forced updates) and broadcasts.

### 📡 Infrastructure

- **Firebase Core** – Firestore (database + image storage), Auth (Email/Password) and Cloud Messaging (FCM).
- **Real‑time Everything** – Live dashboard, leaderboards, shop, wheel, presence and gallery – no manual refreshes.
- **Transaction‑Safe Economy** – Every coin, death and XP change runs in a Firestore transaction on live data.
- **Low‑End Friendly** – R8‑shrunk 3.9 MB release (or a 26 MB full build if you'd rather), Performance Mode, lifecycle‑aware listeners, no background service.
- **In‑App OTA Updates** – Two published builds, per-user choice in Settings, progress dialog and automatic installer handoff.
- **Home Screen Widget** – Global eliminations, online survivors and overload status.
- **75 Unit Tests** – Covering handle rules, theme system, death-pipeline toggle, game-database parsing, economy and presence rules.
- **Firestore Security Rules** – Admin actions are enforced by the **database**, not by the app: only an admin can publish firmware, repoint the update URL, edit the shop, approve game requests or change anybody's admin rights. The rules live in [`firestore.rules`](firestore.rules) and are tested against the Firestore emulator on every push — **45 tests** that check both halves: the app still works, and the admin boundary is still shut. See [docs/ADMIN.md](docs/ADMIN.md).

---

## 🧰 Tech Stack

| Category             | Technology                                   |
|----------------------|----------------------------------------------|
| Platform             | Android 7.0+ (minSdk 24, targetSdk 37)       |
| Language             | Kotlin 2.2                                   |
| UI Framework         | Jetpack Compose + Material 3                 |
| Reactive Layer       | Kotlin Coroutines + Flow                     |
| Architecture         | ViewModel + Repository, pure domain rules    |
| Backend / Database   | Firebase Firestore                           |
| Image Sharing        | Firestore blobs (thumbnail + full image)     |
| Authentication       | Firebase Auth (Email/Password)               |
| Push Notifications   | Firebase Cloud Messaging (FCM)               |
| Testing              | JUnit (economy, presence, handles, themes) + Firestore rules tests (emulator) |
| Security             | Firestore security rules, enforced by the database, not the app |
| Build System         | Gradle (Kotlin DSL), R8                      |

---

## 🚀 Host Your Own Dead-Fest (Full Guide)

Want to run Dead-Fest for **your own** group? The app has **no server of its own to host**. Everything runs on Firebase (Google's backend), and the free tier is plenty for a friends-and-family group. You build the Android app, connect it to *your* Firebase project, and share the APK with your people.

> **Your copy is completely separate.** It has its own database, accounts and admins. It never connects to the official Dead-Fest server.

### 1. What You Need

| Thing | Why | Cost |
|-------|-----|------|
| A computer (Windows, macOS or Linux) | To build the app | – |
| [Android Studio](https://developer.android.com/studio) (latest) | Editing + building (includes the JDK and Android SDK) | Free |
| A Google account | For Firebase | Free |
| A Firebase project | Database, logins, push notifications | Free (Spark plan) |
| An Android phone (Android 7.0+) or the Android Studio emulator | Testing | – |
| [Git](https://git-scm.com/downloads) *(optional)* | Cloning and keeping your copy updated | Free |

### 2. Download the Source

**Option A: Git (recommended)**
```bash
git clone https://github.com/CyberLifeYT/Dead-Fest.git
cd Dead-Fest
```

### 3. Firestore Security Rules

The admin actions are enforced by Firestore itself, not by the app — only an admin can publish
firmware, repoint the update URL, edit the shop or approve game requests. Deploy the rules that
ship with the project before you let anybody in:

```bash
npm install -g firebase-tools
firebase login
firebase deploy --only firestore:rules,firestore:indexes
```

The indexes are used by the Deaths Images filters; without them a filtered view shows a message
asking for them.

The project is already selected in `.firebaserc`, so no project id is needed. See
[docs/ADMIN.md](docs/ADMIN.md) for what the rules protect, and for how to test a change to them
before deploying it.
