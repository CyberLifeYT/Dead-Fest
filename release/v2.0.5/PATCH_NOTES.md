# Dead-Fest v2.0.5 (Build 8): Full Patch Notes

Previous version: v2.0.4 (Build 7). Release APK: 3.82 MB (R8 on).

---

## 1. Daily Rations + Ration Upgrade
- New **Daily Rations** card on the Dashboard. Claimable once per **calendar day** (resets at local midnight).
- Base grant **50 coins**, or **100** with the Ration Upgrade. Both are admin-configurable.
- Claiming shows a confirmation dialog with the amount and new balance.
- After claiming, the card shows a live HH:MM:SS countdown to the next drop.
- New one-time Black Market item **RATION UPGRADE** (default 750 coins). Permanent, and shows an ACTIVE badge once owned.
- **Survived-a-day bonus:** if you haven't recorded a death since your previous claim, claiming also grants survival XP (default 25).

## 2. Casualty Tax, Bankruptcy & Forced Spin Protocol
- **Audit result:** `bankruptcyLevel` and `pendingSpins` already existed on player documents but had no logic behind them. They're now used as the bankruptcy state (0 = none, 1 = Level 1, 2 = Level 2) and the pending-spin counter. No parallel `bankruptcyState` field was added.
- **Casualty tax on every recorded death,** calculated in the same transaction from the live balance:
  - Elite (> 5,000): 30%
  - High (> 1,000): 20%
  - Mid (> 500): 15%
  - Base (≤ 500): flat 25
- **Tax order:** tier tax → Wealth Insulation (multiplicative) → Insurance absorbs a % of the remainder → clamp so the balance never goes below 0.
- **Death report dialog** shows the tax, the balance before and after, insulation savings, the insurance claim, spins added, any bankruptcy change, XP gained and the Mutual Survival Reward payout.
- **Bankruptcy state machine:**
  - NONE → LEVEL_1 when the balance hits 0.
  - LEVEL_1 → NONE as soon as the balance is above 0.
  - LEVEL_1 or LEVEL_2 → LEVEL_2 on any death while bankrupt.
  - LEVEL_2 → NONE once pending spins = 0 **and** balance > 0.
  - These transitions apply after every coin change: deaths, purchases, wheel, scratch, payouts, daily rations and admin edits.
- **Forced Spin Protocol:**
  - +1 pending spin per death, plus 2 more if bankrupt.
  - The Wheel button becomes **EXECUTE PENALTY SPIN (n)** with a pulsing ring (static in Performance Mode).
  - Each spin while spins are pending clears one.
- **Penalty status card** (red for bankruptcy, gold for pending spins only) on the Dashboard and the Wheel, with a PENDING SPINS counter. Tapping it on the Dashboard opens the Wheel.
- **Mutual Survival Reward (new):** the spec described this as existing, but it wasn't in the code.
  - When a survivor dies, every other survivor gets the reward (default 10, admin-configurable, 0 = off). Bankrupt survivors get half.
  - It runs in its own transaction after the death is recorded, so a failure never blocks the death record.
- Deaths are logged to the live feed (`DEATH` category) with the tax amount and any bankruptcy change.

