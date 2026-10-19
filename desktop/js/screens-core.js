/* =============================================================================
   Dead-Fest desktop — core screens
   Home (Command Center), Players, Shop, Wheel, Scratch, Progression.
   ========================================================================== */
window.DF = window.DF || {};

DF.Screens = DF.Screens || {};

/* ---- shared pieces -------------------------------------------------------- */

function liveFeedStrip(message) {
  if (!message) return null;
  return h("div", { class: "banner" },
    h("span", { text: "▸" , style: { color: "var(--ember)", fontWeight: "900" } }),
    h("span", { class: "t-body hi", text: message, style: { flex: "1" } })
  );
}

function alertBanner(icon, title, message, kind) {
  return h("div", { class: "banner banner--" + (kind || "ember") },
    h("span", { text: icon, style: { fontSize: "20px" } }),
    h("div", {},
      h("div", { class: "t-card-title", text: title }),
      h("div", { class: "t-body", text: message })
    )
  );
}

function penaltyStatusCard(player, onSpin) {
  const level = player.bankruptcyLevel || 0;
  const spins = player.pendingSpins || 0;
  if (level <= 0 && spins <= 0) return null;
  let title, message, accent, critical = false;
  if (level >= 2) {
    title = "BANKRUPTCY LEVEL 2";
    message = "Clear every pending spin and get your balance above 0 to escape. Survival payouts are halved.";
    accent = "var(--ember)"; critical = true;
  } else if (level === 1) {
    title = "BANKRUPTCY LEVEL 1";
    message = "Balance hit 0. Survival payouts are halved. Another death means Level 2.";
    accent = "var(--ember)";
  } else {
    title = "FORCED SPIN PROTOCOL";
    message = "Penalty spins are waiting for you on the Wheel of Fate.";
    accent = "var(--amber)";
  }
  return h("div", {
    class: "card card--click",
    style: { borderColor: critical ? "var(--ember)" : "var(--amber)", cursor: "pointer" },
    onclick: onSpin
  },
    h("div", { class: "row" },
      h("div", { class: "icon-tile", style: { "--tint": "rgba(255,213,79,0.18)", "--edge": "rgba(255,213,79,0.45)", "--fg": "var(--amber)" }, text: "🎲" }),
      h("div", { style: { flex: "1" } },
        h("div", { class: "t-card-title", style: { color: accent }, text: title }),
        h("div", { class: "t-body", text: message })
      ),
      spins > 0 ? h("div", { class: "stat-tile", style: { textAlign: "center" } },
        h("div", { class: "stat-value ember", text: String(spins) }),
        h("div", { class: "stat-label", text: "PENDING SPINS" })
      ) : null
    )
  );
}

function dailyRationsCard(player, cfg, onClaim) {
  const now = Date.now();
  const can = DF.Economy.canClaimDaily(player.lastDailyClaim || 0, now);
  const amount = player.dailyUpgradeOwned ? cfg.dailyUpgraded : cfg.dailyBase;
  return h("div", { class: "card" },
    h("div", { class: "row row--between" },
      h("div", {},
        h("div", { class: "t-card-title", text: "DAILY RATIONS" }),
        h("div", { class: "t-body", text: can ? `${fmt(amount)} coins waiting to be claimed.` : "Already claimed today. Come back after midnight." })
      ),
      can
        ? h("button", { class: "btn btn--sm", text: "CLAIM", onclick: onClaim })
        : h("div", { class: "t-stat mono amber", text: DF.Economy.dailyCountdown(now) })
    )
  );
}

