/* =============================================================================
   Dead-Fest desktop — application shell
   Router, navigation, and the actions the screens call into.
   ========================================================================== */
window.DF = window.DF || {};

DF.APP_VERSION = { code: 13, name: "2.0.8" };

DF.App = (function () {
  const NAV = [
    { key: "home",       label: "Dash",     icon: "▦" },
    { key: "players",    label: "Players",  icon: "👥" },
    { key: "shop",       label: "Shop",     icon: "🛒" },
    { key: "wheel",      label: "Wheel",    icon: "🎡" },
    { key: "more",       label: "More",     icon: "⊞" },
    { key: "admin",      label: "Admin",    icon: "🛡", adminOnly: true },
    { key: "settings",   label: "Settings", icon: "⚙" }
  ];

  const local = {};
  let current = "home";
  let lastFocusKey = null;
  let renderQueued = false;

  /* ---- theme -------------------------------------------------------------- */
  function applyTheme(id) {
    const theme = DF.THEMES.find((t) => t.id === id) || DF.THEMES[0];
    document.documentElement.setAttribute("data-theme", theme.id);
    try { localStorage.setItem("deadfest.theme", theme.id); } catch (e) { /* ignore */ }
  }

  function storedTheme() {
    try { return localStorage.getItem("deadfest.theme") || "default"; } catch (e) { return "default"; }
  }

  /* ---- rendering ---------------------------------------------------------- */
  function screenDef(key) { return DF.Screens[key]; }

  function rerender() {
    if (renderQueued) return;
    renderQueued = true;
    requestAnimationFrame(() => {
      renderQueued = false;
      paint();
    });
  }

  function paint() {
    const def = screenDef(current);
    const body = $("#screen-body");
    if (!body || !def) return;

    // Preserve scroll and focus across a data-driven repaint.
    const scrollTop = body.scrollTop;
    const active = document.activeElement;
    const focusKey = active && active.getAttribute ? active.getAttribute("data-focus-key") : null;
    const selStart = active && active.selectionStart;
    const selEnd = active && active.selectionEnd;

    const titleEl = $("#screen-title");
    const subEl = $("#screen-subtitle");
    if (titleEl) titleEl.textContent = def.title;
    if (subEl) subEl.textContent = def.subtitle;

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
    if (body) body.scrollTop = 0;
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

  /* ---- context handed to the screens -------------------------------------- */
  const ctx = {
    state: DF.Data.state,
    appVersion: DF.APP_VERSION.code,
    appVersionName: DF.APP_VERSION.name,
    go,
    rerender,
    local(key, value) {
      if (arguments.length === 2) { local[key] = value; return value; }
      return local[key];
    },
    applyTheme,

    /* ---- home actions ---- */
    claimDaily() {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global) return;
      DF.Data.claimDaily(user.uid, global.economy, Date.now()).then((r) => {
        if (r.claimed) {
          toast(`Claimed ${fmt(r.amount)} coins.`);
          DF.Data.logEvent("DAILY", `${handleOf(user)} claimed ${fmt(r.amount)} daily rations.`, user.uid);
        } else {
          toast("Already claimed today.");
        }
      }).catch(() => toast("Couldn't claim rations.", true));
    },

    recordDeath(sector) {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global) return;
      DF.Data.recordDeath(user.uid, sector, global.economy, Date.now(), global.forceSpinProtocol).then((r) => {
        const o = r.outcome;
        const spinNote = o.spinsAdded > 0 ? ` · +${o.spinsAdded} penalty spin(s)` : "";
        toast(`Death recorded in ${sector} — ${fmt(o.tax.finalTax)} tax${spinNote}`);
        DF.Data.logEvent("DEATH", `${handleOf(user)} died in ${sector}. Casualty tax: ${fmt(o.tax.finalTax)} coins.`, user.uid);
        DF.Data.payMutualReward(user.uid, global.economy).then((paid) => {
          if (paid > 0) toast(`${paid} survivor(s) paid ${fmt(global.economy.survivalReward)} each.`);
        });
      }).catch((e) => toast(e && e.message ? e.message : "Couldn't record death.", true));
    },

    pickSector(sectors, overloadLive, global) {
      const body = h("div", { class: "stack stack--tight" });
      sectors.forEach((sector) => {
        const overloaded = overloadLive && sector === global.overloadedSector;
        body.append(h("div", {
          class: "list-row",
          style: overloaded ? { borderColor: "var(--amber)" } : null,
          onclick: () => { closeModal(); ctx.recordDeath(sector); }
        },
          h("div", { style: { flex: "1" } },
            h("div", { class: "t-card-title", text: sector.toUpperCase() }),
            h("div", { class: "t-body", text: overloaded ? `Overloaded · x${global.multiplier}` : "Where did you fall, survivor?" })
          ),
          h("span", { text: "→", style: { color: "var(--ember)" } })
        ));
      });
      openModal({ title: "SELECT SECTOR", body, wide: false });
    },

    /* ---- players ---- */
    openDossier(user) {
      const pd = user.playerData || {};
      const games = pd.games || {};
      const body = h("div", { class: "stack" },
        h("div", { class: "row" },
          avatar(user, "lg"),
          h("div", { style: { flex: "1" } },
            h("div", { class: "t-card-title", text: handleOf(user) }),
            h("div", { class: "t-body", text: (pd.title || "Survivor") + " · Level " + (pd.level || 1) })
          ),
          pd.isOnline ? statusPill("ONLINE", "toxic") : null
        ),
        h("div", { class: "divider" }),
        keyValue("Coins", fmt(pd.coins || 0)),
        keyValue("Deaths", fmt(totalDeaths(user))),
        keyValue("Level", String(pd.level || 1)),
        keyValue("XP", fmt(pd.xp || 0)),
        keyValue("Prestige", String(pd.prestigeCount || 0)),
        keyValue("Insurance tier", pd.insulationTier ? DF.Economy.roman(pd.insulationTier) : "None"),
        keyValue("Pending spins", String(pd.pendingSpins || 0)),
        keyValue("Status", ["Solvent", "Bankruptcy L1", "Bankruptcy L2"][pd.bankruptcyLevel || 0], (pd.bankruptcyLevel || 0) > 0 ? "var(--ember)" : null),
        keyValue("Last seen", timeAgo(pd.lastSeen))
      );
      const gamesBody = h("div", { class: "stack stack--tight" });
      Object.keys(games).forEach((g) => gamesBody.append(keyValue(g, fmt((games[g] || {}).deaths || 0))));
      if (Object.keys(games).length) {
        body.append(h("div", { class: "divider" }), sectionLabel("Deaths by sector"), gamesBody);
      }
      openModal({ title: "SURVIVOR DOSSIER", body, actions: [h("button", { class: "btn btn--ghost", text: "CLOSE", onclick: () => closeModal() })] });
    },

    /* ---- shop ---- */
    ownedState(item, pd) {
      if (item.id === "shield" && pd.shield) return true;
      if (item.id === "lucky_coin" && pd.luckyCoin) return true;
      if (item.id === "insurance" && pd.insurance) return true;
      if (item.id === "ration_upgrade" && pd.dailyUpgradeOwned) return true;
      if (item.tier > 0) {
        if ((pd.insulationTier || 0) === item.tier) return true;
        if ((pd.insulationTier || 0) !== item.requiresTier) return "TIER_LOCKED";
      }
      return false;
    },

    buy(item, categoryKey) {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global) return;
      const isCurse = item.curseDeaths > 0;
      if (isCurse) {
        openCurseDialog(item, global);
        return;
      }
      DF.Data.applyShopItem(user.uid, item, global.economy).then((r) => {
        if (r.outcome === "APPLIED") {
          toast(`Bought ${item.name}.`);
          DF.Data.logEvent("BLACK_MARKET", `${handleOf(user)} bought ${item.logName}.`, user.uid);
        } else {
          toast(shopOutcomeMessage(r.outcome), true);
        }
      }).catch(() => toast("Purchase failed.", true));
    },

    /* ---- wheel ---- */
    spinWheel(items, wheelEl) {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global || !items.length) return;
      const index = DF.Economy.weightedPick(items.map((i) => i.weight));
      if (index < 0) return;
      const item = items[index];
      local("spinning", true);
      local("wheelResult", null);
      rerender();

      // Land the pointer on the middle of the winning slice.
      const totalWeight = items.reduce((a, i) => a + (i.weight > 0 ? i.weight : 0), 0);
      const before = items.slice(0, index).reduce((a, i) => a + (i.weight > 0 ? i.weight : 0), 0);
      const slicePct = totalWeight > 0 ? ((item.weight > 0 ? item.weight : 0) / totalWeight) * 360 : 360 / items.length;
      const offset = before + slicePct / 2;
      const spin = 5 * 360 + (360 - offset);
      if (wheelEl) {
        wheelEl.style.transition = "transform 2.6s cubic-bezier(0.15,0.85,0.25,1)";
        wheelEl.style.transform = `rotate(${spin}deg)`;
      }

      setTimeout(() => {
        DF.Data.applyWheelResult(user.uid, item, global.economy).then((payout) => {
          local("spinning", false);
          let title, detail;
          if (payout.delta > 0) {
            title = `+${fmt(payout.delta)} COINS`;
            detail = (payout.luckyCoinUsed ? "LUCKY x2! " : "") + "The Wheel of Fate was kind.";
          } else if (payout.delta < 0) {
            title = `-${fmt(-payout.delta)} COINS`;
            detail = "The Wheel of Fate took its cut.";
          } else {
            title = item.label.toUpperCase();
            detail = payout.penaltyCleared
              ? (payout.pendingLeft > 0 ? `Penalty cleared · ${payout.pendingLeft} left` : "All penalty spins cleared")
              : "An event, not coins.";
          }
          local("wheelResult", { title, detail });
          rerender();
          DF.Data.logEvent("WHEEL_SPIN", `${handleOf(user)} spun the Wheel of Fate and ${payout.delta >= 0 ? "won" : "lost"} ${fmt(Math.abs(payout.delta))} coins.`, user.uid);
        }).catch(() => {
          local("spinning", false);
          toast("The spin failed.", true);
          rerender();
        });
      }, 2650);
    },

    /* ---- scratch ---- */
    buyScratch(pool) {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global) return;
      const index = DF.Economy.weightedPick(pool.map((p) => p.weight));
      const prize = pool[index];
      DF.Data.scratchCard(user.uid, prize, global.economy).then((r) => {
        local("scratchBought", true);
        local("scratchRevealed", []);
        local("scratchPrize", r);
        rerender();
        DF.Data.logEvent("SCRATCH", `${handleOf(user)} scratched a card.`, user.uid);
      }).catch((e) => toast(e && e.message ? e.message : "Couldn't buy a card.", true));
    },

    revealScratch(index, pool) {
      const revealed = (local("scratchRevealed", []) || []).slice();
      if (revealed.indexOf(index) >= 0) return;
      revealed.push(index);
      local("scratchRevealed", revealed);
      const result = local("scratchPrize", null);
      if (revealed.length >= 9) {
        toast(result ? `You won: ${result.label}` : "Card revealed.");
        local("scratchBought", false);
      }
      rerender();
    },

    /* ---- progression ---- */
    prestige() {
      const user = DF.Data.state.user;
      const global = DF.Data.state.global;
      if (!user || !global) return;
      confirmDialog({
        title: "PRESTIGE?",
        message: "You go back to level 1 with 0 XP and keep everything you unlocked. This cannot be undone.",
        confirmText: "PRESTIGE",
        onConfirm: () => {
          DF.Data.prestige(user.uid, global.economy).then((count) => {
            if (count) { toast(`Prestige ${count}.`); DF.Data.logEvent("PRESTIGE", `${handleOf(user)} reached prestige ${count}.`, user.uid); }
            else toast("You are not at max level yet.", true);
          }).catch(() => toast("Prestige failed.", true));
        }
      });
    },

    equip(reward) {
      const user = DF.Data.state.user;
      if (!user) return;
      if (reward.type === "title") {
        DF.Data.equipTitle(user.uid, reward.value).then(() => toast("Title equipped.")).catch(() => toast("Couldn't equip.", true));
      } else {
        DF.Data.equipCosmetic(user.uid, reward.type, reward.value).then(() => toast("Equipped.")).catch(() => toast("Couldn't equip.", true));
      }
    },

    /* ---- identity ---- */
    saveHandle(name) {
      const user = DF.Data.state.user;
      if (!user) return;
      DF.Data.changeUsername(user.uid, name).then((r) => {
        if (r.outcome === "OK") { toast(`Handle updated to ${String(name).trim()}.`); }
        else if (r.outcome === "SAME") { toast("That's already your handle."); }
        else if (r.outcome === "TAKEN") { toast(`"${String(name).trim()}" is already taken by another survivor.`, true); }
        else { toast(r.reason || "Couldn't update handle.", true); }
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
      let offline = false;

      const renderManual = () => {
        manual.replaceChildren(
          h("div", { class: "t-body", text: offline ? "The game database is unreachable right now — enter the game by hand." : "No match found — enter the game by hand." }),
          nameInput, coverInput
        );
        manual.style.display = (offline || results.children.length === 0) ? "flex" : "none";
      };

      const doSearch = () => {
        const q = query.value.trim();
        if (q.length < 2) return;
        results.replaceChildren(h("div", { class: "row" }, h("span", { class: "spinner" }), h("span", { class: "t-body", text: "Searching…" })));
        DF.TheGamesDb.search(q).then((games) => {
          offline = games.offline;
          results.replaceChildren();
          if (!games.items.length) {
            results.append(h("div", { class: "t-body", text: "No matches." }));
          } else {
            games.items.forEach((g) => {
              results.append(h("div", { class: "list-row", onclick: () => { nameInput.value = g.title; if (g.coverUrl) coverInput.value = g.coverUrl; renderManual(); } },
                h("div", { style: { flex: "1" } },
                  h("div", { class: "t-card-title", text: g.title }),
                  h("div", { class: "t-body", text: [g.platform, g.releaseDate].filter(Boolean).join(" · ") })
                )
              ));
            });
          }
          renderManual();
        });
      };

      let timer = null;
      query.addEventListener("input", () => {
        if (timer) clearTimeout(timer);
        timer = setTimeout(doSearch, 400);
      });

      const body = h("div", { class: "stack" }, query, results, manual);
      renderManual();

      openModal({
        title: "REQUEST A GAME",
        body,
        wide: true,
        actions: [
          h("button", { class: "btn btn--ghost", text: "CANCEL", onclick: () => closeModal() }),
          h("button", {
            class: "btn", text: "SEND REQUEST",
            onclick: () => {
              const name = nameInput.value.trim();
              if (!name) { toast("Give the game a name first.", true); return; }
              const user = DF.Data.state.user;
              DF.Data.submitGameRequest(name, coverInput.value.trim(), user.uid, handleOf(user)).then((r) => {
                closeModal();
                if (r.outcome === "OK") { toast("Request sent."); rerender(); }
                else toast(r.reason || "Couldn't send the request.", true);
              });
            }
          })
        ]
      });
    },

    /* ---- update + session ---- */
    checkUpdate() {
      const global = DF.Data.state.global;
      if (!global) return;
      if (global.latestVersionCode > DF.APP_VERSION.code) {
        const url = global.updateUrlR8 || global.updateUrl;
        if (url) {
          confirmDialog({
            title: `v${global.latestVersionName} AVAILABLE`,
            message: global.updateNotes || "A new build is available. On the phone app this downloads and installs automatically; on desktop, grab it from the releases page.",
            confirmText: "OPEN LINK",
            onConfirm: () => { window.open(url, "_blank"); }
          });
        } else {
          toast("You are up to date.");
        }
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
      DF.Data.updateGlobalState(map).then(() => toast("Saved.")).catch(() => toast("Save failed.", true));
    },
    setShopOverride(id, patch) {
      const global = DF.Data.state.global;
      if (!global) return;
      const shop = Object.assign({}, global.economy.shop);
      shop[id] = Object.assign({}, shop[id] || {}, patch);
      const map = {};
      map["shopConfig." + id] = shop[id];
      DF.Data.updateGlobalState(map).then(() => toast("Shop updated.")).catch(() => toast("Update failed.", true));
    },
    setScratchPrize(prize, patch) {
      const pool = (DF.Data.state.scratchPrizes && DF.Data.state.scratchPrizes.length) ? DF.Data.state.scratchPrizes : DF.Economy.SCRATCH_POOL;
      const next = pool.map((p) => (p.id === prize.id || p.label === prize.label) ? Object.assign({}, p, patch) : p);
      DF.Data.seedScratchPrizes(next).then(() => toast("Prize pool updated.")).catch(() => toast("Update failed.", true));
    },
    deleteScratchPrize(prize) {
      const pool = (DF.Data.state.scratchPrizes && DF.Data.state.scratchPrizes.length) ? DF.Data.state.scratchPrizes : DF.Economy.SCRATCH_POOL;
      const next = pool.filter((p) => !(p.id === prize.id && prize.id) && p.label !== prize.label);
      DF.Data.seedScratchPrizes(next).then(() => toast("Prize removed.")).catch(() => toast("Remove failed.", true));
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
      const name = (local("newSector", "") || "").trim();
      const global = DF.Data.state.global;
      if (!name) { toast("Type a sector name first.", true); return; }
      if (global && global.games.some((g) => g.toLowerCase() === name.toLowerCase())) { toast("That sector already exists.", true); return; }
      local("newSector", "");
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
      const label = (local("newWheelLabel", "") || "").trim();
      if (!label) { toast("Give the slice a label.", true); return; }
      const value = (local("newWheelValue", "") || "").trim();
      const weight = parseFloat(local("newWheelWeight", "1")) || 1;
      const items = (global ? global.wheelItems : []).slice();
      items.push({ id: String(Date.now()), label, value, actionType: "give", color: "#FF4B4B", enabled: true, weight });
      local("newWheelLabel", "");
      ctx.setGlobal({ wheelItems: items });
    },
    approveRequest(id) {
      const user = DF.Data.state.user;
      DF.Data.setGameRequestStatus(id, true, handleOf(user)).then(() => toast("Request approved.")).catch(() => toast("Approve failed.", true));
    },
    removeRequest(id) {
      DF.Data.removeGameRequest(id).then(() => toast("Request removed.")).catch(() => toast("Remove failed.", true));
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
      const name = (local("archiveName", "") || "").trim();
      if (!name) { toast("Name the season first.", true); return; }
      local("archiveName", "");
      DF.Data.createArchive(name).then(() => toast("Season archived and reset.")).catch(() => toast("Archive failed.", true));
    },
    restoreArchive(archive) {
      confirmDialog({
        title: "RESTORE SEASON?",
        message: `Restore "${archive.name}"? This overwrites current coins, deaths and names with the archived snapshot.`,
        confirmText: "RESTORE",
        destructive: true,
        onConfirm: () => {
          DF.Data.restoreArchive(archive.id).then(() => toast("Season restored.")).catch(() => toast("Restore failed.", true));
        }
      });
    },
    sendBroadcast() {
      const text = (local("broadcastText", "") || "").trim();
      if (!text) { toast("Write something first.", true); return; }
      local("broadcastText", "");
      const user = DF.Data.state.user;
      DF.Data.logEvent("WASTELAND_ALERT", text, user ? user.uid : null);
      toast("Broadcast sent to the feed.");
    },
    openPlayerDetail(user) {
      const pd = user.playerData || {};
      const coinInput = h("input", { value: String(pd.coins || 0) });
      const body = h("div", { class: "stack" },
        h("div", { class: "row" },
          avatar(user, "lg"),
          h("div", { style: { flex: "1" } },
            h("div", { class: "t-card-title", text: handleOf(user) }),
            h("div", { class: "t-body", text: (pd.title || "Survivor") + " · Level " + (pd.level || 1) })
          )
        ),
        h("div", { class: "divider" }),
        keyValue("Coins", fmt(pd.coins || 0)),
        keyValue("Deaths", fmt(totalDeaths(user))),
        keyValue("Pending spins", String(pd.pendingSpins || 0)),
        keyValue("Bankruptcy", ["Solvent", "Level 1", "Level 2"][pd.bankruptcyLevel || 0]),
        h("div", { class: "field" }, h("label", { text: "Set exact coins" }), coinInput),
        h("div", { class: "row row--wrap" },
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
    adminAdjustCoins(uid, delta) {
      DF.Data.adjustCoins(uid, delta).then(() => toast(`Coins ${delta > 0 ? "+" : ""}${delta}.`)).catch(() => toast("Adjust failed.", true));
    }
  };

  /* ---- login -------------------------------------------------------------- */
  function paintLogin() {
    const root = $("#login");
    const app = $("#app");
    root.classList.add("show");
    app.style.display = "none";
    root.replaceChildren(h("div", { class: "login-card" },
      h("div", { class: "login-mark", text: "💀" }),
      h("div", { class: "login-title", text: "DEAD-FEST" }),
      h("div", { class: "t-subtitle login-sub", text: "SURVIVAL TERMINAL" }),
      h("div", { class: "stack" },
        h("div", { class: "field" }, h("label", { text: "Email" }), h("input", { id: "login-email", type: "email", placeholder: "survivor@deadfest" })),
        h("div", { class: "field" }, h("label", { text: "Password" }), h("input", { id: "login-pass", type: "password", placeholder: "••••••••" })),
        h("div", { class: "field" }, h("label", { text: "Callsign (new accounts)" }), h("input", { id: "login-name", placeholder: "Your name" })),
        h("div", { class: "login-err", id: "login-err" })
      ),
      h("button", { class: "btn btn--full", style: { marginTop: "8px" }, text: "SIGN IN", onclick: doLogin }),
      h("button", { class: "btn btn--ghost btn--full", style: { marginTop: "8px" }, text: "CREATE ACCOUNT", onclick: doSignup }),
      h("div", { class: "t-body", style: { marginTop: "14px", textAlign: "center" }, text: "Same accounts as the phone app." })
    ));
    setTimeout(() => { const e = $("#login-email"); if (e) e.focus(); }, 50);
  }

  function showLoginError(message) {
    const el = $("#login-err");
    if (el) el.textContent = message;
  }

  function doLogin() {
    const email = $("#login-email").value.trim();
    const pass = $("#login-pass").value;
    if (!email || !pass) { showLoginError("Email and password are required."); return; }
    showLoginError("Signing in…");
    DF.Data.login(email, pass).then(() => showLoginError("")).catch((e) => {
      showLoginError(friendlyAuthError(e));
    });
  }

  function doSignup() {
    const name = $("#login-name").value.trim();
    const email = $("#login-email").value.trim();
    const pass = $("#login-pass").value;
    if (!name) { showLoginError("Pick a callsign first."); return; }
    if (!email || !pass) { showLoginError("Email and password are required."); return; }
    if (pass.length < 6) { showLoginError("Use at least 6 characters."); return; }
    showLoginError("Creating account…");
    DF.Data.signup(name, email, pass).then(() => showLoginError("")).catch((e) => {
      showLoginError(friendlyAuthError(e));
    });
  }

  function friendlyAuthError(e) {
    const code = e && e.code ? e.code : "";
    if (code === "auth/invalid-credential" || code === "auth/wrong-password" || code === "auth/user-not-found") return "Wrong email or password.";
    if (code === "auth/email-already-in-use") return "That email already has an account.";
    if (code === "auth/weak-password") return "Use at least 6 characters.";
    if (code === "auth/too-many-requests") return "Too many attempts. Try again in a moment.";
    if (code === "auth/network-request-failed") return "Network problem. Check your connection.";
    return e && e.message ? e.message : "Something went wrong.";
  }

  /* ---- boot --------------------------------------------------------------- */
  function enterApp() {
    $("#login").classList.remove("show");
    $("#login").replaceChildren();
    $("#app").style.display = "grid";
    paintNav();
    paint();
  }

  function boot() {
    applyTheme(storedTheme());
    DF.Data.init();
    DF.Data.onAuthStateChanged((user) => {
      if (user) enterApp();
      else paintLogin();
    });

    // Repaint whenever live data changes and the current screen cares about it.
    Object.keys(DF.Data.listeners).forEach((key) => {
      DF.Data.on(key, () => {
        const def = screenDef(current);
        if (!def) return;
        if (def.needs.indexOf(key) < 0) return;
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
    setInterval(() => { if (uid()) DF.Data.heartbeat(uid()); }, 60000);
    setInterval(() => { if (uid()) DF.Data.setOnline(uid(), true); }, 20000);
  }

  return { boot, go, rerender, ctx, applyTheme, local };
})();

document.addEventListener("DOMContentLoaded", () => {
  DF.App.boot();
});
