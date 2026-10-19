# Dead-Fest — desktop client (Windows)

The Dead-Fest companion app for Windows. **Same accounts, same data, same live game** as the
Android build: it talks to the same Firebase project, so your coins, deaths, levels, handles and
the whole survivor network are the ones you already have.

The interface is the phone app, stood up for a desktop: the same ember-on-ink design system, the
same cards, the same type, the same themes.

---

## Run it

**The easy way:** download `Dead-Fest-Desktop-2.0.8.exe` from the
[releases page](https://github.com/CyberLifeYT/Dead-Fest/releases) and run it. It is a
self-extracting portable build: no installer, nothing to uninstall, and it can live anywhere on
your disk.

**From source:**

```bash
npm install
npm start          # run the app
npm run dist       # build dist/Dead-Fest-Desktop-2.0.8.exe
npm run icon       # regenerate assets/icon.png and assets/icon.ico from assets/icon.svg
```

## How it is put together

| Path | What it is |
|---|---|
| `main.js` | Electron main process: the window, the menu (there isn't one), and the external-link handler. |
| `preload.js` | Deliberately empty — the renderer needs no Node access. |
| `index.html` | The shell: sidebar, screen frame, modal and toast roots. |
| `css/styles.css` | The design system, ported token for token from the Android app (`ui/theme/`). |
| `js/config.js` | Firebase configuration, the theme list, and the handle rules. |
| `js/economy.js` | The game rules, ported from `domain/Economy.kt`. |
| `js/data.js` | The data layer, ported from `data/repository/FirebaseRepository.kt`. |
| `js/gamesdb.js` | The game-database lookup, ported from `data/repository/TheGamesDb.kt`. |
| `js/screens-*.js` | The screens. |
| `js/app.js` | Router, navigation and the actions the screens call. |
| `vendor/` | The Firebase JS SDK, vendored so the app never depends on a CDN at runtime. |
| `assets/` | The icon: `icon.svg` is the source, `icon.png` / `icon.ico` are generated from it. |
| `tools/` | `make-icon.js` renders the icon; `stamp-exe.js` brands the built executables. |

### The rules are shared, not re-invented

Every coin, tax, bankruptcy transition and XP curve in `js/economy.js` is a direct port of the
Android app's `domain/Economy.kt`, and every write goes through the same Firestore transactions in
`js/data.js` that `FirebaseRepository.kt` uses. If a rule changes in Kotlin it has to change here
too — that is the whole point of porting rather than approximating.

### Live sync

The app subscribes to the same collections the phone app does — survivors, global state, the event
feed, archives, the scratch pool, the reward track, images and game requests — with
`onSnapshot` listeners. Anything that changes on a phone shows up on the desktop immediately, and
the other way round.

## Two build wrinkles worth knowing about

**The icon.** `electron-builder` normally brands the executable with `rcedit`, but that tool ships
in a package whose extraction needs symlink privileges on Windows, which a normal account does not
have. So the build runs with `signAndEditExecutable: false` and `tools/stamp-exe.js` does the
branding itself using the copy of `rcedit` already in electron-builder's cache.

**The portable build is a self-extractor.** `rcedit` rewrites an executable and drops anything
appended after the PE sections — which, for this build, is the entire 74 MB payload. Stamping it
naively produces an 82 KB file that does nothing. `tools/stamp-exe.js` therefore measures the
overlay out of the original executable and puts it back afterwards, which leaves a branded *and*
whole self-extractor.

## Notes

- The app is unsigned, so Windows SmartScreen will say "unrecognised app" on first run. Choose
  **More info → Run anyway**. It is the same situation as sideloading the Android APK.
- The game-database key is a client-side key, so it ships inside the app. See `docs/SIGNING.md`
  in the repository root.
