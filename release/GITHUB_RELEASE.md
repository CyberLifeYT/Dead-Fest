# DeadFest v2.0.5 [STABLE] – Death Tax, Bankruptcy, Scratch Cards & Survivor Levels

> 🧟 **Dying now costs you. Surviving now pays.**

Dead‑Fest v2.0.5 is the biggest economy patch since the rebuild. Every death has a price, broke survivors sink into Bankruptcy, the Black Market gets real wealth-management upgrades, and every action you take now feeds a long-term level and prestige track.

---

## 💰 1. Daily Rations + Ration Upgrade

- **Free coins every day.** A new **Daily Rations** card on the Dashboard pays **50 coins** once per calendar day. Tap **CLAIM** and a confirmation shows the amount and your new balance.
- **Live countdown.** Already claimed? The card counts down to the next drop instead.
- **Ration Upgrade** *(Black Market, one-time, permanent)* raises your rations from **50 → 100 coins** for good. Once owned it shows as **ACTIVE**.
- **Survivor bonus:** claim your rations after a full day without dying and you also earn **+25 XP**.

---

## 💀 2. Casualty Tax, Bankruptcy & the Forced Spin Protocol

**Casualty Tax.** Every recorded death now costs coins, based on your live balance at the moment you die:

| Tier | Balance | Loss |
|------|---------|------|
| Elite | > 5,000 | 30% |
| High | > 1,000 | 20% |
| Mid | > 500 | 15% |
| Base | ≤ 500 | flat 25 coins |

Your balance never drops below 0. After every death a report shows the tax and anything that reduced it.

**Bankruptcy.**
- **Level 1:** your balance hits 0. Your share of survival payouts is halved.
- **Level 2:** you die again while bankrupt. You take +2 extra penalty spins and need to clear every pending spin, with coins above 0, to escape.
- A high-visibility red warning card appears on the **Dashboard** and the **Wheel**.

**Forced Spin Protocol.**
- Every death adds **1 pending spin**.
- When spins are pending, the Wheel button becomes **EXECUTE PENALTY SPIN**. Penalty spins pay out like normal spins and clear one at a time.
- A live **PENDING SPINS** counter sits on the Dashboard and the Wheel.

**Mutual Survival Reward.** When someone dies, every other survivor collects **10 coins** (5 if they're bankrupt).

---

## 🎟️ 3. Scratch Cards

- A brand-new instant-win mini-game in the **Utility Hub**, also linked from the Black Market.
- Pay the scratch cost, watch the foil scratch away, and see what you've won.
- **Prizes:** coins, a free **Shield**, a free **Lucky Coin**, or nothing. If you scratch an item you already have, it's cashed in for its shop price.
- Results are colour-coded: **green** for a win, **red** for a loss, **gold** for a rare item.
- Odds use the same weighted engine as the Wheel of Fate, decimal weights included, and the full prize pool and odds are shown on screen.
- Every scratch appears in the live Black Market feed.

---

## 🛡️ 4. Insurance Policy

- A new Black Market item that absorbs **50%** of your next casualty tax.
- One use, and it's consumed on your next taxed death. You can't stack them, and an **ARMED** badge shows while it's active.
- When it triggers: *"INSURANCE CLAIMED — 50% of tax absorbed"*.
- **Shield and Insurance work together:** the Shield blocks curses, Insurance softens the tax.

---

## 🏦 5. Wealth Insulation

A new **Insulation** tab in the Black Market: permanent upgrades that cut your tax rate.

| Tier | Item | Effect | Cost |
|------|------|--------|------|
| I | Insulated Ledger | -10% tax | 1,500 |
| II | Offshore Terminal | -25% tax | 5,000 |
| III | Black Vault Protocol | -50% tax | 15,000 |
| IV | Ghost Economy | -100% tax | 50,000 |

- **Buy in order.** Each tier replaces the last; there are no refunds and the tiers don't stack.
- **One rule:** Insulation applies first, then Insurance absorbs part of what's left. For example, an Elite survivor with 10,000 coins, Tier III and Insurance loses **750** instead of 3,000.
- The tab shows your **CURRENT TAX RATE** live, plus a **TIER II / IV** progress tracker.
- **Insulation doesn't stop everything:** you can still go bankrupt, and you still spin on death even at Tier IV.

---

## ⭐ 6. Survivor Levels, XP & Prestige

- **Earn XP** for recording a death (+10), surviving a full day (+25), Black Market purchases (+5), Wheel spins (+3), Scratch Cards (+3) and clearing a penalty spin (+5 bonus).
- **Track it:** an XP bar sits on your profile card and on every survivor's Dossier.
- **Level-ups:** a celebration, a native notification and a feed entry.
- **Rewards:** level-ups unlock **badge colours**, **avatar frames** (Rust, Neon, Toxic, Gold, Inferno) and **titles**. Equip them from **Utility Hub → Progression**.
- **Prestige:** reach level 50 and you can Prestige. Your level and XP reset, but you keep your unlocks and gain a permanent ★ prestige badge and the Prestige frame.

---

## 📡 7. Admin: Live Event Dashboard & New Controls

**New LIVE tab** (opens first), updating in real time:
- Online survivors
- Deaths per minute
- Active overloads with time remaining
- Pending spins per survivor
- Wealth distribution chart by tax tier
- Bankruptcy tracker
- Insulation ownership

**New SHOP tab:**
- Enable or disable any item and set its price.
- Adjust each insulation tier's tax cut.
- Set Insurance protection %, daily ration amounts and the survival reward.
- Turn the whole Insulation system on or off.

**New SCRATCH tab:** global on/off switch, scratch cost, and add / edit / delete prizes with decimal odds. One tap loads a default prize pool.

**Elsewhere:**
- **XP & Levels** settings in the Global tab: every XP value, the level curve and the max level.
- **Sector Overload** can now run for a set number of minutes.
- **Player view** shows level, bankruptcy and pending spins, with a **CLEAR PENALTIES** button.

---

## 🔧 8. Under the Hood

- Every change to coins, deaths, XP and insulation runs inside a Firestore transaction on live data. Two rapid taps can't double-charge or read a stale balance.
- No duplicate listeners. New data streams only connect while their screen is open.
- **Android 13+ notifications fixed:** the app now asks for notification permission once. Without it, push broadcasts never showed on newer phones.
- **New survivors start with 250 coins,** the same as a season reset, so nobody begins life bankrupt.
- **Season reset** now also clears pending spins and bankruptcy. Levels, XP, prestige and permanent upgrades carry over.
- Performance Mode turns off every new animation (scratch reveal, level-up, penalty pulse, tier tracker).
- APK stays tiny: **3.8 MB**.
- Version **2.0.5 (Build 8)**.

---

## 📲 How to Update

- **In‑app:** you'll get an update prompt, or go to Settings → System Updates
- **Manual:** download the v2.0.5 APK from the assets below

---

⚠️ **Not invited? Unauthorised access will result in an immediate and permanent IP ban.**
This app is for friends and family only.

---

**[VERSION 2.0.5 DEPLOYED]**
**[ECONOMY STATUS: TAXED]**
**[BANKRUPTCY PROTOCOL: ARMED]**
**[PROGRESSION: ONLINE]**
**[DATA STATUS: INTACT — EVENT STILL RUNNING]**

*Death was always permanent. Now it's expensive.* 💀💰
