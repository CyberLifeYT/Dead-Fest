# 🛠️ UPDATE PROMPT — Dead-Fest v2.0.5 (Build 8)

## Context
Dead-Fest is a private, real-time multiplayer survival app for friends and family.
Stack: Kotlin, Jetpack Compose, Material 3, Firebase (Firestore, Auth, FCM).
Architecture: `MainViewModel` + `FirebaseRepository`, with game rules in `domain/Economy.kt` and shared UI in `ui/components/Common.kt`.
Previous version: **v2.0.4 (Build 7)**. This update: **v2.0.5 (Build 8)**. Release APK: **3.82 MB** (R8 on).

This document describes everything that was added or changed in v2.0.5.

---

## 1️⃣ DAILY RATIONS + RATION UPGRADE
- **Daily Rations card** on the Dashboard. Claimable once per **calendar day**; it resets at local midnight.
- Pays **50 coins**, or **100** with the Ration Upgrade. Both amounts are admin-configurable.
- Tapping **CLAIM** opens a confirmation dialog with the amount and the new balance.
- After claiming, the card shows a live HH:MM:SS countdown to the next drop.
- **Ration Upgrade:** a one-time, permanent Black Market item (default 750). Shows an `ACTIVE` badge once owned.
- **Survivor bonus:** no recorded death since your last claim earns **+25 XP** when you claim.
- **New fields:** `dailyUpgradeOwned`, `lastDailyClaim`, `lastDeathAt`.

## 2️⃣ CASUALTY TAX, BANKRUPTCY & FORCED SPIN PROTOCOL
- **Built on existing fields:** `bankruptcyLevel` (0 = none, 1, 2) and `pendingSpins` were on the player document but had no logic. They're now used. No parallel field was added.

**Casualty Tax**, applied on every recorded death and calculated in a transaction from the live balance:

| Tier | Balance | Loss |
|------|---------|------|
| Elite | > 5,000 | 30% |
| High | > 1,000 | 20% |
| Mid | > 500 | 15% |
| Base | ≤ 500 | flat 25 |

- **Order:** tier tax → Wealth Insulation → Insurance → clamp, so the balance never goes below 0.
- **Death report dialog** shows the tax, the balance before and after, insulation savings, the insurance claim, spins added, any bankruptcy change, XP gained and payouts.

**Bankruptcy state machine:**
- `NONE → LEVEL_1` when the balance hits 0.
- `LEVEL_1 → NONE` once the balance is above 0.
- `LEVEL_1 or LEVEL_2 → LEVEL_2` on any death while bankrupt.
- `LEVEL_2 → NONE` once pending spins = 0 **and** the balance is above 0.
- These transitions apply after every coin change.

**Forced Spin Protocol:**
- Every death adds **+1** pending spin; dying while bankrupt adds **+2** more.
- The Wheel button becomes **"EXECUTE PENALTY SPIN (n)"** with a pulsing ring. Each spin clears one pending spin.
- A red/gold **penalty status card** and a **PENDING SPINS** counter appear on the Dashboard and the Wheel.

**Mutual Survival Reward (new):**
- When anyone dies, every other survivor gets **10 coins**, or 5 if they're bankrupt.
- The amount is admin-configurable, and 0 turns it off.
- It runs in its own transaction, so a failure never blocks the death record.

**Feed:** deaths are logged to the feed under the `DEATH` category.

## 3️⃣ SCRATCH CARDS
- **New Scratch Cards screen:** a Utility Hub tile, plus an entry card in the Black Market.
- **Cost:** default 25 coins.
- **Prize types:** coins, Shield, Lucky Coin, none. An item you already own is cashed in for its shop price.
- **Odds:** the same weighted RNG as the Wheel (`Weighted` in `Economy.kt`), with decimal weights. There's one RNG for the whole app.
- **Foil scratch reveal:** about 1.2 s, instant in Performance Mode.
- **Colour coding:** green = win, red = loss, gold = item.
- The prize pool and odds are shown on screen. Every scratch is logged to the Black Market feed.
- **Admin:** on/off switch, cost, prize create/edit/delete, and **LOAD DEFAULTS** (a 7-prize pool).
- **Firestore:** new `scratchPrizes` collection, plus `scratchCost` and `scratchEnabled` on the global document.

## 4️⃣ LIVE EVENT DASHBOARD (Admin → LIVE, opens first)
- **Panels:**
  - **Online now:** a `lastSeen` heartbeat within 2 minutes. The app sends a heartbeat every 60 s while it's on screen.
  - **Death rate:** deaths per minute over the last 10 minutes, from `eventLog`.
  - **Active overloads** with time remaining.
  - **Pending spins:** the total, plus a per-player list.
  - **Wealth distribution:** a bar chart of players per tax tier.
  - **Bankruptcy tracker:** counts at Level 1 and Level 2.
  - **Insulation ownership:** counts per tier, plus a per-player list.
