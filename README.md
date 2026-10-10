DeadFest 🎮💀
⚠️ PRIVATE APP – FRIENDS & FAMILY ONLY
This application is strictly for my personal circle of friends and family.
Do not download, install, or attempt to use it.
Unauthorized access will result in an immediate and permanent IP ban, and may lead to further action.
If you’re not personally invited, you are not welcome here.

Kotlin Jetpack Compose Firebase Platform Version APK

DeadFest is a real‑time multiplayer survival companion built for a small, private community.
Record your deaths, survive the casualty tax, spin the Wheel of Fate, scratch for prizes, level up, and see who's online — all synced live for every survivor.

✅ PROJECT STATUS – REBUILD COMPLETE
Survivors, the terminal has risen.

The original source was lost to a failed hard drive, so DeadFest was rebuilt from scratch (v2.0.0) while the event kept running. The rebuild is now complete and receiving regular updates.

✅ All data survived – coins, deaths, titles and history were never affected.
✅ The event never stopped – the current run stays live.
✅ Updates are back – new versions arrive in‑app (update prompt / Settings → System Updates).
🪶 Built for every phone – small APK, Performance Mode, and live data that disconnects in the background to save battery.
🔄 What’s New
v2.0.6 – Presence, Images & Battery
🟢 Live Online/Offline Status – You're online while the app is open and offline the moment you leave. Crashed or lost signal? You drop offline within ~2.5 minutes. Shown on Players ("last seen 5m ago"), the Dashboard, the Admin Live Dashboard and the home‑screen widget.
🖼️ Images Gallery (More → Images) – Share photos with every survivor. Auto‑rotated, compressed, thumbnails for fast scrolling, full‑screen pinch‑to‑zoom, uploader/admin delete, offline uploads queued.
🔋 Battery Optimisation Setting – One‑time first‑launch prompt + Settings → System → Background Activity, so alerts and updates aren't delayed by battery savers.
v2.0.5 – The Economy Update
💰 Daily Rations – Claim free coins once a day (50, or 100 with the permanent Ration Upgrade). Survive a full day for bonus XP.
💀 Casualty Tax – Every death costs coins: 30% (>5,000), 20% (>1,000), 15% (>500), or a flat 25.
📉 Bankruptcy Levels 1 & 2 – Hit 0 coins and payouts are halved; die again while broke and you must clear penalty spins to escape.
🎡 Forced Spin Protocol – Every death adds a pending penalty spin on the Wheel.
🤝 Mutual Survival Reward – When someone dies, everyone else gets paid.
🎟️ Scratch Cards – Instant‑win mini‑game with coins, Shields and Lucky Coins.
🛡️ Insurance Policy – Absorbs 50% of your next casualty tax.
🏦 Wealth Insulation – Four permanent tiers cutting your tax by up to 100%.
⭐ Levels, XP & Prestige – Earn XP from everything; unlock badge colours, avatar frames and titles; prestige at level 50.
📡 Admin Live Event Dashboard – Real‑time online count, death rate, overloads, pending spins, wealth distribution and bankruptcy tracker.
v2.0.4 – Smoother & Stronger
Faster, smaller (3.7 MB) and battery‑friendly; Performance Mode for low‑end phones.
Shop items and Wheel payouts fully working (Shield, Curse, Revive, Lucky Coin).
Dozens of crash and logic fixes; new consistent design across every screen.
(Full release notes for every version are on the Releases page.)