## 3. Scratch Cards
- New **Scratch Cards** screen (Utility Hub tile, plus an entry card in the Black Market's All and Economy tabs).
- Pay the scratch cost (default 25) to scratch a card.
- **Prize types:** `coins`, `shield`, `lucky_coin`, `none`. An item you already own converts to coins at its shop price.
- **Odds** come from the shared weighted picker (`domain/Economy.kt → Weighted`), which the Wheel now uses too. There's one RNG, and decimal weights work.
- **Reveal:** an offscreen-layer foil with a zig-zag scratch stroke, 1.2 s, instant in Performance Mode.
- **Colour coding:** green = win (won ≥ cost), red = loss, gold = item.
- The prize pool and odds are shown on screen. Every scratch is logged to the Black Market feed.
- **Firestore:** new `scratchPrizes` collection (label, weight, value, type, color, enabled), plus `scratchCost` and `scratchEnabled` on the global document.

## 4. Live Event Dashboard (Admin → LIVE, opens first)
- **Online now:** survivors with a heartbeat (`playerData.lastSeen`) in the last 2 minutes. The app now sends a heartbeat once a minute while it's on screen.
- **Death rate:** deaths per minute over the last 10 minutes, from `DEATH` entries in `eventLog` (the app's activity log).
- **Active overloads** with time remaining. Overload now takes an optional duration (`overloadEndsAt`).
- **Pending spins:** the total, plus a per-survivor list (tap to open that player).
- **Wealth distribution:** a bar chart of survivors per tax tier.
- **Bankruptcy tracker:** counts at Level 1 and Level 2.
- **Wealth Insulation ownership:** counts per tier, plus a per-survivor list.
- **Data sources:** existing users and global-state listeners, plus one windowed `eventLog` listener (last 15 minutes) that only runs while the tab is open. No polling: the only timer re-evaluates the time windows every 10 s.

## 5. Insurance Policy
- New Black Market item **INSURANCE POLICY** (default 150 coins).
- Absorbs the configured % (default 50%) of the next casualty tax, applied after Wealth Insulation.
- **One use.** It's consumed on the next death that triggers a tax, even if it saves nothing (for example, at 0 coins). It isn't consumed if Tier IV insulation brings the tax to 0.
- **ARMED** badge on the shop item, the profile card and the Dossier.
- Shows "INSURANCE CLAIMED — X% of tax absorbed" in the death report, and logs "X's Insurance absorbed the casualty tax" to the feed.

## 6. Wealth Insulation
- New **Insulation** tab in the Black Market, with four permanent tiers:
  - I: Insulated Ledger, -10%, 1,500 coins
  - II: Offshore Terminal, -25%, 5,000 coins
  - III: Black Vault Protocol, -50%, 15,000 coins
  - IV: Ghost Economy, -100%, 50,000 coins
- **Bought strictly in order.** The next tier replaces the current one: lower tiers are consumed, with no refund, and the purchase dialog says so.
- The tab shows a **CURRENT TAX RATE** readout (your bracket, your effective rate, plus Insurance if armed) and a TIER x / IV tracker that animates on upgrade (instant in Performance Mode).
- Each tier card shows OWNED / ACTIVE / LOCKED / DISABLED and the rate you'd pay at your current balance.
- **What it doesn't protect against:** you can still go bankrupt, and you still get forced spins even at Tier IV.
- **Feed and messages:** "X upgraded their Wealth Insulation to Tier II". On purchase: "WEALTH INSULATION UPGRADED: Tier II active". On death: "TAX REDUCED: Insulation Tier II saved you N coins".
- **Player document:** `insulationTier` (0–4).

## 7. Player Levels, XP & Prestige
- **XP sources** (all admin-configurable):
  - Death: 10
  - Survived a day: 25
  - Purchase: 5
  - Wheel spin: 3
  - Scratch card: 3
  - Penalty spin cleared: +5 bonus on top of the spin XP
- **Curve:** `xpForLevel(n) = round(base × n^exponent)`, the XP needed to go from level n to n+1. Defaults: base 100, exponent 1.5, max level 50. All configurable.
- An **XP bar** on the Dashboard profile card, the Progression screen and every survivor's Dossier (the Players detail dialog). **LV** and ★ prestige badges appear in the Players list.
- **Level-up:** an in-app dialog (bouncy scale-in, static in Performance Mode), a native notification and a `LEVEL_UP` feed entry.
- **Rewards:** badge colours, avatar frames and titles, from a built-in catalog (`LevelRewardCatalog` in `ShopItem.kt`). If the `levelRewards` Firestore collection has documents, it replaces the catalog. Document fields: `level`, `rewardId`, `type` (color / frame / title), `label`, `value`.
- **New Progression screen** (Utility Hub): level, XP sources, the reward track with EQUIP buttons, frame removal, and Prestige.
- **Prestige** at max level: level and XP reset to 1 / 0, `prestigeCount` goes up by 1, every unlock is kept, and the Prestige frame unlocks.
- **Player document:** `xp`, `level`, `prestigeCount`, `avatarFrame`.

## Admin Additions
- **New tabs:** LIVE (dashboard), SHOP and SCRATCH.
- **SHOP tab:**
  - Every item: enabled switch and price.
  - Insulation tiers: tax cut %.
  - Economy settings: daily rations (base and upgraded), Mutual Survival Reward, Insurance %.
  - Wealth Insulation system on/off.
  - Stored under `shopConfig` on the global document.
- **SCRATCH tab:** system on/off, cost, and prize create / edit / delete. LOAD DEFAULTS seeds a 7-prize pool.
- **GLOBAL tab:** new XP & LEVELS card (`xpConfig` on the global document).
- **OVERLOAD tab:** optional duration in minutes.
- **Player detail:**
  - New PROGRESSION & PENALTIES card (level, XP, prestige, insulation, ration upgrade, bankruptcy, pending spins) with CLEAR PENALTIES.
  - INJECT DEATH now runs the full death pipeline (tax, spins, bankruptcy, XP, payout). Remove Death just lowers the count, with no refund.
- **Admin coin edits** (±10/100 and set exact) now run as transactions, so bankruptcy follows the balance.

## Technical
- **Transactions:** every write touching coins, deaths, XP, insulation tier, bankruptcy or pending spins runs in a Firestore transaction on live data. The one exception is season reset and archive restore, which stay atomic batches of fixed values.
- **Shared game rules:** all the maths lives in a new file, `domain/Economy.kt` (tax, bankruptcy, death rules, leveling, daily clock, weighted RNG). The UI readouts and the transactions use the same functions.
- **Unit tests:** `app/src/test/java/com/deadfest/EconomyTest.kt`, 19 tests, all passing.
- **Listener audit:** 11 shared, lifecycle-aware streams, each backed by exactly one Firestore listener that only runs while a screen is collecting it. New streams (scratch prizes, level rewards, dashboard events) attach only on their screens. No other snapshot listeners anywhere.
- **No login flicker:** the auth check still lives in `MainViewModel` before any UI renders.
- **Notification permission:** POST_NOTIFICATIONS is requested once on Android 13+. Previously it was never requested, so notifications were silently blocked on newer phones.
- **New survivors** start with 250 coins.
- **Season reset** also clears pending spins and bankruptcy. XP, levels, prestige, Ration Upgrade and Insulation carry over.
- **Performance Mode** disables the new animations: scratch reveal, level-up, penalty pulse, tier tracker and XP bar fill.
- **New shared components** in `ui/components/Common.kt`: FramedAvatar, LevelBadge, PrestigeBadge, XpBar, AlertBanner, PenaltyStatusCard, PendingSpinsChip, DashboardPanel, parseThemeColor. The result dialogs are in `ui/components/ResultDialogs.kt`.
- **Survival Intel** has a new "Economy Protocols" section explaining the rules to players.
- **Dependencies:** none added.

## New Firestore Fields

**Player document (`users/{uid}.playerData`):**
- `dailyUpgradeOwned`
- `lastDailyClaim`
- `lastDeathAt`
- `insurance`
- `insulationTier`
- `xp`
- `level`
- `prestigeCount`
- `avatarFrame`
- `lastSeen` (server timestamp)

**Global document (`gameState/global`):**
- `dailyBase`
- `dailyUpgraded`
- `survivalReward`
- `insurancePct`
- `insulationEnabled`
- `scratchEnabled`
- `scratchCost`
- `shopConfig`
- `xpConfig`
- `overloadEndsAt`

**New collections:**
- `scratchPrizes`
- `levelRewards` (optional)

All fields have safe defaults, so nothing needs seeding for the app to work.

## Design Decisions to Review
- **Curse deaths aren't taxed.** Curses add deaths to the target as before, with no tax, forced spin or payout. The tax pipeline runs on Record Death and admin Inject Death only.
- **Daily rations are claimed with a tap,** not granted silently on login, so the confirmation dialog makes sense.
- **"Theme" rewards** are delivered as badge colours. The app has no theme system yet.
- **Firestore rules:** the Mutual Survival Reward and curses write to *other* players' documents. If your rules forbid that, the death still records, and the report shows "Mutual Survival Reward couldn't be paid out".
