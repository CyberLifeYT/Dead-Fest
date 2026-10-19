# Dead-Fest v2.0.7.1 (Build 11)

**Platform:** Android, minSdk 24 (Android 7.0) to targetSdk 37.
**Stack:** Kotlin, Jetpack Compose, Material 3, Firebase (Auth, Firestore, Cloud Messaging).
**Release APK:** 3.74 MB / 3,917,372 bytes, sha256 `b5f604d4c62734d132b5725a7cdc11f8ec050af0ec9d3e9f49d466804abe3455`.
R8 on, no new dependencies. That is **131 KB smaller** than v2.0.7 despite the redesign.

This is a **hotfix + interface release**. It fixes the Dead-Fest countdown so it actually ticks,
rebuilds every screen on one design system, and replaces the last of the stock Android Studio
artwork with Dead-Fest branding.

**Nothing is reset by this update.** No storage key, schema, session or auth path changed, so
upgrading from v2.0.7 keeps you signed in with your coins, level, titles and history intact.

---

## 1. The countdown actually counts down

The Next Dead-Fest countdown (More tab) previously did not tick. It now does.

- **Live to the second.** DAYS / HRS / MIN / SEC update once per second while the panel is on screen.
- **Performance Mode aware.** With Performance Mode on it updates once every 30 seconds and the
  SEC tile is hidden — a seconds digit that only changed every 30 s would be a lie, whereas
  days/hours/minutes stay correct at that rate. Nobody sees a value that is wrong.
- **Correct after backgrounding.** The timer is bound to the screen's lifecycle. Put the phone in
  your pocket for an hour, come back, and the first thing that happens is a re-read of the clock —
  so it snaps to the right value instead of resuming from a stale one.
- **Stops when there is nothing to count.** No target date configured, or the start time already
  passed: no timer is scheduled at all, and the panel shows the correct final state (`TBA` or
  `STARTED`).
- **Bounded cost.** Only the two small composables that actually display the numbers read the
  clock. The season name, game chips and formatted start time are hoisted out of the ticking
  subtree, so a one-second tick cannot recompose the whole panel.
- **Timezone-safe.** It works purely in epoch milliseconds and formats with the device locale, so
  it is correct in every timezone and across DST.
- **No leaks.** `LaunchedEffect` cancels the coroutine when the panel leaves the screen, so tab
  switching cannot accumulate timers.

### A real bug fixed on the Command Center

The Daily Rations card ran a one-second timer that **never stopped**, even after the reward became
claimable and even while the app was in the background. That is a genuine battery and CPU drain,
and it recomposed the whole card every second. It now:

- runs only while a drop is genuinely still pending, and stops permanently the moment it can be
  claimed;
- is cancelled while the app is backgrounded and re-read on resume;
- confines its ticking to the status line and button, so the avatar and title never recompose.

There is now exactly **one** implementation of a repeating clock in the app (`rememberLiveNow`),
and every live readout goes through it.

## 2. Complete interface rebuild

Every screen, dialog, sheet and menu was rebuilt on a single design system rather than patchwork
styling. New in `ui/theme/`: a proper colour scale, a spacing scale, a radius scale, a stroke and
tint scale, and a semantic type scale.

- **One app background.** The ink base, the ember/plasma washes and the terminal grid are painted
  once, behind the navigation host, instead of each screen painting its own flat background.
  In Performance Mode the grid is skipped entirely.
- **One card.** Every screen used to hand-roll its own card with the same radius and border
  repeated dozens of times. There is now a single `SurfaceCard`, a `HeroPanel` for the most
  important block on a screen, and shared `IconTile`/`StatusPill`/`KeyValueRow`/`PillChip`/
  `RankBadge`/`EmptyStateCard`/`LoadingState`/`ErrorState` parts.
- **Redesigned navigation bar.** Floating bar with a gradient scrim so scrolling content dissolves
  behind it, an ember indicator above the selected tab, and the compact icon-only mode for
  admins' seven tabs preserved.
- **Redesigned screens.** Command Center (new hero showing global eliminations with a skull
  watermark), Login (brand mark, gradient wordmark, pill mode switch), Utility Hub (colour-coded
  grid — each destination owns one accent hue), Black Market, Survivor Network (rank medals,
  gradient wash for the top three), Terminal Config, Wheel of Fate, Scratch Cards, Progression,
  Archives, Team Progress, Images, Survival Intel and the Admin console.
- **Better states.** Lists that can be empty show a proper empty state; failures show a
  retry-able error state; first loads show a loading state.

## 3. Custom artwork, replacing the stock template

The app icon was still the default Android Studio template — a green robot on a green background.

- **New app icon.** A hand-authored vector skull with an ember gradient on deep ink, with a warm
  bloom behind it. Supplied as an adaptive icon (background, foreground and themed monochrome
  layers) so it renders correctly under every launcher mask — circle, squircle, rounded square and
  Android 13+ themed icons — plus a full-bleed vector fallback for Android 7.
- **New brand mark.** The same skull is now the app's visual signature: in the title area of every
  screen header, on the login screen, as the watermark on the Command Center hero, and as the
  source of the themed icon.
