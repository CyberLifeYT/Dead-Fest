# Dead-Fest v2.0.6 (Build 9)

**Platform:** Android, minSdk 24 (Android 7.0) to targetSdk 37.
**Stack:** Kotlin, Jetpack Compose, Material 3, Firebase (Auth, Firestore, Cloud Messaging).
**Release APK:** 3.83 MB (R8 on). No new dependencies.

---

## 🔋 Battery Optimisation Setting
- **First launch:** a one-time "KEEP DEAD-FEST AWAKE?" prompt. ALLOW opens Android's own "unrestricted battery" dialog.
- **Settings → System → Background Activity** shows the live status. Battery optimisation shows as ON or OFF, with a **DISABLE** button. If you've restricted background usage yourself (Android 9+), it shows that too, with a button to open App info.
- **Live re-check:** the status refreshes as soon as you return from system settings.
- **Fallbacks:** if a phone blocks the direct prompt, the app opens the battery optimisation list, then App info.
- **Permission:** the manifest now declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. That's fine for a sideloaded app, but Google Play restricts it.
- **What it doesn't do:** Dead-Fest doesn't run a background service, so this costs no extra battery. It keeps push alerts and OTA downloads from being held back by the phone's battery saver.

## 🟢 Online / Offline Status
- **Online** while you're in the app. A heartbeat refreshes `playerData.lastSeen` every 60 s.
- **Offline** the moment the app leaves the screen. Rotating the phone doesn't count.
- **Crashes, killed app or lost signal:** the heartbeat stops and you show as offline within 2.5 minutes. No ghost "online" survivors.
- **Shown everywhere:**
  - Green online dots and online-first sorting on Players.
  - **"OFFLINE · last seen 5m ago"** in the Dossier.
  - A real online count on the Dashboard (the old `onlineCount` field was never updated).
  - The Admin Live Dashboard.
  - The home-screen widget.
- **One rule:** everything above uses the same presence check (`domain/Presence.kt`).

## 🖼️ Images (Utility Hub → IMAGES)
- **Share:** pick a photo with the system photo picker (no storage permission needed) and add an optional caption (140 characters).
- **Every survivor sees it** in a live grid, newest first.
- **Viewer:** tap a thumbnail for full screen with pinch-to-zoom. The uploader or an admin can delete it.
- **Compression:**
  - EXIF rotation is fixed.
  - The image is scaled to at most 1600 px and compressed to ≤ 700 KB.
  - A 320 px thumbnail is made for the grid.
  - The full photo is never decoded at full resolution, so it won't run old phones out of memory.
- **Low-end friendly:**
  - The grid only downloads thumbnails, about 20 KB each.
  - The full image loads only when opened, then stays in the offline cache.
  - Thumbnails decode off the main thread into an 8 MB cache.
  - Zooming runs in the draw layer, so pinching doesn't redraw the screen.
- **Offline uploads** are queued and sent when you're back online.
- **Feed:** shares appear in the live feed ("X shared an image").

## ⚙️ Firestore Rules To Add
The gallery uses two new collections. Add this inside `match /databases/{database}/documents { ... }`:

```
function signedIn() { return request.auth != null; }
function isAdmin() {
  return signedIn() && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.admin == true;
}

match /images/{id} {
  allow read: if signedIn();
  allow create: if signedIn()
    && request.resource.data.uploaderUid == request.auth.uid
    && request.resource.data.thumb.size() < 100 * 1024;
  allow delete: if signedIn() && (resource.data.uploaderUid == request.auth.uid || isAdmin());
}

match /imageBlobs/{id} {
  allow read: if signedIn();
  allow create: if signedIn()
    && request.resource.data.uploaderUid == request.auth.uid
    && request.resource.data.data.size() < 900 * 1024;
  allow delete: if signedIn() && (resource.data.uploaderUid == request.auth.uid || isAdmin());
}
```

Presence only writes to each survivor's own `users/{uid}` document, which your rules already allow.

## Why Not Peer-to-Peer?
- **Phones can't act as servers to each other.** On mobile data and most home Wi-Fi (NAT, carrier-grade NAT), a phone can't accept incoming connections from other phones.
- **Closed apps can't serve anything.** Images on a phone that's offline would vanish for everyone else.
- **P2P still needs servers.** WebRTC needs a signalling server, plus STUN/TURN relays when direct connections fail.
- **Local P2P only covers the same room.** Wi-Fi Direct and Nearby work only between phones close together.

So presence and images ride on the Firestore backend the app already uses. It relays and caches data for every survivor, and there's still no server for you to run.

## Storage Budget
- Firestore's free tier holds 1 GiB, roughly 1,400 shared images at the size limit; typical photos come in well under it.
- The gallery streams the newest 120.
- Cloud Storage wasn't used: new Firebase Storage buckets require the paid Blaze plan.