function eliminationHero(total, online, sectors, yourDeaths) {
  return h("div", { class: "hero" },
    h("div", { class: "hero-mark", text: "💀" }),
    h("div", { class: "hero-eyebrow", text: "GLOBAL ELIMINATIONS" }),
    h("div", { class: "t-stat-hero", text: fmt(total) }),
    h("div", { class: "row", style: { marginTop: "12px", gap: "24px" } },
      h("div", {},
        h("div", { class: "t-micro", text: "ONLINE NOW" }),
        h("div", { class: "t-stat toxic", text: String(online) })
      ),
      h("div", {},
        h("div", { class: "t-micro", text: "SECTORS" }),
        h("div", { class: "t-stat", text: String(sectors) })
      ),
      h("div", {},
        h("div", { class: "t-micro", text: "YOUR DEATHS" }),
        h("div", { class: "t-stat ember", text: fmt(yourDeaths) })
      )
    )
  );
}

function survivorProfileCard(user, player, cfg, totalDeaths) {
  if (!user) return null;
  const pd = user.playerData || player || {};
  const xpCfg = cfg.xp;
  const progress = DF.Economy.xpProgress(pd.xp || 0, pd.level || 1, xpCfg);
  const gear = [
    pd.shield ? ["SHIELD ARMED", "var(--plasma)"] : null,
    pd.luckyCoin ? ["LUCKY COIN", "var(--amber)"] : null,
    pd.insurance ? ["INSURED", "var(--toxic)"] : null,
    pd.insulationTier ? ["INSULATION " + DF.Economy.roman(pd.insulationTier), "var(--prestige)"] : null
  ].filter(Boolean);

  return h("div", { class: "card" },
    h("div", { class: "row" },
      avatar(user, "lg"),
      h("div", { style: { flex: "1" } },
        h("div", { class: "t-card-title", text: handleOf(user) }),
        h("div", { class: "t-body", text: (pd.title || "Survivor") + " · Level " + (pd.level || 1) })
      ),
      h("div", { style: { textAlign: "right" } },
        h("div", { class: "t-micro", text: "COINS" }),
        h("div", { class: "t-stat amber", text: fmt(pd.coins || 0) })
      )
    ),
    h("div", { class: "divider" }),
    h("div", { class: "row row--between" },
      h("div", { class: "t-micro", text: `LEVEL ${pd.level || 1} · ${fmt(pd.xp || 0)} XP` }),
      h("div", { class: "t-micro", text: `NEXT ${fmt(DF.Economy.xpForLevel(pd.level || 1, xpCfg))} XP` })
    ),
    h("div", { class: "bar", style: { marginTop: "6px" } }, h("i", { style: { width: (progress * 100) + "%" } })),
    gear.length ? h("div", { class: "row row--wrap", style: { marginTop: "12px" } },
      gear.map((g) => statusPill(g[0], g[1].includes("var(--ember)") ? "ember" : g[1].includes("plasma") ? "plasma" : g[1].includes("amber") ? "amber" : g[1].includes("toxic") ? "toxic" : "prestige"))
    ) : null,
    h("div", { class: "row", style: { marginTop: "12px", gap: "18px" } },
      h("div", {}, h("div", { class: "t-micro", text: "DEATHS" }), h("div", { class: "t-stat", text: fmt(totalDeaths) })),
      h("div", {}, h("div", { class: "t-micro", text: "PRESTIGE" }), h("div", { class: "t-stat prestige", text: String(pd.prestigeCount || 0) })),
      h("div", {}, h("div", { class: "t-micro", text: "BANKRUPTCY" }), h("div", { class: "t-stat", style: { color: (pd.bankruptcyLevel || 0) > 0 ? "var(--ember)" : null }, text: ["SOLVENT", "LEVEL 1", "LEVEL 2"][pd.bankruptcyLevel || 0] }))
    )
  );
}

function sectorChips(sectors, lockedGames, playerGames, overloadedSector, overloadLive, onPick) {
  return h("div", { class: "row row--wrap" },
    sectors.map((sector) => {
      const locked = lockedGames.indexOf(sector) >= 0;
      const overloaded = overloadLive && sector === overloadedSector;
      const deaths = (playerGames || {})[sector] ? ((playerGames || {})[sector].deaths || 0) : 0;
      return h("button", {
        class: "chip" + (overloaded ? " on" : ""),
        style: locked ? { opacity: "0.5" } : null,
        title: locked ? "Locked" : `${deaths} deaths`,
        onclick: () => !locked && onPick(sector)
      },
        h("span", { text: sector.toUpperCase() }),
        h("span", { class: "mono", style: { color: "var(--amber)", fontWeight: "900" }, text: String(deaths) })
      );
    })
  );
}

