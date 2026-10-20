/* =============================================================================
   Dead-Fest desktop — application shell
   Router, navigation, local preferences and the actions the screens call into.

   The shell mirrors MainActivity.kt: the same seven destinations, the same
   global snackbar behaviour, the same presence heartbeat, and the same
   one-at-a-time result dialogs after a death, a ration claim and a level-up.
   ========================================================================== */
window.DF = window.DF || {};

DF.APP_VERSION = { code: 15, name: "2.1.0" };

/* ---- local preferences (mirrors data/AppPrefs.kt) ------------------------- */
DF.Prefs = (function () {
  const KEY = "deadfest.prefs.v1";
  let stored = {};
  try { stored = JSON.parse(localStorage.getItem(KEY) || "{}") || {}; } catch (e) { stored = {}; }

  const defaults = {
    wheelAlerts: true,
    marketAlerts: true,
    wastelandAlerts: true,
    performanceMode: false,
    updateVariant: null,     // null = follow the build actually running
    theme: "default"
  };

  const values = Object.assign({}, defaults, stored);

  function save() {
    try { localStorage.setItem(KEY, JSON.stringify(values)); } catch (e) { /* storage full or blocked */ }
  }

  return {
    get(key) { return values[key]; },
    set(key, value) { values[key] = value; save(); return value; },
    all() { return Object.assign({}, values); }
  };
})();

