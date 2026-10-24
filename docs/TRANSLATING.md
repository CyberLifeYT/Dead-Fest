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

Every screen's text is in `strings.xml` (about 1,150 strings), including the Survival Intel
manual, shop toasts, admin results, notification channels and weekly challenge titles.

| Not translated | Why |
|----------------|-----|
| Feed entries, alerts, chat and broadcasts | Stored in the database as written, so they appear in the sender's language |
| Shop item and title names (SHIELD, WARLORD, ...) | They are game identifiers saved on accounts; their descriptions are translated |
| Theme and avatar-frame names | Proper names |

## Adding new text

- **Inside a composable:** `stringResource(R.string.key)`.
- **Anywhere else** (ViewModel, click handlers, notifications, top-level lists):
  `tr(R.string.key)` or `tr(R.string.key, arg1, arg2)` from `com.deadfest.util.tr`.
  A list of text kept outside a composable should be a getter (`val items get() = listOf(...)`)
  so it follows a language change.
- Use `%1$s` / `%1$d` placeholders instead of string concatenation so translators can reorder words.
- Android trims spaces at the start and end of a string. If they matter, wrap the whole value
  in double quotes: `<string name="x">" · %1$s left"</string>`.
- Never compare a translated string to decide behaviour (`if (label == "ACTIVE")`). Compare the
  underlying state, or the same `tr(...)` value.

Unit tests that reach `tr()` call `TestStrings.install()` (see `CountdownTest`), which reads the
English `strings.xml` so tests check the real text without an Android device.

## Accessibility checks

`app/src/test/java/com/deadfest/ContrastTest.kt` checks every app theme against WCAG 2.1:
body text 4.5:1, captions and accents 3:1, button labels 3:1. Adding or changing a theme in
`ui/theme/Palette.kt` runs these checks on the next `./gradlew testDebugUnitTest`.
