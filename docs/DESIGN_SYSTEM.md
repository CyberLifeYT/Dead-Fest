# Dead-Fest Design System (v2.0.7.1)

Everything visual in Dead-Fest comes from the files below. If you are adding or changing UI,
use these; do not invent new colours, radii, spacings or one-off card implementations.

## Files

| File | Contains |
| --- | --- |
| `ui/theme/Color.kt` | The ink scale, ember/amber/plasma accents, legacy token aliases |
| `ui/theme/Type.kt` | Material `Typography` + `DeadFestType` semantic styles |
| `ui/theme/Shape.kt` | `Radius`, `Spacing`, `Stroke`, `Tint` tokens |
| `ui/theme/Theme.kt` | `DeadFestTheme` — colour scheme, shapes, typography |
| `ui/components/Design.kt` | The component kit (below) |
| `ui/components/Common.kt` | Domain components (headers, avatars, badges, countdown, …) |

## Colour

Surfaces, darkest first: `Ink0` (app background) → `Ink1` → `Ink2` (standard card) →
`Ink3` (raised / input) → `Ink4` (chip / pressed). Hairlines: `InkLine`, texture: `InkGrid`.

Accents: `Ember` (`RedAccent`) brand red · `EmberDeep` · `EmberGlow` · `Amber` (`Gold`) reward ·
`Plasma` (`ShieldCyan`) tech · `Toxic` (`OnlineGreen`) positive · `Prestige` progression.

Text: `TextHi` (`TextPrimary`) · `TextMid` (`TextSecondary`) · `TextLow` (`TextMuted`).

Legacy names still work and now resolve to the refreshed palette. Prefer the new names in new code.

**Never** hard-code a hex colour in a screen. If a new shade is genuinely needed, add it to
`Color.kt` with a comment explaining its role.

## Spacing and radius

Use `Spacing.gutter` (16dp) for screen edge padding, and `Spacing.xxs/xs/sm/md/lg/xl` between
blocks. Use `Radius.xs/sm/md/lg/xl/xxl/pill` for corners. Use `Stroke.hair` (1dp) for borders and
`Tint.wash/fill/edge/edgeStrong` for `accent.copy(alpha = …)` values.

## Typography

`DeadFestType.screenTitle`, `.screenSubtitle`, `.section`, `.cardTitle`, `.body`, `.microLabel`,
`.stat`, `.statHero`, `.chip`, `.action`. Reach for these instead of raw `fontSize = 13.sp`.

## Components (Design.kt)

| Component | Use for |
| --- | --- |
| `DeadFestBackground` | Already applied app-wide in `MainActivity`. Never add another. |
| `ScreenScaffold(title, subtitle, header, body)` | Standard screen frame; `body` hosts the caller's `LazyColumn`/grid |
| `SurfaceCard(accent, onClick)` | **The** card. Pass `accent` for a tinted "highlighted" variant |
| `HeroPanel(eyebrow, title, icon, accent)` | Highest-priority block on a screen |
| `PrimaryAction(text, onClick)` | Full-width primary button |
| `GhostAction(text, onClick)` | Quiet secondary button |
| `KeyValueRow(label, value)` | Label/value row inside a card |
| `PillChip(text, selected, onClick)` | Filters, admin module switching, choices |
| `DeadFestProgress(fraction, accent)` | Slim progress rail |
| `RankBadge(rank)` | Medal for 1/2/3, neutral chip otherwise |
| `GradientRule()` | Thin ember divider between major blocks |
| `LoadingState(text)` | First-load spinner |
| `ErrorState(message, onRetry)` | Recoverable failure |
| `EmptyStateCard(icon, title, message, action)` | Empty list / nothing here yet |
| `Modifier.enterOnMount(index)` | Subtle fade+rise on first appearance (skipped in Performance Mode) |

## Components (Common.kt)

