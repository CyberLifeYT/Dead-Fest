# Dead-Fest v2.1.2

**Build 17.** The "stay in the loop" update: real Android notifications that work while the app is
closed, weekly challenges, reactions and comments on the gallery, and the whole app ready for translation.

Updating installs straight over what you already have. Nothing is reset, nobody is logged out, and
your coins, level and history are untouched.

> **Admins: publish the new Firestore rules before handing out this build.** Image reactions and
> comments are new; until [`firestore.rules`](https://github.com/CyberLifeYT/Dead-Fest/blob/main/firestore.rules)
> is deployed (`firebase deploy --only firestore:rules`), reacting or commenting is refused.
> Everything else works on the 2.1.1 rules.

## Proper Android notifications

- **Six notification categories**, each of which can be muted or changed separately in Android
  settings (Settings → System → Notification categories takes you straight there):
  Personal alerts, Group chat, Levels & achievements, Wasteland broadcasts, Black Market, App updates.
- **The Dead-Fest skull in the status bar** instead of a blank square.
- **Tapping a notification opens the right screen**: an alert opens your Alerts inbox, a chat
  message opens Group Chat, a finished challenge opens Challenges, a flash sale opens the shop,
  an update opens Settings.
- **Reply straight from a chat notification** without opening the app.
- **Grouping**: a burst of alerts or level-ups folds into one expandable summary instead of
  flooding the tray. Chat messages stack into one conversation.
- New **Chat messages** switch in Settings. You won't be notified about your own messages, or while
  you're already looking at the chat.

## Background check-ins

Notifications no longer need the app to be open. About every 15 minutes, Android's own scheduler
wakes Dead-Fest for a few seconds to look for new personal alerts, chat messages, admin broadcasts,
flash sales and app updates, and shows anything new as a normal notification.

- No server and no paid Firebase plan: it reads the database directly with your saved login.
- Android decides the exact timing to save battery, so a notification can take up to ~15 minutes
  (sometimes longer on phones with aggressive battery savers). Only runs with an internet connection.
- Skipped while the app is open, because the app already shows things live.
- Can be switched off in Settings → System → **Background check-ins**. Logging out stops it.

## Weekly challenges (More → Challenges)

Four challenges each week, the same for everyone, picked from a pool of 11: spin the Wheel 10 or
25 times, scratch 5 cards, make 3 Black Market purchases, land 2 curses, survive 3 days, claim
rations 5 days, send 15 chat messages, share 2 images, comment on 5 images, react to 10 images.

- Each one pays **coins and XP** when you claim it (60 to 300 coins).
- Progress counts automatically. You get a notification the moment one is complete.
- New challenges every Monday. The screen shows how long is left, and the More tab shows a badge
  when something is ready to claim.

## Reactions and comments on the gallery

Open any image in Shared Images:

- **React** with 🔥 💀 😂 😮 ❤️ 👍. One reaction per survivor; tap yours again to remove it, or tap
  another to switch.
- **Comment** (up to 300 characters). The uploader gets an alert when someone comments.
- You can delete your own comments. The uploader can delete comments on their image, and admins
  can delete any.
- Deleting an image also removes its reactions and comments.

## The whole app is ready for translation

Around 1,000 pieces of text from the older screens (Dashboard, Shop, Wheel, Players, Settings,
Admin, Survival Intel and more) now live in `strings.xml`, so Dead-Fest can be translated into any
language by copying one file. See [docs/TRANSLATING.md](https://github.com/CyberLifeYT/Dead-Fest/blob/main/docs/TRANSLATING.md).
The English text is unchanged.

## Under the hood

- Security rules cover reactions (fixed emoji set, one per survivor) and comments (your own
  name only, 1–300 characters). 63 rules tests pass, 7 of them new.
- 100 unit tests pass, including new weekly challenge tests (week boundaries, the same picks for
  everyone, progress and claiming).
- Fixed spacing in a few messages, such as "Starts in 2 days", where Android was trimming spaces.
