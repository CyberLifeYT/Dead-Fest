/* =============================================================================
   Dead-Fest desktop — core screens
   -----------------------------------------------------------------------------
   Home, Players, Shop, Wheel, Scratch and Progression, rebuilt against the
   Android screens of record. Every string, gate and ordering rule here comes
   from HomeScreen.kt, PlayersScreen.kt, ShopScreen.kt, WheelScreen.kt,
   ScratchScreen.kt and ProgressionScreen.kt rather than being invented.
   ========================================================================== */
window.DF = window.DF || {};
DF.Screens = DF.Screens || {};

(function () {
  /* ---- state helpers ------------------------------------------------------ */
  const cfg = () => (DF.Data.state.global && DF.Data.state.global.economy) || DF.Economy.DEFAULT_CONFIG;
  const world = () => DF.Data.state.global || {};
  const user = () => DF.Data.state.user;
  const pd = () => (user() && user().playerData) || {};
  const myCoins = () => pd().coins || 0;
  const isLocked = (sector) => (world().lockedGames || []).indexOf(sector) >= 0;
  const openSectors = () => (world().games || []).filter((s) => !isLocked(s));
  const L = (ctx, key, fallback) => {
    const v = ctx.local(key);
    return v === undefined || v === null ? fallback : v;
  };
  const sectorDeaths = (playerData, sector) => ((playerData.games || {})[sector] || {}).deaths || 0;

  function overloadLive(now) {
    const g = world();
    if (!g.sectorOverload || !g.overloadedSector) return false;
    return !g.overloadEndsAt || g.overloadEndsAt > (now || Date.now());
  }

  function overloadRemaining(endsAt, now) {
    if (!endsAt) return "no time limit";
    const left = Math.max(0, endsAt - (now || Date.now()));
    const mins = Math.floor(left / 60000);
    if (mins >= 60) return `${Math.floor(mins / 60)}h ${mins % 60}m left`;
    if (mins >= 1) return `${mins}m left`;
    return `${Math.max(0, Math.floor(left / 1000))}s left`;
  }

  function onlineCount() {
    return (DF.Data.state.users || []).filter((u) => isOnlineNow(u)).length;
  }

  /* ---- live feed ---------------------------------------------------------- */
  function feedEvents() {
    const prefs = DF.Prefs;
    const wheel = prefs.get("wheelAlerts") !== false;
    const market = prefs.get("marketAlerts") !== false;
    const wasteland = prefs.get("wastelandAlerts") !== false;
    return (DF.Data.state.events || []).filter((e) => {
      if (e.category === "WHEEL_SPIN") return wheel;
      if (e.category === "BLACK_MARKET") return market;
      return wasteland;
    }).slice(0, 14);
  }

  function feedStrip() {
    const events = feedEvents();
    const strip = h("div", { class: "hscroll feed-strip" });
    if (!events.length) {
      strip.append(h("div", { class: "feed-empty", text: "Awaiting wasteland transmission..." }));
    } else {
      events.forEach((e) => {
        strip.append(h("div", { class: "feed-item", title: e.message },
          h("span", { class: "feed-dot" }),
          h("span", { class: "feed-msg", text: e.message }),
          e.timestamp ? h("span", { class: "feed-time", "data-live": "ago", "data-at": String(e.timestamp), text: timeAgo(e.timestamp) }) : null
        ));
      });
    }
    return h("div", { class: "feed" },
      h("div", { class: "row row--between" },
        h("div", { class: "t-micro", text: "LIVE FEED" }),
        h("div", { class: "row", style: { gap: "5px" } },
          h("span", { class: "live-dot" }),
          h("span", { class: "t-micro toxic", text: "LIVE" })
        )
      ),
      strip
    );
  }

  /* ---- home --------------------------------------------------------------- */
  function dailyCard(ctx, playerData, economy, now) {
    const claimable = DF.Economy.canClaimDaily(playerData.lastDailyClaim || 0, now);
    const amount = playerData.dailyUpgradeOwned ? economy.dailyUpgraded : economy.dailyBase;
    const tint = claimable ? "var(--toxic)" : "var(--text-low)";
    const card = h("div", {
      class: "card",
      style: {
        borderColor: claimable ? "rgba(76,224,127,0.45)" : "var(--ink-line)",
        background: claimable ? "linear-gradient(90deg, rgba(76,224,127,0.12), transparent 70%), var(--ink2)" : "var(--ink2)"
      }
    },
      h("div", { class: "row" },
        h("div", { class: "tile", style: { "--tint": tint }, text: "📦" }),
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-card-title", text: "DAILY RATIONS" }),
          claimable
            ? h("div", { class: "t-body", text: `${fmt(amount)} coins ready to collect` })
            : h("div", { class: "t-body", "data-live": "daily", "data-at": String(now), text: "Next drop in " + DF.Economy.dailyCountdown(now) }),
          playerData.dailyUpgradeOwned
            ? h("div", { class: "t-micro amber", style: { marginTop: "4px" }, text: "RATION UPGRADE ACTIVE" })
            : null
        ),
        claimable
          ? h("button", { class: "btn btn--toxic btn--sm", text: "CLAIM", onclick: () => ctx.claimDaily() })
          : h("span", { class: "t-micro", text: "TOMORROW" })
      )
    );
    return card;
  }

  function heroStat(label, value, color) {
    return h("div", { style: { minWidth: "70px" } },
      h("div", { class: "hero-stat-val", style: { color: color || "var(--text-hi)" }, text: String(value) }),
      h("div", { class: "t-micro", text: label })
    );
  }

  function eliminationsHero(playerData) {
    const g = world();
    const deaths = g.grandTotal || 0;
    return h("div", {
      class: "hero",
      style: { background: "linear-gradient(160deg, rgba(255,75,75,0.16) 0%, rgba(255,75,75,0.02) 45%, transparent 100%), var(--ink2)" }
    },
      h("div", { class: "hero-mark", text: "💀" }),
      h("div", { class: "row", style: { alignItems: "flex-start" } },
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "hero-eyebrow", text: "GLOBAL ELIMINATIONS" }),
          h("div", { style: { fontSize: "44px", lineHeight: "48px", fontWeight: "900", letterSpacing: "-1px" }, text: fmt(deaths) })
        ),
        h("div", { class: "tile", style: { "--tint": "var(--ember)" }, text: "💀" })
      ),
      h("hr", { class: "rule" }),
      h("div", { class: "hero-stats" },
        heroStat("SURVIVORS", (DF.Data.state.users || []).length, "var(--text-hi)"),
        heroStat("SECTORS", (g.games || []).length, "var(--text-hi)"),
        heroStat("YOUR DEATHS", totalDeaths(user()), "var(--ember)")
      )
    );
  }

  function profileCard(ctx, me, economy) {
    const p = me.playerData || {};
    return h("div", { class: "card", style: { padding: "var(--md)" } },
      h("div", { class: "row" },
        avatar(me, 52),
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-card-title", text: displayNameOf(me) }),
          h("div", { class: "row row--wrap", style: { gap: "6px", marginTop: "3px" } },
            levelBadge(p.level || 1, p.prestigeCount || 0),
            h("span", { class: "t-micro ember", text: String(p.title || "SURVIVOR").toUpperCase() }),
            isOnlineNow(me) ? h("span", { class: "t-micro toxic", text: "ONLINE" }) : null
          )
        ),
        coinBadge(p.coins || 0)
      ),
      h("div", { style: { marginTop: "var(--sm)" } }, xpBar(p.xp || 0, p.level || 1, economy.xp)),
      h("div", { class: "row", style: { marginTop: "var(--sm)", gap: "6px" } },
        miniStat("DEATHS", String(totalDeaths(me)), "var(--ember)"),
        miniStat("SHIELD", p.shield ? "ACTIVE" : "NONE", p.shield ? "var(--plasma)" : "var(--text-low)"),
        miniStat("LUCKY", p.luckyCoin ? "ARMED" : "NONE", p.luckyCoin ? "var(--amber)" : "var(--text-low)")
      ),
      h("div", { class: "row", style: { marginTop: "6px", gap: "6px" } },
        miniStat("INSURANCE", p.insurance ? "ARMED" : "NONE", p.insurance ? "var(--plasma)" : "var(--text-low)"),
        miniStat("INSULATION", p.insulationTier ? "TIER " + DF.Economy.roman(p.insulationTier) : "NONE", p.insulationTier ? "var(--amber)" : "var(--text-low)"),
        miniStat("SPINS DUE", String(p.pendingSpins || 0), (p.pendingSpins || 0) > 0 ? "var(--ember)" : "var(--text-low)")
      )
    );
  }

  function sectorChips(playerData) {
    const games = world().games || [];
    if (!games.length) {
      return h("div", { class: "t-body", text: "No sectors have been configured yet." });
    }
    const strip = h("div", { class: "hscroll" });
    games.forEach((sector) => {
      const locked = isLocked(sector);
      const deaths = sectorDeaths(playerData, sector);
      strip.append(h("div", { class: "sector-chip" + (locked ? " sector-chip--locked" : "") },
        h("div", { class: "sector-chip-name", text: String(sector).toUpperCase() }),
        h("div", {
          class: "t-micro " + (locked ? "ember" : deaths > 0 ? "ember" : ""),
          style: locked ? null : { color: deaths > 0 ? "var(--ember)" : "var(--text-low)" },
          text: locked ? "LOCKED" : `${deaths} YOUR DEATHS`
        })
      ));
    });
    return strip;
  }

  function recordDeathBar(ctx, now) {
    const g = world();
    const lockedDown = g.lockdownActive === true;
    const sectors = openSectors();
    const can = !lockedDown && sectors.length > 0;
    let label = "RECORD DEATH";
    if (lockedDown) { label = "DEATH RECORDING LOCKED"; }
    else if (!sectors.length) { label = "NO OPEN SECTORS"; }

    return h("div", { class: "sticky-bar" },
      primaryAction(label, () => ctx.pickSector(sectors, overloadLive(now), g), can, "var(--ember)")
    );
  }

  DF.Screens.home = {
    title: "COMMAND CENTER",
    subtitle: "Sector Net // Live Feed",
    pill() {
      return statusPill(`${onlineCount()} ONLINE`, "toxic");
    },
    render(ctx) {
      const me = user();
      if (!me) return emptyState("🔒", "SIGNAL LOST", "Sign in to open the command center.");
      const g = world();
      const p = me.playerData || {};
      const economy = cfg();
      const now = Date.now();
      const root = h("div", { class: "stack", style: { gap: "var(--md)" } });

      root.append(feedStrip());
      if (g.lockdownActive) {
        root.append(alertBanner("🔒", "DEAD-FEST IS IN LOCKDOWN",
          "Core protocol actions are restricted by an administrator.", "var(--ember)"));
      }
      if (overloadLive(now)) {
        root.append(alertBanner("⚡", `SECTOR OVERLOAD: ${String(g.overloadedSector).toUpperCase()}`,
          `x${g.multiplier} multiplier is active in this sector · ${overloadRemaining(g.overloadEndsAt, now)}.`, "var(--amber)"));
      }

      const penalty = penaltyStatusCard(p, () => ctx.go("wheel"));
      if (penalty) root.append(penalty);

      root.append(dailyCard(ctx, p, economy, now));
      root.append(eliminationsHero(p));
      root.append(profileCard(ctx, me, economy));

      root.append(sectionLabel("Active Sectors"));
      root.append(sectorChips(p));
      root.append(recordDeathBar(ctx, now));

      return root;
    }
  };

  /* ---- players ------------------------------------------------------------ */
  function playerRow(ctx, u, rank, tab) {
    const p = u.playerData || {};
    const isMe = user() && user().uid === u.uid;
    const coins = p.coins || 0;
    const deaths = totalDeaths(u);
    const right = tab === "wealth"
      ? h("div", { class: "right" },
          h("div", { class: "t-stat amber", text: fmt(coins) }),
          h("div", { class: "t-micro", text: "COINS" })
        )
      : tab === "prowess"
        ? h("div", { class: "right" },
            h("div", { class: "t-stat ember", text: fmt(deaths) }),
            h("div", { class: "t-micro", text: "DEATHS" })
          )
        : h("div", { class: "right" },
            h("div", { class: "row", style: { gap: "5px", justifyContent: "flex-end" } }, coinIcon(13), h("div", { class: "t-card-title", text: fmt(coins) })),
            h("div", { class: "t-micro", text: `${deaths} DEATHS` })
          );

    return h("div", { class: "list-row", onclick: () => ctx.openDossier(u) },
      rank ? rankBadge(rank) : null,
      avatar(u, 44),
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "row", style: { gap: "6px" } },
          h("div", { class: "t-card-title", style: { fontSize: "14px" }, text: displayNameOf(u) }),
          isMe ? h("span", { class: "me-pill", text: "YOU" }) : null,
          p.shield ? h("span", { class: "t-micro plasma", style: { fontSize: "13px" }, text: "🛡" }) : null,
          (p.bankruptcyLevel || 0) > 0 ? h("span", { class: "t-micro ember", style: { fontSize: "13px" }, text: "💸" }) : null
        ),
        h("div", { class: "row", style: { gap: "6px", marginTop: "2px" } },
          levelBadge(p.level || 1, p.prestigeCount || 0),
          h("span", { class: "t-micro", text: String(p.title || "SURVIVOR").toUpperCase() })
        )
      ),
      right
    );
  }

  DF.Screens.players = {
    title: "SURVIVOR NETWORK",
    get subtitle() { return `${(DF.Data.state.users || []).length} registered // leaderboard`; },
    pill() { return statusPill(`${onlineCount()} ONLINE`, "toxic"); },
    render(ctx) {
      const tab = L(ctx, "playersTab", "survivors");
      const query = L(ctx, "playersQuery", "");
      const all = (DF.Data.state.users || []).slice();

      if (tab === "wealth") {
        all.sort((a, b) => ((b.playerData || {}).coins || 0) - ((a.playerData || {}).coins || 0));
      } else if (tab === "prowess") {
        all.sort((a, b) => totalDeaths(a) - totalDeaths(b) || (((b.playerData || {}).coins || 0) - ((a.playerData || {}).coins || 0)));
      } else {
        all.sort((a, b) => (Number(isOnlineNow(b)) - Number(isOnlineNow(a))) || displayNameOf(a).localeCompare(displayNameOf(b)));
      }

      const shown = all.filter((u) => displayNameOf(u).toLowerCase().indexOf(query.toLowerCase()) >= 0);

      const search = h("input", {
        placeholder: "Search survivors...",
        value: query,
        "data-focus-key": "players-search",
        style: { paddingRight: "38px" }
      });
      search.addEventListener("input", () => {
        ctx.local("playersQuery", search.value);
        ctx.rerender();
      });

      const root = h("div", { class: "stack", style: { gap: "var(--md)" } },
        h("div", { class: "tabbar" },
          pillChip("Survivors", tab === "survivors", () => { ctx.local("playersTab", "survivors"); ctx.rerender(); }),
          pillChip("Wealth", tab === "wealth", () => { ctx.local("playersTab", "wealth"); ctx.rerender(); }),
          pillChip("Prowess", tab === "prowess", () => { ctx.local("playersTab", "prowess"); ctx.rerender(); })
        ),
        h("div", { style: { position: "relative" } },
          search,
          query ? h("button", {
            class: "search-clear",
            title: "Clear search",
            text: "✕",
            onclick: () => { ctx.local("playersQuery", ""); ctx.rerender(); }
          }) : null
        )
      );

      if (!all.length) {
        root.append(emptyStateCard("👥", "NO SURVIVORS YET", "The network is quiet. Be the first to sign in."));
      } else if (!shown.length) {
        root.append(emptyStateCard("🔍", "NO MATCHES", `No survivor matches "${query}".`));
      } else {
        const list = h("div", { class: "stack stack--tight" });
        shown.forEach((u, i) => {
          // Ranks are a property of the sorted tab, not of the filtered list.
          const rank = tab === "survivors" ? 0 : all.findIndex((x) => x.uid === u.uid) + 1;
          list.append(playerRow(ctx, u, rank, tab));
        });
        root.append(list);
      }
      return root;
    }
  };

  /* ---- shop --------------------------------------------------------------- */
  const TITLES = [
    { title: "SURVIVOR", price: 0, description: "The name every survivor starts with." },
    { title: "UNDEAD LEGEND", price: 250, description: "You died. You got better." },
    { title: "WARLORD", price: 500, description: "Sectors fall where you walk." },
    { title: "SHADOW ASSASSIN", price: 750, description: "Nobody sees the curse coming." },
    { title: "APOCALYPSE EMPEROR", price: 1500, description: "The wasteland answers to you." }
  ];

  function itemStatus(item, p) {
    if (item.id === "shield") return p.shield ? "ACTIVE" : null;
    if (item.id === "lucky_coin") return p.luckyCoin ? "ARMED" : null;
    if (item.id === "insurance") return p.insurance ? "ARMED" : null;
    if (item.id === "ration_upgrade") return p.dailyUpgradeOwned ? "ACTIVE" : null;
    if (item.tier > 0) {
      const owned = p.insulationTier || 0;
      if (owned === item.tier) return "ACTIVE";
      if (owned > item.tier) return "OWNED";
      if (owned + 1 < item.tier) return "LOCKED";
    }
    return null;
  }

  function itemDescription(item, economy, p) {
    if (item.id === "insurance") {
      return `${economy.insurancePct}% of your next casualty tax is covered.`;
    }
    if (item.id === "ration_upgrade") {
      return `${fmt(economy.dailyUpgraded)} coins every day instead of ${fmt(economy.dailyBase)}.`;
    }
    if (item.tier > 0) {
      return `Tier ${DF.Economy.roman(item.tier)}: ${DF.Economy.reductionIn(item, economy)}% off every casualty tax.`;
    }
    return item.desc;
  }

  function itemCard(ctx, item, economy, p) {
    const price = DF.Economy.priceIn(item, economy);
    const enabled = DF.Economy.enabledIn(item, economy);
    const status = itemStatus(item, p);
    const affordable = (p.coins || 0) >= price;
    const blocked = status === "ACTIVE" || status === "OWNED" || status === "LOCKED" || !enabled;

    const right = h("div", { class: "stack", style: { alignItems: "flex-end", gap: "6px", flex: "none" } });

    if (!enabled) {
      right.append(statusPill("DISABLED", "low"));
    } else if (status) {
      right.append(statusPill(status, status === "ACTIVE" || status === "ARMED" ? "toxic" : status === "LOCKED" ? "low" : "plasma"));
      right.append(priceTag(price, false));
    } else {
      right.append(priceTag(price, affordable));
      right.append(h("button", {
        class: "btn btn--sm" + (affordable ? "" : " btn--off"),
        text: "BUY",
        disabled: !affordable,
        onclick: () => openBuy(ctx, item, price, economy, p)
      }));
    }

    return h("div", { class: "card item-card" },
      itemEmblem(item.tier > 0 ? "🏦" : item.id === "curse" || item.id === "double_curse" ? "🪦" : item.id === "shield" ? "🛡" : item.id === "revive" ? "💉" : item.id === "lucky_coin" ? "🍀" : item.id === "insurance" ? "📄" : item.id === "ration_upgrade" ? "📦" : "⚙", "var(--ember)"),
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "row", style: { gap: "6px" } },
          h("div", { class: "t-card-title", text: item.name }),
          status === "ACTIVE" || status === "ARMED" ? h("span", { class: "t-micro toxic", text: "●" }) : null
        ),
        h("div", { class: "t-body", text: itemDescription(item, economy, p) }),
        item.curseDeaths > 0
          ? h("div", { class: "t-micro", text: `CURSES · ${item.curseDeaths} DEATH${item.curseDeaths === 1 ? "" : "S"}` })
          : null
      ),
      right
    );
  }

  function openBuy(ctx, item, price, economy, p) {
    if (item.curseDeaths > 0) { ctx.openCurse(item); return; }
    const desc = itemDescription(item, economy, p);
    const permanent = item.oneTime ? "\n\nOne-time purchase. It's yours for good." : "";
    const message = `${desc}${permanent}\n\n${fmt(price)} coins will be taken from your balance of ${fmt(p.coins || 0)}.`;
    if (item.tier > 0) {
      const current = p.insulationTier || 0;
      const replacing = current > 0 ? ` Replaces Tier ${DF.Economy.roman(current)}.` : "";
      confirmDialog({
        title: `UPGRADE TO TIER ${DF.Economy.roman(item.tier)}?`,
        message: `${desc}${replacing}\n\n${fmt(price)} coins will be taken from your balance of ${fmt(p.coins || 0)}.`,
        confirmText: "UPGRADE",
        onConfirm: () => ctx.applyPurchase(item)
      });
      return;
    }
    confirmDialog({
      title: `BUY ${item.name}?`,
      message,
      confirmText: "BUY",
      onConfirm: () => ctx.applyPurchase(item)
    });
  }

  function scratchEntryCard(ctx) {
    return h("div", { class: "card card--click", style: { borderColor: "rgba(77,208,225,0.4)" }, onclick: () => ctx.go("scratch") },
      h("div", { class: "row" },
        itemEmblem("🎟", "var(--plasma)"),
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-card-title", text: "SCRATCH CARDS" }),
          h("div", { class: "t-body", text: `A quick gamble. ${fmt(cfg().scratchCost)} coins a card.` })
        ),
        h("span", { class: "t-micro plasma", text: "OPEN →" })
      )
    );
  }

  function insulationTab(ctx, economy, p) {
    const current = p.insulationTier || 0;
    const tiers = DF.Economy.SHOP.filter((i) => i.tier > 0).sort((a, b) => a.tier - b.tier);
    const activeReduction = DF.Economy.insulationReduction(current, economy);
    const root = h("div", { class: "stack" });

    const tracker = h("div", { class: "row", style: { gap: "4px" } });
    for (let t = 1; t <= 4; t++) {
      tracker.append(h("div", {
        class: "tier-seg" + (t <= current ? " on" : ""),
        style: t <= current ? { background: "var(--amber)", borderColor: "var(--amber)" } : null
      }));
    }

    root.append(heroPanel({
      eyebrow: "Wealth Insulation",
      title: current === 0 ? "NO TIER YET" : `TIER ${DF.Economy.roman(current)} OF IV`,
      glyph: "🏦",
      accent: "var(--amber)",
      badge: statusPill(current === 0 ? "UNPROTECTED" : `${activeReduction}% COVERED`, current === 0 ? "ember" : "amber"),
      children: h("div", { class: "stack", style: { marginTop: "var(--sm)" } },
        tracker,
        h("div", { class: "t-body", text: current === 0
          ? "Every death takes a cut of your coins. Insulation shrinks that cut, permanently."
          : `Tier ${DF.Economy.roman(current)} erases ${activeReduction}% of every casualty tax, permanently.` })
      )
    }));

    if (!economy.insulationEnabled) {
      root.append(noticeCard("🚫", "INSULATION OFFLINE", "Wealth insulation is disabled by an administrator."));
      return root;
    }

    root.append(sectionLabel("Insulation Tiers"));
    tiers.forEach((item) => root.append(itemCard(ctx, item, economy, p)));
    return root;
  }

  function titlesTab(ctx, p) {
    const root = h("div", { class: "stack" });
    const owned = Array.isArray(p.ownedTitles) ? p.ownedTitles : [];
    TITLES.forEach((offer) => {
      const isEquipped = String(p.title || "Survivor").toUpperCase() === offer.title;
      const isOwned = offer.price === 0 || owned.indexOf(offer.title) >= 0;
      const affordable = (p.coins || 0) >= offer.price;
      const right = h("div", { class: "stack", style: { alignItems: "flex-end", gap: "6px", flex: "none" } });
      if (isEquipped) {
        right.append(statusPill("EQUIPPED", "toxic"));
      } else if (isOwned) {
        right.append(h("button", { class: "btn btn--ghost btn--sm", text: "EQUIP", onclick: () => ctx.equipTitleNow(offer.title) }));
      } else {
        right.append(priceTag(offer.price, affordable));
        right.append(h("button", {
          class: "btn btn--sm" + (affordable ? "" : " btn--off"),
          text: "BUY",
          disabled: !affordable,
          onclick: () => confirmDialog({
            title: `UNLOCK ${offer.title}?`,
            message: `${offer.description}\n\n${fmt(offer.price)} coins will be taken from your balance of ${fmt(p.coins || 0)}.`,
            confirmText: "UNLOCK",
            onConfirm: () => ctx.buyTitleNow(offer)
          })
        }));
      }
      root.append(h("div", { class: "card item-card" },
        itemEmblem("🎖", "var(--prestige)"),
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-card-title", text: offer.title }),
          h("div", { class: "t-body", text: offer.description })
        ),
        right
      ));
    });
    return root;
  }

  DF.Screens.shop = {
    title: "BLACK MARKET",
    subtitle: "Illegal Gear & Curses",
    pill() { return coinBadge(myCoins()); },
    render(ctx) {
      const g = world();
      const economy = cfg();
      const p = pd();

      if (g.lockdownActive) {
        return noticeCard("🔒", "SYSTEM LOCKDOWN ACTIVE",
          "The Black Market is currently locked down by an administrator.");
      }
      if (!g.marketEnabled) {
        return noticeCard("🚫", "SHOP CLOSED BY ADMIN",
          "All transactions and illegal gear acquisitions are temporarily suspended.");
      }

      const tab = L(ctx, "shopTab", "all");
      const root = h("div", { class: "stack", style: { gap: "var(--md)" } });
      const tabs = h("div", { class: "tabbar" });
      [["all", "All"], ["combat", "Combat"], ["economy", "Economy"], ["insulation", "Insulation"], ["titles", "Titles"]].forEach(([key, label]) => {
        tabs.append(pillChip(label, tab === key, () => { ctx.local("shopTab", key); ctx.rerender(); }));
      });
      root.append(tabs);

      if (tab === "insulation") {
        root.append(insulationTab(ctx, economy, p));
        return root;
      }
      if (tab === "titles") {
        root.append(titlesTab(ctx, p));
        return root;
      }

      if ((tab === "all" || tab === "economy") && economy.scratchEnabled) {
        root.append(scratchEntryCard(ctx));
      }

      let items = DF.Economy.SHOP.slice();
      if (tab === "combat") items = items.filter((i) => i.category === "COMBAT");
      if (tab === "economy") items = items.filter((i) => i.category === "ECONOMY");
      items.forEach((item) => root.append(itemCard(ctx, item, economy, p)));
      return root;
    }
  };

  /* ---- wheel -------------------------------------------------------------- */
  function wheelItems() {
    return (world().wheelItems || []).filter((i) => i.enabled !== false && i.weight > 0);
  }

  function wheelDisc(items, angle) {
    const total = items.reduce((a, i) => a + i.weight, 0);
    let acc = 0;
    const stops = items.map((i) => {
      const from = acc / total * 360;
      acc += i.weight;
      const to = acc / total * 360;
      return `${i.color} ${from}deg ${to}deg`;
    });
    const disc = h("div", {
      id: "wheel-disc",
      class: "wheel-disc",
      style: {
        background: `conic-gradient(from 0deg, ${stops.join(",")})`,
        transform: `rotate(${angle || 0}deg)`
      }
    });

    let cursor = 0;
    items.forEach((item) => {
      const mid = (cursor + item.weight / 2) / total * 360;
      cursor += item.weight;
      disc.append(h("div", {
        class: "wheel-label",
        style: { transform: `rotate(${mid}deg) translateY(-38%) rotate(${-mid}deg)` },
        text: String(item.label || "").toUpperCase()
      }));
    });
    disc.append(h("div", { class: "wheel-hub", text: "💀" }));
    return disc;
  }

  function wheelResultLabel(result) {
    if (!result) return "";
    if (result.penaltyCleared) {
      return result.pendingLeft > 0 ? `PENALTY CLEARED · ${result.pendingLeft} LEFT` : "ALL PENALTY SPINS CLEARED";
    }
    if (result.delta > 0) return `YOU WON ${fmt(result.delta)} COINS`;
    if (result.delta < 0) return `YOU LOST ${fmt(Math.abs(result.delta))} COINS`;
    return "NOTHING THIS TIME";
  }

  DF.Screens.wheel = {
    title: "WHEEL OF FATE",
    subtitle: "TEST YOUR LUCK, SURVIVOR",
    pill() {
      const p = pd();
      return (p.pendingSpins || 0) > 0
        ? statusPill(`${p.pendingSpins} PENALTY SPIN${p.pendingSpins === 1 ? "" : "S"}`, "ember")
        : coinBadge(myCoins());
    },
    back(ctx) { return ghostAction("BACK", () => ctx.go("more")); },
    render(ctx) {
      const g = world();
      const economy = cfg();
      const p = pd();
      if (g.lockdownActive) {
        return noticeCard("🔒", "SYSTEM LOCKDOWN ACTIVE",
          "The Wheel of Fate is currently locked down by an administrator.");
      }
      if (!g.wheelEnabled) {
        return noticeCard("🚫", "WHEEL OF FATE OFFLINE",
          "Fate is taking a break. The wheel has been disabled by an administrator.");
      }

      const items = wheelItems();
      const spinning = ctx.local("spinning") === true;
      const result = ctx.local("wheelResult");
      const pending = p.pendingSpins || 0;
      const angle = ctx.local("wheelAngle") || 0;

      const root = h("div", { class: "stack", style: { gap: "var(--md)", alignItems: "center" } });

      if (!items.length) {
        root.append(noticeCard("🎡", "WHEEL IS EMPTY", "An administrator hasn't loaded any slices yet."));
        return root;
      }

      root.append(h("div", { class: "wheel-hero" },
        h("div", { class: "wheel-pointer", text: "▼" }),
        wheelDisc(items, angle),
        h("div", { class: "wheel-center-ring" })
      ));

      const stateLabel = spinning ? "SPIN IN PROGRESS"
        : result ? "SPIN COMPLETE"
        : pending > 0 ? "PENALTY SPINS QUEUED"
        : "READY TO SPIN";
      root.append(h("div", {
        id: "wheel-state",
        class: "t-micro " + (spinning ? "amber" : result ? "toxic" : pending > 0 ? "ember" : ""),
        text: stateLabel
      }));

      if (result) {
        const payout = result.result || result;
        root.append(h("div", {
          class: "card",
          style: {
            width: "100%",
            borderColor: payout.penaltyCleared ? "rgba(255,75,75,0.45)" : payout.delta >= 0 ? "rgba(76,224,127,0.45)" : "rgba(255,75,75,0.45)",
            background: payout.penaltyCleared ? "rgba(255,75,75,0.07)" : "var(--ink2)"
          }
        },
          h("div", { class: "stack stack--tight", style: { alignItems: "center" } },
            h("div", {
              class: "t-stat",
              style: { color: payout.penaltyCleared ? "var(--ember)" : payout.delta > 0 ? "var(--toxic)" : payout.delta < 0 ? "var(--ember)" : "var(--text-mid)" },
              text: wheelResultLabel(payout)
            }),
            payout.luckyCoinUsed ? h("div", { class: "t-micro amber", text: "LUCKY COIN · PAYOUT DOUBLED" }) : null,
            h("div", { class: "t-micro", text: String(result.item.label || "").toUpperCase() + (result.item.value ? " · " + result.item.value : "") })
          )
        ));
      }

      const disabled = spinning || pending === 0 && !g.wheelEnabled;
      const label = spinning ? "FATE IS DECIDING..."
        : pending > 0 ? `EXECUTE PENALTY SPIN (${pending})`
        : "INITIATE SPIN";
      const btn = h("button", {
        id: "wheel-btn",
        class: "btn btn--full btn--tall" + (pending > 0 ? "" : " btn--ember"),
        style: pending > 0 ? { background: "var(--ember)" } : null,
        disabled,
        text: label,
        onclick: () => ctx.spinWheel(items)
      });
      root.append(btn);

      if (pending > 0) {
        root.append(alertBanner("🎰", "PENALTY SPINS QUEUED",
          `${pending} forced spin${pending === 1 ? "" : "s"} must be cleared before you can spin for fun.`, "var(--ember)"));
      }

      root.append(h("div", { class: "card", style: { width: "100%" } },
        h("div", { class: "t-micro", text: "ON THE WHEEL" }),
        h("div", { class: "stack stack--tight", style: { marginTop: "6px" } },
          ...items.map((i) => h("div", { class: "row row--between" },
            h("div", { class: "row", style: { gap: "6px" } },
              h("span", { style: { width: "10px", height: "10px", borderRadius: "2px", background: i.color, flex: "none" } }),
              h("span", { class: "t-body", text: String(i.label).toUpperCase() })
            ),
            h("span", { class: "t-micro", text: i.actionType === "take" ? `−${i.value}` : i.value ? `+${i.value}` : "—" })
          ))
        )
      ));

      return root;
    }
  };

  /* ---- scratch ------------------------------------------------------------ */
  function scratchPrizeText(prize) {
    if (!prize) return "?";
    if (prize.type === "coins") return `${fmt(parseInt(prize.value, 10) || 0)} COINS`;
    if (prize.type === "shield") return "SHIELD";
    if (prize.type === "lucky_coin") return "LUCKY COIN";
    if (prize.type === "revive") return "REVIVE";
    return String(prize.label || "NOTHING").toUpperCase();
  }

  DF.Screens.scratch = {
    title: "SCRATCH CARDS",
    subtitle: "INSTANT WASTELAND LOTTERY",
    pill() { return coinBadge(myCoins()); },
    back(ctx) { return ghostAction("BACK", () => ctx.go("more")); },
    render(ctx) {
      const g = world();
      const economy = cfg();
      const p = pd();
      if (g.lockdownActive) {
        return noticeCard("🔒", "SYSTEM LOCKDOWN ACTIVE",
          "Scratch cards are currently locked down by an administrator.");
      }
      if (!economy.scratchEnabled) {
        return noticeCard("🚫", "SCRATCH CARDS OFFLINE",
          "Scratch cards are disabled by an administrator.");
      }

      const cost = economy.scratchCost;
      const busy = ctx.local("scratchBusy") === true;
      const prize = ctx.local("scratchPrize");
      const enabled = (DF.Data.state.scratchPrizes || []).filter((x) => x.enabled !== false);
      const total = enabled.reduce((a, x) => a + (x.weight > 0 ? x.weight : 0), 0);

      const root = h("div", { class: "stack", style: { gap: "var(--md)" } });

      if (!enabled.length) {
        root.append(noticeCard("🎟", "NO PRIZES LOADED",
          "An administrator hasn't loaded the prize pool yet."));
        return root;
      }

      const foil = h("div", {
        id: "scratch-foil",
        class: "scratch-foil" + (prize && Date.now() - (prize.at || 0) > 4000 ? " gone" : ""),
        text: busy ? "PRINTING..." : "SCRATCH"
      });
      if (prize && Date.now() - (prize.at || 0) <= 4000) {
        setTimeout(() => { if (foil.isConnected) foil.classList.add("gone"); }, 40);
      }

      root.append(h("div", { class: "scratch-card" },
        h("div", { class: "scratch-prize" },
          h("div", { class: "t-micro", text: prize ? String(prize.prize.label || "PRIZE").toUpperCase() : "UNSCRATCHED" }),
          h("div", { class: "scratch-prize-value", style: prize ? { color: (prize.prize.color || "var(--toxic)") } : null, text: prize ? scratchPrizeText(prize.prize) : "•••" }),
          prize && prize.convertedItem ? h("div", { class: "t-body", text: `${prize.convertedItem} added automatically.` }) : null,
          prize && prize.coinsWon ? h("div", { class: "t-micro amber", text: `PAYOUT ${fmt(prize.coinsWon)} COINS` }) : null
        ),
        foil
      ));

      const affordable = (p.coins || 0) >= cost;
      const label = busy ? "SCRATCHING..." : !affordable ? `NEED ${fmt(cost)} COINS` : `SCRATCH A CARD · ${fmt(cost)} COINS`;
      root.append(h("button", {
        class: "btn btn--full btn--tall" + (busy || !affordable ? " btn--off" : ""),
        disabled: busy || !affordable,
        text: label,
        onclick: () => ctx.buyScratch(enabled)
      }));
      root.append(h("div", { class: "t-micro", style: { textAlign: "center" }, text: `BALANCE ${fmt(p.coins || 0)} COINS` }));

      const odds = h("div", { class: "card" });
      odds.append(h("div", { class: "t-micro", text: "PRIZE ODDS" }));
      const rows = h("div", { class: "stack stack--tight", style: { marginTop: "6px" } });
      enabled.slice().sort((a, b) => (b.weight || 0) - (a.weight || 0)).forEach((x) => {
        const chance = total > 0 ? ((x.weight || 0) / total) * 100 : 0;
        rows.append(h("div", { class: "row row--between" },
          h("div", { class: "row", style: { gap: "6px" } },
            h("span", { style: { width: "10px", height: "10px", borderRadius: "2px", background: x.color || "var(--ink4)", flex: "none" } }),
            h("span", { class: "t-body", text: String(x.label).toUpperCase() })
          ),
          h("div", { class: "row", style: { gap: "8px" } },
            h("span", { class: "t-micro", text: (chance < 1 ? chance.toFixed(1) : Math.round(chance)) + "%" }),
            h("span", { class: "t-micro", text: scratchPrizeText(x) })
          )
        ));
      });
      odds.append(rows);
      root.append(odds);
      return root;
    }
  };

  /* ---- progression -------------------------------------------------------- */
  const XP_ROWS = [
    { key: "death", label: "Logging a death" },
    { key: "survivalDay", label: "Surviving a full day" },
    { key: "purchase", label: "Black Market purchase" },
    { key: "spin", label: "Wheel spin" },
    { key: "scratch", label: "Scratch card" },
    { key: "penaltySpin", label: "Penalty spin" }
  ];

  function isEquipped(reward, p) {
    if (reward.type === "title") return String(p.title || "").toUpperCase() === String(reward.value).toUpperCase();
    if (reward.type === "color") return String(p.color || "").toUpperCase() === String(reward.value).toUpperCase();
    if (reward.type === "frame") return p.avatarFrame === reward.value;
    return false;
  }

  function rewardRow(ctx, reward, p, level, prestige) {
    const unlocked = DF.Economy.rewardUnlocked(reward, level, prestige);
    const equipped = isEquipped(reward, p);
    const right = h("div", { style: { flex: "none" } });
    if (!unlocked) {
      right.append(statusPill(`LV ${reward.level}`, "low"));
    } else if (equipped) {
      right.append(statusPill("EQUIPPED", "toxic"));
    } else {
      right.append(h("button", { class: "btn btn--ghost btn--sm", text: "EQUIP", onclick: () => ctx.equip(reward) }));
    }
    const icon = reward.type === "color" ? "🎨" : reward.type === "frame" ? "👤" : "🎖";
    return h("div", { class: "list-row " + (unlocked ? "" : "locked") },
      itemEmblem(icon, reward.type === "title" ? "var(--prestige)" : reward.type === "frame" ? "var(--amber)" : "var(--plasma)", 38),
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "t-card-title", style: { fontSize: "13px" }, text: reward.label }),
        h("div", { class: "t-micro", text: reward.type === "title" ? "TITLE" : reward.type === "frame" ? "AVATAR FRAME" : "BADGE COLOUR" })
      ),
      right
    );
  }

  DF.Screens.progression = {
    title: "PROGRESSION",
    subtitle: "LEVELS, REWARDS & PRESTIGE",
    back(ctx) { return ghostAction("BACK", () => ctx.go("more")); },
    render(ctx) {
      const economy = cfg();
      const p = pd();
      const level = p.level || 1;
      const prestige = p.prestigeCount || 0;
      const rewards = (DF.Data.state.levelRewards && DF.Data.state.levelRewards.length)
        ? DF.Data.state.levelRewards.slice()
        : DF.Economy.LEVEL_REWARDS.slice();
      rewards.sort((a, b) => a.level - b.level);

      const root = h("div", { class: "stack", style: { gap: "var(--md)" } });

      root.append(heroPanel({
        eyebrow: "Survivor Progression",
        title: `Level ${level} of ${economy.xp.maxLevel}`,
        glyph: "★",
        accent: "var(--ember)",
        badge: prestige > 0 ? statusPill(`★ PRESTIGE ${prestige}`, "prestige") : null,
        children: h("div", { style: { marginTop: "var(--sm)" } }, xpBar(p.xp || 0, level, economy.xp))
      }));

      root.append(h("div", { class: "card" },
        h("div", { class: "t-micro", text: "HOW XP IS EARNED" }),
        h("div", { class: "stack stack--tight", style: { marginTop: "6px" } },
          ...XP_ROWS.map((row) => h("div", { class: "row row--between" },
            h("span", { class: "t-body", text: row.label }),
            h("span", { class: "t-body amber", style: { fontWeight: "900" }, text: `+${fmt(economy.xp[row.key] || 0)} XP` })
          ))
        )
      ));

      const maxed = DF.Economy.canPrestige(level, economy);
      root.append(h("div", { class: "card " + (maxed ? "card--prestige" : ""), style: { borderColor: maxed ? "rgba(179,136,255,0.5)" : "var(--ink-line)" } },
        h("div", { class: "row" },
          h("div", { class: "tile", style: { "--tint": "var(--prestige)" }, text: "★" }),
          h("div", { style: { flex: "1", minWidth: "0" } },
            h("div", { class: "t-card-title", text: "PRESTIGE" }),
            h("div", { class: "t-body", text: maxed
              ? "You've reached the top. Prestige resets your level and XP, and unlocks every cosmetic."
              : `Reach level ${economy.xp.maxLevel} to prestige. Prestiges so far: ${prestige}` })
          )
        ),
        h("div", { style: { marginTop: "var(--sm)" } },
          primaryAction(maxed ? "PRESTIGE" : `LEVEL ${economy.xp.maxLevel} REQUIRED`, () => ctx.prestige(), maxed, "var(--prestige)")
        )
      ));

      root.append(sectionLabel("Level Rewards"));
      const list = h("div", { class: "stack stack--tight" });
      rewards.forEach((r) => list.append(rewardRow(ctx, r, p, level, prestige)));
      root.append(list);

      const frameRow = h("div", { class: "stack stack--tight" });
      if (prestige > 0) {
        frameRow.append(h("div", { class: "list-row" },
          itemEmblem("👑", "var(--prestige)", 38),
          h("div", { style: { flex: "1", minWidth: "0" } },
            h("div", { class: "t-card-title", style: { fontSize: "13px" }, text: "PRESTIGE FRAME" }),
            h("div", { class: "t-micro", text: "PRESTIGE " + prestige })
          ),
          p.avatarFrame === "prestige"
            ? statusPill("EQUIPPED", "toxic")
            : h("button", { class: "btn btn--ghost btn--sm", text: "EQUIP", onclick: () => ctx.equip({ type: "frame", label: "Prestige frame", value: "prestige" }) })
        ));
      }
      frameRow.append(h("div", { class: "row", style: { justifyContent: "center", marginTop: "var(--xs)" } },
        ghostAction("REMOVE AVATAR FRAME", () => ctx.equip({ type: "frame", label: "No frame", value: "none" }), "var(--text-low)")
      ));
      root.append(frameRow);

      return root;
    }
  };
})();