- **Live data:** snapshot listeners only. The extra eventLog listener only runs while the tab is open.
- **Overload duration:** Sector Overload now takes an optional duration (`overloadEndsAt`).

## 5️⃣ INSURANCE POLICY
- **New Black Market item** (default 150 coins). It absorbs **50%** of the next casualty tax; the percentage is configurable.
- **Order:** applied after Wealth Insulation.
- **One use.** It's consumed on the next taxed death, even if it saves nothing.
- **No stacking:** you can't buy another while one is active. An `ARMED` badge shows on the shop item, the profile and the Dossier.
- **Messages:** "INSURANCE CLAIMED — X% of tax absorbed" in the death report, and "X's Insurance absorbed the casualty tax" in the feed.
- **New field:** `insurance`.

## 6️⃣ WEALTH INSULATION
- **New Insulation tab** in the Black Market:

| Tier | Item | Effect | Cost |
|------|------|--------|------|
| I | Insulated Ledger | -10% | 1,500 |
| II | Offshore Terminal | -25% | 5,000 |
| III | Black Vault Protocol | -50% | 15,000 |
| IV | Ghost Economy | -100% | 50,000 |

- **Bought in order.** Each tier replaces the last, with no refund (the purchase dialog says so).
- **Multiplicative** with the tier rate, and applied before Insurance. Example: 10,000 coins + Tier III + Insurance = **750** lost.
- **Live readout:** a **CURRENT TAX RATE** display plus a **TIER x / IV** tracker, animated unless Performance Mode is on.
- **Tier cards** show `OWNED`, `ACTIVE`, `LOCKED` or `DISABLED`.
- **Limits:** insulation doesn't block bankruptcy or forced spins, even at Tier IV.
- **Admin:** each tier's price and tax cut %, plus a system on/off switch.
- **New field:** `insulationTier` (0–4).

## 7️⃣ PLAYER LEVELS, XP & PRESTIGE
- **XP sources:**
  - Death: 10
  - Survived a day: 25
  - Purchase: 5
  - Wheel spin: 3
  - Scratch card: 3
  - Penalty spin cleared: +5
  
  All are admin-configurable.
- **Curve:** `xpForLevel(n) = round(100 × n^1.5)`, max level 50. All configurable.
- **Where it shows:** an XP bar on the Dashboard profile, the Progression screen and the Dossier. LV and ★ prestige badges appear in the Players list.
- **Level-up:** an in-app dialog, a native notification and a `LEVEL_UP` feed entry.
- **Rewards:** badge colours, avatar frames (Rust, Neon, Toxic, Gold, Inferno) and titles. They come from a built-in catalog, replaced by the `levelRewards` collection if it has documents.
- **New Progression screen** (Utility Hub): the reward track, EQUIP buttons and Prestige.
- **Prestige** at the max level resets level and XP but keeps your unlocks, adds a permanent ★ badge and unlocks the Prestige frame.
- **New fields:** `xp`, `level`, `prestigeCount`, `avatarFrame`.

## 🛡️ ADMIN ADDITIONS
- **New tabs:** LIVE, SHOP and SCRATCH.
- **SHOP:** every item's on/off switch and price, insulation tax cuts, daily ration amounts, survival reward and Insurance %.
- **GLOBAL:** a new XP & LEVELS card.
- **OVERLOAD:** an optional duration in minutes.
- **Player detail:** a Progression & Penalties card with **CLEAR PENALTIES**.
- **Inject Death** now runs the full death pipeline.
- **Coin edits** are now transactional.

## 🔧 TECHNICAL
- **Transactions:** every coin, death, XP, insulation, bankruptcy and pending-spin write runs in a Firestore transaction on live data.
- **Shared rules:** the maths lives in `domain/Economy.kt`, covered by **19 passing unit tests** (`EconomyTest.kt`).
- **Listeners:** 11 shared, lifecycle-aware streams, each backed by one Firestore listener. No duplicates.
- **No login flicker.** No new dependencies.
- **Notifications:** POST_NOTIFICATIONS is now requested once on Android 13+. Notifications were blocked before this.
- **New survivors** start with 250 coins.
- **Season reset** also clears pending spins and bankruptcy. XP, levels and permanent upgrades carry over.
- **Performance Mode** disables every new animation.
- **New shared components:** FramedAvatar, LevelBadge, PrestigeBadge, XpBar, AlertBanner, PenaltyStatusCard, PendingSpinsChip, DashboardPanel. The result dialogs live in `ResultDialogs.kt`.
- **Survival Intel:** a new "Economy Protocols" section.

## 📌 DESIGN DECISIONS
- **Curse deaths aren't taxed.** The full death pipeline runs only on Record Death and admin Inject Death.
- **Daily rations need a tap on CLAIM;** they aren't granted silently at login.
- **"Theme" rewards** are delivered as badge colours.
- **Firestore rules** must allow writes to other players' documents for curses and the survival payout.