/* =============================================================================
   HOME
   ========================================================================== */
DF.Screens.home = {
  title: "COMMAND CENTER",
  subtitle: "Sector Net // Live Feed",
  needs: ["user", "global", "users", "events"],
  render(ctx) {
    const { state, go } = ctx;
    const user = state.user;
    const global = state.global;
    const player = user ? (user.playerData || {}) : null;
    const cfg = global ? global.economy : DF.Economy.DEFAULT_CONFIG;
    const online = (state.users || []).filter((u) => u.playerData && u.playerData.isOnline).length;
    const totalDeaths = player ? Object.values(player.games || {}).reduce((a, s) => a + ((s && s.deaths) || 0), 0) : 0;
    const unlocked = global ? global.games.filter((g) => global.lockedGames.indexOf(g) < 0) : [];
    const overloadLive = global ? global.sectorOverload && global.overloadedSector && (!global.overloadEndsAt || Date.now() < global.overloadEndsAt) : false;
    const latest = (state.events || [])[0];

    const wrap = h("div", { class: "stack" });
    wrap.append(liveFeedStrip(latest && latest.message));

    if (global && global.lockdownActive) {
      wrap.append(alertBanner("🔒", "DEAD-FEST IS IN LOCKDOWN", "Core protocol actions are restricted by an administrator."));
    }
    if (player) wrap.append(penaltyStatusCard(player, () => go("wheel")));
    if (overloadLive) {
      wrap.append(alertBanner("⚡", `SECTOR OVERLOAD: ${global.overloadedSector.toUpperCase()}`,
        `x${global.multiplier} multiplier is active in this sector.`, "amber"));
    }
    if (player) {
      wrap.append(dailyRationsCard(player, cfg, () => ctx.claimDaily()));
    }
    wrap.append(eliminationHero(global ? global.grandTotal : 0, online, global ? global.games.length : 0, totalDeaths));

    wrap.append(sectionLabel("Survivor Profile"));
    wrap.append(survivorProfileCard(user, player, cfg, totalDeaths));

    wrap.append(sectionLabel("Active Sectors"));
    if (!global || !global.games.length) {
      wrap.append(h("div", { class: "t-body", text: "No sectors configured yet." }));
    } else {
      wrap.append(sectorChips(global.games, global.lockedGames, player ? player.games : {}, global.overloadedSector, overloadLive, (sector) => ctx.recordDeath(sector)));
    }

    wrap.append(h("div", { class: "row", style: { marginTop: "8px" } },
      h("button", {
        class: "btn btn--full", style: { maxWidth: "340px" },
        disabled: !global || global.lockdownActive || !unlocked.length,
        text: global && global.lockdownActive ? "Death recording locked" : !unlocked.length ? "No open sectors" : "Record Death",
        onclick: () => ctx.pickSector(unlocked, overloadLive, global)
      })
    ));
    return wrap;
  }
};

/* =============================================================================
   PLAYERS
   ========================================================================== */
