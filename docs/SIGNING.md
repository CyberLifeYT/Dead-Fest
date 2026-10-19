# Signing

Everything in this document exists to protect one property: **an update installs over the app
people already have.**

Android identifies an app by its signing certificate, not by its name. If a new build is signed
with a different key, the installer refuses it with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and the
only way forward is to uninstall first — which wipes the saved Firebase session and the local
preferences file. That is why the key is treated as fixed infrastructure rather than as a build
detail, and why the release workflow refuses to publish anything carrying a different certificate.

## The key

| | |
|---|---|
| Certificate | `CN=Android Debug, O=Android, C=US` |
| SHA-256 | `876d1bfd47566e4c3530397ececebc562795059c7d984e65a4d8f85caf260bcc` |
| Alias | `androiddebugkey` |
| Store / key password | `android` |

It is the Android debug keystore, and that is deliberate. Every build distributed so far — the
`Dead-Fest.apk` already on people's phones included — was signed with this key, so it is the only
key that can update them. Switching to a "proper" release key now would be a breaking change for
every existing user, not a tidy-up. If that trade is ever worth making, it needs a migration plan
(users install a final build signed with the old key that then hands over to the new one), not a
quiet edit to `build.gradle.kts`.

> The debug keystore is normally disposable, and Android Studio regenerates it with a *different*
> key if it goes missing. For this project it is not disposable. Back it up.

## Where it lives

| | |
|---|---|
| Working copy | `C:\Users\cyber\.deadfest\deadfest-signing.jks` |
| Original | `C:\Users\cyber\.android\debug.keystore` (same file) |
| Local build config | `local.properties` (gitignored, never committed) |
| CI | repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` |

`app/build.gradle.kts` reads the keystore from `local.properties` locally and from `DEADFEST_STORE_FILE`
and friends in CI. If neither is present the build still succeeds and simply produces unsigned
output, so a missing keystore can never break someone else's checkout.

Both `debug` and `release` are signed with this key on purpose. The two published APKs — `Dead-Fest.apk`
(full) and `Dead-Fest R8.apk` (shrunk) — have to be installable over each other, or choosing the
smaller build in Settings would force an uninstall.

## The desktop build is produced locally, not in CI

`desktop/` is a Windows Electron client for the same Firebase project, so it is built on a Windows
machine rather than on the Linux runner:

```bash
cd desktop
npm install
npm run dist        # -> dist/Dead-Fest-Desktop-2.0.8.exe
```

The executable is then uploaded to the release by hand, and the release notes are refreshed so the
attached-files table lists it alongside the two APKs. **Re-publishing the tag replaces the two
APKs** (the desktop asset keeps its own name and survives, but it drops out of the table the
workflow generates) — so a re-publish means re-uploading the desktop build and refreshing the notes
again.

Two build details that look like mistakes but are not:

- **`signAndEditExecutable: false`.** electron-builder brands an executable with `rcedit`, which
  ships in a package whose extraction needs symlink privileges on Windows — a privilege a normal
  account does not have, so the build cannot fetch it. `tools/stamp-exe.js` runs the copy already in
  electron-builder's cache instead.
- **The portable build is a self-extractor.** `rcedit` drops anything appended after the PE
  sections, which for this build is the entire 74 MB payload; stamping it naively produces an 82 KB
  file that does nothing. `tools/stamp-exe.js` measures the overlay out of the original executable
  and puts it back, leaving a branded *and* whole self-extractor.

## The game-database key is a different kind of secret

The game-request lookup uses a client-side API key for TheGamesDB. Unlike the signing key it is **not
secret in the cryptographic sense**: it is compiled into `BuildConfig` and therefore ships inside the
APK, where anyone who unpacks the build can read it. Treat it as public.

It follows the same loading pattern as the keystore — `local.properties` (`deadfest.theGamesDbKey`,
gitignored) locally, `DEADFEST_THEGAMESDB_API_KEY` in CI — and the build succeeds with it blank. With
no key the request screen simply hides the search box and offers manual entry, so a missing key
degrades one feature rather than breaking the build.

Because it is extractable, the only real protection is the provider's dashboard: scope or rotate the
key there if that ever becomes possible. The workflow checks for it and warns rather than failing,
since a release must not be blocked by an optional feature.

## Verify a build before publishing it

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs `
    "D:\AndroidProjects\app\build\outputs\apk\release\app-release.apk" |
    Select-String "Signer #1 certificate SHA-256"
```

The SHA-256 must be the value in the table above. `aapt2 dump badging` gives the version:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\aapt2.exe" dump badging <apk> |
    Select-String "package:"
```

## Publishing a release

Pushing a tag runs `.github/workflows/release.yml`, which tests, builds both variants, checks both
certificates against the expected fingerprint, and attaches the two APKs to a release. A manual run
of the same workflow produces the APKs as build artifacts without publishing. Nothing else needs the
keystore — including the release itself, which the publish step does with the built-in `GITHUB_TOKEN`.

The notes are assembled in the runner rather than published verbatim: the size and SHA-256 of each
APK are read off the files being attached, because a hash copied from a build log goes stale the
moment CI produces different bytes than the machine that wrote the log. Build outputs are not
reproducible byte-for-byte across machines, so never hard-code them in the committed notes.

### Asset names

GitHub **does not allow spaces in release asset file names and rewrites them**, so uploading
`Dead-Fest R8.apk` stores `Dead-Fest.R8.apk`. That rename is GitHub's, not ours — sending the name
percent-encoded as `%20` makes no difference, it still comes back with a dot. So the two assets are:

| Local file the workflow uploads | Name stored on the release | Display label |
|---|---|---|
| `Dead-Fest.apk` | `Dead-Fest.apk` | — (unchanged) |
| `Dead-Fest R8.apk` | `Dead-Fest.R8.apk` | `Dead-Fest R8.apk` |

`softprops/action-gh-release` notices the rewrite and PATCHes the asset's **label** back to the
original name, and GitHub renders the label in place of the file name on the release page. The
download URL still uses the stored name, so the R8 link contains `Dead-Fest.R8.apk` — copy the link
from the page rather than typing the label. The in-app label is a display string and stays
"Dead-Fest R8".

An existing asset is deleted and re-uploaded when the workflow runs again for the same tag
(`overwrite_files` defaults to true), so re-pointing a tag at a fix replaces the wrong artifact
instead of failing with a 422.

### Repository secrets the workflow expects

All five are already set. The workflow fails on a missing one before it starts building, rather than
three minutes later with an error that reads like a code problem.

| Secret | What it is |
|---|---|
| `KEYSTORE_BASE64` | the keystore above, base64-encoded |
| `KEYSTORE_PASSWORD` | `android` |
| `KEY_ALIAS` | `androiddebugkey` |
| `KEY_PASSWORD` | `android` |
| `GOOGLE_SERVICES_JSON_BASE64` | `app/google-services.json`, base64-encoded |

`google-services.json` is gitignored because it is project configuration rather than source, so CI
has to be handed it the same way it is handed the keystore. To regenerate either value:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Users\cyber\.deadfest\deadfest-signing.jks"))
```
