/* =============================================================================
   Dead-Fest desktop — configuration and constants
   ========================================================================== */
window.DF = window.DF || {};

DF.FIREBASE = {
  // The same Firebase project the Android app talks to, so accounts and data are
  // shared. This is the Android API key from app/google-services.json: it is
  // accepted by the Identity Toolkit's web endpoint (verified), which is what
  // lets a desktop client authenticate against the existing accounts.
  apiKey: "AIzaSyAlaJ6CKPTvf3V0fGrkMp2XgSU4CbDUP-M",
  authDomain: "dead-fest-b4fc2.firebaseapp.com",
  projectId: "dead-fest-b4fc2",
  storageBucket: "dead-fest-b4fc2.firebasestorage.app",
  messagingSenderId: "883158666114",
  appId: "1:883158666114:android:74fc24f6045c9cbfcb9d9b"
};

/* ---- App themes (mirrors ui/theme/Palette.kt) -----------------------------
   The semantic accents are identical in every theme on purpose; only the ink
   scale and the brand change. */
DF.THEMES = [
  { id: "default",  name: "Default",       ink0: "#08080a", ink2: "#131318", ink3: "#1c1c23", ink4: "#26262f", line: "#272730", ember: "#ff4b4b", glow: "#ff8a5b" },
  { id: "halloween", name: "Halloween",     ink0: "#0a0812", ink2: "#171126", ink3: "#201735", ink4: "#2a1f45", line: "#2e2547", ember: "#ff7a18", glow: "#ffb347" },
  { id: "christmas", name: "Christmas",     ink0: "#070d0a", ink2: "#0f1c16", ink3: "#15271f", ink4: "#1c3229", line: "#22382f", ember: "#d62828", glow: "#ff6b6b" },
  { id: "toxic",     name: "Toxic Storm",   ink0: "#080d08", ink2: "#111b11", ink3: "#172517", ink4: "#1e2f1e", line: "#243424", ember: "#84cc16", glow: "#bef264" },
  { id: "blood",     name: "Blood Moon",    ink0: "#0d0809", ink2: "#1c1113", ink3: "#26171a", ink4: "#301d21", line: "#382428", ember: "#b91c1c", glow: "#ef4444" }
];

/* ---- The game database key -----------------------------------------------
   Client-side, exactly like the Android build: it ships inside the app and is
   therefore readable by anyone who unpacks it. The request screen degrades to
   manual entry when the lookup is unavailable, so a dead service never blocks
   the feature. */
DF.THEGAMESDB_API_KEY = "c07a0f90c7365d5401fbcc6b0aba5edd9168a1c964874432d7e76565215b818";

/* ---- Firestore paths (mirrors FirebaseRepository) ------------------------- */
DF.PATHS = {
  users: "users",
  global: "gameState/global",
  eventLog: "eventLog",
  messages: "messages",
  votes: "votes",
  archives: "archives",
  scratchPrizes: "scratchPrizes",
  levelRewards: "levelRewards",
  images: "images",
  imageBlobs: "imageBlobs",
  usernames: "usernames",
  gameRequests: "gameRequests"
};

DF.DEFAULT_GAME = "Dead Island 2";
DF.STARTING_COINS = 250;
DF.MAX_GALLERY = 120;

/* ---- Handle rules (mirrors domain/UsernameRules.kt) ----------------------- */
DF.USERNAME = {
  min: 3,
  max: 20,
  pattern: /^[A-Za-z][A-Za-z0-9_]{2,19}$/,
  reserved: ["admin", "administrator", "deadfest", "dead_fest", "moderator", "mod", "support", "official", "root", "system", "null", "undefined"]
};

DF.usernameError = function (raw) {
  const name = (raw || "").trim();
  if (!name) return "Enter a handle first.";
  if (name.length < DF.USERNAME.min) return `Handles need at least ${DF.USERNAME.min} characters.`;
  if (name.length > DF.USERNAME.max) return `Handles can be at most ${DF.USERNAME.max} characters.`;
  if (!DF.USERNAME.pattern.test(name)) return "Letters, numbers and underscores only — and it must start with a letter.";
  if (DF.USERNAME.reserved.indexOf(name.toLowerCase()) >= 0) return "That handle is reserved.";
  return null;
};