DF.Screens.players = {
  title: "SURVIVOR NETWORK",
  subtitle: "Every survivor // live presence",
  needs: ["users", "global"],
  render(ctx) {
    const { state } = ctx;
    const users = state.users || [];
    const global = state.global;
    const query = ctx.local("playersQuery", "");
    const filtered = query ? users.filter((u) => handleOf(u).toLowerCase().indexOf(query.toLowerCase()) >= 0) : users;

    const wrap = h("div", { class: "stack" });
    wrap.append(h("div", { class: "row" },
      h("div", { class: "field", style: { flex: "1", maxWidth: "360px" } },
        h("input", {
          value: query,
          placeholder: "Find survivor...",
          "data-focus-key": "playersSearch",
          oninput: (e) => { ctx.local("playersQuery", e.target.value); ctx.rerender(); }
        })
      ),
      h("div", { class: "spacer" }),
      h("div", { class: "t-micro", text: `${filtered.length} SURVIVOR${filtered.length === 1 ? "" : "S"}` })
    ));

    if (!filtered.length) {
      wrap.append(emptyState("👥", "No survivors found", query ? `Nobody matches "${query}".` : "No player records have loaded yet."));
      return wrap;
    }

    const grid = h("div", { class: "grid-2" });
    filtered.forEach((u) => {
      const pd = u.playerData || {};
      const deaths = Object.values(pd.games || {}).reduce((a, s) => a + ((s && s.deaths) || 0), 0);
      grid.append(h("div", { class: "list-row", onclick: () => ctx.openDossier(u) },
        avatar(u),
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-card-title", text: handleOf(u), style: { whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" } }),
          h("div", { class: "t-body", text: `${fmt(pd.coins || 0)} coins · ${fmt(deaths)} deaths` })
        ),
        pd.isOnline ? statusPill("ONLINE", "toxic") : h("div", { class: "t-micro", text: timeAgo(pd.lastSeen) }),
        u.admin ? statusPill("ADMIN", "ember") : null
      ));
    });
    wrap.append(grid);
    return wrap;
  }
};

/* =============================================================================
   SHOP (BLACK MARKET)
   ========================================================================== */
DF.Screens.shop = {
  title: "BLACK MARKET",
  subtitle: "Wealth is a weapon",
  needs: ["user", "global", "users"],
  render(ctx) {
    const { state } = ctx;
    const user = state.user;
    const global = state.global;
    const cfg = global ? global.economy : DF.Economy.DEFAULT_CONFIG;
    const pd = user ? (user.playerData || {}) : {};
    const wrap = h("div", { class: "stack" });

    wrap.append(h("div", { class: "card" },
      h("div", { class: "row" },
        h("div", { class: "icon-tile", style: { "--tint": "rgba(255,213,79,0.18)", "--edge": "rgba(255,213,79,0.45)", "--fg": "var(--amber)" }, text: "🪙" }),
        h("div", { style: { flex: "1" } },
          h("div", { class: "t-micro", text: "YOUR BALANCE" }),
          h("div", { class: "t-stat-hero amber", text: fmt(pd.coins || 0) })
        ),
        h("div", { style: { textAlign: "right" } },
          h("div", { class: "t-micro", text: "CASUALTY TAX" }),
          h("div", { class: "t-stat", text: DF.Economy.effectiveRateLabel(pd.coins || 0, DF.Economy.insulationReduction(pd.insulationTier || 0, cfg)) })
        )
      )
    ));

    if (global && !global.marketEnabled) {
      wrap.append(alertBanner("🚫", "BLACK MARKET CLOSED", "An administrator has disabled purchases."));
    }

    const categories = [
      { key: "COMBAT", label: "Combat" },
      { key: "ECONOMY", label: "Economy" },
      { key: "INSULATION", label: "Insulation" }
    ];
    categories.forEach((cat) => {
      const items = DF.Economy.SHOP.filter((i) => i.category === cat.key && DF.Economy.enabledIn(i, cfg));
      if (!items.length) return;
      wrap.append(sectionLabel(cat.label));
      const grid = h("div", { class: "grid-3" });
      items.forEach((item) => {
        const price = DF.Economy.priceIn(item, cfg);
        const owned = ctx.ownedState(item, pd);
        const affordable = (pd.coins || 0) >= price;
        grid.append(h("div", { class: "card" },
          h("div", { class: "row row--between" },
            h("div", { class: "t-card-title", text: item.name }),
            owned ? statusPill("OWNED", "toxic") : h("div", { class: "t-stat amber", style: { fontSize: "16px" }, text: fmt(price) })
          ),
          h("div", { class: "t-body", style: { marginTop: "4px" }, text: item.desc }),
          item.tier > 0 ? h("div", { class: "t-micro", style: { marginTop: "4px" }, text: `TAX REDUCTION ${DF.Economy.reductionIn(item, cfg)}%` }) : null,
          h("div", { style: { marginTop: "12px" } },
            h("button", {
              class: "btn btn--full btn--sm",
              disabled: owned === true || !affordable || (global && !global.marketEnabled),
              text: owned === true ? "OWNED" : owned === "TIER_LOCKED" ? "LOCKED" : !affordable ? "TOO EXPENSIVE" : "BUY",
              onclick: () => ctx.buy(item, cat.key)
            })
          )
        ));
      });
      wrap.append(grid);
    });
    return wrap;
  }
};

