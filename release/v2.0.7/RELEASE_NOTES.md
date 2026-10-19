# Dead-Fest v2.0.7 (Build 10)

**Platform:** Android, minSdk 24 (Android 7.0) to targetSdk 37.
**Stack:** Kotlin, Jetpack Compose, Material 3, Firebase (Auth, Firestore, Cloud Messaging).
**Release APK:** 3.86 MB (R8 on). No new dependencies.

---

## Next Dead-Fest (More tab)

A new headline panel at the top of the **More** tab that tells everyone what is coming.

- **Season name** - e.g. **"Dead-Fest Season 2"**, shown big and clear.
- **Live countdown** - DAYS / HRS / MIN / SEC tiles that tick down to the start time.
  Long countdowns refresh every 15 seconds; only the final day counts down every second,
  so it stays cheap on low-end phones.
- **Game line-up** - every game queued for the next event, in play order, as scrollable chips.
- **Start time** - the exact date and time, shown in the phone's own format.
- **Blurb** - optional admin notes (rules, theme, prizes).
- **States handled** - before the date is set it reads "Start time hasn't been locked in yet",
  and once the countdown hits zero it switches to **STARTED**.

Everything above is set in **Admin -> Event** (see below).

## Team Progress (More tab)

A new screen that shows the state of the whole team, past and present.

- **Current Dead-Fest** - live aggregate of the season running right now: survivors, total
  deaths, coins in play, average and top level, prestiges, bankruptcies, pending spins and
  insulated survivors. The season name is editable in Admin -> Event.
- **Eliminations by sector** - a bar per sector, biggest first.
- **Current standings** - the three deadliest and the three richest survivors right now.
- **Past Dead-Fests** - every archived season, newest first, with its deaths, survivor count
  and champion. Tap one to open the full season: summary stats, eliminations by sector and
  the complete final standings.
- **Reads existing data** - the live numbers are aggregated from the same player documents
  the rest of the app already reads. No new collections, no extra Firestore writes, no extra
  cost on the free plan.

## Admin -> Event

One place to run the whole season cycle.

- Rename the **current season** (shown on Team Progress).
- Turn the **Next Dead-Fest** panel on or off.
- Set the next **season name**, **start date & time** and an optional **blurb**.
- Build the **game line-up**: add from your existing sectors with one tap, type a custom game,
  reorder with up/down, or remove.
- **Save** publishes it to every survivor instantly.

## Richer season archives

Archiving a season now captures far more than coins and deaths.

- New snapshots also store **level, XP, prestige count, title, avatar, badge colour,
  insulation tier, bankruptcy level and pending spins**.
- Each archive records the **sector roster**, the **global eliminations counter** and the
  **current season name** at the moment it closed.
- **Old archives still work.** They are read exactly as before and simply report the extra
  stats as "not recorded" (a short note says so on the season screen).
- The **Archives** screen now shows level badges and titles for seasons archived from
  v2.0.7 onwards.

## Layout & UI/UX

- **More tab reorganised** into clear sections - *Season & History*, *Profile & Rewards*,
  *Intel* - instead of one flat grid of tiles.
- **Better hub tiles** with a bigger icon, a one-line description and a "NEW" badge. The
  grid is adaptive to the screen width.
- **Bottom bar no longer clips.** With six or seven tabs (admins see seven), small phones
  used to cut the labels off. Unselected tabs now collapse to their icon only, and the
  selected tab keeps its label. Plain icons are also slightly larger and easier to hit.
- **Admins get a nudge:** until a Next Dead-Fest is configured, the More tab shows a
  "Set up Next Dead-Fest" card that jumps straight to Admin.
- **Low-end first:** no new dependencies, no new animations, countdown ticking is throttled
  outside the final day, and the new screens use the same lightweight cards as the rest of
  the app.

## Data & rules

- Everything new lives on the **existing** documents:
  - `gameState/global` gains `currentSeasonName`, `nextEventEnabled`, `nextEventName`,
    `nextEventStartsAt`, `nextEventGames` and `nextEventNotes`.
  - `archives` documents gain the richer snapshot fields.
- **No Firestore rule changes are required.** `gameState` is already readable by any signed-in
  survivor and writable by admins; `archives` is already admin-writable.
- Every new field has a safe default, so an existing database upgrades itself the first time
  an admin saves.

---

## Heads-up: signing

The APK attached to this release is **unsigned** (`DeadFest-v2.0.7-unsigned.apk`), exactly
like the files kept in `release/` for previous versions. An unsigned APK **cannot be
installed** and **cannot be used for an in-app update** - Android rejects it at install time.

To ship this build:

1. Build a **signed** release with the same keystore you have always used
   (see README -> *Sign & Distribute*), **or** sign this one with `apksigner`.
2. Upload the signed APK wherever your OTA download links point.
3. In the app: **Admin -> Global -> Publish Firmware** - version name `2.0.7`,
   version code `10`, and the direct APK URL.

Phones only accept updates signed with the **same key** as the installed build, so the
keystore matters more than the APK.