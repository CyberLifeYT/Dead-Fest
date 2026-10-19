/* =============================================================================
   Dead-Fest desktop — Firebase data layer
   -----------------------------------------------------------------------------
   A port of data/repository/FirebaseRepository.kt. Every write goes through the
   same Firestore transactions the Android app uses, against the same documents,
   so both clients see identical state and identical rules.

   The mobile app is the reference: paths, field names and transaction bodies are
   copied from it rather than invented here.
   ========================================================================== */
window.DF = window.DF || {};

DF.Data = (function () {
  let app = null;
  let auth = null;
  let db = null;
  const unsubs = [];

  const state = {
    user: null,        // the signed-in survivor's own document
    users: [],         // every survivor
    global: null,      // gameState/global
    events: [],        // eventLog
    messages: [],
    votes: [],
    archives: [],
    scratchPrizes: [],
    levelRewards: [],
    images: [],
    gameRequests: []
  };

  const listeners = { user: [], users: [], global: [], events: [], messages: [], votes: [], archives: [], scratch: [], rewards: [], images: [], requests: [] };

  function emit(key) { listeners[key].forEach((fn) => { try { fn(state[key]); } catch (e) { console.error(e); } }); }

  /* ---- boot --------------------------------------------------------------- */
  function init() {
    if (app) return;
    app = firebase.initializeApp(DF.FIREBASE);
    auth = firebase.auth();
    db = firebase.firestore();
    // The web SDK's long-polling is not needed for a desktop client with a real socket, but
    // leaving it on keeps behaviour identical to the browser SDK defaults.
    try { db.settings({ ignoreUndefinedProperties: true }, { merge: true }); } catch (e) { /* already set */ }
  }

  function onAuthStateChanged(cb) {
    init();
    return auth.onAuthStateChanged((user) => {
      if (user) {
        state.user = null;
        subscribeAll(user.uid);
      } else {
        state.user = null;
        state.users = [];
        state.global = null;
        teardown();
      }
      cb(user);
    });
  }

  function teardown() {
    while (unsubs.length) { try { unsubs.pop()(); } catch (e) { /* ignore */ } }
  }

  function sub(target, key, mapper) {
    unsubs.push(target.onSnapshot(
      (snap) => {
        state[key] = mapper ? mapper(snap) : snap;
        emit(key);
      },
      (err) => {
        console.error("Firestore listener failed for " + key, err);
        toast("Live data error: " + (err && err.message ? err.message : "permission denied"), true);
      }
    ));
  }

  /**
   * Makes sure the signed-in survivor has a profile document.
   *
   * The security rules decide whether a write is allowed by reading the admin flag off this
   * document, so a survivor without one cannot write anything - not even their own record. A
   * sign-up that lost its connection half way through leaves an account with no profile, and
   * that account would be locked out of the whole game. Repairing it here means the one
   * document everything depends on always exists.
   */
  function ensureProfile(uid) {
    return userRef(uid).get().then((snap) => {
      if (snap.exists) return null;
      return userRef(uid).set({
        uid,
        displayName: "",
        email: (auth.currentUser && auth.currentUser.email) || "",
        admin: false,
        username: "",
        playerData: { coins: DF.STARTING_COINS, level: 1, xp: 0, games: {} }
      });
    }).catch(() => null);
  }

  /* ---- subscriptions ------------------------------------------------------ */
  function subscribeAll(uid) {
    teardown();
    ensureProfile(uid);
    sub(db.collection(DF.PATHS.users).orderBy("playerData.coins", "desc"), "users", mapUsers);
    sub(db.doc(DF.PATHS.global), "global", mapGlobal);
    sub(db.collection(DF.PATHS.eventLog).orderBy("timestamp", "desc").limit(50), "events");
    sub(db.collection(DF.PATHS.messages).orderBy("timestamp", "desc").limit(100), "messages");
    sub(db.collection(DF.PATHS.votes), "votes");
    sub(db.collection(DF.PATHS.archives).orderBy("timestamp", "desc"), "archives");
    sub(db.collection(DF.PATHS.scratchPrizes), "scratchPrizes");
    sub(db.collection(DF.PATHS.levelRewards), "levelRewards");
    sub(db.collection(DF.PATHS.images).orderBy("createdAt", "desc").limit(DF.MAX_GALLERY), "images");
    sub(db.collection(DF.PATHS.gameRequests).orderBy("createdAt", "desc"), "gameRequests");
    sub(db.collection(DF.PATHS.users).doc(uid), "user", (snap) => {
      const d = snap.exists ? snap.data() : null;
      if (!d) return null;
      d.uid = snap.id;
      return d;
    });
  }

  /* ---- mapping ------------------------------------------------------------ */
  function mapGlobal(doc) {
    if (!doc || !doc.exists) return null;
    const d = doc.data() || {};
    const num = (v, fallback) => (typeof v === "number" && isFinite(v) ? v : fallback);
    const list = (v) => (Array.isArray(v) ? v.filter((x) => typeof x === "string") : []);
    return {
      marketEnabled: d.marketEnabled !== false,
      wheelEnabled: d.wheelEnabled !== false,
      reviveEnabled: d.reviveEnabled !== false,
      multiplier: num(d.multiplier, 1),
      sectorOverload: d.sectorOverload === true,
      flashSaleActive: d.flashSaleActive === true,
      featuredVideoUrl: d.featuredVideoUrl || "",
      showFeaturedVideo: d.showFeaturedVideo === true,
      lockdownActive: d.lockdownActive === true,
      grandTotal: num(d.grandTotal, 0),
      onlineCount: num(d.onlineCount, 0),
      latestVersionName: d.latestVersionName || "1.5.9",
      latestVersionCode: num(d.latestVersionCode, 1),
      updateUrl: d.updateUrl || "",
      updateUrlR8: d.updateUrlR8 || "",
      updateNotes: d.updateNotes || "",
      forceUpdate: d.forceUpdate === true,
      forceSpinProtocol: d.forceSpinProtocol !== false,
      games: list(d.games),
      lockedGames: list(d.lockedGames),
      overloadedSector: d.overloadedSector || "",
      overloadEndsAt: num(d.overloadEndsAt, 0),
      currentSeasonName: d.currentSeasonName || "Dead-Fest Season 1",
      nextEventEnabled: d.nextEventEnabled === true,
      nextEventName: d.nextEventName || "",
      nextEventStartsAt: num(d.nextEventStartsAt, 0),
      nextEventGames: list(d.nextEventGames),
      nextEventNotes: d.nextEventNotes || "",
      wheelItems: (Array.isArray(d.wheelItems) ? d.wheelItems : []).map((it) => ({
        id: (it && it.id) || "",
        label: (it && it.label) || "",
        value: it && it.value !== undefined ? String(it.value) : "",
        actionType: (it && it.actionType) || "give",
        color: (it && it.color) || "#FF0000",
        enabled: !it || it.enabled !== false,
        weight: it && typeof it.weight === "number" ? it.weight : 1
      })),
      economy: DF.Economy.parseConfig(d)
    };
  }

  function mapUsers(snap) {
    const out = [];
    if (!snap) return out;
    snap.forEach((doc) => {
      const d = doc.data() || {};
      const pd = d.playerData || {};
      out.push({
        uid: doc.id,
        displayName: d.displayName || "",
        username: d.username || "",
        email: d.email || "",
        admin: d.admin === true,
        playerData: {
          coins: num(pd.coins, 0),
          luckyCoin: pd.luckyCoin === true,
          avatar: pd.avatar || "💀",
          color: pd.color || "#536DFE",
          bankruptcyLevel: num(pd.bankruptcyLevel, 0),
          games: pd.games && typeof pd.games === "object" ? pd.games : {},
          pendingSpins: num(pd.pendingSpins, 0),
          shield: pd.shield === true,
          title: pd.title || "Survivor",
          isOnline: pd.isOnline === true,
          dailyUpgradeOwned: pd.dailyUpgradeOwned === true,
          lastDailyClaim: num(pd.lastDailyClaim, 0),
          lastDeathAt: num(pd.lastDeathAt, 0),
          insurance: pd.insurance === true,
          insulationTier: num(pd.insulationTier, 0),
          xp: num(pd.xp, 0),
          level: num(pd.level, 1),
          prestigeCount: num(pd.prestigeCount, 0),
          avatarFrame: pd.avatarFrame || "none",
          ownedTitles: Array.isArray(pd.ownedTitles) ? pd.ownedTitles : [],
          lastSeen: pd.lastSeen && pd.lastSeen.toMillis ? pd.lastSeen.toMillis() : num(pd.lastSeen, 0)
        }
      });
    });
    return out;
  }

  function num(v, fallback) { return typeof v === "number" && isFinite(v) ? v : fallback; }

  /* ---- auth --------------------------------------------------------------- */
  function login(email, password) {
    init();
    return auth.signInWithEmailAndPassword(email.trim(), password);
  }

  function signup(name, email, password) {
    init();
    return auth.createUserWithEmailAndPassword(email.trim(), password).then((cred) => {
      const uid = cred.user.uid;
      return db.collection(DF.PATHS.users).doc(uid).set({
        uid,
        displayName: (name || "").trim(),
        email: email.trim(),
        admin: false,
        username: "",
        playerData: { coins: DF.STARTING_COINS, level: 1, xp: 0, games: {} }
      }).then(() => cred);
    });
  }

  function logout() { return auth.signOut(); }

  function currentUser() { return auth ? auth.currentUser : null; }

  /* ---- writes ------------------------------------------------------------- */
  function userRef(uid) { return db.collection(DF.PATHS.users).doc(uid); }

  function bumpGrandTotal(delta) {
    if (!delta) return;
    db.doc(DF.PATHS.global).update("grandTotal", firebase.firestore.FieldValue.increment(delta))
      .catch(() => { /* best effort, never blocks a death */ });
  }

  function logEvent(category, message, userUid, targetUid) {
    return db.collection(DF.PATHS.eventLog).add({
      id: String(Date.now()),
      category, message,
      userUid: userUid || null,
      targetUid: targetUid || null,
      timestamp: firebase.firestore.FieldValue.serverTimestamp()
    }).catch(() => { /* the feed is not worth failing an action over */ });
  }

  function updateGlobalState(map) {
    return db.doc(DF.PATHS.global).set(map, { merge: true });
  }

  function setOnline(uid, isOnline) {
    return userRef(uid).update({ "playerData.isOnline": isOnline }).catch(() => {});
  }

  function heartbeat(uid) {
    return userRef(uid).update({
      "playerData.lastSeen": firebase.firestore.FieldValue.serverTimestamp(),
      "playerData.isOnline": true
    }).catch(() => {});
  }

  function goOffline(uid) {
    return userRef(uid).update({
      "playerData.lastSeen": firebase.firestore.FieldValue.serverTimestamp(),
      "playerData.isOnline": false
    }).catch(() => {});
  }

  /** Records a death: the full v2.0.5 pipeline, in one transaction (mirrors recordDeath). */
  function recordDeath(uid, game, cfg, now, forceSpinProtocol) {
    const ref = userRef(uid);
    return db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const d = snap.data();
      if (!d) throw new Error("Survivor record not found");
      const pd = d.playerData || {};
      const outcome = DF.Economy.resolveDeath({
        coins: pd.coins || 0,
        bankruptcyLevel: pd.bankruptcyLevel || 0,
        pendingSpins: pd.pendingSpins || 0,
        insulationTier: pd.insulationTier || 0,
        insulationReductionPct: DF.Economy.insulationReduction(pd.insulationTier || 0, cfg),
        insuranceActive: pd.insurance === true,
        insurancePct: cfg.insurancePct,
        forceSpinProtocol: forceSpinProtocol !== false
      });
      const xp = DF.Economy.addXp(pd.xp || 0, pd.level || 1, cfg.xp.death, cfg.xp);
      const games = pd.games || {};
      const stat = games[game] || { deaths: 0 };
      const deaths = (stat.deaths || 0) + 1;

      tx.update(ref, { [`playerData.games.${game}.deaths`]: deaths });
      const update = {
        "playerData.coins": outcome.coinsAfter,
        "playerData.bankruptcyLevel": outcome.levelAfter,
        "playerData.pendingSpins": outcome.pendingAfter,
        "playerData.lastDeathAt": now,
        "playerData.xp": xp.xp,
        "playerData.level": xp.level
      };
      if (outcome.tax.insuranceUsed) update["playerData.insurance"] = false;
      tx.update(ref, update);
      return { game, outcome, xp };
    }).then((result) => { bumpGrandTotal(1); return result; });
  }

  /** Pays the Mutual Survival Reward to every other survivor. Best effort. */
  function payMutualReward(deadUid, cfg) {
    if (!cfg.survivalReward || cfg.survivalReward <= 0) return Promise.resolve(0);
    return db.collection(DF.PATHS.users).get().then((snap) => {
      const refs = snap.docs.map((d) => d.ref).filter((r) => r.id !== deadUid);
      if (!refs.length) return 0;
      return db.runTransaction(async (tx) => {
        const snaps = await Promise.all(refs.map((r) => tx.get(r)));
        let paid = 0;
        for (const s of snaps) {
          const d = s.data();
          if (!d) continue;
          const pd = d.playerData || {};
          const amount = DF.Economy.payoutFor(pd.bankruptcyLevel || 0, cfg.survivalReward);
          if (amount <= 0) continue;
          const coins = (pd.coins || 0) + amount;
          tx.update(s.ref, {
            "playerData.coins": coins,
            "playerData.bankruptcyLevel": DF.Economy.afterBalanceChange(pd.bankruptcyLevel || 0, coins, pd.pendingSpins || 0)
          });
          paid++;
        }
        return paid;
      });
    }).catch(() => -1);
  }

  /** Applies a Wheel of Fate result (mirrors applyWheelResult). */
  function applyWheelResult(uid, item, cfg) {
    const ref = userRef(uid);
    return db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const d = snap.data();
      if (!d) return { delta: 0, luckyCoinUsed: false };
      const pd = d.playerData || {};
      const amount = DF.Economy.coinAmount(item);
      const take = DF.Economy.isTake(item);
      let lucky = false;
      let delta = 0;
      if (amount !== null) {
        if (take) delta = -Math.min(amount, pd.coins || 0);
        else { lucky = pd.luckyCoin === true; delta = lucky ? amount * 2 : amount; }
      }
      const penalty = (pd.pendingSpins || 0) > 0;
      const pending = penalty ? (pd.pendingSpins || 0) - 1 : Math.max(0, pd.pendingSpins || 0);
      const coins = (pd.coins || 0) + delta;
      const level = DF.Economy.afterBalanceChange(pd.bankruptcyLevel || 0, coins, pending);
      const xp = DF.Economy.addXp(pd.xp || 0, pd.level || 1, cfg.xp.spin + (penalty ? cfg.xp.penaltySpin : 0), cfg.xp);
      const update = {
        "playerData.coins": coins,
        "playerData.bankruptcyLevel": level,
        "playerData.pendingSpins": pending,
        "playerData.xp": xp.xp,
        "playerData.level": xp.level
      };
      if (lucky) update["playerData.luckyCoin"] = false;
      tx.update(ref, update);
      return { delta, luckyCoinUsed: lucky, penaltyCleared: penalty, pendingLeft: pending, bankruptcyLevel: level, xp };
    });
  }

  /** Buys and applies a Black Market item in one transaction. */
  function applyShopItem(uid, item, cfg, targetUid, sector) {
    const buyerRef = userRef(uid);
    const targetRef = targetUid ? userRef(targetUid) : null;
    const price = DF.Economy.priceIn(item, cfg);

    return db.runTransaction(async (tx) => {
      const buyerSnap = await tx.get(buyerRef);
      const buyer = buyerSnap.data();
      if (!buyer) return { outcome: "TARGET_MISSING" };
      const data = buyer.playerData || {};

      let target = null;
      if (item.curseDeaths > 0) {
        if (!targetRef) return { outcome: "TARGET_MISSING" };
        const targetSnap = await tx.get(targetRef);
        target = targetSnap.data();
        if (!target) return { outcome: "TARGET_MISSING" };
      }

      if (!DF.Economy.enabledIn(item, cfg)) return { outcome: "DISABLED" };

      // Ownership checks come before the price check, exactly as in the mobile app.
      if (item.id === "shield" && data.shield) return { outcome: "ALREADY_ACTIVE" };
      if (item.id === "lucky_coin" && data.luckyCoin) return { outcome: "ALREADY_ACTIVE" };
      if (item.id === "insurance" && data.insurance) return { outcome: "ALREADY_ACTIVE" };
      if (item.id === "ration_upgrade" && data.dailyUpgradeOwned) return { outcome: "ALREADY_ACTIVE" };
      if (item.tier > 0) {
        if ((data.insulationTier || 0) === item.tier) return { outcome: "ALREADY_ACTIVE" };
        if ((data.insulationTier || 0) !== item.requiresTier) return { outcome: "TIER_LOCKED" };
      }
      if ((data.coins || 0) < price) return { outcome: "INSUFFICIENT_FUNDS" };

      const xp = DF.Economy.addXp(data.xp || 0, data.level || 1, cfg.xp.purchase, cfg.xp);
      const coins = (data.coins || 0) - price;
      const update = {
        "playerData.coins": coins,
        "playerData.bankruptcyLevel": DF.Economy.afterBalanceChange(data.bankruptcyLevel || 0, coins, data.pendingSpins || 0),
        "playerData.xp": xp.xp,
        "playerData.level": xp.level
      };
      let deathDelta = 0;
      let sectorName = null;

      if (item.id === "shield") update["playerData.shield"] = true;
      else if (item.id === "lucky_coin") update["playerData.luckyCoin"] = true;
      else if (item.id === "insurance") update["playerData.insurance"] = true;
      else if (item.id === "ration_upgrade") update["playerData.dailyUpgradeOwned"] = true;
      else if (item.tier > 0) update["playerData.insulationTier"] = item.tier;
      else if (item.id === "revive") {
        const games = data.games || {};
        const worst = Object.keys(games)
          .map((k) => ({ k, deaths: (games[k] || {}).deaths || 0 }))
          .filter((g) => g.deaths > 0)
          .sort((a, b) => b.deaths - a.deaths)[0];
        if (!worst) return { outcome: "NO_DEATHS" };
        sectorName = worst.k;
        deathDelta = -1;
        tx.update(buyerRef, { [`playerData.games.${worst.k}.deaths`]: Math.max(0, worst.deaths - 1) });
      } else if (item.id === "curse" || item.id === "double_curse") {
        const victim = target.playerData || {};
        if (victim.shield) {
          tx.update(targetRef, { "playerData.shield": false });
          return { outcome: "BLOCKED_BY_SHIELD" };
        }
        const sector = sectorName || (Object.keys(victim.games || {})[0]) || DF.DEFAULT_GAME;
        const stat = (victim.games || {})[sector] || { deaths: 0 };
        const deaths = (stat.deaths || 0) + item.curseDeaths;
        tx.update(targetRef, { [`playerData.games.${sector}.deaths`]: deaths });
        deathDelta = item.curseDeaths;
        sectorName = sector;
      }
      tx.update(buyerRef, update);
      return { outcome: "APPLIED", sector: sectorName, deathDelta, xp };
    }).then((r) => { bumpGrandTotal(r.deathDelta || 0); return r; });
  }

  /** Claims the daily rations (mirrors claimDaily). */
  function claimDaily(uid, cfg, now) {
    const ref = userRef(uid);
    return db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const d = snap.data();
      if (!d) throw new Error("Survivor record not found");
      const pd = d.playerData || {};
      if (!DF.Economy.canClaimDaily(pd.lastDailyClaim || 0, now)) {
        return { amount: 0, newBalance: pd.coins || 0, upgraded: false, survivalBonusXp: 0, claimed: false };
      }
      const amount = pd.dailyUpgradeOwned ? cfg.dailyUpgraded : cfg.dailyBase;
      const survived = (pd.lastDailyClaim || 0) > 0 && (pd.lastDeathAt || 0) < pd.lastDailyClaim;
      const bonusXp = survived ? cfg.xp.survivalDay : 0;
      const xp = bonusXp > 0 ? DF.Economy.addXp(pd.xp || 0, pd.level || 1, bonusXp, cfg.xp) : null;
      const coins = (pd.coins || 0) + amount;
      const update = {
        "playerData.coins": coins,
        "playerData.bankruptcyLevel": DF.Economy.afterBalanceChange(pd.bankruptcyLevel || 0, coins, pd.pendingSpins || 0),
        "playerData.lastDailyClaim": now
      };
      if (xp) { update["playerData.xp"] = xp.xp; update["playerData.level"] = xp.level; }
      tx.update(ref, update);
      return { amount, newBalance: coins, upgraded: !!pd.dailyUpgradeOwned, survivalBonusXp: bonusXp, xp, claimed: true };
    });
  }

  /** Prestige: back to level 1, +1 prestige (mirrors prestige). */
  function prestige(uid, cfg) {
    const ref = userRef(uid);
    return db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const d = snap.data();
      if (!d) return null;
      const pd = d.playerData || {};
      if (!DF.Economy.canPrestige(pd.level || 1, cfg.xp)) return null;
      const count = (pd.prestigeCount || 0) + 1;
      tx.update(ref, { "playerData.level": 1, "playerData.xp": 0, "playerData.prestigeCount": count });
      return count;
    });
  }

  /* ---- admin -------------------------------------------------------------- */
  function setPlayerCoins(uid, coins) {
    return db.runTransaction(async (tx) => {
      const ref = userRef(uid);
      const snap = await tx.get(ref);
      const d = snap.data();
      if (!d) return 0;
      const pd = d.playerData || {};
      const safe = Math.max(0, coins);
      tx.update(ref, {
        "playerData.coins": safe,
        "playerData.bankruptcyLevel": DF.Economy.afterBalanceChange(pd.bankruptcyLevel || 0, safe, pd.pendingSpins || 0)
      });
      return safe;
    });
  }

  function adjustCoins(uid, delta) {
    return db.runTransaction(async (tx) => {
      const ref = userRef(uid);
      const snap = await tx.get(ref);
      const d = snap.data();
      if (!d) return 0;
      const pd = d.playerData || {};
      const coins = Math.max(0, (pd.coins || 0) + delta);
      tx.update(ref, {
        "playerData.coins": coins,
        "playerData.bankruptcyLevel": DF.Economy.afterBalanceChange(pd.bankruptcyLevel || 0, coins, pd.pendingSpins || 0)
      });
      return coins;
    });
  }

  function updatePlayerDeaths(uid, amount, gameName) {
    return db.runTransaction(async (tx) => {
      const ref = userRef(uid);
      const snap = await tx.get(ref);
      const d = snap.data();
      if (!d) return 0;
      const pd = d.playerData || {};
      const games = pd.games || {};
      const game = gameName || Object.keys(games)[0] || DF.DEFAULT_GAME;
      const current = (games[game] || {}).deaths || 0;
      const updated = Math.max(0, current + amount);
      tx.update(ref, { [`playerData.games.${game}.deaths`]: updated });
      return updated - current;
    }).then((delta) => { bumpGrandTotal(delta); return delta; });
  }

  function clearPenalties(uid) {
    const ref = userRef(uid);
    return db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const d = snap.data();
      if (!d) return;
      const pd = d.playerData || {};
      tx.update(ref, {
        "playerData.pendingSpins": 0,
        "playerData.bankruptcyLevel": (pd.coins || 0) > 0 ? DF.Economy.BANKRUPTCY.NONE : DF.Economy.BANKRUPTCY.LEVEL_1
      });
    });
  }

  function toggleAdminStatus(uid, isAdmin) {
    return userRef(uid).update({ admin: isAdmin });
  }

  function equipCosmetic(uid, field, value) {
    if (field !== "color" && field !== "avatarFrame") return Promise.reject(new Error("bad field"));
    return userRef(uid).update({ [`playerData.${field}`]: value });
  }

  /* ---- identity (v2.0.8) -------------------------------------------------- */
  /** Changes a handle: releases the old registry entry and claims the new one, atomically. */
  function changeUsername(uid, desired) {
    const name = (desired || "").trim();
    const problem = DF.usernameError(name);
    if (problem) return Promise.resolve({ outcome: "INVALID", reason: problem });
    const key = name.toLowerCase();

    return db.runTransaction(async (tx) => {
      const userSnap = await tx.get(userRef(uid));
      const current = userSnap.data();
      const currentKey = current && current.username ? current.username.trim().toLowerCase() : null;
      if (currentKey === key) return { outcome: "SAME" };

      const claimSnap = await tx.get(db.collection(DF.PATHS.usernames).doc(key));
      if (claimSnap.exists()) return { outcome: "TAKEN" };

      if (currentKey) tx.delete(db.collection(DF.PATHS.usernames).doc(currentKey));
      tx.set(db.collection(DF.PATHS.usernames).doc(key), { uid, display: name });
      tx.update(userRef(uid), { username: name });
      return { outcome: "OK" };
    }).catch(() => ({ outcome: "INVALID", reason: "Couldn't save that handle. Check your connection and try again." }));
  }

  /* ---- game requests (v2.0.8) --------------------------------------------- */
  function submitGameRequest(name, coverUrl, uid, byName) {
    const cleaned = (name || "").trim();
    if (cleaned.length < 2) return Promise.resolve({ outcome: "INVALID", reason: "Give the game a name first." });
    if (cleaned.length > 80) return Promise.resolve({ outcome: "INVALID", reason: "That name is too long." });

    return db.collection(DF.PATHS.gameRequests).get().then((snap) => {
      const clash = snap.docs
        .map((d) => d.data())
        .find((r) => String(r.name || "").trim().toLowerCase() === cleaned.toLowerCase());
      if (clash) {
        return {
          outcome: "INVALID",
          reason: clash.status === "approved"
            ? `"${clash.name}" is already an approved game.`
            : `"${clash.name}" is already waiting in the queue.`
        };
      }
      return db.collection(DF.PATHS.gameRequests).add({
        name: cleaned,
        coverUrl: (coverUrl || "").trim(),
        requestedBy: uid,
        requestedByName: byName,
        status: "pending",
        createdAt: Date.now(),
        decidedByName: ""
      }).then(() => ({ outcome: "OK" }));
    }).catch(() => ({ outcome: "INVALID", reason: "Couldn't send the request. Check your connection and try again." }));
  }

  function setGameRequestStatus(id, approved, decidedByName) {
    return db.collection(DF.PATHS.gameRequests).doc(id).update({
      status: approved ? "approved" : "pending",
      decidedByName: decidedByName || ""
    });
  }

  function removeGameRequest(id) {
    return db.collection(DF.PATHS.gameRequests).doc(id).delete();
  }

  /** Buys and resolves one Scratch Card (the prize is picked by the shared weighted RNG). */
  function scratchCard(uid, prize, cfg) {
    const ref = userRef(uid);
    return db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const d = snap.data();
      if (!d) throw new Error("Survivor record not found");
      const pd = d.playerData || {};
      const cost = Math.max(0, cfg.scratchCost);
      if ((pd.coins || 0) < cost) throw new Error(`Not enough coins (${cost} needed)`);

      let coins = (pd.coins || 0) - cost;
      let won = 0;
      let converted = false;
      const extra = {};
      const type = prize.type || "coins";
      if (type === "coins") {
        won = Math.max(0, parseInt(prize.value || "0", 10) || 0);
      } else if (type === "shield") {
        if (pd.shield) { won = DF.Economy.SHOP.find((s) => s.id === "shield").price; converted = true; }
        else extra["playerData.shield"] = true;
      } else if (type === "lucky_coin") {
        if (pd.luckyCoin) { won = DF.Economy.SHOP.find((s) => s.id === "lucky_coin").price; converted = true; }
        else extra["playerData.luckyCoin"] = true;
      }
      coins += won;
      const xp = DF.Economy.addXp(pd.xp || 0, pd.level || 1, cfg.xp.scratch, cfg.xp);
      const update = {
        "playerData.coins": coins,
        "playerData.bankruptcyLevel": DF.Economy.afterBalanceChange(pd.bankruptcyLevel || 0, coins, pd.pendingSpins || 0),
        "playerData.xp": xp.xp,
        "playerData.level": xp.level
      };
      Object.assign(update, extra);
      tx.update(ref, update);
      const isItem = type === "shield" || type === "lucky_coin";
      const outcome = isItem ? "ITEM" : (won >= cost && won > 0 ? "WIN" : "LOSS");
      return { prize, outcome, coinsWon: won, convertedItem: converted, xp };
    });
  }

  /* ---- admin content ------------------------------------------------------ */
  function seedScratchPrizes(prizes) {
    const batch = db.batch();
    prizes.forEach((p) => batch.set(db.collection(DF.PATHS.scratchPrizes).doc(), p));
    return batch.commit();
  }

  function equipTitle(uid, title) {
    return userRef(uid).update({
      "playerData.title": title,
      "playerData.ownedTitles": firebase.firestore.FieldValue.arrayUnion(title)
    });
  }

  /** Archives the season: snapshots every survivor, then resets coins, deaths and penalties. */
  function createArchive(name) {
    return db.collection(DF.PATHS.users).get().then((snap) => {
      const users = snap.docs.map((d) => Object.assign({ uid: d.id }, d.data() || {}));
      const snapshots = users.map((u) => {
        const pd = u.playerData || {};
        return {
          uid: u.uid,
          displayName: handleOf(u),
          coins: pd.coins || 0,
          totalDeaths: Object.values(pd.games || {}).reduce((a, s) => a + ((s && s.deaths) || 0), 0),
          gameDeaths: Object.fromEntries(Object.entries(pd.games || {}).map(([k, v]) => [k, (v && v.deaths) || 0])),
          level: pd.level || 1,
          xp: pd.xp || 0,
          prestigeCount: pd.prestigeCount || 0,
          title: pd.title || "Survivor",
          insulationTier: pd.insulationTier || 0,
          bankruptcyLevel: pd.bankruptcyLevel || 0,
          pendingSpins: pd.pendingSpins || 0,
          avatar: pd.avatar || "💀",
          avatarFrame: pd.avatarFrame || "none",
          color: pd.color || "#536DFE"
        };
      });
      return db.doc(DF.PATHS.global).get().then((g) => {
        const data = g.data() || {};
        const archive = {
          id: String(Date.now()),
          name,
          timestamp: Date.now(),
          playerSnapshots: snapshots,
          schemaVersion: 2,
          startedAt: 0,
          sectorRoster: data.games || [],
          grandTotalAtEnd: data.grandTotal || 0,
          notes: ""
        };
        return db.collection(DF.PATHS.archives).doc(archive.id).set(archive).then(() => {
          const batch = db.batch();
          users.forEach((u) => {
            const ref = userRef(u.uid);
            batch.update(ref, {
              "playerData.coins": DF.STARTING_COINS,
              "playerData.pendingSpins": 0,
              "playerData.bankruptcyLevel": DF.Economy.BANKRUPTCY.NONE
            });
            const pd = u.playerData || {};
            Object.keys(pd.games || {}).forEach((g) => {
              batch.update(ref, { [`playerData.games.${g}.deaths`]: 0 });
            });
          });
          return batch.commit();
        });
      });
    });
  }

  function restoreArchive(archiveId) {
    return db.collection(DF.PATHS.archives).doc(archiveId).get().then((doc) => {
      const archive = doc.data();
      if (!archive) return;
      const batch = db.batch();
      (archive.playerSnapshots || []).forEach((p) => {
        if (!p.uid) return;
        const ref = userRef(p.uid);
        batch.update(ref, { "playerData.coins": p.coins || 0, "username": p.displayName || "" });
        Object.entries(p.gameDeaths || {}).forEach(([game, deaths]) => {
          batch.update(ref, { [`playerData.games.${game}.deaths`]: deaths });
        });
      });
      return batch.commit();
    });
  }

  return {
    init, onAuthStateChanged, login, signup, logout, currentUser,
    state, listeners,
    on(key, fn) { listeners[key].push(fn); fn(state[key]); },
    mapGlobal, mapUsers,
    logEvent, updateGlobalState, setOnline, heartbeat, goOffline,
    recordDeath, payMutualReward, applyWheelResult, applyShopItem, claimDaily, prestige,
    setPlayerCoins, adjustCoins, updatePlayerDeaths, clearPenalties, toggleAdminStatus, equipCosmetic,
    changeUsername, submitGameRequest, setGameRequestStatus, removeGameRequest,
    scratchCard, seedScratchPrizes, equipTitle, createArchive, restoreArchive
  };
})();