`BrandMarkBadge` · `ScreenHeader` (used by every screen) · `SectionLabel` ·
`IconTile` · `StatusPill` · `NoticeCard` · `EmptyState` · `CoinBadge` · `CoinIcon` · `PriceTag` ·
`FramedAvatar` · `LevelBadge` · `PrestigeBadge` · `XpBar` · `AlertBanner` · `PenaltyStatusCard` ·
`PendingSpinsChip` · `DashboardPanel` · `StatTile` · `ProportionBar` · `CountdownTiles` ·
`NextEventHero` · `ConfirmDialog` · `deadFestFieldColors()` · `Modifier.pressClick(shape, onClick)` ·
`formatNumber` · `formatCountdown` · `formatEventTime` · `showDateTimePicker` ·
`rememberLiveNow(intervalMs, running, key, stopWhen)`

## Performance rules (these are requirements, not suggestions)

The target device is a low-end phone. Every rule below is enforced in the shared components, so
following the design system keeps you compliant automatically.

1. **No bitmaps, no blur, no shadow passes.** Gradients, solid fills and hairlines only.
2. **Respect Performance Mode.** Read `LocalPerformanceMode.current` and skip animations when it
   is true. Never add a *new* animation that ignores it.
3. **Never create an ad-hoc timer.** Use `rememberLiveNow`, which is lifecycle-aware (stops when
   backgrounded), self-cancelling (stops when the composable leaves) and free when idle
   (`running = false` schedules nothing).
4. **Scope reads.** Any value that changes over time (a countdown, a live counter) must be read
   with `.value` inside the *smallest* composable that displays it, never at screen level.
5. **Don't nest scroll containers in the same axis.**
6. **No expensive per-frame work** — no `Canvas` redrawn on every frame, no unbounded particles.

## Data safety rules

- Never change an existing `SharedPreferences` file name or key in `data/AppPrefs.kt`.
  Users' settings and Performance Mode choice live there. Adding a *new* key with a safe default
  is fine; renaming or removing one silently resets that setting for every existing user.
- Never change Firestore collection/field names without a backward-compatible read path.
- Never clear caches or call `signOut()` as part of an upgrade.
- Compose `rememberSaveable` keys are user-visible state; don't rename them casually.

## Accessibility rules

TalkBack and Switch Access are expected to work on every screen. The shared components already do
the right thing, so following the design system keeps you compliant — but a new bespoke tile or row
is where these get broken.

1. **Every touch target is at least 48dp.** If the design needs something visually smaller, keep the
   visual size and reserve the target with `Modifier.minimumInteractiveComponentSize()` (Material 3).
   The header back button is the worked example: a 38dp tile with a 48dp hit area.
2. **Decorative icons are `contentDescription = null`.** An icon sitting beside text that already says
   the same thing must not be read twice.
3. **An icon-only control describes its *action*, with the real subject**: `"Delete image by $uploader"`,
   not `"Delete"`, and never the icon's name.
4. **Section titles are headings.** `SectionLabel` does this for you; a plain `Text` acting as a section
   title needs `Modifier.semantics { heading() }`. This is what makes a long screen navigable.
5. **Label and value are one fact.** Rows that pair them (`KeyValueRow`, `StatTile`, `XpBar`) merge
   their descendants so they read as `"Coins, 1,240"` rather than as two unrelated stops.
6. **A drawn bar must report itself.** A `Canvas`/`Box` progress rail is invisible to a screen reader;
   add `Modifier.progressSemantics(target)`. Report the *target*, not the animated value, or it
   narrates the animation.
7. **Selection is announced, not just tinted.** Use `Modifier.semantics { selected = ... }` (as `PillChip`
   does). Colour is never the only signal for anything.
8. **Never add `liveRegion`.** It interrupts constantly. A value that changes on screen must instead be
   *spoken at a coarser granularity than it animates* — the countdown is the worked example: the tiles
   tick every second, the spoken form is rounded to the minute and holds still for a whole minute.
9. **Don't merge across an interactive child.** `Modifier.clickable` already merges its descendants, so
   rows built on `SurfaceCard(onClick = ...)` or `Modifier.pressClick` are correct as they stand. Adding
   `mergeDescendants` to a container that holds a button makes that button unreachable — which is why
   `ErrorState` and `EmptyStateCard`, both of which can hold an action, are deliberately not merged.