/* =============================================================================
   WHEEL OF FATE
   ========================================================================== */
DF.Screens.wheel = {
  title: "WHEEL OF FATE",
  subtitle: "Spin // win // or owe",
  needs: ["user", "global"],
  render(ctx) {
    const { state } = ctx;
    const user = state.user;
    const global = state.global;
    const cfg = global ? global.economy : DF.Economy.DEFAULT_CONFIG;
    const pd = user ? (user.playerData || {}) : {};
    const items = global ? global.wheelItems.filter((i) => i.enabled !== false) : [];
    const totalWeight = items.reduce((a, i) => a + (i.weight > 0 ? i.weight : 0), 0);
    const pending = pd.pendingSpins || 0;

    const wrap = h("div", { class: "stack" });
    if (pending > 0) {
      wrap.append(alertBanner("🎲", `${pending} PENALTY SPIN${pending === 1 ? "" : "S"} WAITING`,
        "This spin clears one of your pending penalty spins.", "amber"));
    }
    if (global && !global.wheelEnabled) {
      wrap.append(alertBanner("🚫", "WHEEL DISABLED", "An administrator has disabled spinning."));
    }

    if (!items.length) {
      wrap.append(emptyState("🎡", "The Wheel has no slices", "An admin has to add some before it can spin."));
      return wrap;
    }

    const wheel = h("div", { class: "wheel", id: "wheel-disc" });
    const stops = items.map((i) => {
      const pct = totalWeight > 0 ? ((i.weight > 0 ? i.weight : 0) / totalWeight) * 100 : 100 / items.length;
      return `${i.color} 0% ${i.color} ${pct}%`;
    });
    wheel.style.background = `conic-gradient(${stops.join(", ")})`;
    wheel.append(h("div", { class: "wheel-hub", text: "💀" }));

    const list = h("div", { class: "stack stack--tight", style: { flex: "1", minWidth: "320px" } });
    list.append(sectionLabel("Slices"));
    items.forEach((item) => {
      const pct = totalWeight > 0 ? ((item.weight > 0 ? item.weight : 0) / totalWeight) * 100 : 0;
      list.append(h("div", { class: "row", style: { padding: "6px 0" } },
        h("span", { style: { width: "12px", height: "12px", borderRadius: "50%", background: item.color, flex: "none" } }),
        h("div", { style: { flex: "1" } },
          h("div", { class: "t-body hi", text: item.label }),
          h("div", { class: "t-micro", text: `${item.actionType === "take" ? "TAKE" : "GIVE"} ${item.value || "-"} · ${pct.toFixed(1)}%` })
        )
      ));
    });

    const result = ctx.local("wheelResult", null);
    const spinBtn = h("button", {
      class: "btn", style: { minWidth: "220px" },
      disabled: !global || !global.wheelEnabled || ctx.local("spinning", false),
      text: ctx.local("spinning", false) ? "SPINNING..." : "SPIN",
      onclick: () => ctx.spinWheel(items, wheel)
    });

    wrap.append(h("div", { class: "wheel-wrap" },
      h("div", { style: { position: "relative" } },
        h("div", { class: "wheel-pointer" }),
        wheel
      ),
      h("div", { class: "stack", style: { flex: "1", minWidth: "300px" } },
        result ? h("div", { class: "card card--accent" },
          h("div", { class: "t-micro", text: "RESULT" }),
          h("div", { class: "t-stat-hero", text: result.title }),
          h("div", { class: "t-body", text: result.detail })
        ) : null,
        spinBtn,
        h("div", { class: "card" },
          h("div", { class: "row row--between" },
            h("div", {}, h("div", { class: "t-micro", text: "COINS" }), h("div", { class: "t-stat amber", text: fmt(pd.coins || 0) })),
            h("div", { style: { textAlign: "right" } }, h("div", { class: "t-micro", text: "PENDING SPINS" }), h("div", { class: "t-stat ember", text: String(pending) }))
          )
        ),
        list
      )
    ));
    return wrap;
  }
};

