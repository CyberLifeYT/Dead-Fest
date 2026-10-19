# Baseline profile

## Status: plumbing in place, profile not yet measured

`androidx.profileinstaller` is a dependency, which is the runtime half: it is what actually applies
a baseline profile and drives profile-guided compilation. The profile itself —
`app/src/main/baseline-prof.txt` — **does not exist yet, on purpose.**

A baseline profile is a list of methods that were *observed* to be hot during a real cold start on a
real device. It has to be measured. Writing one by hand from a reading of the source would produce
plausible-looking lines that describe guesses rather than measurements, and the whole value of the
file comes from it being true. There is no device or emulator on the machine this was built on, so
the measurement could not be taken.

The honest summary: this release adds the plumbing and nothing else. The win arrives with the first
measured profile.

## How to generate and ship one

The profile is produced by running a macrobenchmark on a connected device or emulator. One-time
setup, then it is a single command forever after.

1. Add the benchmark module and plugin. In `gradle/libs.versions.toml`:

   ```toml
   [versions]
   baselineprofile = "1.4.1"

   [plugins]
   androidx-baselineprofile = { id = "androidx.baselineprofile", version.ref = "baselineprofile" }
   ```

   and in the root `build.gradle.kts`, `alias(libs.plugins.androidx.baselineprofile) apply false`.

   > Check the plugin supports the AGP version in use (9.4.0 at the time of writing) before wiring
   > it in. If it does not, the build fails at configuration time.

2. Create a `:baselineprofile` module of type `com.android.test` with a `BaselineProfileRule` test
   that launches `MainActivity` and waits for the home screen to settle.

3. Plug in a phone, then:

   ```powershell
   .\gradlew :baselineprofile:generateBaselineProfile
   ```

   This writes `app/src/main/baseline-prof.txt`. Commit it. The release build picks it up
   automatically and `profileinstaller` applies it on first run.

## What to measure

The cold-start path, in the order it actually runs:

- `DeadFestApplication.onCreate` — Firebase init
- `MainActivity.onCreate` — the splash gate, then first composition
- `DeadFestTheme`, `DeadFestBackground`
- `FirebaseRepository` snapshot listeners attached by `MainViewModel`
- whichever screen the app opens on, and the bottom bar's seven destinations

Warm-start paths matter less. If the profile list is getting long, keep the cold-start half and drop
the rest: that is where the perceived win is.