DF.App = (function () {
  const NAV = [
    { key: "home",     label: "Dash",     icon: "▦" },
    { key: "players",  label: "Players",  icon: "👥" },
    { key: "shop",     label: "Shop",     icon: "🛒" },
    { key: "wheel",    label: "Wheel",    icon: "🎡" },
    { key: "more",     label: "More",     icon: "⊞" },
    { key: "admin",    label: "Admin",    icon: "🛡", adminOnly: true },
    { key: "settings", label: "Settings", icon: "⚙" }
  ];

  const local = {};

  /** Reads or writes a per-session UI value. Two arguments write, one reads. */
  function localState(key, value) {
    if (arguments.length === 2) { local[key] = value; return value; }
    return local[key];
  }
  let current = "home";
  let lastFocusKey = null;
  let renderQueued = false;
  let ticker = null;
  let dailyWasClaimable = null;

  /* ---- theme + performance mode ------------------------------------------ */
  function applyTheme(id) {
    const theme = DF.THEMES.find((t) => t.id === id) || DF.THEMES[0];
    document.documentElement.setAttribute("data-theme", theme.id);
    DF.Prefs.set("theme", theme.id);
    local.theme = theme.id;
  }

  function applyPerformanceMode(on) {
    DF.Prefs.set("performanceMode", !!on);
    local.perfMode = !!on;
    document.documentElement.classList.toggle("perf", !!on);
  }

  const performanceMode = () => DF.Prefs.get("performanceMode") === true;

  /* ---- login errors (mirrors friendlyAuthError in MainViewModel) ---------- */
  function friendlyAuthError(e) {
    const code = e && e.code ? e.code : "";
    if (code === "auth/user-not-found") return "No survivor found with that email.";
    if (code === "auth/wrong-password" || code === "auth/invalid-credential" || code === "auth/invalid-login-credentials") return "Wrong email or password.";
    if (code === "auth/email-already-in-use") return "That email is already registered. Try logging in.";
    if (code === "auth/weak-password") return "Password is too weak. Use at least 6 characters.";
    if (code === "auth/network-request-failed") return "No connection. Check your internet and try again.";
    if (code === "auth/too-many-requests") return "Too many attempts. Try again in a moment.";
    return (e && e.message) || "Something went wrong.";
  }

  /** The same wording the mobile app uses when a wait runs out. */
  function friendlyError(e) {
    const code = (e && e.code) || "";
    if (code === "permission-denied") return "access denied by server rules";
    if (code === "unavailable") return "you're offline";
    const msg = (e && e.message) || "";
    if (msg.toLowerCase().indexOf("offline") >= 0) return "you're offline";
    return msg || "unknown error";
  }

  /* ---- router ------------------------------------------------------------- */
  function screenDef(key) { return DF.Screens[key]; }

  function ctxState() { return DF.Data.state; }

  function rerender() {
    if (renderQueued) return;
    renderQueued = true;
    requestAnimationFrame(() => {
      renderQueued = false;
      paint(true);
    });
  }

  function paint(keepScroll) {
    const def = screenDef(current);
    if (!def) return;
    const body = $("#screen-body");
    if (!body) return;
    const scrollTop = keepScroll ? body.scrollTop : 0;

    const active = document.activeElement;
    let focusKey = null;
    let selStart = null;
    let selEnd = null;
    if (active && active.hasAttribute && active.hasAttribute("data-focus-key")) {
      focusKey = active.getAttribute("data-focus-key");
      try { selStart = active.selectionStart; selEnd = active.selectionEnd; } catch (e) { /* not a text field */ }
    }

    const header = $(".screen-header");
    if (header) {
      const titleEl = $("#screen-title");
      const subEl = $("#screen-subtitle");
      const slot = $("#screen-trailing");
      if (titleEl) titleEl.textContent = def.title;
      if (subEl) subEl.textContent = (def.subtitle || "").toUpperCase();
      if (slot) {
        slot.replaceChildren();
        const pill = def.pill ? def.pill(ctx) : null;
        if (pill) slot.append(pill);
        const back = def.back && def.back(ctx);
        if (back) slot.prepend(back);
      }
    }

    body.replaceChildren(def.render(ctx));
    body.scrollTop = scrollTop;

    if (focusKey) {
      const next = body.querySelector(`[data-focus-key="${focusKey}"]`);
      if (next) {
        next.focus();
        try { if (selStart !== null && selStart !== undefined) next.setSelectionRange(selStart, selEnd); } catch (e) { /* not a text field */ }
      }
    }
  }

  function go(key) {
    if (!screenDef(key)) return;
    current = key;
    local.__last = key;
    const body = $("#screen-body");
    if (body) { body.scrollTop = 0; }
    paint();
    paintNav();
  }

  function paintNav() {
    const nav = $("#nav");
    if (!nav) return;
    const isAdmin = DF.Data.state.user && DF.Data.state.user.admin === true;
    nav.replaceChildren(...NAV.filter((n) => !n.adminOnly || isAdmin).map((n) =>
      h("div", {
        class: "nav-item" + (n.key === current ? " active" : ""),
        onclick: () => go(n.key)
      },
        h("span", { class: "nav-ico", text: n.icon }),
        h("span", { text: n.label.toUpperCase() })
      )
    ));

    const foot = $("#nav-foot");
    if (foot) {
      const user = DF.Data.state.user;
      foot.replaceChildren(
        h("div", { class: "row" },
          user ? avatar(user, "sm") : null,
          h("div", { style: { flex: "1", minWidth: "0" } },
            h("div", { class: "t-card-title", style: { fontSize: "12px" }, text: user ? handleOf(user) : "—" }),
            h("div", { class: "t-micro", text: user ? `${fmt((user.playerData || {}).coins || 0)} COINS` : "" })
          )
        )
      );
    }
  }

  /* ---- shared live-updating text -----------------------------------------
     One second timer that rewrites any element carrying data-live, so a
     countdown ticks without re-rendering (and therefore without stealing
     focus from whatever the survivor is typing into). */
  function liveTick() {
    const now = Date.now();
    $$("[data-live]").forEach((el) => {
      const kind = el.getAttribute("data-live");
      if (kind === "daily") {
        el.textContent = "Next drop in " + DF.Economy.dailyCountdown(now);
      } else if (kind === "ago") {
        const v = Number(el.getAttribute("data-at")) || 0;
        el.textContent = timeAgo(v);
      } else if (kind === "since") {
        const v = Number(el.getAttribute("data-at")) || 0;
        el.textContent = DF.Presence.lastSeenLabel(v, now);
      } else if (kind === "cd-days" || kind === "cd-hrs" || kind === "cd-min" || kind === "cd-sec") {
        const target = Number(el.getAttribute("data-at")) || 0;
        const left = Math.max(0, Math.floor((target - now) / 1000));
        const days = Math.floor(left / 86400);
        const hrs = Math.floor((left % 86400) / 3600);
        const min = Math.floor((left % 3600) / 60);
        const sec = left % 60;
        const value = kind === "cd-days" ? days : kind === "cd-hrs" ? hrs : kind === "cd-min" ? min : sec;
        el.textContent = String(value).padStart(2, "0");
      } else if (kind === "live-line") {
        const target = Number(el.getAttribute("data-at")) || 0;
        if (target > 0) el.textContent = now >= target ? "It's on. Good luck out there." : "Locked in.";
      }
    });

    // The daily card swaps between a countdown and a CLAIM button. Re-render once at the rollover
    // instead of every tick, so nothing the survivor is doing is disturbed.
    const user = DF.Data.state.user;
    if (user && user.playerData) {
      const cfg = DF.Data.state.global ? DF.Data.state.global.economy : DF.Economy.DEFAULT_CONFIG;
      const claimable = DF.Economy.canClaimDaily(user.playerData.lastDailyClaim || 0, now);
      if (dailyWasClaimable !== null && claimable !== dailyWasClaimable && (current === "home")) {
        rerender();
      }
      dailyWasClaimable = claimable;
    }
  }

  /* ---- context handed to the screens -------------------------------------- */
  const ctx = {
    get state() { return DF.Data.state; },
    appVersion: DF.APP_VERSION.code,
    appVersionName: DF.APP_VERSION.name,
    go,
    rerender,
    performanceMode,
    prefs: DF.Prefs,
    local(key, value) {
      if (arguments.length === 2) { local[key] = value; return value; }
      return local[key];
    },
    applyTheme,
    applyPerformanceMode,

    /* ---- home actions ---- */
    claimDaily() {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global) return;
      DF.Data.claimDaily(user.uid, global.economy, Date.now()).then((r) => {
        if (!r.claimed) {
          toast("Today's rations are already claimed.");
          return;
        }
        DF.Data.logEvent("DAILY", `${handleOf(user)} claimed ${fmt(r.amount)} daily rations.`, user.uid);
        DF.Dialogs.dailyClaim(r);
        DF.Dialogs.maybeLevelUp(r.xp, global.economy);
      }).catch((e) => toast("Couldn't claim rations: " + friendlyError(e), true));
    },

    recordDeath(sector) {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global) return;
      DF.Data.recordDeath(user.uid, sector, global.economy, Date.now(), global.forceSpinProtocol).then((r) => {
        const o = r.outcome;
        DF.Data.logEvent("DEATH", `${handleOf(user)} died in ${sector}. Casualty tax: ${fmt(o.tax.finalTax)} coins.`, user.uid);
        const reward = DF.Data.payMutualReward(user.uid, global.economy);
        DF.Dialogs.deathReport(r, reward, global.economy);
        DF.Dialogs.maybeLevelUp(r.xp, global.economy);
      }).catch((e) => toast("Couldn't record death: " + friendlyError(e), true));
    },

    /** The sector picker (the phone app's ModalBottomSheet, with the same wording). */
    pickSector(sectors, overloadLive, global) {
      const body = h("div", { class: "stack" },
        h("div", { class: "row" },
          h("div", { class: "tile", style: { "--tint": "var(--ember)" }, text: "💀" }),
          h("div", { style: { flex: "1" } },
            h("div", { class: "t-card-title", text: "SELECT SECTOR" }),
            h("div", { class: "t-body", text: "Where did you fall, survivor?" })
          )
        ),
        h("hr", { class: "rule" })
      );
      const list = h("div", { class: "stack stack--tight scroll-420" });
      sectors.forEach((sector) => {
        const overloaded = overloadLive && sector === global.overloadedSector;
        list.append(h("div", {
          class: "list-row",
          style: overloaded ? { background: "color-mix(in srgb, var(--amber) 12%, var(--ink3))", borderColor: "rgba(255,213,79,0.45)" } : { background: "var(--ink3)" },
          onclick: () => { closeModal(); ctx.recordDeath(sector); }
        },
          h("span", { text: "🎮", style: { color: overloaded ? "var(--amber)" : "var(--text-low)" } }),
          h("div", { style: { flex: "1", minWidth: "0" } },
            h("div", { class: "t-body hi", style: { fontWeight: "700" }, text: sector.toUpperCase() })
          ),
          overloaded ? statusPill("OVERLOAD", "amber") : h("span", { class: "muted", text: "→" })
        ));
      });
      body.append(list);
      body.append(h("button", { class: "btn btn--ghost btn--full", style: { marginTop: "var(--sm)" }, text: "CANCEL", onclick: () => closeModal() }));
      openModal({ title: null, body, wide: true });
    },

    /* ---- players ---- */
    openDossier(user) {
      const pd = user.playerData || {};
      const games = pd.games || {};
      const cfg = DF.Data.state.global ? DF.Data.state.global.economy : DF.Economy.DEFAULT_CONFIG;
      const deaths = totalDeaths(user);
      const body = h("div", { class: "stack" },
        h("div", { class: "row" },
          avatar(user, "lg"),
          h("div", { style: { flex: "1", minWidth: "0" } },
            h("div", { class: "t-micro ember", text: "SURVIVOR DOSSIER" }),
            h("div", { class: "t-card-title", text: displayNameOf(user) }),
            h("div", { class: "row", style: { gap: "6px" } },
              levelBadge(pd.level || 1, pd.prestigeCount || 0),
              h("span", { class: "t-micro ember", text: (pd.title || "SURVIVOR").toUpperCase() })
            )
          )
        ),
        xpBar(pd.xp || 0, pd.level || 1, cfg.xp),
        sectionLabel("Survival Stats"),
        keyValue("Total deaths", fmt(deaths), "var(--ember)")
      );
      Object.keys(games).sort().forEach((g) => {
        body.append(keyValue(g, `${fmt((games[g] || {}).deaths || 0)} deaths`));
      });
      body.append(h("hr", { class: "rule" }));
      body.append(sectionLabel("Standing"));
      body.append(keyValue("Bank coins", fmt(pd.coins || 0), "var(--amber)"));
      body.append(keyValue("Shield", pd.shield ? "ACTIVE" : "NONE", pd.shield ? "var(--plasma)" : "var(--text-low)"));
      body.append(keyValue("Insurance", pd.insurance ? "ARMED" : "NONE", pd.insurance ? "var(--plasma)" : "var(--text-low)"));
      body.append(keyValue("Insulation", pd.insulationTier ? `TIER ${DF.Economy.roman(pd.insulationTier)}` : "NONE", pd.insulationTier ? "var(--amber)" : "var(--text-low)"));
      body.append(keyValue("Bankruptcy", (pd.bankruptcyLevel || 0) > 0 ? `LEVEL ${pd.bankruptcyLevel}` : "SOLVENT", (pd.bankruptcyLevel || 0) > 0 ? "var(--ember)" : "var(--toxic)"));
      body.append(keyValue("Pending spins", String(pd.pendingSpins || 0), (pd.pendingSpins || 0) > 0 ? "var(--ember)" : "var(--text-low)"));
      body.append(keyValue("Prestige", (pd.prestigeCount || 0) > 0 ? `★ ${pd.prestigeCount}` : "NONE", (pd.prestigeCount || 0) > 0 ? "var(--prestige)" : "var(--text-low)"));
      body.append(keyValue("Status", isOnlineNow(user) ? "ONLINE" : `OFFLINE · ${lastSeenLabel(user)}`, isOnlineNow(user) ? "var(--toxic)" : "var(--text-low)"));
      if (user.bio && String(user.bio).trim()) {
        body.append(sectionLabel("Bio"));
        body.append(h("div", { class: "t-body", text: user.bio }));
      }
      openModal({
        title: null,
        body,
        wide: true,
        actions: [h("button", { class: "btn", text: "CLOSE", onclick: () => closeModal() })]
      });
    },

    /* ---- shop -------------------------------------------------------------
       The phone answers every purchase with a snackbar, not a dialog, so the
       same nine outcome strings are reused here. The confirmations live in the
       shop screen because they are made of the live item descriptions. */

    openCurse(item) {
      const g = DF.Data.state.global;
      if (!g) return;
      openCurseDialog(item, g);
    },

    applyPurchase(item, targetUser, sector) {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global) return;
      const targetUid = targetUser ? targetUser.uid : null;
      DF.Data.applyShopItem(user.uid, item, global.economy, targetUid, sector).then((r) => {
        if (r.outcome === "APPLIED") {
          DF.Data.logEvent("BLACK_MARKET", `${handleOf(user)} bought ${item.logName}.`, user.uid, targetUid);
          toast(purchaseMessage(item, r, targetUser));
          DF.Dialogs.maybeLevelUp(r.xp);
        } else {
          toast(shopOutcomeMessage(item, r.outcome, targetUser), true);
        }
      }).catch((e) => toast("Purchase failed: " + friendlyError(e), true));
    },

    buyTitleNow(offer) {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global) return;
      DF.Data.buyTitle(user.uid, offer.title, offer.price, global.economy).then((r) => {
        if (r.outcome === "APPLIED") {
          DF.Data.logEvent("BLACK_MARKET", `${handleOf(user)} bought the title ${offer.title}.`, user.uid);
          toast(`Title unlocked and equipped: ${offer.title}.`);
          DF.Dialogs.maybeLevelUp(r.xp);
        } else {
          toast(shopOutcomeMessage({ name: offer.title }, r.outcome), true);
        }
      }).catch((e) => toast("Purchase failed: " + friendlyError(e), true));
    },

    equipTitleNow(title) {
      const user = DF.Data.state.user;
      if (!user) return;
      DF.Data.equipTitle(user.uid, title).then(() => toast(`Equipped ${title}.`))
        .catch((e) => toast("Couldn't equip title: " + friendlyError(e), true));
    },

    /* ---- wheel ------------------------------------------------------------
       3.6 s and six turns, or 1.6 s and three turns in performance mode — the
       exact figures from WheelScreen.kt. The result is read off the hero rather
       than through a dialog. */
    spinWheel(items) {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global || !items.length) return;
      if (localState("spinning", false)) return;

      const index = DF.Economy.weightedPick(items.map((i) => i.weight));
      if (index < 0) return;
      const item = items[index];

      localState("spinning", true);
      localState("wheelResult", null);

      const btn = document.getElementById("wheel-btn");
      const state = document.getElementById("wheel-state");
      if (btn) { btn.disabled = true; btn.textContent = "FATE IS DECIDING..."; }
      if (state) { state.textContent = "SPIN IN PROGRESS"; state.className = "t-micro amber"; }

      // Land somewhere in the middle 40% of the winning slice, like the phone.
      const per = 360 / items.length;
      const jitter = (Math.random() - 0.5) * per * 0.6;
      const landing = index * per + per / 2 + jitter;
      const turns = performanceMode() ? 3 : 6;
      const ms = performanceMode() ? 1600 : 3600;
      const start = localState("wheelAngle", 0) || 0;
      const target = start + turns * 360 + ((360 - (landing % 360)) - (start % 360) + 360) % 360;
      localState("wheelAngle", target);

      const disc = document.getElementById("wheel-disc");
      if (disc) {
        disc.style.transition = performanceMode() ? "none" : `transform ${ms}ms cubic-bezier(0.15,0,0.1,1)`;
        void disc.offsetWidth;
        disc.style.transform = `rotate(${target}deg)`;
      }

      setTimeout(() => {
        DF.Data.applyWheelResult(user.uid, item, global.economy).then((r) => {
          localState("spinning", false);
          localState("wheelResult", { item, result: r });
          DF.Data.logEvent("WHEEL_SPIN",
            `${handleOf(user)} spun the Wheel of Fate and ${r.delta >= 0 ? "won" : "lost"} ${fmt(Math.abs(r.delta))} coins.`,
            user.uid);
          rerender();
          DF.Dialogs.maybeLevelUp(r.xp);
        }).catch((e) => {
          localState("spinning", false);
          toast("The spin failed: " + friendlyError(e), true);
          rerender();
        });
      }, ms + 60);
    },

    /* ---- scratch ----------------------------------------------------------
       One card at a time, 25 coins, foil wipes away over 1.2 s. The prize is
       already committed when the wipe starts; no dialog follows. */
    buyScratch(pool) {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global || localState("scratchBusy", false)) return;
      const index = DF.Economy.weightedPick(pool.map((p) => p.weight));
      if (index < 0) return;
      const prize = pool[index];
      localState("scratchBusy", true);
      localState("scratchPrize", null);
      rerender();
      DF.Data.scratchCard(user.uid, prize, global.economy).then((r) => {
        localState("scratchBusy", false);
        localState("scratchPrize", Object.assign({ at: Date.now() }, r));
        rerender();
        DF.Data.logEvent("SCRATCH", `${handleOf(user)} scratched a card and won ${r.prize.label}.`, user.uid);
        DF.Dialogs.maybeLevelUp(r.xp);
      }).catch((e) => {
        localState("scratchBusy", false);
        rerender();
        toast("Couldn't buy a card: " + friendlyError(e), true);
      });
    },

    /* ---- progression ------------------------------------------------------ */
    prestige() {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global) return;
      if (!DF.Economy.canPrestige((user.playerData || {}).level || 1, global.economy)) {
        toast("Reach level 50 to prestige.");
        return;
      }
      confirmDialog({
        title: "PRESTIGE?",
        message: "You go back to level 1 with 0 XP and keep everything you unlocked. This cannot be undone.",
        confirmText: "PRESTIGE",
        onConfirm: () => {
          DF.Data.prestige(user.uid, global.economy).then((count) => {
            if (count) {
              DF.Data.logEvent("PRESTIGE", `${handleOf(user)} reached prestige ${count}.`, user.uid);
              toast(`PRESTIGE ${count} UNLOCKED. Back to level 1, legend.`);
            } else {
              toast("Reach level 50 to prestige.");
            }
          }).catch((e) => toast("Prestige failed: " + friendlyError(e), true));
        }
      });
    },

    equip(reward) {
      const user = DF.Data.state.user;
      if (!user) return;
      if (reward.type === "title") {
        DF.Data.equipTitle(user.uid, reward.value)
          .then(() => toast(`Equipped ${reward.label}.`))
          .catch((e) => toast("Couldn't equip title: " + friendlyError(e), true));
      } else {
        DF.Data.equipCosmetic(user.uid, reward.type, reward.value)
          .then(() => toast(reward.type === "color" ? "Badge colour equipped." : "Avatar frame equipped."))
          .catch((e) => toast("Couldn't equip cosmetic: " + friendlyError(e), true));
      }
    },

    /* ---- images ---- */
    pickImage() {
      const input = h("input", { type: "file", accept: "image/*", style: { display: "none" } });
      document.body.append(input);
      input.addEventListener("change", () => {
        const file = input.files && input.files[0];
        input.remove();
        if (file) ctx.prepareImage(file);
      });
      input.click();
    },

    prepareImage(file) {
      localState("imagePending", null);
      localState("imageProcessing", true);
      localState("imageError", null);
      rerender();
      DF.Media.process(file).then((processed) => {
        localState("imageProcessing", false);
        localState("imagePending", processed);
        rerender();
        openPostImageDialog();
      }).catch((e) => {
        localState("imageProcessing", false);
        rerender();
        toast("Couldn't load that image: " + ((e && e.message) || "unsupported file"), true);
      });
    },

    postImage(caption) {
      const user = DF.Data.state.user;
      const pending = localState("imagePending", null);
      if (!user || !pending) return Promise.resolve();
      const clean = String(caption || "").slice(0, 140);
      localState("imageUploading", true);
      rerender();
      return DF.Data.uploadImage(user.uid, handleOf(user), clean, pending.full, pending.thumb, pending.width, pending.height)
        .then(() => {
          localState("imageUploading", false);
          localState("imagePending", null);
          closeModal();
          rerender();
          toast("Image shared with every survivor.");
          DF.Data.logEvent("IMAGES", clean ? `${handleOf(user)} shared an image: "${clean}"` : `${handleOf(user)} shared an image.`, user.uid);
        })
        .catch((e) => {
          localState("imageUploading", false);
          rerender();
          toast("Upload failed: " + friendlyError(e), true);
        });
    },

    cancelImage() {
      if (localState("imageUploading", false)) return;
      localState("imagePending", null);
      closeModal();
      rerender();
    },

    openViewer(image) {
      DF.Viewer.open(image, ctx);
    },

    /* ---- identity ---- */
    saveHandle(name) {
      const user = DF.Data.state.user;
      if (!user) return;
      DF.Data.changeUsername(user.uid, name).then((r) => {
        if (r.outcome === "OK") toast(`Handle updated to ${String(name).trim()}.`);
        else if (r.outcome === "SAME") toast("That's already your handle.");
        else if (r.outcome === "TAKEN") toast(`"${String(name).trim()}" is already taken by another survivor.`, true);
        else toast(r.reason || "Couldn't update handle.", true);
        rerender();
      });
    },

    /* ---- game requests ---- */
    openRequestDialog() {
      const query = h("input", { placeholder: "Search the game database", "data-focus-key": "reqSearch" });
      const nameInput = h("input", { placeholder: "Game name", "data-focus-key": "reqName" });
      const coverInput = h("input", { placeholder: "Cover image URL (optional)" });
      const results = h("div", { class: "stack stack--tight" });
      const manual = h("div", { class: "stack stack--tight" });
      const progress = h("div", { class: "bar", style: { display: "none" } }, h("i", { style: { width: "40%" } }));
      let offline = false;

      const renderManual = () => {
        const show = offline || (!results.children.length);
        manual.replaceChildren(
          h("div", { class: "t-body", text: offline ? "The game database is unreachable right now — enter the game by hand." : "No match found — enter the game by hand." }),
          h("div", { class: "field" }, h("label", { text: "Game name" }), nameInput),
          h("div", { class: "field" }, h("label", { text: "Cover image URL (optional)" }), coverInput)
        );
        manual.style.display = show ? "flex" : "none";
      };

      const doSearch = () => {
        const q = query.value.trim();
        if (q.length < 2) return;
        progress.style.display = "block";
        DF.TheGamesDb.search(q).then((games) => {
          progress.style.display = "none";
          offline = games.offline;
          results.replaceChildren();
          games.items.forEach((g) => {
            results.append(h("div", { class: "list-row", onclick: () => {
              nameInput.value = g.title;
              if (g.coverUrl) coverInput.value = g.coverUrl;
            } },
              h("div", { style: { flex: "1", minWidth: "0" } },
                h("div", { class: "t-card-title", text: g.title }),
                [g.platform, g.releaseDate].filter(Boolean).length
                  ? h("div", { class: "t-body", text: [g.platform, g.releaseDate].filter(Boolean).join(" · ") })
                  : null
              )
            ));
          });
          renderManual();
        });
      };

      let timer = null;
      query.addEventListener("input", () => {
        if (timer) clearTimeout(timer);
        timer = setTimeout(doSearch, 400);
      });

      const body = h("div", { class: "stack" },
        h("div", { class: "field" }, h("label", { text: "Search the game database" }), query),
        progress,
        results,
        manual
      );
      renderManual();

      openModal({
        title: "REQUEST A GAME",
        body,
        wide: true,
        actions: [
          h("button", { class: "btn btn--ghost", text: "CANCEL", onclick: () => { ctx.clearGameSearch(); closeModal(); } }),
          h("button", {
            class: "btn", text: "SEND REQUEST",
            onclick: () => {
              const name = nameInput.value.trim();
              const user = DF.Data.state.user;
              DF.Data.submitGameRequest(name, coverInput.value.trim(), user.uid, handleOf(user)).then((r) => {
                if (r.outcome === "OK") toast(`Request sent: ${name}`);
                else toast(r.reason || "Couldn't send the request.", true);
              });
            }
          })
        ]
      });
    },

    clearGameSearch() { /* nothing cached on the desktop; kept for parity with the phone's VM */ },

    /* ---- update + session ---- */
    updateVariant() {
      const stored = DF.Prefs.get("updateVariant");
      return stored || "Standard";
    },

    /** The URL to offer, resolved the same way GlobalState.updateUrlFor does. */
    updateUrlFor(variant, global) {
      const standard = (global && global.updateUrl) || "";
      const r8 = (global && global.updateUrlR8) || "";
      return variant === "R8" ? (r8 || standard) : (standard || r8);
    },

    checkUpdate() {
      const global = DF.Data.state.global;
      if (!global) return;
      const url = ctx.updateUrlFor(ctx.updateVariant(), global);
      if (global.latestVersionCode > DF.APP_VERSION.code && url) {
        DF.Dialogs.enqueue({
          body: h("div", { class: "result", style: { "--accent": "var(--ember)" } },
            h("div", { class: "result-head", text: "UPDATE AVAILABLE" }),
            h("div", { class: "stack" },
              h("div", { class: "result-hero" },
                h("div", { class: "result-big ember", text: "v" + global.latestVersionName }),
                h("div", { class: "t-body", text: `Build ${global.latestVersionCode} is published for the phone app.` })
              ),
              global.updateNotes ? h("div", { class: "t-body", text: global.updateNotes }) : null,
              h("div", { class: "t-body", text: "This terminal is a Windows build: open the release page to download the current desktop build." })
            )
          ),
          button: "OPEN RELEASE PAGE",
          onClose: () => window.open(url, "_blank")
        });
      } else {
        toast("Terminal is up to date (v" + DF.APP_VERSION.name + ").");
      }
    },

    logout() {
      confirmDialog({
        title: "TERMINATE SESSION?",
        message: "You'll be logged out of Dead-Fest on this device. Your coins, level and history stay safe on the server.",
        confirmText: "LOG OUT",
        destructive: true,
        onConfirm: () => DF.Data.logout()
      });
    },

    /* ---- admin actions ---- */
    setGlobal(map) {
      DF.Data.updateGlobalState(map).then(() => toast("Saved.")).catch((e) => toast("Save failed: " + friendlyError(e), true));
    },
    setShopOverride(id, patch) {
      const map = {};
      map["shopConfig." + id] = patch;
      DF.Data.updateGlobalState(map).then(() => toast("Shop updated.")).catch((e) => toast("Update failed: " + friendlyError(e), true));
    },
    setScratchPrize(prize) {
      DF.Data.saveScratchPrize(prize)
        .then(() => toast(prize.id ? "Prize updated." : "Prize added."))
        .catch((e) => toast("Update failed: " + friendlyError(e), true));
    },
    deleteScratchPrize(prize) {
      if (!prize || !prize.id) { toast("That prize isn't saved yet.", true); return; }
      DF.Data.deleteScratchPrize(prize.id)
        .then(() => toast("Prize removed."))
        .catch((e) => toast("Remove failed: " + friendlyError(e), true));
    },
    loadDefaultPrizes() {
      DF.Data.seedScratchPrizes(DF.Economy.SCRATCH_POOL)
        .then(() => toast("Default prize pool loaded."))
        .catch((e) => toast("Couldn't load defaults: " + friendlyError(e), true));
    },
    toggleSectorLock(sector) {
      const global = DF.Data.state.global;
      if (!global) return;
      const locked = global.lockedGames.indexOf(sector) >= 0;
      const next = locked ? global.lockedGames.filter((g) => g !== sector) : global.lockedGames.concat([sector]);
      ctx.setGlobal({ lockedGames: next });
    },
    removeSector(sector) {
      const global = DF.Data.state.global;
      if (!global) return;
      confirmDialog({
        title: "REMOVE SECTOR?",
        message: `"${sector}" will disappear from the sector list. Player death records are kept.`,
        confirmText: "REMOVE",
        destructive: true,
        onConfirm: () => ctx.setGlobal({
          games: global.games.filter((g) => g !== sector),
          lockedGames: global.lockedGames.filter((g) => g !== sector)
        })
      });
    },
    addSector() {
      const name = (localState("newSector", "") || "").trim();
      const global = DF.Data.state.global;
      if (!name) { toast("Type a sector name first.", true); return; }
      if (global && global.games.some((g) => g.toLowerCase() === name.toLowerCase())) { toast("That sector already exists.", true); return; }
      localState("newSector", "");
      ctx.setGlobal({ games: (global ? global.games : []).concat([name]) });
    },
    queueEventGame(game) {
      const global = DF.Data.state.global;
      if (!global) return;
      const queue = (global.nextEventGames || []).slice();
      const at = queue.indexOf(game);
      if (at >= 0) queue.splice(at, 1); else queue.push(game);
      ctx.setGlobal({ nextEventGames: queue });
    },
    updateWheelItem(id, patch) {
      const global = DF.Data.state.global;
      if (!global) return;
      const items = (global.wheelItems || []).slice();
      if (patch === null) {
        ctx.setGlobal({ wheelItems: items.filter((i) => i.id !== id) });
        return;
      }
      ctx.setGlobal({ wheelItems: items.map((i) => (i.id === id ? Object.assign({}, i, patch) : i)) });
    },
    addWheelSlice() {
      const global = DF.Data.state.global;
      const label = (localState("newWheelLabel", "") || "").trim();
      if (!label) { toast("Give the slice a label.", true); return; }
      const value = (localState("newWheelValue", "") || "").trim();
      const weight = parseFloat(localState("newWheelWeight", "1")) || 1;
      const items = (global ? global.wheelItems : []).slice();
      items.push({ id: String(Date.now()), label, value, actionType: "give", color: "#FF4B4B", enabled: true, weight });
      localState("newWheelLabel", "");
      ctx.setGlobal({ wheelItems: items });
    },

    /** Upserts one wheel slice from the editor. */
    saveWheelSlice(slice) {
      const global = DF.Data.state.global;
      if (!global) return;
      const items = (global.wheelItems || []).slice();
      const copy = Object.assign({}, slice);
      if (!copy.id) copy.id = String(Date.now());
      const at = items.findIndex((i) => i.id === copy.id);
      if (at >= 0) items[at] = Object.assign({}, items[at], copy);
      else items.push(copy);
      ctx.setGlobal({ wheelItems: items });
    },

    deleteWheelSlice(id) {
      const global = DF.Data.state.global;
      if (!global) return;
      ctx.setGlobal({ wheelItems: (global.wheelItems || []).filter((i) => i.id !== id) });
    },

    renameSector(oldName, newName) {
      const global = DF.Data.state.global;
      const name = String(newName || "").trim();
      if (!global) return;
      if (!name) { toast("Sector names can't be empty.", true); return; }
      if (name === oldName) return;
      if (global.games.some((g) => g.toLowerCase() === name.toLowerCase())) { toast("That sector already exists.", true); return; }
      ctx.setGlobal({
        games: global.games.map((g) => (g === oldName ? name : g)),
        lockedGames: global.lockedGames.map((g) => (g === oldName ? name : g)),
        nextEventGames: (global.nextEventGames || []).map((g) => (g === oldName ? name : g))
      });
      toast(`Renamed to ${name}.`);
    },

    moveSector(name, delta) {
      const global = DF.Data.state.global;
      if (!global) return;
      const games = global.games.slice();
      const at = games.indexOf(name);
      const to = at + delta;
      if (at < 0 || to < 0 || to >= games.length) return;
      games.splice(at, 1);
      games.splice(to, 0, name);
      ctx.setGlobal({ games });
    },

    setXpConfig(key, value) {
      const map = {};
      map["xpConfig." + key] = value;
      ctx.setGlobal(map);
    },

    setEconomyValue(key, value) {
      const map = {};
      map[key] = value;
      ctx.setGlobal(map);
    },
    approveRequest(id) {
      const user = DF.Data.state.user;
      DF.Data.setGameRequestStatus(id, true, handleOf(user)).then(() => toast("Request approved.")).catch((e) => toast("Approve failed: " + friendlyError(e), true));
    },
    removeRequest(id) {
      DF.Data.removeGameRequest(id).then(() => toast("Request removed.")).catch((e) => toast("Remove failed: " + friendlyError(e), true));
    },
    openFirmwareDialog() {
      const global = DF.Data.state.global || {};
      const codeInput = h("input", { value: String(global.latestVersionCode || 1) });
      const nameInput = h("input", { value: global.latestVersionName || "" });
      const urlInput = h("input", { value: global.updateUrl || "" });
      const urlR8Input = h("input", { value: global.updateUrlR8 || "" });
      const notesInput = h("textarea", {}, global.updateNotes || "");
      const forceInput = h("input", { type: "checkbox" });
      forceInput.checked = global.forceUpdate === true;
      const body = h("div", { class: "stack" },
        h("div", { class: "field" }, h("label", { text: "Version code" }), codeInput),
        h("div", { class: "field" }, h("label", { text: "Version name" }), nameInput),
        h("div", { class: "field" }, h("label", { text: "URL — Dead-Fest" }), urlInput),
        h("div", { class: "field" }, h("label", { text: "URL — Dead-Fest R8" }), urlR8Input),
        h("div", { class: "field" }, h("label", { text: "Notes" }), notesInput),
        h("div", { class: "row" }, h("label", { text: "Force update" }), forceInput)
      );
      openModal({
        title: "PUBLISH FIRMWARE",
        body,
        wide: true,
        actions: [
          h("button", { class: "btn btn--ghost", text: "CANCEL", onclick: () => closeModal() }),
          h("button", {
            class: "btn", text: "PUBLISH",
            onclick: () => {
              const code = parseInt(codeInput.value, 10);
              if (!Number.isFinite(code) || !nameInput.value.trim()) { toast("Version code and name are required.", true); return; }
              const url = urlInput.value.trim();
              const urlR8 = urlR8Input.value.trim();
              if (!url && !urlR8) { toast("At least one download URL is required.", true); return; }
              ctx.setGlobal({
                latestVersionCode: code,
                latestVersionName: nameInput.value.trim(),
                updateUrl: url,
                updateUrlR8: urlR8,
                updateNotes: notesInput.value,
                forceUpdate: forceInput.checked
              });
              DF.Data.logEvent("FIRMWARE_UPDATE", `Firmware v${nameInput.value.trim()} is now available.`);
              closeModal();
            }
          })
        ]
      });
    },
    createArchive() {
      const name = (localState("archiveName", "") || "").trim();
      if (!name) { toast("Name the season first.", true); return; }
      localState("archiveName", "");
      DF.Data.createArchive(name).then(() => toast("Season archived and reset.")).catch((e) => toast("Archive failed: " + friendlyError(e), true));
    },
    restoreArchive(archive) {
      confirmDialog({
        title: "RESTORE SEASON?",
        message: `Restore "${archive.name}"? This overwrites current coins, deaths and names with the archived snapshot.`,
        confirmText: "RESTORE",
        destructive: true,
        onConfirm: () => {
          DF.Data.restoreArchive(archive.id).then(() => toast("Season restored.")).catch((e) => toast("Restore failed: " + friendlyError(e), true));
        }
      });
    },
    sendBroadcast() {
      const text = (localState("broadcastText", "") || "").trim();
      if (!text) { toast("Write something first.", true); return; }
      localState("broadcastText", "");
      const user = DF.Data.state.user;
      DF.Data.logEvent("WASTELAND_ALERT", text, user ? user.uid : null);
      toast("Broadcast sent to the feed.");
    },
    openPlayerDetail(user) {
      const pd = user.playerData || {};
      const coinInput = h("input", { value: String(pd.coins || 0) });
      const me = DF.Data.state.user;
      const isMe = me && me.uid === user.uid;

      const adminSwitch = h("div", { class: "switch" + (user.admin ? " on" : ""), onclick: () => ctx.adminToggleAdmin(user.uid, !user.admin) });

      const body = h("div", { class: "stack" },
        h("div", { class: "row" },
          avatar(user, "lg"),
          h("div", { style: { flex: "1", minWidth: "0" } },
            h("div", { class: "t-card-title", text: handleOf(user) }),
            h("div", { class: "t-body", text: (pd.title || "Survivor") + " · Level " + (pd.level || 1) })
          ),
          user.admin ? statusPill("ADMIN", "ember") : null
        ),
        h("div", { class: "divider" }),
        keyValue("Coins", fmt(pd.coins || 0)),
        keyValue("Deaths", fmt(totalDeaths(user))),
        keyValue("Level", String(pd.level || 1)),
        keyValue("Pending spins", String(pd.pendingSpins || 0)),
        keyValue("Bankruptcy", ["Solvent", "Level 1", "Level 2"][pd.bankruptcyLevel || 0]),
        h("div", { class: "divider" }),
        sectionLabel("Admin rights"),
        h("div", { class: "row" },
          h("div", { style: { flex: "1" } },
            h("div", { class: "t-body", text: isMe ? "You cannot revoke your own access" : "Grants full access to the admin panel" })
          ),
          adminSwitch
        ),
        h("div", { class: "divider" }),
        sectionLabel("Death record"),
        h("div", { class: "t-body", text: "Injecting a death runs the full pipeline: casualty tax, penalty spin, bankruptcy and XP." }),
        h("div", { class: "row", style: { marginTop: "10px" } },
          h("button", { class: "btn btn--sm", text: "Inject death", onclick: () => ctx.pickSectorFor(user.uid, (sector) => ctx.adminInjectDeath(user.uid, sector)) }),
          h("button", { class: "btn btn--ghost btn--sm", text: "Remove death", onclick: () => ctx.pickSectorFor(user.uid, (sector) => ctx.adminRemoveDeath(user.uid, sector)) })
        ),
        h("div", { class: "divider" }),
        h("div", { class: "field" }, h("label", { text: "Set exact coins" }), coinInput),
        h("div", { class: "row row--wrap", style: { marginTop: "10px" } },
          [-100, -10, 10, 100].map((d) => h("button", { class: "btn btn--ghost btn--sm", text: (d > 0 ? "+" : "") + d, onclick: () => ctx.adminAdjustCoins(user.uid, d) }))
        )
      );
      openModal({
        title: "PLAYER CONTROL",
        body,
        wide: true,
        actions: [
          h("button", { class: "btn btn--ghost", text: "CLEAR PENALTIES", onclick: () => { DF.Data.clearPenalties(user.uid).then(() => toast("Penalties cleared.")); closeModal(); } }),
          h("button", {
            class: "btn", text: "SET COINS",
            onclick: () => {
              const v = parseInt(coinInput.value, 10);
              if (!Number.isFinite(v)) { toast("Not a number.", true); return; }
              DF.Data.setPlayerCoins(user.uid, v).then(() => { toast("Coins set."); closeModal(); });
            }
          })
        ]
      });
    },

    /** Sector picker, shared by the admin death controls. */
    pickSectorFor(uid, onPick) {
      const global = DF.Data.state.global;
      const sectors = global ? global.games : [];
      if (!sectors.length) { toast("No sectors configured.", true); return; }
      const body = h("div", { class: "stack stack--tight" });
      sectors.forEach((sector) => {
        body.append(h("div", { class: "list-row", onclick: () => { closeModal(); onPick(sector); } },
          h("div", { style: { flex: "1" } },
            h("div", { class: "t-card-title", text: sector.toUpperCase() }),
            h("div", { class: "t-body", text: "Where did they fall?" })
          ),
          h("span", { text: "→", style: { color: "var(--ember)" } })
        ));
      });
      openModal({ title: "SELECT SECTOR", body });
    },

    /** Injects a death through the full pipeline, exactly as the phone app's admin does. */
    adminInjectDeath(uid, game) {
      const global = DF.Data.state.global;
      const target = (DF.Data.state.users || []).find((u) => u.uid === uid);
      if (!global) return;
      DF.Data.recordDeath(uid, game, global.economy, Date.now(), global.forceSpinProtocol).then((r) => {
        const o = r.outcome;
        const spinNote = o.spinsAdded > 0 ? ` · +${o.spinsAdded} penalty spin(s)` : "";
        toast(`Death injected into ${target ? handleOf(target) : uid} — ${game}: ${fmt(o.tax.finalTax)} tax${spinNote}`);
        DF.Data.logEvent("DEATH", `Admin injected a death for ${target ? handleOf(target) : uid} in ${game}. Casualty tax: ${fmt(o.tax.finalTax)} coins.`, uid);
        DF.Data.payMutualReward(uid, global.economy);
      }).catch((e) => toast("Couldn't inject the death: " + friendlyError(e), true));
    },

    /** Removes a death without a refund, matching the phone app. */
    adminRemoveDeath(uid, game) {
      DF.Data.updatePlayerDeaths(uid, -1, game).then(() => toast(`Death removed from ${game}.`)).catch((e) => toast("Couldn't remove the death: " + friendlyError(e), true));
    },

    /** Grants or revokes admin rights. You cannot revoke your own. */
    adminToggleAdmin(uid, isAdmin) {
      const me = DF.Data.state.user;
      if (me && uid === me.uid && !isAdmin) { toast("You can't revoke your own admin rights."); return; }
      DF.Data.toggleAdminStatus(uid, isAdmin)
        .then(() => toast(isAdmin ? "Admin rights granted." : "Admin rights revoked."))
        .catch((e) => toast("Couldn't change admin rights: " + friendlyError(e), true));
    },
    adminAdjustCoins(uid, delta) {
      DF.Data.adjustCoins(uid, delta).then(() => toast(`Coins ${delta > 0 ? "+" : ""}${delta}.`)).catch((e) => toast("Adjust failed: " + friendlyError(e), true));
    }
  };

  /* ---- curse target picker (mirrors the phone's CurseTargetDialog) --------- */
  function openCurseDialog(item, global) {
    const me = DF.Data.state.user;
    const cfg = global.economy || DF.Economy.DEFAULT_CONFIG;
    const price = DF.Economy.priceIn(item, cfg);
    const others = (DF.Data.state.users || []).filter((u) => u.uid !== me.uid);
    const sectors = (global.games || []).filter((g) => global.lockedGames.indexOf(g) < 0);
    let target = null;
    let sector = sectors[0] || DF.DEFAULT_GAME;

    const targetList = h("div", { class: "stack stack--tight scroll-260" });
    others.forEach((u) => {
      const row = h("div", {
        class: "list-row card--click",
        style: { cursor: "pointer" },
        onclick: () => { target = u; paintTargets(); }
      },
        avatar(u, 38),
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-body hi", text: displayNameOf(u) }),
          h("div", { class: "t-micro", text: `${fmt((u.playerData || {}).coins || 0)} COINS · ${totalDeaths(u)} DEATHS` })
        ),
        (u.playerData || {}).shield ? statusPill("SHIELDED", "plasma") : null
      );
      row.dataset.uid = u.uid;
      targetList.append(row);
    });

    const warning = h("div");
    const sectorRow = h("div", { class: "row row--wrap" });

    function paintTargets() {
      Array.from(targetList.children).forEach((row) => {
        const on = target && row.dataset.uid === target.uid;
        row.style.borderColor = on ? "var(--ember)" : "var(--ink-line)";
        row.style.background = on ? "color-mix(in srgb, var(--ember) 10%, var(--ink2))" : "";
      });
      sectorRow.replaceChildren(...sectors.map((s) => pillChip(s, s === sector, () => { sector = s; paintTargets(); })));
      warning.replaceChildren();
      if (target && (target.playerData || {}).shield) {
        warning.append(alertBanner("🛡", "SHIELD DETECTED",
          `${displayNameOf(target)}'s Shield will absorb this curse and break.`, "var(--plasma)"));
      }
    }

    const body = h("div", { class: "stack" },
      h("div", { class: "t-body", text: `Adds ${item.curseDeaths} death${item.curseDeaths === 1 ? "" : "s"} to another survivor.` }),
      sectionLabel("Where did they fall?"),
      sectors.length ? sectorRow : h("div", { class: "t-body", text: "Every sector is locked right now." }),
      sectionLabel("Target"),
      others.length ? targetList : emptyStateCard("👥", "NO SURVIVORS", "You're the last one standing."),
      warning,
      h("div", { class: "row row--between" },
        priceTag(price, (me.playerData || {}).coins >= price),
        h("div", { class: "t-micro", text: `BALANCE ${fmt((me.playerData || {}).coins || 0)}` })
      )
    );

    openModal({
      title: `CAST ${item.name}`,
      body,
      wide: true,
      onClose: paintTargets,
      actions: [
        h("button", { class: "btn btn--ghost", text: "CANCEL", onclick: () => closeModal() }),
        h("button", {
          class: "btn",
          text: "CAST CURSE",
          onclick: () => {
            if (!target) { toast("Pick a target first.", true); return; }
            closeModal();
            ctx.applyPurchase(item, target, sector);
          }
        })
      ]
    });
    paintTargets();
  }

  /* ---- post image dialog --------------------------------------------------- */
  function openPostImageDialog() {
    const pending = localState("imagePending", null);
    if (!pending) return;
    const caption = h("textarea", { placeholder: "Caption (optional)", maxLength: "140", style: { minHeight: "62px" } });
    const counter = h("div", { class: "hint", text: "0/140" });
    caption.addEventListener("input", () => { counter.textContent = `${caption.value.length}/140`; });
    const uploadPreview = DF.Media.objectUrl("pending-thumb", pending.thumb, "image/jpeg");

    const body = h("div", { class: "stack" },
      h("div", { class: "gallery-thumb", style: { aspectRatio: "1.4", maxHeight: "240px" } },
        h("img", { src: uploadPreview, alt: "Preview" })
      ),
      h("div", { class: "t-micro", text: `Compressed to ${Math.round(pending.sizeBytes / 1024)} KB · visible to every survivor` }),
      h("div", { class: "field" }, h("label", { text: "Caption (optional)" }), caption, counter)
    );

    openModal({
      title: "SHARE IMAGE",
      body,
      wide: true,
      actions: [
        h("button", { class: "btn btn--ghost", text: "CANCEL", onclick: () => ctx.cancelImage() }),
        h("button", { class: "btn", text: "POST", onclick: () => ctx.postImage(caption.value) })
      ]
    });
  }

  /* ---- login (mirrors LoginScreen.kt) -------------------------------------- */
  let loginMode = "login";
  let loginBusy = false;

  function paintLogin() {
    const root = $("#login");
    const app = $("#app");
    root.classList.add("show");
    app.style.display = "none";

    const email = h("input", { type: "email", placeholder: "survivor@deadfest", autocomplete: "username" });
    const pass = h("input", { type: "password", placeholder: "••••••••", autocomplete: "current-password" });
    let passVisible = false;
    const name = h("input", { placeholder: "Display name" });
    const err = h("div", { class: "login-err" });

    const nameField = h("div", { class: "field" }, h("label", { text: "Display Name" }), name);
    const submit = h("button", { class: "btn btn--full login-submit", text: "ENTER THE WASTELAND" });
    const eye = h("button", { class: "btn btn--ghost btn--sm", text: "SHOW", type: "button" });

    function showError(message) { err.textContent = message || ""; }

    function setMode(mode) {
      loginMode = mode;
      paintMode();
      showError("");
    }

    function paintMode() {
      const isLogin = loginMode === "login";
      loginPill.className = "chip" + (isLogin ? " on" : "");
      signupPill.className = "chip" + (isLogin ? "" : " on");
      nameField.style.display = isLogin ? "none" : "flex";
      submit.textContent = isLogin ? "ENTER THE WASTELAND" : "JOIN SURVIVORS";
    }

    const loginPill = h("button", { class: "chip on", text: "LOG IN", type: "button", onclick: () => setMode("login") });
    const signupPill = h("button", { class: "chip", text: "SIGN UP", type: "button", onclick: () => setMode("signup") });

    eye.addEventListener("click", () => {
      passVisible = !passVisible;
      pass.type = passVisible ? "text" : "password";
      eye.textContent = passVisible ? "HIDE" : "SHOW";
      eye.setAttribute("aria-label", passVisible ? "Hide password" : "Show password");
    });

    function guard() {
      if (loginBusy) return true;
      return false;
    }

    function submitForm() {
      if (guard()) return;
      const address = email.value.trim();
      const password = pass.value;
      if (loginMode === "Sign up".toLowerCase() && !name.value.trim()) { showError("Pick a display name."); return; }
      if (!address) { showError("Enter your email."); return; }
      if (!password) { showError(loginMode === "login" ? "Enter your email and password." : "Password needs at least 6 characters."); return; }
      if (loginMode !== "login" && password.length < 6) { showError("Password needs at least 6 characters."); return; }

      loginBusy = true;
      submit.disabled = true;
      submit.textContent = "CONNECTING…";
      showError("");
      const call = loginMode === "login"
        ? DF.Data.login(address, password)
        : DF.Data.signup(name.value.trim(), address, password);
      call.catch((e) => {
        showError(friendlyAuthError(e));
      }).then(() => {
        loginBusy = false;
        submit.disabled = false;
        paintMode();
      });
    }

    submit.addEventListener("click", submitForm);
    [email, pass, name].forEach((el) => el.addEventListener("keydown", (e) => { if (e.key === "Enter") submitForm(); }));

    root.replaceChildren(h("div", { class: "login-card" },
      h("div", { class: "login-mark", text: "💀" }),
      h("div", { class: "login-title", text: "DEAD-FEST" }),
      h("div", { class: "t-micro", style: { textAlign: "center", marginTop: "4px" }, text: `VERSION ${DF.APP_VERSION.name} // SECURE TERMINAL` }),
      h("div", { class: "stack", style: { marginTop: "var(--lg)" } },
        h("div", { class: "row" }, loginPill, signupPill),
        nameField,
        h("div", { class: "field" }, h("label", { text: "Email" }), email),
        h("div", { class: "field" }, h("label", { text: "Password" }), h("div", { class: "row" }, pass, eye)),
        err
      ),
      submit,
      h("div", { class: "t-micro", style: { marginTop: "14px", textAlign: "center" }, text: "Friends & family only. Unauthorised access is logged." })
    ));
    paintMode();
    setTimeout(() => email.focus(), 50);
  }

  /* ---- boot --------------------------------------------------------------- */
  function enterApp() {
    $("#login").classList.remove("show");
    $("#login").replaceChildren();
    $("#app").style.display = "grid";
    paintNav();
    paint();
    if (!ticker) ticker = setInterval(liveTick, 1000);
  }

  function boot() {
    applyTheme(DF.Prefs.get("theme"));
    applyPerformanceMode(DF.Prefs.get("performanceMode"));
    DF.Data.init();
    DF.Data.onAuthStateChanged((user) => {
      if (user) enterApp();
      else { paintLogin(); dailyWasClaimable = null; }
    });

    // Repaint whenever live data changes and the current screen cares about it.
    Object.keys(DF.Data.listeners).forEach((key) => {
      DF.Data.on(key, () => {
        const def = screenDef(current);
        if (!def) return;
        if (def.needs && def.needs.indexOf(key) < 0) return;
        rerender();
        if (key === "user") paintNav();
      });
    });

    // Presence: online while the window is open, offline when it closes.
    const uid = () => {
      const u = DF.Data.currentUser();
      return u ? u.uid : null;
    };
    window.addEventListener("beforeunload", () => { if (uid()) DF.Data.goOffline(uid()); });
    setInterval(() => { if (uid()) DF.Data.heartbeat(uid()); }, DF.Presence.HEARTBEAT_MS);
    setInterval(() => { if (uid()) DF.Data.setOnline(uid(), true); }, 20000);
    document.addEventListener("visibilitychange", () => {
      if (!uid()) return;
      if (document.hidden) DF.Data.goOffline(uid());
      else DF.Data.setOnline(uid(), true);
    });
  }

  return { boot, go, rerender, ctx, applyTheme, applyPerformanceMode, local, friendlyError };
})();

document.addEventListener("DOMContentLoaded", () => {
  DF.App.boot();
});