/* =============================================================================
   SCRATCH CARDS
   ========================================================================== */
DF.Screens.scratch = {
  title: "SCRATCH CARDS",
  subtitle: "Instant wins // instant ruin",
  needs: ["user", "global"],
  render(ctx) {
    const { state } = ctx;
    const user = state.user;
    const global = state.global;
    const cfg = global ? global.economy : DF.Economy.DEFAULT_CONFIG;
    const pd = user ? (user.playerData || {}) : {};
    const pool = (state.scratchPrizes && state.scratchPrizes.length)
      ? state.scratchPrizes
      : DF.Economy.SCRATCH_POOL;

    const wrap = h("div", { class: "stack" });
    const bought = ctx.local("scratchBought", false);
    const revealed = ctx.local("scratchRevealed", []);

    const grid = h("div", { class: "scratch-grid" });
    for (let i = 0; i < 9; i++) {
      const done = revealed.indexOf(i) >= 0;
      const cell = h("div", { class: "scratch-cell" + (done ? " scratched" : "") },
        h("span", { text: done ? "✦" : "▒" })
      );
      if (!done && bought) {
        cell.onclick = () => ctx.revealScratch(i, pool);
      }
      grid.append(cell);
    }

    wrap.append(h("div", { class: "card" },
      h("div", { class: "row row--between" },
        h("div", {},
          h("div", { class: "t-micro", text: "COST PER CARD" }),
          h("div", { class: "t-stat amber", text: fmt(cfg.scratchCost) })
        ),
        h("div", { style: { textAlign: "right" } },
          h("div", { class: "t-micro", text: "YOUR BALANCE" }),
          h("div", { class: "t-stat", text: fmt(pd.coins || 0) })
        )
      )
    ));

    if (!cfg.scratchEnabled) {
      wrap.append(alertBanner("🚫", "SCRATCH DISABLED", "An administrator has disabled scratch cards."));
    } else if (!bought) {
      wrap.append(h("button", {
        class: "btn btn--full", style: { maxWidth: "300px" },
        disabled: (pd.coins || 0) < cfg.scratchCost,
        text: (pd.coins || 0) < cfg.scratchCost ? "NOT ENOUGH COINS" : `BUY A CARD (${fmt(cfg.scratchCost)})`,
        onclick: () => ctx.buyScratch(pool)
      }));
    } else {
      wrap.append(h("div", { class: "card" },
        h("div", { class: "t-card-title", text: "SCRATCH THE GRID" }),
        h("div", { class: "t-body", text: "Nine squares. One holds your prize." }),
        h("div", { style: { marginTop: "16px", maxWidth: "360px" } }, grid)
      ));
    }

    wrap.append(sectionLabel("Prize Pool & Odds"));
    const table = h("table", { class: "tbl" },
      h("thead", {}, h("tr", {},
        h("th", { text: "PRIZE" }), h("th", { text: "CHANCE" }), h("th", { text: "VALUE" })
      ))
    );
    const tbody = h("tbody");
    const chances = DF.Economy.weightedChances(pool.map((p) => p.weight));
    pool.forEach((prize, i) => {
      tbody.append(h("tr", {},
        h("td", { class: "hi", text: prize.label }),
        h("td", { text: chances[i].toFixed(2) + "%" }),
        h("td", { text: prize.value || (prize.type === "shield" ? "SHIELD" : prize.type === "lucky_coin" ? "LUCKY COIN" : "-") })
      ));
    });
    table.append(tbody);
    wrap.append(h("div", { class: "card scroll-420" }, table));
    return wrap;
  }
};

