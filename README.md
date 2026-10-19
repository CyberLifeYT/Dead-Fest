# DeadFest 🎮💀

> ⚠️ **THE OFFICIAL DEAD-FEST SERVER IS PRIVATE – FRIENDS & FAMILY ONLY**  
> The official app and its database are strictly for my personal circle of friends and family.  
> **Do not try to join, sign in to, or install the official build.** Unauthorized access will result in an **immediate and permanent IP ban**.  
> The **source code** is published so it can never be lost again – and so you can [build and host **your own** private copy](#-host-your-own-dead-fest-full-guide) with your own Firebase project.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase)](https://firebase.google.com)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-3DDC84?logo=android)](https://developer.android.com)
[![Version](https://img.shields.io/badge/Version-2.0.7.2-FF4B4B)](https://github.com/CyberLifeYT/Dead-Fest/releases)
[![APK](https://img.shields.io/badge/APK-3.8%20MB%20(R8)%20%2F%2026.8%20MB-555)](https://github.com/CyberLifeYT/Dead-Fest/releases)

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

---

## 🔄 What’s New

### v2.0.7.2 – Two Builds, Splash & Screen Readers
- **📦 Pick your build** – Every release is now published twice: **Dead-Fest R8.apk** (3.78 MB, shrunk by R8) and **Dead-Fest.apk** (26.8 MB, the full build). Same version, same signing key, so either installs over the other whenever you change your mind. **Settings → Update** shows both and remembers your choice, starting you on the same kind of build you're already running. If only one has been uploaded, everyone is served that one rather than being told there's nothing to install.
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
- **📡 Admin Live Event Dashboard** – Real‑time online count, death rate, overloads, pending spins, wealth distribution and bankruptcy tracker.

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
- **Bankruptcy & Forced Spins** – Economic pressure with escalating penalties and a live pending‑spin counter.
- **Wheel of Fate** – Weighted spins (decimal odds for ultra‑rare loot) that really give or take coins; penalty spins when you owe the Wheel.
- **Scratch Cards** – Instant prizes using the same odds engine as the Wheel.
- **Black Market** – Shield, Curse, Double Curse, Revive, Lucky Coin, Insurance, Ration Upgrade, Wealth Insulation tiers and Titles.
- **Daily Rations** – Free daily coins with a live countdown.
- **Sector Overloads** – Admin‑triggered events with multipliers and optional timers.
- **Runs (Seasons)** – Archive a season and reset coins, deaths and penalties; levels and permanent upgrades carry over. Track every season afterwards in **Team Progress**.

### 👥 Player & Social Features

- **Survivor Identity** – Display name, avatar, badge colour, avatar frame and earnable **Titles**.
- **Levels, XP & Prestige** – A long‑term progression track with cosmetic rewards and a permanent prestige badge.
- **Live Presence** – See who's in the app right now and when others were last seen.
- **Images Gallery** – A shared photo wall for the whole community.
- **Survivor Dossiers** – Tap any survivor for deaths per sector, coins, level, gear, bankruptcy and status.
- **Feed & Alert Filters** – Choose which events show in your live ticker and push notifications.

### 🗳️ Community & Competition

- **Leaderboards** – **Wealth** (most coins) and **Survival Prowess** (fewest deaths).
- **Wasteland Archives** – Past seasons with champions and full standings.
- **Team Progress** – Live season aggregate (survivors, deaths, coins, levels, prestiges, bankruptcies, spins, insulation) plus eliminations by sector and every past Dead-Fest, browsable in full.
- **Next Dead-Fest Countdown** – The upcoming season by name, with a live countdown, the game line-up in play order and the exact start time.
- **Live Ticker** – Deaths, purchases, spins, scratches, level‐ups and image shares as they happen.

### 🛡️ Admin Override Suite

- **Live Event Dashboard** – Online count, deaths/minute, overloads, pending spins, wealth chart, bankruptcy and insulation tracker.
- **Shop & Economy Control** – Enable/disable and price every item, tune insurance, rations, survival reward and insulation tiers.
- **Scratch & Wheel Editors** – Prize pools with decimal weights and live odds.
- **XP & Levels** – Configure every XP source and the level curve.
- **Player Control** – Coins, death injection (full tax pipeline), admin rights, clear penalties.
- **Season & Event Control** – Rename the running season, schedule the next Dead-Fest (name, start time, blurb) and queue its games.
- **System Switches** – Market, Wheel, Revives, Lockdown, firmware publishing (with forced updates) and broadcasts.

### 📡 Infrastructure

- **Firebase Core** – Firestore (database + image storage), Auth (Email/Password) and Cloud Messaging (FCM).
- **Real‑time Everything** – Live dashboard, leaderboards, shop, wheel, presence and gallery – no manual refreshes.
- **Transaction‑Safe Economy** – Every coin, death and XP change runs in a Firestore transaction on live data.
- **Low‐End Friendly** – R8‐shrunk 3.78 MB release (or a 26.8 MB full build if you'd rather), Performance Mode, lifecycle‐aware listeners, no background service.
- **In‐App OTA Updates** – Two published builds, per-user choice in Settings, progress dialog and automatic installer handoff.
- **Home Screen Widget** – Global eliminations, online survivors and overload status.

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
| Testing              | JUnit (economy & presence rules)             |
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

**Option B: ZIP.** On the GitHub page click **Code → Download ZIP**, then extract it.

Then open Android Studio → **File → Open** → select the `Dead-Fest` folder. Let Gradle sync finish; the first sync downloads dependencies and can take several minutes.

> The project will **not build yet**. It needs your own `google-services.json` (step 4). That file is deliberately **not** in this repo.

### 3. Create Your Firebase Project

1. Go to the **[Firebase console](https://console.firebase.google.com)** → **Add project** → give it a name, e.g. `my-deadfest`. Google Analytics is optional.
2. **Authentication:** go to Build → **Authentication** → Get started → **Sign-in method** → enable **Email/Password**.
3. **Firestore:** go to Build → **Firestore Database** → **Create database**. Pick a location close to your players (this can't be changed later). Start in **production mode**; you'll paste the rules in step 5.
4. **Cloud Messaging** (push notifications) is enabled automatically. Nothing to do.

> You do **not** need Cloud Storage, Realtime Database or the paid Blaze plan. Shared images are stored inside Firestore.

### 4. Get `google-services.json`

This file tells the app which Firebase project to talk to.

1. In the Firebase console, open ⚙️ **Project settings** → **General** → **Your apps** → click the **Android** icon.
2. **Android package name:** enter **`com.deadfest`**.
   - Want a different package name (for example, to install alongside another copy)? Change `namespace` and `applicationId` in `app/build.gradle.kts` **and** the `com.deadfest.provider` authority in `AndroidManifest.xml` and `UpdateManager.kt` to match, then register *that* name instead.
3. Nickname and SHA-1 are optional (email login doesn't need SHA-1). Click **Register app**.
4. Click **Download google-services.json**.
5. Put the file at **`app/google-services.json`**, inside the `app` folder, next to `build.gradle.kts`.
6. In Android Studio: **File → Sync Project with Gradle Files**.

> 🔒 **Never commit `google-services.json` to a public repo.** It's already in `.gitignore`. Keep a backup somewhere safe; you can always re-download it from Project settings.

### 5. Firestore Security Rules

In the Firebase console go to **Firestore Database → Rules**, replace everything with the rules below, and click **Publish**. These are sensible starter rules:
- **Signed-in players** can play the game.
- **Admins** (users with `admin: true`) can change global settings.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn() { return request.auth != null; }
    function isAdmin() {
      return signedIn() &&
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.admin == true;
    }

    // Player profiles. Other players' docs must be writable for curses and the
    // Mutual Survival Reward, but nobody except an admin can change the admin flag.
    match /users/{uid} {
      allow read: if signedIn();
      allow create: if signedIn() && request.auth.uid == uid
                    && request.resource.data.get('admin', false) == false;
      allow update: if signedIn() && (isAdmin()
                    || !request.resource.data.diff(resource.data).affectedKeys().hasAny(['admin']));
      allow delete: if isAdmin();
    }

    // Global game state: everyone reads; admins configure.
    // Signed-in players may only bump the global death counter.
    match /gameState/{doc} {
      allow read: if signedIn();
      allow write: if isAdmin()
        || (signedIn() && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['grandTotal']));
    }

    // Live feed
    match /eventLog/{id} {
      allow read, create: if signedIn();
      allow update, delete: if isAdmin();
    }

    // Admin-managed content
    match /archives/{id}      { allow read: if signedIn(); allow write: if isAdmin(); }
    match /scratchPrizes/{id} { allow read: if signedIn(); allow write: if isAdmin(); }
    match /levelRewards/{id}  { allow read: if signedIn(); allow write: if isAdmin(); }

    // Shared images (thumbnail doc + full-size blob doc)
    match /images/{id} {
      allow read: if signedIn();
      allow create: if signedIn() && request.resource.data.uploaderUid == request.auth.uid
                    && request.resource.data.thumb.size() < 100 * 1024;
      allow delete: if signedIn() && (resource.data.uploaderUid == request.auth.uid || isAdmin());
    }
    match /imageBlobs/{id} {
      allow read: if signedIn();
      allow create: if signedIn() && request.resource.data.uploaderUid == request.auth.uid
                    && request.resource.data.data.size() < 900 * 1024;
      allow delete: if signedIn() && (resource.data.uploaderUid == request.auth.uid || isAdmin());
    }

    // Optional / future features
    match /messages/{id} { allow read, create: if signedIn(); allow delete: if isAdmin(); }
    match /votes/{id}    { allow read, update: if signedIn(); allow create, delete: if isAdmin(); }
  }
}
```

> These rules trust the app for game logic (coin amounts and so on), which is fine for a group of friends. A determined player with technical skills could still cheat. For stricter control you'd move the economy into Cloud Functions, which needs the Blaze plan.

### 6. Build & Run

**From Android Studio:** plug in your phone (with USB debugging on) or start an emulator, select the `app` configuration, and press ▶ **Run**.

**From the command line** (from the project folder):
```bash
# Windows
gradlew.bat assembleDebug
# macOS / Linux
./gradlew assembleDebug
```
The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

Run the unit tests (the economy and presence rules):
```bash
./gradlew testDebugUnitTest
```

### 7. Make Yourself Admin & Set Up the Game

1. Open the app → **Sign up** with your email. This creates your document in `users`.
2. In the Firebase console → **Firestore** → `users` → your document → add or edit the field **`admin`** (boolean) = **`true`**.
3. Restart the app. An **Admin** tab appears in the bottom bar.
4. Set up your world from the Admin tab:
   - **Sectors:** add your games or zones (e.g. "World War Z", "Dying Light").
   - **Wheel:** add slices (label, coins, give/take, weight, colour).
   - **Scratch:** tap **LOAD DEFAULTS** for a starter prize pool.
   - **Shop / Global:** tune prices, daily rations, XP and system switches.
   - Make other trusted people admins from **Admin → Players**.

> The `gameState/global` document is created automatically the first time an admin saves a setting. Every value has a safe default.

### 8. Sign & Distribute (Deploy)

Release builds must be **signed**. Phones only accept updates signed with the **same key**, so create one key and guard it forever.

1. **Create a keystore** (once): Android Studio → **Build → Generate Signed App Bundle / APK** → **APK** → **Create new…**
   - Save it somewhere safe *outside* the project, e.g. `deadfest-release.jks`, and **back it up** to cloud storage or a password manager along with the passwords.
   - ⚠️ **Lose the keystore and no one can ever update the app.** Everyone would have to uninstall and reinstall.
2. **Build a signed release:** in the same wizard pick your keystore → **release** → **Create**. The APK lands in `app/release/` (about 4 MB, shrunk with R8).
3. **Share it with your players:**
   - Upload the APK to a **GitHub Release** in *your* repo (Releases → Draft a new release → attach the APK), or Google Drive, Discord, etc.
   - Players download it and allow **"Install unknown apps"** for their browser or file manager when Android asks.
   - Dead-Fest isn't on the Play Store; it's sideloaded.

*(Optional)* Automate signing via `signingConfigs` in `app/build.gradle.kts`, reading the passwords from `local.properties` or environment variables. **Never** commit passwords or the `.jks` file.

> This project already does exactly that — see [docs/SIGNING.md](docs/SIGNING.md). Both the `debug` and `release` variants are signed with the *same* key on purpose, so the two published APKs stay installable over each other. If you host your own copy, make that key your own.

### 9. Publishing Updates (OTA)

Dead-Fest updates itself; players never need to re-download manually.

1. Make your changes, then bump **both** `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Build **both** variants with the **same keystore** — `assembleDebug` (full) and `assembleRelease` (R8). Rename them for download as `Dead-Fest.apk` and `Dead-Fest R8.apk`, matching the names the app shows.
3. Upload both to a release with **direct download links**:
   - **GitHub Release asset:** right-click the asset → copy link. It looks like `https://github.com/<you>/<repo>/releases/download/v2.0.7.2/Dead-Fest%20R8.apk`.
4. In the app: **Admin → Global → Publish Firmware**. Enter the version code, version name, a URL for each build and the notes, and tick **Force** if the update is mandatory.
   - Both builds share one version — they are built together from the same source — so there is one code/name pair and one URL each.
   - Leave a URL blank if you only uploaded one build: anyone who picked the missing build falls back to the other one instead of being told there's no update.
5. Every player gets an **update prompt** for whichever build they picked in **Settings → Update**. The app downloads the APK with a progress bar and opens the installer.

### 10. Editing the App

| Want to change… | Look in |
|-----------------|---------|
| Colours / theme | `app/src/main/java/com/deadfest/ui/theme/` |
| Shared UI pieces (cards, badges, dialogs) | `ui/components/Common.kt`, `ResultDialogs.kt` |
| Screens | `ui/screens/` (Home, Players, Shop, Wheel, Scratch, Images, Progression, Team Progress, Admin, Settings…) |
| Season stats & history | `ui/screens/TeamProgressScreen.kt` (live aggregates + past Dead-Fests) |
| Next Dead-Fest panel | `ui/components/Common.kt` (`NextEventHero`) + fields in `data/model/GlobalState.kt` |
| Season archives (snapshot fields) | `data/model/Archive.kt` |
| Game rules: tax, bankruptcy, XP, odds, daily reset | `domain/Economy.kt` (unit-tested in `app/src/test/`) |
| Online/offline rules | `domain/Presence.kt` |
| Shop items, prices, level rewards | `data/model/ShopItem.kt` (prices can also be changed live in Admin → Shop) |
| Firestore reads/writes & transactions | `data/repository/FirebaseRepository.kt` |
| App logic and state | `ui/viewmodel/MainViewModel.kt` |
| App name | `app/src/main/res/values/strings.xml` |
| App icon | Android Studio → right-click `res` → **New → Image Asset** |
| Version | `app/build.gradle.kts` (`versionCode`, `versionName`) |

**Tips:**
- **Test the release build** (`assembleRelease`) before shipping. R8 shrinking can break reflection; Firestore models are already protected in `app/proguard-rules.pro`.
- **Keep new Firestore models** in `data/model/` so they stay covered by that rule.
- **Turn on Performance Mode** (Settings → System) to check how the app feels on low-end phones.
- **Keep your copy updated** with upstream changes: `git pull`.

### 11. Costs & Limits

The Firebase **Spark (free)** plan is enough for a typical group of 5–50 players:

| Limit (free) | What it means here |
|--------------|-------------------|
| 1 GiB Firestore storage | Roughly 1,500–3,000 shared images plus all game data |
| 50,000 reads / day | Comfortable for dozens of active players (listeners only run while the app is open) |
| 20,000 writes / day | Each death writes once, plus once per other player for the survival reward. Set the reward to 0 in Admin → Shop for very large groups. |
| Auth, FCM | Free |

Watch usage in the Firebase console → **Usage and billing**. Nothing is charged on Spark; the app just gets errors if you hit a daily limit.

### 12. Troubleshooting

| Problem | Fix |
|---------|-----|
| `File google-services.json is missing` | Put it in `app/` (step 4) and re-sync Gradle. |
| `No matching client found for package name` | The package name registered in Firebase must exactly match `applicationId` (`com.deadfest`). |
| "access denied by server rules" in the app | Publish the rules from step 5, and make sure you're signed in. |
| No Admin tab | Set `admin: true` on your user doc (step 7) and restart the app. |
| Gradle / JDK errors | Use the latest Android Studio. It bundles the right JDK (File → Settings → Build Tools → Gradle → Gradle JDK = *jbr*). |
| "App not installed" when updating | The new APK was signed with a different key, or `versionCode` wasn't increased. |
| Update prompt never appears | `latestVersionCode` in Admin → Global must be **higher** than the installed `versionCode`, and the URL must be a direct `.apk` link. |
| Notifications don't arrive | Allow notifications when asked (Android 13+), and disable battery optimisation in Settings → System. |
| Images won't upload | Check the `images` / `imageBlobs` rules (step 5). Very large photos are compressed automatically. |

---

## 🤝 Contributing

This is a private project. Contributions are by invitation only.

## 📄 License

All rights reserved. You may build and run a **private, non-commercial** copy for your own friends and family using your own Firebase project. Redistribution, resale or publishing it to app stores is not permitted without permission.