- **Custom shop artwork.** Black Market items now carry a hex-plate emblem tinted to the item's
  own hue (cyan = defensive, purple/red = curses, amber = economy, green = a second chance), so the
  shop is scannable by colour before you read a name.
- **All vector.** No bitmaps, no per-density icon sets. The new icon art is roughly 1 KB and stays
  sharp at every size. The five unused stock AOSP foreground bitmaps (~173 KB) were deleted,
  because nothing referenced them any more.
- **Cold start matches.** The pre-Compose window, the status bar and the navigation bar are all
  painted to the same ink colour as the app background, so there is no white flash on launch and no
  colour seam when the launcher hands over.

## 4. Performance

- **No new dependencies.** Nothing was added to `build.gradle.kts` except the version bump.
- **Performance Mode is respected everywhere.** It turns off the background grid, the entrance
  animations, the press-scale feedback and the avatar-frame tween, drops the countdown to a 30 s
  tick, and disables navigation transitions.
- **Animation is draw-layer only.** Entrance and press animations go through `graphicsLayer`, so
  they never trigger recomposition.
- **Timer discipline.** One shared, lifecycle-aware, self-cancelling clock; runs only when there is
  something to count; reads are scoped to the smallest composable that displays them.
- **Smaller APK.** ~173 KB of unreachable stock icon art removed.

## 5. Data safety (why nobody is logged out)

This release deliberately does **not** touch:

- `data/AppPrefs.kt` — no SharedPreferences file name or key was renamed or removed. Every
  existing setting (Performance Mode, the three alert toggles) reads back exactly as before.
- Firebase Auth, session handling and the sign-in flow — `login`, `signup` and `logout` are
  unchanged, and nothing signs out on upgrade.
- Firestore collection or field names — no writes changed shape, so **no rule changes are needed**.
- Cached data or archives — old archives are still read as `schemaVersion` 1 and still report the
  richer fields as "not recorded".

Upgrading over v2.0.7 is an ordinary in-place install: your session, coins, level, titles,
inscriptions and history all survive.

## 6. Admin console

- The module switcher is now a clear, obviously tappable pill row with the selected module
  highlighted in its own accent colour.
- Each module's fields are grouped into labelled cards with a short explanation of what the field
  does.
- Destructive actions are visually separated, use the ember danger accent, sit last in their
  section, and keep every existing confirmation step. No write path, field name or guard was
  weakened.

---

## Installing

Phones only accept updates signed with the **same key** as the installed build, so you must sign
this APK with your usual keystore before it can be installed or used for the in-app update.

1. Build or sign a release APK with your existing keystore (see README → *Sign & Distribute*).
2. Upload the signed APK wherever your OTA download links point.
3. In the app: **Admin → Global → Publish Firmware** — version name `2.0.7.1`, version code `11`,
   and the direct APK URL.

The APK kept in `release/v2.0.7.1/` is **unsigned**, exactly like the files for previous versions.
An unsigned APK cannot be installed: Android rejects it at install time.

## Verification

What was actually run and checked on this machine:

- **`compileDebugKotlin`** — clean, zero errors.
- **`testDebugUnitTest`** — all tests pass, including **7 new tests that pin the countdown
  arithmetic** (`CountdownTest`): the day/hour/minute/second breakdown, that the four tiles always
  add back up to the exact total, that no tile can run past its own unit (the classic "HRS 27"
  bug), and that Performance Mode removes only the seconds tile and leaves the rest identical.
- **`assembleDebug` and `assembleRelease`** — both build successfully.
- **Release APK inspected (`aapt2`), not just built:** it reports `versionCode=11`,
  `versionName=2.0.7.1`, the launcher icon resolves to the new asset, and — importantly — the icon
  is a genuine adaptive icon carrying **background, foreground *and* monochrome** layers, so
  Android 13+ themed icons tint the skull itself rather than a grey disc. `drawable/ic_deadfest_mark`
  also survives resource shrinking, which is what lets the hero panels draw their watermark in a
  release build.
- **Behaviour-change audit.** Every modified screen was diffed against the previous version for
  removed API usage: **zero** ViewModel calls, navigation routes, preference accesses, ViewModel
  method references or `BuildConfig` reads were dropped by the redesign. That is the mechanical
  proof behind the "nobody is logged out and nothing is reset" claim, alongside the fact that
  `data/`, `viewmodel/`, `repository/`, `domain/` and `AndroidManifest.xml` were not touched at all.

What was **not** verified, and is worth a click-through on your own phone before you publish:

- **No on-device or emulator run happened here.** There is no device attached to this machine, so
  layout, animation timing, the countdown visibly ticking, the admin date picker and live Firestore
  behaviour are compile-verified (and, for the countdown maths, unit-verified) but **not
  run-verified**. Treat the first install as the real test.
- Suggested click-through: open **More** and watch the countdown tick each second; turn
  **Performance Mode** on under Settings → System and confirm the SEC tile disappears and the
  remaining tiles are still right; background the app for a minute and confirm the value snaps
  forward on return; open every tab, the admin console and a dialog; buy something in the Black
  Market; confirm the launcher icon shows the skull.
