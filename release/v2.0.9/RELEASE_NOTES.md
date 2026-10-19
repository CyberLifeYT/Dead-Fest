# Dead-Fest v2.0.9

**Build 14.** The admin side is now enforced by the database instead of by good intentions — and
the desktop console can do everything the phone console can.

Updating installs straight over what you already have. Nothing is reset, nobody is logged out, and
your coins, level and history are untouched.

## The admin boundary lives in the database

Firestore security rules are now part of the project, in [`firestore.rules`](../firestore.rules),
and they are tested on every change. That is not a formality: **the two features added in 2.0.8 —
changing your handle, and asking for a game — were being refused by the server**, because nothing
had ever told Firestore those two collections were allowed to exist. Renaming a handle and filing a
request both failed with a permission error.

Those rules are now written down, and they close the doors that were open:

- **Only an admin can change the global document.** That is the one that matters most: it holds the
  download link every survivor is told to install, the economy settings, the sector list and every
  system switch. Before, whether a player could repoint it depended entirely on how the rules
  happened to be set in the console.
- **Nobody can promote themselves.** The admin flag is pinned to false on the way in and cannot be
  written by its own owner. Promoting somebody is always a deliberate act by somebody who is
  already an admin.
- **Only an admin can approve or remove a game request**, or edit the shop, the wheel, the scratch
  pool, the sectors, the reward track and the season archives.
- **A handle can only be claimed once.** It is claimed by creating its registry entry, so a name
  that is already taken cannot be quietly overwritten by a second player.
- **The global death counter can only move one step at a time**, so it can be neither inflated nor
  zeroed.

Forty-five tests decide every one of those cases against the real rules file, on the Firestore
emulator, on every push. They check both halves: that the app still works, and that the admin
boundary is still shut. See [docs/ADMIN.md](../docs/ADMIN.md).

## The desktop console caught up

The Windows client gained the two player controls the phone app has had all along:

- **Inject death** runs the whole pipeline — casualty tax, penalty spin, bankruptcy and XP — and
  **Remove death** takes one back without a refund.
- **Grant and revoke admin rights**, with the same guard the phone app has: you cannot revoke your
  own access and lock yourself out.

The console also now refuses to render at all for anybody who is not an admin. The navigation hides
it, the rules refuse the writes, and now the screen itself agrees.

## One quiet fix

The rules read the admin flag from your player document, so a survivor whose document was missing
could not write anything at all. The app now repairs that document the moment it notices it is
gone — which only ever happens if a sign-up lost its connection half way through.

## The honest limit

The **economy is still client-trusted**: a survivor can write their own coins, XP and level,
because those are the same writes the app makes when you spin the wheel. Rules cannot tell the two
apart. Moving the economy into Cloud Functions, where the server decides what a spin pays, is the
real fix and it needs the Blaze plan. The rules do close every door that *can* be closed without it.