/* =============================================================================
   PROGRESSION
   ========================================================================== */
DF.Screens.progression = {
  title: "PROGRESSION",
  subtitle: "Levels // prestige // unlocks",
  needs: ["user", "global", "levelRewards"],
  render(ctx) {
    const { state } = ctx;
    const user = state.user;
    const global = state.global;
    const cfg = global ? global.economy : DF.Economy.DEFAULT_CONFIG;
    const pd = user ? (user.playerData || {}) : {};
    const rewards = (state.levelRewards && state.levelRewards.length)
      ? state.levelRewards
      : DF.Economy.LEVEL_REWARDS;
    const level = pd.level || 1;
    const progress = DF.Economy.xpProgress(pd.xp || 0, level, cfg.xp);

    const wrap = h("div", { class: "stack" });
    wrap.append(h("div", { class: "hero" },
      h("div", { class: "hero-mark", text: "🏅" }),
      h("div", { class: "hero-eyebrow", text: "CURRENT LEVEL" }),
      h("div", { class: "t-stat-hero", text: String(level) }),
      h("div", { class: "row row--between", style: { marginTop: "10px" } },
        h("div", { class: "t-body", text: `${fmt(pd.xp || 0)} XP` }),
        h("div", { class: "t-body", text: `NEXT ${fmt(DF.Economy.xpForLevel(level, cfg.xp))} XP` })
      ),
      h("div", { class: "bar", style: { marginTop: "8px" } }, h("i", { style: { width: (progress * 100) + "%" } })),
      h("div", { class: "row", style: { marginTop: "14px", gap: "20px" } },
        h("div", {}, h("div", { class: "t-micro", text: "PRESTIGE" }), h("div", { class: "t-stat prestige", text: String(pd.prestigeCount || 0) })),
        h("div", {}, h("div", { class: "t-micro", text: "TITLE" }), h("div", { class: "t-stat", style: { fontSize: "15px" }, text: pd.title || "Survivor" })),
        DF.Economy.canPrestige(level, cfg.xp)
          ? h("button", { class: "btn btn--sm", text: "PRESTIGE", onclick: () => ctx.prestige() })
          : null
      )
    ));

    wrap.append(sectionLabel("Reward Track"));
    const grid = h("div", { class: "grid-3" });
    rewards.forEach((r) => {
      const unlocked = DF.Economy.rewardUnlocked(r, level, pd.prestigeCount || 0);
      const equipped =
        (r.type === "color" && pd.color === r.value) ||
        (r.type === "frame" && pd.avatarFrame === r.value) ||
        (r.type === "title" && pd.title === r.value);
      grid.append(h("div", { class: "card", style: unlocked ? null : { opacity: "0.45" } },
        h("div", { class: "row row--between" },
          h("div", { class: "t-card-title", text: r.label }),
          h("div", { class: "t-micro", text: "LV " + r.level })
        ),
        h("div", { style: { marginTop: "10px" } },
          h("button", {
            class: "btn btn--ghost btn--sm btn--full",
            disabled: !unlocked || equipped,
            text: equipped ? "EQUIPPED" : unlocked ? "EQUIP" : "LOCKED",
            onclick: () => ctx.equip(r)
          })
        )
      ));
    });
    wrap.append(grid);
    return wrap;
  }
};
