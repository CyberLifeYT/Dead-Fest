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
It’s a space to play mini‑games, track stats, customise your profile, and hang out together.

---

## 📖 Table of Contents

- [Features](#-features)
  - [🎮 Gameplay & Interactive Elements](#-gameplay--interactive-elements)
  - [👥 Player & Social Features](#-player--social-features)
  - [📡 Infrastructure](#-infrastructure)
- [Tech Stack](#-tech-stack)
- [Getting Started (Invited Users Only)](#-getting-started-invited-users-only)
- [Contributing](#-contributing)
- [License](#-license)

---

## ✨ Features

### 🎮 Gameplay & Interactive Elements

- **Sectors System** – Dynamic game sectors where you track deaths and progress.
- **Flappy Bird Mini‑Game** – A built‑in arcade game with persistent highscore tracking.
- **Virtual Wheel of Fortune** – Spin the wheel and see what fate (coins, status effects, etc.) brings you.
- **Shop System** – Spend your earned coins on items that can shake up the game.

### 👥 Player & Social Features

- **User Identity** – Create a unique profile with your own display name, avatar, and theme colour.
- **Communication** – Chat in real time with other players (history can be turned on when needed).
- **Social Connectivity** – Your coins, deaths, and achievements are always up to date.  
  You can even export/import your data if we ever take the fun somewhere else.

### 📡 Infrastructure

- **Firebase Core** – Firestore (database), Auth (Google/Email login), and Cloud Messaging (instant alerts).
- **Remote Config** – Allows the app to enter a quiet “Maintenance Mode” when needed, without an update.
- **Modern Android Architecture** – Built with Kotlin, Jetpack Compose, Coroutines, and Flow.

---

## 🧰 Tech Stack

| Category             | Technology                            |
|----------------------|---------------------------------------|
| Language             | Kotlin                                |
| UI Framework         | Jetpack Compose                       |
| Reactive Layer       | Kotlin Coroutines + Flow              |
| Image Loading        | Coil                                  |
| Backend / Database   | Firebase Firestore                    |
| Authentication       | Firebase Auth (Google, Email/Password)|
| Push Notifications   | Firebase Cloud Messaging (FCM)        |
| Feature Flags        | Firebase Remote Config                |
| Build System         | Gradle (Kotlin DSL)                   |

---

## 🚀 Getting Started (Invited Users Only)

*This section is only relevant for the handful of people who have explicit permission to run the app.*

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- A Firebase project with Firestore, Auth, FCM, and Remote Config enabled
- A physical or emulated Android device running API 26+

### Firebase Setup

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com).
2. Register the Android app with the correct package name.
3. Download `google-services.json` and place it in the `app/` directory.
4. Enable **Firestore Database**, **Authentication** (Google & Email/Password), and **Cloud Messaging**.
5. Set up **Remote Config** with a `maintenance_mode` flag (optional).

### Build & Run

```bash
git clone https://github.com/yourusername/deadfest.git
cd deadfest
# Open in Android Studio, sync Gradle, and run on a device/emulator
