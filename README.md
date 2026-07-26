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
It’s a space to compete, track stats, customise your profile, and survive together – now sharper, faster, and packed with new ways to play.

---

## 🔄 What’s New in v1.4 (The Definitive Apocalypse)

- **Total Visual Modernisation** – Every screen (Dashboard, Shop, Players, Settings) has been redesigned with a sleek, professional dark‑mode aesthetic and a unified component system.
- **Runs System** – Play isolated seasons or events. Coins, deaths, and progress are unique to each Run, so you can start fresh without losing your history.
- **Flash Sales** – Timed discounts appear in the Black Market – grab items while they’re cheap!
- **Voting Box** – Propose and vote on new ideas, games, or features directly inside the app.
- **Dynamic Leaderboards** – Compete to be the Top Survivor by Coin Wealth or Survival Prowess (Least Deaths).
- **Hall of Fame** – Relive past events with historical MVPs and deadliest sectors archived forever.

*(Full release notes for every version are on the [Releases page](https://github.com/CyberLifeYT/Dead-Fest/releases).)*

---

## 📖 Table of Contents

- [Features](#-features)
  - [🎮 Gameplay & Interactive Elements](#-gameplay--interactive-elements)
  - [👥 Player & Social Features](#-player--social-features)
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
- **Virtual Wheel of Fate** – Spin the wheel and let fate decide: coins, status effects, and more. Now with a completely overhauled precise landing engine.
- **Black Market Shop** – Spend your coins on game‑changing items. Timed **Flash Sales** drop discounts in real time – act fast!
- **Sectors System** – Dynamic game zones where you track deaths and progress. Visualised on a tactical dashboard.

### 👥 Player & Social Features

- **Survivor Identity** – Create your unique profile with a display name, bio, avatar, theme colour, and earnable **Titles**.
- **Encrypted Comms** – Real‑time chat with asymmetric message bubbles and optional persistent history.
- **Stats & Connectivity** – Coins, deaths, achievements, and titles are always up to date. Export/import your data if needed.
- **Daily Supply Drops** – Log in after 24 hours to claim a pulsing crate of **+100 Coins** automatically.

### 🗳️ Community & Competition

- **The Voting Box** – Submit and upvote ideas for future updates, mini‑games, or DLC. Your voice shapes the apocalypse.
- **Dynamic Leaderboards** – Two ranking categories: **Top Survivor (Wealth)** and **Survival Prowess (Least Deaths)**.
- **Hall of Fame** – A cinematic timeline of past runs, complete with historical MVPs, deadliest sectors, and per‑event charts.

### 📡 Infrastructure

- **Firebase Core** – Firestore (database), Auth (Google/Email login), Cloud Messaging (FCM), and Remote Config.
- **Real‑time Everything** – Live updates on the dashboard, leaderboards, shop, and wheel – no manual refreshes needed.
- **Modern Android Architecture** – Built with Kotlin, Jetpack Compose, Coroutines, Flow, and Coil for a smooth, responsive experience.

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

📄 License
This project is currently unlicensed. All rights are reserved. Unauthorized use, distribution, or access is strictly prohibited.

<p align="center">Made with ❤️ for the people who matter – using Kotlin, Jetpack Compose, and Firebase</p>