📖 Table of Contents
Features
🎮 Gameplay & Economy
👥 Player & Social Features
🗳️ Community & Competition
🛡️ Admin Override Suite
📡 Infrastructure
Tech Stack
Getting Started (Invited Users Only)
Contributing
License
✨ Features
🎮 Gameplay & Economy
Sectors System – Game zones where every death is recorded to a specific sector, shown on a tactical dashboard.
Casualty Tax – Wealth‑based death penalty calculated from your live balance.
Bankruptcy & Forced Spins – Economic pressure with escalating penalties and a live pending‑spin counter.
Wheel of Fate – Weighted spins (decimal odds for ultra‑rare loot) that really give or take coins; penalty spins when you owe the Wheel.
Scratch Cards – Instant prizes using the same odds engine as the Wheel.
Black Market – Shield, Curse, Double Curse, Revive, Lucky Coin, Insurance, Ration Upgrade, Wealth Insulation tiers and Titles.
Daily Rations – Free daily coins with a live countdown.
Sector Overloads – Admin‑triggered events with multipliers and optional timers.
Runs (Seasons) – Archive a season and reset coins, deaths and penalties; levels and permanent upgrades carry over.
👥 Player & Social Features
Survivor Identity – Display name, avatar, badge colour, avatar frame and earnable Titles.
Levels, XP & Prestige – A long‑term progression track with cosmetic rewards and a permanent prestige badge.
Live Presence – See who's in the app right now and when others were last seen.
Images Gallery – A shared photo wall for the whole community.
Survivor Dossiers – Tap any survivor for deaths per sector, coins, level, gear, bankruptcy and status.
Feed & Alert Filters – Choose which events show in your live ticker and push notifications.
🗳️ Community & Competition
Leaderboards – Wealth (most coins) and Survival Prowess (fewest deaths).
Wasteland Archives – Past seasons with champions and full standings.
Live Ticker – Deaths, purchases, spins, scratches, level‑ups and image shares as they happen.
🛡️ Admin Override Suite
Live Event Dashboard – Online count, deaths/minute, overloads, pending spins, wealth chart, bankruptcy and insulation tracker.
Shop & Economy Control – Enable/disable and price every item, tune insurance, rations, survival reward and insulation tiers.
Scratch & Wheel Editors – Prize pools with decimal weights and live odds.
XP & Levels – Configure every XP source and the level curve.
Player Control – Coins, death injection (full tax pipeline), admin rights, clear penalties.
System Switches – Market, Wheel, Revives, Lockdown, firmware publishing (with forced updates) and broadcasts.
📡 Infrastructure
Firebase Core – Firestore (database + image storage), Auth (Email/Password) and Cloud Messaging (FCM).
Real‑time Everything – Live dashboard, leaderboards, shop, wheel, presence and gallery – no manual refreshes.
Transaction‑Safe Economy – Every coin, death and XP change runs in a Firestore transaction on live data.
Low‑End Friendly – R8‑shrunk 3.8 MB APK, Performance Mode, lifecycle‑aware listeners, no background service.
In‑App OTA Updates – Progress dialog and automatic installer handoff.
Home Screen Widget – Global eliminations, online survivors and overload status.
🧰 Tech Stack
Category	Technology
Platform	Android 7.0+ (minSdk 24, targetSdk 37)
Language	Kotlin 2.2
UI Framework	Jetpack Compose + Material 3
Reactive Layer	Kotlin Coroutines + Flow
Architecture	ViewModel + Repository, pure domain rules
Backend / Database	Firebase Firestore
Image Sharing	Firestore blobs (thumbnail + full image)
Authentication	Firebase Auth (Email/Password)
Push Notifications	Firebase Cloud Messaging (FCM)
Testing	JUnit (economy & presence rules)
Build System	Gradle (Kotlin DSL), R8
🚀 Getting Started (Invited Users Only)
This section is only relevant for the handful of people who have explicit permission to run the app.

Prerequisites
A recent Android Studio (with the bundled JBR / JDK 17+)
A Firebase project with Firestore, Auth and FCM enabled
A physical or emulated Android device running Android 7.0 (API 24) or newer
Firebase Setup
Create a Firebase project at console.firebase.google.com.
Register the Android app with the package name com.deadfest.
Download google-services.json and place it in the app/ directory.
Enable Firestore Database, Authentication (Email/Password) and Cloud Messaging.
Add Firestore rules allowing signed‑in survivors to read/write the images and imageBlobs collections (see the v2.0.6 release notes).
Build
./gradlew assembleRelease
Sign the release APK with the project keystore before distributing it.

🤝 Contributing
This is a private project. Contributions are by invitation only.

📄 License
All rights reserved. Not licensed for public use or redistribution.
