# DeadFest 🎮💀

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase)](https://firebase.google.com)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android)](https://developer.android.com)

**DeadFest** is a real‑time multiplayer, Firebase‑powered mobile application built for community‑driven games and interactive experiences.  
A central administrative backend allows full control over global game states, player stats, and live content – all in real time.

---

## 📖 Table of Contents

- [Features](#-features)
  - [🎮 Gameplay & Interactive Elements](#-gameplay--interactive-elements)
  - [👥 Player & Social Features](#-player--social-features)
  - [🛠️ Administrative Control](#️-administrative-control)
  - [📡 Infrastructure](#-infrastructure)
- [Tech Stack](#-tech-stack)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Firebase Setup](#firebase-setup)
  - [Build & Run](#build--run)
- [Contributing](#-contributing)
- [License](#-license)

---

## ✨ Features

### 🎮 Gameplay & Interactive Elements

- **Sectors System** – Dynamic, admin‑managed game sectors where players track deaths and progress.
- **Flappy Bird Mini‑Game** – Built‑in arcade game with persistent highscore tracking via Firebase.
- **Virtual Wheel of Fortune** – Interactive spin‑the‑wheel mechanic with configurable outcomes (coin rewards/penalties, status effects).
- **Shop System** – Digital economy; players spend coins on items that affect gameplay or other players.

### 👥 Player & Social Features

- **User Identity** – Customisable profiles: unique display names, avatar selection, theme colour choices.
- **Communication** – Integrated real‑time messaging with optional persistent chat history.
- **Social Connectivity** – Tracks coins, deaths, achievements, and other stats per user. Import/export user data for cross‑platform event management.

### 🛠️ Administrative Control

- **Dynamic Admin Dashboard** – Dedicated UI to manage the entire game ecosystem in real time.
  - *Sector Management* – Add/remove, lock/unlock, and reorder game sectors.
  - *Player Administration* – Manually adjust stats, add/remove deaths, and reward users.
  - *Global Event Controls* – Activate temporary multipliers (e.g., x2 XP/coins for a duration).
  - *Broadcasts* – Send global push notifications to all users.
- **Auto‑Updater System** – Admins publish new APK versions (with release notes and forced‑update toggles); clients automatically download and show an update overlay.

### 📡 Infrastructure

- **Firebase Core** – Firestore (database), Auth (Google/Email login), Cloud Messaging (FCM for real‑time alerts), Remote Config (instant feature toggles like Maintenance Mode).
- **Security** – Encrypted connectivity, admin‑privilege verification, internal caching for seamless updates.
- **Modern Android Architecture** – Built entirely with Kotlin, Jetpack Compose, Coroutines, and Flow.

---

## 🧰 Tech Stack

| Category             | Technology                                                            |
|----------------------|-----------------------------------------------------------------------|
| Language             | Kotlin                                                                |
| UI Framework         | Jetpack Compose                                                       |
| Reactive Layer       | Kotlin Coroutines + Flow                                              |
| Image Loading        | Coil                                                                  |
| Backend / Database   | Firebase Firestore                                                    |
| Authentication       | Firebase Auth (Google, Email/Password)                                |
| Push Notifications   | Firebase Cloud Messaging (FCM)                                        |
| Feature Flags        | Firebase Remote Config                                                |
| Build System         | Gradle (Kotlin DSL)                                                   |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- A Firebase project with Firestore, Auth, FCM, and Remote Config enabled
- A physical or emulated Android device running API 26+

### Firebase Setup

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com).
2. Register your Android app with the package name `com.deadfest.app` (or your chosen package).
3. Download the `google-services.json` file and place it in the `app/` module directory.
4. Enable **Firestore Database**, **Authentication** (Google & Email/Password), and **Cloud Messaging**.
5. Set up **Remote Config** with your desired feature flags (e.g., `maintenance_mode`, `update_required`).

### Build & Run

```bash
# Clone the repository
git clone https://github.com/yourusername/deadfest.git
cd deadfest

# Open in Android Studio, sync Gradle, and run on a device/emulator
