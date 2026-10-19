# The admin side

Everything an administrator can do, how it is protected, and how to publish a change to the rules
without breaking the app.

---

## The admin surface

Both clients show the same console, in the same twelve modules:

| Module | What it does |
|---|---|
| **Live** | Online count, survivors, spins due, global eliminations, the event feed. |
| **Players** | Every survivor: coins, deaths, admin rights, death injection, clearing penalties. |
| **Shop** | Enable, reprice and re-tier every Black Market item. |
| **Scratch** | The scratch prize pool and its weights. |
| **Sectors** | Add, rename, reorder, lock and remove the sectors deaths are recorded against. |
| **Event** | The next Dead-Fest: name, start time, blurb and the game line-up. |
| **Overload** | Start and stop a coin multiplier on one sector. |
| **Wheel** | The Wheel of Fate slices: labels, values, weights, colours. |
| **Requests** | The community game queue: approve or remove. |
| **Global** | System switches, the Forced Spin Protocol kill switch, and firmware publishing. |
| **Backup** | Archive a season, or restore one. |
| **Broadcast** | Post a message to the live feed and push notifications. |

Admin rights live on the survivor's own document (`users/{uid}.admin`). Promoting somebody is a
deliberate act in **Admin → Players**, and the rules make it impossible for a survivor to do it to
themselves.

---

## What the rules protect, and what they cannot

[`firestore.rules`](../firestore.rules) is the security boundary. It is enforced by Firestore, not
by the app, so it holds no matter what a client does.

**Enforced by the rules:**

- Only an admin can write the global document — which carries the update URL every survivor is
  told to download, the economy settings, the sector list and every system switch. This is the
  single most valuable rule in the file.
- Only an admin can change the `admin` flag, and nobody can arrive with it already set.
- Only an admin can approve or remove a game request.
- Only an admin can edit the shop, the wheel, the scratch pool, the sectors, the reward track and
  the season archives.
- A handle can only be claimed by creating its registry document, so a handle already taken
  cannot be overwritten by a second player.
- A survivor can only write their own document, apart from the four fields the app legitimately
  writes on other people's behalf: coins and bankruptcy level for the mutual survival reward, and
  a death and a shield for a curse.
- The global death counter can only move one step at a time, so it cannot be inflated or zeroed.

**Not enforced, and honestly so:**

- **The economy is client-trusted.** A survivor can write their own coins, XP and level, because
  those are the same writes the app makes when you spin the wheel or buy something. Rules cannot
  tell those two apart — the operation looks identical. This is the same trade the app has always
  made, and it is documented rather than hidden.

  The real fix is to move the economy into Cloud Functions, where the server decides what a spin
  pays. That needs the Blaze plan.

  In the meantime, the blast radius of a cheat is one survivor's own record.

---

## The rules engine's evaluation, which is not what you would guess

Two things about Firestore rules are worth knowing, because both produce confusing errors:

1. **The engine evaluates every operand of `&&`.** It only folds away a literal `false` on the
   left. So `exists(path) && get(path).data.field == true` does **not** protect against the
   `get()` — it is evaluated anyway, and a `get()` on a document that does not exist is an
   evaluation error rather than a clean refusal.

2. **`resource` is null on a create.** Any reference to it in a rule that the engine also evaluates
   for a create is an evaluation error. The rules are written to survive this: the error makes the
   *update* rule refuse, which is the right answer for a create, and the *create* rule decides on
   its own.

The practical consequence of the first point: a survivor whose profile document is missing cannot
write anything, because the engine cannot tell whether they are an admin. That is the safe way
round, and the app repairs the document on the next launch (see `ensureProfile` in the repository),
so it only ever affects a genuinely broken account.

---

## Publishing a change to the rules

The rules are deployed with the Firebase CLI:

```bash
npm install -g firebase-tools
firebase login
firebase deploy --only firestore:rules
```

The project is already selected in `.firebaserc` (`dead-fest-b4fc2`).

**Before you deploy, run the tests.** A rule file nobody has run is a rule file that is either
broken — the app cannot write — or wide open.

From the repository root:

```bash
npm run test:rules
```

That installs the test harness and starts the Firestore emulator, which decides every case against
the real `firestore.rules` — so the tests answer the question that actually matters: does the app
still work, and is the admin boundary still shut?

The same job runs on every push to `main` (`.github/workflows/firestore-rules.yml`), so a rule that
breaks the app fails the build.

### After deploying

1. Open the app as a **survivor** and record a death, spin the wheel and buy something. If the
   rules are too strict, these fail with "permission denied" and the app says so.
2. Open **Admin → Global** and change a system switch. If the rules are too loose, this succeeds
   when it should not.
3. Rename your handle and submit a game request. Those two write to the collections added in
   v2.0.8, which had no rules at all before this file existed.

---

## The trust model in one paragraph

The app is a closed community of friends, and the rules reflect that: signed-in survivors can see
each other, and only admins can change the world. The one thing that is genuinely protected is the
update URL — if a survivor could repoint it, the whole install base would be told to download
somebody else's APK, and the only thing standing in the way would be the requester's signature.
Everything else is either an admin action or a decision the client makes about itself.
