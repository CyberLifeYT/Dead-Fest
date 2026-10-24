# Translating Dead-Fest

Dead-Fest's interface text lives in `app/src/main/res/values/strings.xml`. Android picks the
phone's language automatically, so a translation is just a second copy of that file.

## Add a language

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-xx/strings.xml`,
   where `xx` is the language code: `values-es` (Spanish), `values-de` (German),
   `values-fr` (French), `values-pt-rBR` (Brazilian Portuguese), and so on.
2. Translate the text between the tags. Leave the `name="..."` part alone.
3. Keep placeholders exactly as they are: `%1$s` is text, `%1$d` is a number, `%%` is a
   literal percent sign. Their order may change to suit the language (`%2$s ... %1$s`).
4. Strings marked `translatable="false"` (the app name) must not be translated.
5. Build and switch the phone's language to check. Anything not yet translated falls back
   to English automatically.

## What's covered

| Area | Status |
|------|--------|
| Bottom navigation | In `strings.xml` |
| Group chat, Alerts, My Stats, Edit Profile, Achievements | In `strings.xml` |
| Flash sale banner and admin card, overload payout text | In `strings.xml` |
| Utility Hub social tiles, personal-alerts setting | In `strings.xml` |
| Older screens (Home, Shop, Wheel, Players, Settings, Admin, ...) | Still hard-coded; migrating screen by screen |
| Feed entries, alerts and broadcasts | Stored in the database as written, so they appear in the sender's language |

When you migrate a screen, move each `Text("...")` into `strings.xml` and read it with
`stringResource(R.string.key)`. Use `%1$s` placeholders instead of string concatenation so
translators can reorder words.

## Accessibility checks

`app/src/test/java/com/deadfest/ContrastTest.kt` checks every app theme against WCAG 2.1:
body text 4.5:1, captions and accents 3:1, button labels 3:1. Adding or changing a theme in
`ui/theme/Palette.kt` runs these checks on the next `./gradlew testDebugUnitTest`.
