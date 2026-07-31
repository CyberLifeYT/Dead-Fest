# DeadFest 🎮💀

> ⚠️ **PRIVATE APP – FRIENDS & FAMILY ONLY**  
> This application is strictly for my personal circle of friends and family.  
> **Do not download, install, or attempt to use it.**  
> Unauthorized access will result in an **immediate and permanent IP ban**, and may lead to further action.  
> If you’re not personally invited, you are not welcome here.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase)](https://firebase.google.com)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android)](https://developer.android.com)

**DeadFest** is a real‑time multiplayer mobile experience built for a small, private community.  
It’s a space to compete, track stats, customise your profile, and survive together – now with a fully self‑hosted media hub, live Telegram sync, and deep community tools.

---

## 🔄 What’s New in v1.5

### 💎 Visual Revolution
- **High‑End UI Overhaul** – A total redesign with a sleek, high‑contrast design language, layered depth, and glassmorphism accents.
- **Unified Design System** – Every button, card, and header now uses a custom DeadFest component library for 100% consistency.
- **Modern Typography** – Refined SansSerif hierarchy for a crisp, industrial feel.

### 🎬 Media & Utility Hub
- **"More" Terminal Tab** – A new navigation hub consolidating the Voting Box and Media Feed.
- **Live Telegram Sync** – Full integration with the private *Dead‑Fest | Media* channel.
  - **In‑App Media Player** – Watch videos and view photos directly inside the app via Media3 (ExoPlayer). No external apps needed.
  - **Smart Detection** – Automatically sorts content into Images and Videos by scanning message types and captions.
- **Portable Termux Bridge** – A specialised Python bridge runs 24/7 on your phone via Termux, making your media feed fully self‑hosted without a PC.
- **Bridge Cache Acceleration** – Caches media to your phone after the first view; subsequent loads are instant.
- **Purge Cache Tool** – Remotely clear the bridge’s storage from Settings.

### 🛠️ Admin & System Operations
- **Telegram Auth Terminal** – Manage MTProto sessions directly in the app (request login codes, generate permanent session strings).
- **Advanced Flash Sales** – Set exact duration and discount % (e.g., 20%–90% off); the Black Market updates instantly.
- **Full Data Restoration** – Restore your entire app state (Users, Sectors, Settings) from a JSON backup file.
- **Enhanced Security** – Custom Network Security Policy for secure local communication with the Termux bridge.

### 📡 v1.5.1 Patch – Network Bridge Auto‑Sync
- **Network Bridge Config** – New section in Admin > Telegram.
- **IP Broadcasting** – Enter your phone’s local IP and tap **BROADCAST IP**.
- **Automatic Sync** – All users instantly switch to the new bridge IP via Firestore – no restarts or updates needed.

*(Full release notes for every version are on the [Releases page](https://github.com/CyberLifeYT/Dead-Fest/releases).)*

---

## 📖 Table of Contents

- [Features](#-features)
  - [🎮 Gameplay & Interactive Elements](#-gameplay--interactive-elements)
  - [👥 Player & Social Features](#-player--social-features)
  - [🎬 Media & Self-Hosting](#-media--self-hosting)
  - [🗳️ Community & Competition](#-community--competition)
  - [📡 Infrastructure](#-infrastructure)
- [Tech Stack](#-tech-stack)
- [Getting Started (Invited Users Only)](#-getting-started-invited-users-only)
- [Contributing](#-contributing)
- [License](#-license)

---

## ✨ Features

### 🎮 Gameplay & Interactive Elements

- **Runs (Seasons)** – Take part in isolated events where your coins, deaths, and progress stay separate. Perfect for fresh starts without resetting your profile.
- **Flappy Dead Mini‑Game** – The arcade is live! Dodge pipes and chase high scores directly inside DeadFest.
- **Virtual Wheel of Fate** – Spin the wheel and let fate decide: coins, status effects, and more. Now with a completely overhauled precise landing engine.
- **Black Market Shop** – Spend your coins on game‑changing items. Timed **Flash Sales** drop discounts in real time – act fast!
- **Sectors System** – Dynamic game zones where you track deaths and progress. Visualised on a tactical dashboard.

### 👥 Player & Social Features

- **Survivor Identity** – Create your unique profile with a display name, bio, avatar, theme colour, and earnable **Titles**.
- **Encrypted Comms** – Real‑time chat with asymmetric message bubbles and optional persistent history.
- **Stats & Connectivity** – Coins, deaths, achievements, and titles are always up to date. Export/import your data if needed.
- **Daily Supply Drops** – Log in after 24 hours to claim a pulsing crate of **+100 Coins** automatically.

### 🎬 Media & Self-Hosting

- **Live Telegram Sync** – Your private media channel streams photos and videos straight into the app.
- **In‑App Playback** – Watch everything with the built‑in Media3 (ExoPlayer) viewer – no switching apps.
- **Termux Bridge** – A self‑hosted Python server runs on your phone, serving media to everyone without a PC.
- **Instant Cache** – Media is saved locally after the first view for lightning‑fast reloads.
- **Network Bridge Auto‑Sync** – Admins can broadcast their bridge IP; all users instantly connect to the correct address.

### 🗳️ Community & Competition

- **The Voting Box** – Submit and upvote ideas for future updates, mini‑games, or DLC. Your voice shapes the apocalypse.
- **Dynamic Leaderboards** – Two ranking categories: **Top Survivor (Wealth)** and **Survival Prowess (Least Deaths)**.
- **Hall of Fame** – A cinematic timeline of past runs, complete with historical MVPs, deadliest sectors, and per‑event charts.

### 📡 Infrastructure

- **Firebase Core** – Firestore (database), Auth (Google/Email login), Cloud Messaging (FCM), and Remote Config.
- **Real‑time Everything** – Live updates on the dashboard, leaderboards, shop, wheel, and media feed – no manual refreshes needed.
- **Admin Over‑the‑Air Control** – Broadcast new bridge IPs, toggle features, and push updates without user intervention.
- **Modern Android Architecture** – Built with Kotlin, Jetpack Compose, Coroutines, Flow, Coil, and Media3.

---

## 🧰 Tech Stack

| Category             | Technology                            |
|----------------------|---------------------------------------|
| Language             | Kotlin                                |
| UI Framework         | Jetpack Compose                       |
| Reactive Layer       | Kotlin Coroutines + Flow              |
| Image Loading        | Coil                                  |
| Media Playback       | Media3 (ExoPlayer)                    |
| Backend / Database   | Firebase Firestore                    |
| Authentication       | Firebase Auth (Google, Email/Password)|
| Push Notifications   | Firebase Cloud Messaging (FCM)        |
| Feature Flags        | Firebase Remote Config                |
| Self‑Hosted Bridge   | Python + Termux                       |
| Build System         | Gradle (Kotlin DSL)                   |

---

## 🚀 Getting Started (Invited Users Only)

*This section is only relevant for the handful of people who have explicit permission to run the app.*

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- A Firebase project with Firestore, Auth, FCM, and Remote Config enabled
- A physical or emulated Android device running API 26+
- (For media hosting) Termux installed on the admin’s phone with the Python bridge script

### Firebase Setup

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com).
2. Register the Android app with the correct package name.
3. Download `google-services.json` and place it in the `app/` directory.
4. Enable **Firestore Database**, **Authentication** (Google & Email/Password), and **Cloud Messaging**.
5. Set up **Remote Config** with a `maintenance_mode` flag (optional).
