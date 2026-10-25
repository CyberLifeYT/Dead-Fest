# Dead-Fest v2.1.3

**Build 18.** Xbox death evidence: a PC companion that spots deaths in your Xbox video and keeps
a screenshot of each, a Deaths Images gallery for everyone, and admin verification that never
touches coins. Also fixes the R8 build crashing on launch.

Updating installs straight over what you already have. Nothing is reset, nobody is logged out, and
your coins, level and history are untouched.

> **Admins: publish the new Firestore rules and indexes before handing out this build:**
> `firebase deploy --only firestore:rules,firestore:indexes`. Until the rules are deployed, death
> evidence can't be uploaded or reviewed. Until the indexes are built (a few minutes after
> deploying), filtered views of Deaths Images show a message asking for them.

## The R8 build no longer crashes

`Dead-Fest.R8.apk` from 2.1.2 closed as soon as it opened. The cause: R8, the tool that shrinks
that build, removed a constructor that the background check-ins' job database needs (Android's
WorkManager creates it by reflection). The full-size `Dead-Fest.apk` was not affected. The
constructor is now kept, and every release build checks R8's report for this so it can't
happen silently again.

## Xbox Death Detector (new, for the PC)

A small Windows program in [`detector/`](https://github.com/CyberLifeYT/Dead-Fest/tree/main/detector)
for the PC your Xbox is connected to:

- Watches the gameplay video from an **HDMI capture card**, or from the **Xbox app's Remote Play
  window**. It only sees the picture; it never touches the console, its memory or its traffic.
- Spots death screens in **Dying Light, Dying Light 2, Dying Light: The Beast, World War Z, Dead
  Island, Dead Island Riptide, Dead Island 2 and State of Decay 2**, using Windows' built-in text
  recognition and templates you capture from a real death screen.
- Saves each death to **`D:\XboxDeathDetector\<Game>\`** as a PNG (plus the frame just before),
  with a record of the game, time, player, confidence and status. Files are never overwritten, and
  nothing is ever written outside that folder. If the drive is unavailable it holds the death and
  retries rather than saving elsewhere.
- Counts each death **once**, however long the death screen stays up.
- Tells deaths apart: your own, a teammate's (when the game names them), another player's.
  When the game shows nothing, nothing is invented.
- **Private by default.** Sign in and opt in to share. Then a compressed copy uploads for admin
  review (never video), and offline uploads wait and retry.
- Admin decisions come back to the PC: rejected screenshots move to `D:\XboxDeathDetector\rejected\`.

Detection isn't perfect: it depends on the game, the video quality and what's on screen. Each
game's death screen should be checked on real footage, and capturing a template makes detection
much more reliable. See the [detector guide](https://github.com/CyberLifeYT/Dead-Fest/blob/main/detector/README.md).

## More → Deaths Images

Every survivor can browse the shared death screenshots: game, time, player and verification status
on each, filters by game and status, newest or oldest first, a zoomable full-size view, and clear
messages when there's nothing yet, something fails to load, or an image file is missing. It loads
in pages, so a big collection stays fast on low-end phones.

## Admin → Deaths

- Search by event id, player or submitter; filter by game, status, player and date.
- **Verify** (choose whose death and which sector): adds exactly one death to that player's count.
- **Reject** with a reason: takes it back off the count if it was counted.
- **Correct** the game, classification, name or player; moving a counted death moves the count.
- **Remove** (soft delete, reversible) and **Restore** (counted again exactly once if it had been verified).
- **Delete forever**, only for records already off the count; the history is kept.
- **Check images** finds records whose image file is missing, shown separately from rejected ones.
- A full **history** for every record: who did what, when and why.

## Coins are never touched by death moderation

Verifying, rejecting, correcting, removing or restoring a death changes the death count only. It
does not charge Casualty Tax, pay the Mutual Survival Reward, or move anybody's coins. If you want
a death's full economic consequences, the existing "inject death" control is unchanged.

This is enforced by the Firestore security rules, not just the app:

- moderation is admin-only;
- a verified record and the player's death count must change together, in one commit;
- any moderation commit that changes a coin balance is refused as a whole;
- every decision must write its history entry.

Tested against the Firestore emulator, reading real balances before and after: verify, reject,
remove, restore, correct, repeated requests, and a refused commit all leave coins unchanged and
never count a death twice.

## Under the hood

- 85 security-rule tests pass, 22 of them new for death evidence; 118 app unit tests, 18 of them new.
- The detector has 62 tests, including real Windows OCR on rendered death screens, the full
  pipeline on generated footage, and an end-to-end run against the emulator (detect, upload,
  moderate, mirror the decision back to the PC).
- New Firestore collections: `deathEvidence` (with an `audit` history), `deathEvidenceBlobs`; 14
  indexes in `firestore.indexes.json`.
