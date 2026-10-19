# Dead-Fest v2.0.8

**Build 13.** A handle you can change yourself, app themes, a community game-request queue, and a
kill switch for the Forced Spin Protocol.

Updating installs straight over what you already have. Nothing is reset, nobody is logged out, and
your coins, level and history are untouched.

## Your handle, your call

You can now change the name the community sees — on the player list, the death feed, the game-request
queue — from **Settings → General → Identity**. It updates everywhere the moment you save it: the
name lives on your player document, and every screen reads it from the same live listener, so there
is no refresh to wait for and no second place to update.

Handles are unique across the whole app, checked case-insensitively: `Slayer` and `slayer` are the
same name, and only one of you can have it. Names are 3–20 characters of letters, numbers and
underscores, and the obvious impersonations (`Admin`, `Dead-Fest`, `Moderator`) are reserved.

Changing your handle touches nothing else. Coins, level, stats, titles and history all stay exactly
where they were, and nothing that already stored your name breaks.

## Themes

**Settings → General → Themes** now offers Default, Halloween, Christmas, Toxic Storm and Blood Moon.
Each one previews with its own real colours before you pick it, and the change is instant across the
whole app.

The themes restyle the surfaces and the brand colour, and deliberately nothing else: amber is always
currency, green always success, cyan always tech. A "success" pill that turned red at Christmas would
be worse than no theme at all, so the semantic colours hold still in every season.

## Request a game

**More → Request Game** opens the community queue:

- **Request Game** looks the title up in a game database and shows real box art, so you pick from
  actual matches instead of typing blind. Tap one to fill the request.
- If the lookup can't reach the database — or finds nothing — you can enter the game by hand
  instead, so the feature works even when the external service is down.
- **Pending games** shows what is waiting for an admin, so you can see whether your request is in the
  queue without asking in chat.
- **Approved games** is out in the open: those are what the community has already won.

Admins get a **Requests** module in the panel with the pending queue (approve / remove) and the
approved list (remove). Only admins can approve or remove. The queue is the same live stream the
request screen reads, so the two can never disagree.

## Forced Spin Protocol kill switch

Admins can now turn the protocol off from **Admin → Global → System switches**. While it is off, a
death adds no penalty spin to the Wheel of Fate. Existing spins a survivor already had are left
alone, and bankruptcy escalation still applies — that is a separate system.

The switch is read live, so it takes effect on the very next death rather than after a restart. It
defaults to on, so a server document written before this field existed keeps the behaviour everyone
already has.

## Notes

- The game lookup uses a client-side API key. It ships inside the APK, which means it can be read by
  anyone who unpacks the build — treat it as public, and rotate it if the provider ever lets you
  scope it.
- The game database service has been unreachable from this build environment, so the request screen's
  manual-entry path is the one that has actually been exercised end to end. The lookup is wired to the
  documented API and will use it the moment it answers.

## Not in this release

- **Still no baseline profile.** Same reason as 2.0.7.2: it has to be measured on a real device, and
  inventing the numbers would be worse than shipping none.
