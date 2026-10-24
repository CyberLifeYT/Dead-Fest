# Dead-Fest v2.1.1

**Build 16.** The community update: group chat, personal alerts, achievements, your own stats,
editable profiles and flash sales. Sector Overloads now actually pay out.

v2.1.1 is the published build of everything developed as 2.1.0 (2.1.0 was never released as an APK).

Updating installs straight over what you already have. Nothing is reset, nobody is logged out, and
your coins, level and history are untouched.

## The desktop version has been discontinued

The Windows client (`Dead-Fest-Desktop-*.exe`) is **discontinued**. Desktop 2.1.0 is its final
release: it will receive no further updates or fixes, and the features in this update (group chat,
alerts, achievements, My Stats, flash sales) are not coming to it. Existing desktop builds still
connect to the same game for now, but may stop working as the mobile app moves on.

**The Android app is Dead-Fest, and it will keep getting updates.** If you were playing on desktop,
install the APK from this page; your account, coins, level and history are exactly where you left them.

> **Admins: publish the new Firestore rules before handing out this build.** Alerts and chat are
> new collections; until [`firestore.rules`](https://github.com/CyberLifeYT/Dead-Fest/blob/main/firestore.rules) is deployed
> (`firebase deploy --only firestore:rules`), sending an alert or a chat message is refused.

## Sector Overloads finally pay

Admins could always set "x3 in this sector", and the Dashboard showed the banner, but the
multiplier was never applied to anything. Now a death recorded in the overloaded sector:

- pays **every other survivor x3** the Mutual Survival Reward (halved as usual if they're bankrupt);
- earns the dying survivor **x3 death XP**;
- says so in the death report and the live feed.

The multiplier is capped at x10, so a typo can't flood the economy.

## Group chat (More → Group Chat)

A single chat for the whole group, showing the newest 100 messages live. Attach a photo; it's
compressed exactly like gallery images, and the full picture loads only when someone taps it.
Long-press your own message to delete it; admins can delete any.

## Personal alerts

When something happens *to you*, you hear about it:

- **You were cursed by X** (with the sector and how many deaths);
- **Your Shield blocked a curse** from X;
- **Your image was removed** by an admin;
- an admin changed your coins, injected or removed a death, or cleared your penalties;
- your game request was approved.

Alerts become a phone notification while the app is open, and wait in the **Alerts** inbox (bell
on the Dashboard, or More → Alerts) for whenever you next open it. They can be switched off in
Settings → System → Personal alerts.

*Why only while the app is open:* push to a closed app needs a server sending it, which on
Firebase means Cloud Functions and the paid Blaze plan. The inbox makes sure nothing is lost.

## Achievements

Sixteen badges, shown under Progression and celebrated with a pop-up, notification and feed entry:
First Blood · Centurion (100 deaths) · Survived 7 Days · Bankrupt and Back · Jackpot (250+ coins
from one spin or scratch) · High Roller (10,000 coins) · Seasoned (level 10) · Veteran (level 25)
· Legend (prestige) · Ghost Economy (Insulation Tier IV) · Hex Master (land a curse) · Deflector
(Shield blocks a curse) · Covered (Insurance pays out) · Lucky Strike · Photographer · Comms Online.

## My Stats (More → My Stats)

Your deaths per sector, coin balance over time (sampled daily, last 60 days) and deaths per day for
the last two weeks, drawn like the admin dashboard. History starts from this update.

## Edit Profile (More → Edit Profile, or tap your profile card)

Pick an emoji avatar, write a bio (160 characters) and choose a badge colour. Eight colours are
free; the ones you've unlocked through levels are added on top. Frames and titles stay in
Progression.

## Flash sales

Admins: **Admin → Shop → Flash Sale**. Set a discount (up to 90%) and a duration (or leave it
running until you end it). Every Black Market item and title drops in price, the shop shows a
countdown banner and crossed-out old prices, and the sale ends itself when the timer runs out.
Purchases are charged the sale price inside the same transaction that shows it, so the label and
the charge always agree.

## Accessibility & translations

- **Every theme passes WCAG contrast checks**, enforced by a new automated test. It found two real
  problems: white button text on the **Halloween** orange (2.6:1) and **Toxic Storm** lime (2.0:1)
  was hard to read; those buttons now use dark text. The **Blood Moon** red was brightened very
  slightly (B91C1C → CC2222) so it reads on its own cards.
- New screens have spoken labels for every button, chart and badge, 48dp touch targets, and charts
  that read out their data.
- All new text, plus the bottom navigation, now lives in `strings.xml`, ready for translation. See
  [`docs/TRANSLATING.md`](https://github.com/CyberLifeYT/Dead-Fest/blob/main/docs/TRANSLATING.md). Older screens are still being moved over.

## Under the hood

- New Firestore rules for alerts (`users/{uid}/alerts`) and stricter chat rules (sender must be
  you, text ≤ 500 characters, small thumbnails, delete your own). 11 new rules tests; 56/56 pass.
- New pure game rules in `domain/Community.kt` (overload, flash sale, achievements, stats history),
  covered by 16 new unit tests plus 3 contrast tests; 94/94 pass.
- New profile fields: `survivalStreak`, `everBankrupt`, `coinHistory`, `deathsByDay`. All default
  safely on existing accounts.
- Release APK: about 4 MB (R8 build).
- The discontinued desktop client is unchanged and will not be updated.
