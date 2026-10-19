/* =============================================================================
   Dead-Fest desktop — utility screens
   More hub, Team Progress, Archives, Images, Survival Intel, Request Game,
   Settings (identity + themes).
   ========================================================================== */
window.DF = window.DF || {};
DF.Screens = DF.Screens || {};

function hubButton(label, description, icon, accent, onClick, badge) {
  return h("div", {
    class: "hub",
    style: { "--wash": "transparent", "--accent": accent },
    onclick: onClick
  },
    h("div", { class: "hub-top" },
      h("div", { class: "icon-tile", style: { "--tint": accent + "22", "--edge": accent + "66", "--fg": accent }, text: icon }),
      badge ? statusPill(badge, "ember") : null
    ),
    h("div", {},
      h("div", { class: "hub-label", text: label }),
      h("div", { class: "hub-desc", text: description })
    )
  );
}

/* =============================================================================
   MORE
   ========================================================================== */
DF.Screens.more = {
  title: "UTILITY HUB",
  subtitle: "Advanced terminal systems",
  needs: ["global", "user"],
  render(ctx) {
    const { state, go } = ctx;
    const global = state.global;
    const isAdmin = state.user && state.user.admin === true;
    const wrap = h("div", { class: "stack" });

    if (global && global.nextEventEnabled) {
      wrap.append(h("div", { class: "hero hero--amber" },
        h("div", { class: "hero-mark", text: "🌙" }),
        h("div", { class: "hero-eyebrow", text: (global.currentSeasonName || "NEXT DEAD-FEST").toUpperCase() }),
        h("div", { class: "hero-title", text: global.nextEventName || "Next Dead-Fest" }),
        h("div", { class: "t-body", style: { marginTop: "6px" }, text: global.nextEventGames.length ? global.nextEventGames.join(" · ") : (global.nextEventNotes || "Details to be announced.") })
      ));
    } else if (isAdmin) {
      wrap.append(h("div", { class: "card card--click card--amber", onclick: () => go("admin") },
        h("div", { class: "row" },
          h("div", { class: "icon-tile", style: { "--tint": "rgba(255,213,79,0.18)", "--edge": "rgba(255,213,79,0.45)", "--fg": "var(--amber)" }, text: "➕" }),
          h("div", { style: { flex: "1" } },
            h("div", { class: "t-card-title", text: "SET UP NEXT DEAD-FEST" }),
            h("div", { class: "t-body", text: "Admin → Event: name the season, set the start time and queue the games." })
          )
        )
      ));
    }

    wrap.append(sectionLabel("Season & History"));
    const g1 = h("div", { class: "grid-3" });
    g1.append(hubButton("TEAM PROGRESS", "Live season stats & every past Dead-Fest", "👥", "var(--plasma)", () => go("team"), "NEW"));
    g1.append(hubButton("ARCHIVES", "Season history & hall of fame", "📜", "var(--amber)", () => go("archives")));
    wrap.append(g1);

    wrap.append(sectionLabel("Profile & Rewards"));
    const g2 = h("div", { class: "grid-3" });
    g2.append(hubButton("PROGRESSION", "Levels, rewards & prestige", "🎖", "var(--prestige)", () => go("progression")));
    g2.append(hubButton("SCRATCH CARDS", "Instant-win lottery", "🎟", "var(--toxic)", () => go("scratch")));
    g2.append(hubButton("IMAGES", "Photos shared by every survivor", "🖼", "var(--ember-glow)", () => go("images")));
    wrap.append(g2);

    wrap.append(sectionLabel("Intel"));
    const g3 = h("div", { class: "grid-3" });
    g3.append(hubButton("SURVIVAL INTEL", "System mechanics & lore", "ℹ", "var(--ember)", () => go("intel")));
    g3.append(hubButton("REQUEST GAME", "Ask for a game to become a sector", "🎮", "var(--prestige)", () => go("request"), "NEW"));
    wrap.append(g3);
    return wrap;
  }
};

/* =============================================================================
   TEAM PROGRESS
   ========================================================================== */
DF.Screens.team = {
  title: "TEAM PROGRESS",
  subtitle: "The season in numbers",
  needs: ["users", "global", "archives"],
  render(ctx) {
    const { state, go } = ctx;
    const users = state.users || [];
    const global = state.global;
    const archives = state.archives || [];

    const coins = users.reduce((a, u) => a + ((u.playerData && u.playerData.coins) || 0), 0);
    const deaths = users.reduce((a, u) => a + Object.values((u.playerData && u.playerData.games) || {}).reduce((x, s) => x + ((s && s.deaths) || 0), 0), 0);
    const spins = users.reduce((a, u) => a + ((u.playerData && u.playerData.pendingSpins) || 0), 0);
    const levels = users.map((u) => (u.playerData && u.playerData.level) || 1);
    const avgLevel = levels.length ? (levels.reduce((a, b) => a + b, 0) / levels.length) : 0;
    const bankrupt = users.filter((u) => ((u.playerData && u.playerData.bankruptcyLevel) || 0) > 0).length;
    const richest = users.slice().sort((a, b) => ((b.playerData && b.playerData.coins) || 0) - ((a.playerData && a.playerData.coins) || 0))[0];
    const deadliest = users.slice().sort((a, b) => totalDeaths(b) - totalDeaths(a))[0];

    const wrap = h("div", { class: "stack" });
    wrap.append(h("div", { class: "hero hero--plasma" },
      h("div", { class: "hero-mark", text: "📊" }),
      h("div", { class: "hero-eyebrow", text: (global && global.currentSeasonName || "DEAD-FEST SEASON").toUpperCase() }),
      h("div", { class: "t-stat-hero", text: String(users.length) }),
      h("div", { class: "t-body", style: { marginTop: "4px" }, text: "survivors in the network" })
    ));

    wrap.append(sectionLabel("Current standings"));
    wrap.append(h("div", { class: "grid-4" },
      statTile("TOTAL DEATHS", fmt(deaths), "var(--ember)"),
      statTile("COINS IN PLAY", fmt(coins), "var(--amber)"),
      statTile("AVG LEVEL", avgLevel.toFixed(1)),
      statTile("SPINS DUE", fmt(spins), "var(--ember)")
    ));
    wrap.append(h("div", { class: "grid-4", style: { marginTop: "12px" } },
      statTile("BANKRUPT", String(bankrupt), bankrupt ? "var(--ember)" : null),
      statTile("TOP LEVEL", String(levels.length ? Math.max.apply(null, levels) : 0), "var(--prestige)"),
      statTile("RICHEST", richest ? handleOf(richest) : "—", "var(--amber)"),
      statTile("DEADLIEST", deadliest ? handleOf(deadliest) : "—", "var(--ember)")
    ));

    wrap.append(sectionLabel("Past Dead-Fests"));
    if (!archives.length) {
      wrap.append(emptyState("📜", "No past seasons yet", "An admin archives a season when it ends."));
    } else {
      const grid = h("div", { class: "grid-3" });
      archives.forEach((a) => {
        grid.append(h("div", { class: "card card--click", onclick: () => go("archives") },
          h("div", { class: "t-card-title", text: a.name || "Season" }),
          h("div", { class: "t-body", text: dateStr(a.timestamp) }),
          h("div", { class: "row", style: { marginTop: "10px", gap: "16px" } },
            h("div", {}, h("div", { class: "t-micro", text: "DEATHS" }), h("div", { class: "t-stat", text: fmt(a.grandTotalAtEnd || 0) })),
            h("div", {}, h("div", { class: "t-micro", text: "SURVIVORS" }), h("div", { class: "t-stat", text: String((a.playerSnapshots || []).length) }))
          )
        ));
      });
      wrap.append(grid);
    }
    return wrap;
  }
};

function totalDeaths(u) {
  const pd = (u && u.playerData) || {};
  return Object.values(pd.games || {}).reduce((a, s) => a + ((s && s.deaths) || 0), 0);
}

/* =============================================================================
   ARCHIVES
   ========================================================================== */
DF.Screens.archives = {
  title: "WASTELAND ARCHIVES",
  subtitle: "Every past Dead-Fest",
  needs: ["archives"],
  render(ctx) {
    const { state } = ctx;
    const archives = state.archives || [];
    const selected = ctx.local("archiveId", archives.length ? archives[0].id : null);
    const archive = archives.find((a) => a.id === selected) || archives[0];

    const wrap = h("div", { class: "stack" });
    if (!archive) {
      wrap.append(emptyState("📜", "Nothing archived yet", "Past seasons will appear here once an admin closes one."));
      return wrap;
    }

    const chips = h("div", { class: "row row--wrap" });
    archives.forEach((a) => {
      chips.append(h("button", {
        class: "chip" + (a.id === archive.id ? " on" : ""),
        text: a.name || "Season",
        onclick: () => { ctx.local("archiveId", a.id); ctx.rerender(); }
      }));
    });
    wrap.append(chips);

    wrap.append(h("div", { class: "hero" },
      h("div", { class: "hero-mark", text: "🏆" }),
      h("div", { class: "hero-eyebrow", text: (archive.name || "SEASON").toUpperCase() }),
      h("div", { class: "t-stat-hero", text: fmt(archive.grandTotalAtEnd || 0) }),
      h("div", { class: "t-body", style: { marginTop: "4px" }, text: "total eliminations · " + dateStr(archive.timestamp) })
    ));

    const table = h("table", { class: "tbl" },
      h("thead", {}, h("tr", {},
        h("th", { text: "#" }), h("th", { text: "SURVIVOR" }), h("th", { text: "COINS" }),
        h("th", { text: "DEATHS" }), h("th", { text: "LEVEL" })
      ))
    );
    const tbody = h("tbody");
    const snaps = (archive.playerSnapshots || []).slice().sort((a, b) => (b.coins || 0) - (a.coins || 0));
    snaps.forEach((p, i) => {
      const deaths = Object.values(p.gameDeaths || {}).reduce((a, s) => a + (s || 0), 0);
      tbody.append(h("tr", {},
        h("td", { text: String(i + 1) }),
        h("td", { class: "hi", text: p.displayName || "Survivor" }),
        h("td", { text: fmt(p.coins || 0) }),
        h("td", { text: fmt(deaths) }),
        h("td", { text: String(p.level || 1) })
      ));
    });
    table.append(tbody);
    wrap.append(sectionLabel("Final standings"));
    wrap.append(h("div", { class: "card scroll-420" }, table));
    return wrap;
  }
};

/* =============================================================================
   IMAGES
   ========================================================================== */
DF.Screens.images = {
  title: "SHARED IMAGES",
  subtitle: "Every survivor's wall",
  needs: ["images"],
  render(ctx) {
    const images = ctx.state.images || [];
    const wrap = h("div", { class: "stack" });
    if (!images.length) {
      wrap.append(emptyState("🖼", "No images yet", "Photos shared by survivors show up here."));
      return wrap;
    }
    const grid = h("div", { class: "grid-4" });
    images.forEach((img) => {
      const cell = h("div", { class: "card", style: { padding: "8px" } });
      const box = h("div", { style: { aspectRatio: "1", borderRadius: "10px", overflow: "hidden", background: "var(--ink3)" } });
      if (img.thumb) {
        const url = URL.createObjectURL(img.thumb);
        box.append(h("img", { src: url, style: { width: "100%", height: "100%", objectFit: "cover" } }));
      }
      cell.append(box,
        h("div", { class: "t-micro", style: { marginTop: "6px" }, text: img.uploaderName || "Survivor" }),
        img.caption ? h("div", { class: "t-body", text: img.caption }) : null
      );
      grid.append(cell);
    });
    wrap.append(grid);
    return wrap;
  }
};

/* =============================================================================
   SURVIVAL INTEL
   ========================================================================== */
DF.Screens.intel = {
  title: "SURVIVAL INTEL",
  subtitle: "System mechanics & lore",
  needs: ["global"],
  render(ctx) {
    const global = ctx.state.global;
    const cfg = global ? global.economy : DF.Economy.DEFAULT_CONFIG;
    const sections = [
      ["CASUALTY TAX", `Every death costs coins. Over ${fmt(5000)} the rate is 30% of your balance, over ${fmt(1000)} it is 20%, over ${fmt(500)} it is 15%, and below that it is a flat 25. Wealth Insulation cuts it; Insurance absorbs part of what is left.`],
      ["FORCED SPIN PROTOCOL", `Every death normally adds a penalty spin to the Wheel of Fate, and a second death while bankrupt adds two more. An admin can switch the protocol off from the console.`],
      ["BANKRUPTCY", "At zero coins you enter Level 1 and survival payouts are halved. Another death takes you to Level 2, where the only way out is a positive balance and every pending spin cleared."],
      ["DAILY RATIONS", `Claim ${fmt(cfg.dailyBase)} coins once a day. The permanent Ration Upgrade doubles that to ${fmt(cfg.dailyUpgraded)}, and surviving a full day without a recorded death pays a survival bonus in XP.`],
      ["MUTUAL SURVIVAL REWARD", `When a survivor dies, every other survivor is paid ${fmt(cfg.survivalReward)} coins — halved for anyone in bankruptcy.`],
      ["WHEEL OF FATE", "Weighted slices that really give and really take. A Lucky Coin doubles the next win, and a penalty spin clears one spin of debt every time you spin."]
    ];
    const wrap = h("div", { class: "stack" });
    sections.forEach(([title, body]) => {
      wrap.append(h("div", { class: "card" },
        h("div", { class: "t-card-title", text: title }),
        h("div", { class: "t-body", style: { marginTop: "6px" }, text: body })
      ));
    });
    return wrap;
  }
};

/* =============================================================================
   REQUEST GAME (v2.0.8)
   ========================================================================== */
DF.Screens.request = {
  title: "REQUEST GAME",
  subtitle: "Suggest a sector",
  needs: ["gameRequests", "user"],
  render(ctx) {
    const requests = ctx.state.gameRequests || [];
    const pending = requests.filter((r) => r.status !== "approved");
    const approved = requests.filter((r) => r.status === "approved");
    const showPending = ctx.local("showPending", false);

    const wrap = h("div", { class: "stack" });
    wrap.append(h("button", { class: "btn", style: { maxWidth: "280px" }, text: "Request Game", onclick: () => ctx.openRequestDialog() }));

    wrap.append(sectionLabel("Approved games"));
    if (!approved.length) {
      wrap.append(emptyState("🎮", "No approved games yet", "Games the admin has approved will show up here."));
    } else {
      const grid = h("div", { class: "grid-3" });
      approved.forEach((r) => grid.append(gameRequestCard(r, false)));
      wrap.append(grid);
    }

    wrap.append(h("button", {
      class: "btn btn--ghost btn--full",
      text: showPending ? "Hide pending games" : `Pending games (${pending.length})`,
      onclick: () => { ctx.local("showPending", !showPending); ctx.rerender(); }
    }));
    if (showPending) {
      if (!pending.length) {
        wrap.append(emptyState("✅", "Nothing waiting for approval", ""));
      } else {
        const grid = h("div", { class: "grid-3" });
        pending.forEach((r) => grid.append(gameRequestCard(r, true)));
        wrap.append(grid);
      }
    }
    return wrap;
  }
};

function gameRequestCard(r, showRequester) {
  return h("div", { class: "card" },
    h("div", { class: "row" },
      h("div", { class: "icon-tile", style: { "--tint": "rgba(179,136,255,0.18)", "--edge": "rgba(179,136,255,0.45)", "--fg": "var(--prestige)" }, text: "🎮" }),
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "t-card-title", text: r.name }),
        showRequester ? h("div", { class: "t-body", text: "Requested by " + (r.requestedByName || "Survivor") }) : null
      ),
      statusPill(r.status === "approved" ? "APPROVED" : "PENDING", r.status === "approved" ? "toxic" : "amber")
    )
  );
}

/* =============================================================================
   SETTINGS
   ========================================================================== */
DF.Screens.settings = {
  title: "TERMINAL CONFIG",
  subtitle: "System operations & personalization",
  needs: ["user", "global"],
  render(ctx) {
    const tab = ctx.local("settingsTab", "General");
    const tabs = ["General", "System", "Diagnostics", "Themes"];
    const wrap = h("div", { class: "stack" });

    const chips = h("div", { class: "row row--wrap" });
    tabs.forEach((t) => {
      chips.append(h("button", {
        class: "chip" + (t === tab ? " on" : ""),
        text: t,
        onclick: () => { ctx.local("settingsTab", t); ctx.rerender(); }
      }));
    });
    wrap.append(chips);

    const panel = h("div", { class: "stack" });
    if (tab === "General") panel.append(settingsGeneral(ctx));
    else if (tab === "System") panel.append(settingsSystem(ctx));
    else if (tab === "Diagnostics") panel.append(settingsDiagnostics(ctx));
    else panel.append(settingsThemes(ctx));
    wrap.append(panel);
    return wrap;
  }
};

function settingsGeneral(ctx) {
  const { state } = ctx;
  const user = state.user;
  const global = state.global;
  const wrap = h("div", { class: "stack" });

  // Firmware
  const latest = global ? global.latestVersionCode : 1;
  const myVersion = ctx.appVersion;
  const updateAvailable = latest > myVersion;
  wrap.append(h("div", { class: "hero" },
    h("div", { class: "hero-eyebrow", text: updateAvailable ? "UPDATE AVAILABLE" : "FIRMWARE CURRENT" }),
    h("div", { class: "hero-title", text: "v" + ctx.appVersionName }),
    h("div", { class: "t-body", style: { marginTop: "4px" }, text: "Build " + myVersion }),
    h("div", { style: { marginTop: "14px" } },
      h("button", {
        class: "btn", style: { maxWidth: "320px" },
        text: updateAvailable ? "CHECK FOR UPDATES" : "CHECK FOR UPDATES",
        onclick: () => ctx.checkUpdate()
      })
    )
  ));

  // Identity (v2.0.8)
  const currentHandle = handleOf(user);
  const input = h("input", {
    value: ctx.local("handleDraft", currentHandle),
    "data-focus-key": "handle",
    oninput: (e) => { ctx.local("handleDraft", e.target.value); }
  });
  wrap.append(sectionLabel("Identity"));
  wrap.append(h("div", { class: "card" },
    h("div", { class: "t-body", text: "What the community sees on the player list, the death feed and game requests." }),
    h("div", { class: "field", style: { marginTop: "12px" } },
      h("label", { text: "Handle" }),
      input,
      h("div", { class: "hint", text: "3-20 characters. Letters, numbers and underscores." })
    ),
    h("div", { style: { marginTop: "12px" } },
      h("button", {
        class: "btn btn--sm",
        text: "SAVE HANDLE",
        onclick: () => ctx.saveHandle(ctx.local("handleDraft", currentHandle))
      })
    )
  ));

  // About
  wrap.append(sectionLabel("About Dead-Fest"));
  wrap.append(h("div", { class: "card" },
    h("div", { class: "t-body", text: "The ultimate companion for your survival journey: your central hub for data tracking, surveillance, and economic warfare." })
  ));

  // Session
  wrap.append(sectionLabel("Session"));
  wrap.append(h("button", { class: "btn btn--ember btn--full", style: { maxWidth: "320px" }, text: "Terminate session (log out)", onclick: () => ctx.logout() }));
  return wrap;
}

function settingsSystem(ctx) {
  const wrap = h("div", { class: "stack" });
  wrap.append(sectionLabel("Performance"));
  wrap.append(h("div", { class: "card" },
    settingRow("Performance mode", "Turns off animations, trims the background grid and drops the countdown to one update every 30 seconds.", ctx.local("perfMode", false), (v) => { ctx.local("perfMode", v); ctx.rerender(); })
  ));
  wrap.append(sectionLabel("Feed & alerts"));
  wrap.append(h("div", { class: "card" },
    settingRow("Wheel spins", "Show other survivors' spins in the live ticker", ctx.local("wheelAlerts", true), (v) => ctx.local("wheelAlerts", v)),
    h("div", { class: "divider" }),
    settingRow("Black Market", "Purchases and curses", ctx.local("marketAlerts", true), (v) => ctx.local("marketAlerts", v)),
    h("div", { class: "divider" }),
    settingRow("Wasteland alerts", "Admin broadcasts and firmware notices", ctx.local("wastelandAlerts", true), (v) => ctx.local("wastelandAlerts", v))
  ));
  return wrap;
}

function settingRow(title, subtitle, checked, onChange) {
  const sw = h("div", { class: "switch" + (checked ? " on" : ""), onclick: () => onChange(!checked) });
  return h("div", { class: "row" },
    h("div", { style: { flex: "1" } },
      h("div", { class: "t-card-title", text: title }),
      h("div", { class: "t-body", text: subtitle })
    ),
    sw
  );
}

function settingsDiagnostics(ctx) {
  const user = ctx.state.user;
  const wrap = h("div", { class: "stack" });
  wrap.append(sectionLabel("Uplink"));
  wrap.append(h("div", { class: "card" },
    keyValue("Desktop version", ctx.appVersionName + " (" + ctx.appVersion + ")"),
    keyValue("Survivor ID", user && user.uid ? String(user.uid).slice(0, 12) + "…" : "-"),
    keyValue("Admin", user && user.admin ? "YES" : "NO", user && user.admin ? "var(--ember)" : null)
  ));
  wrap.append(sectionLabel("Firebase"));
  wrap.append(h("div", { class: "card" },
    keyValue("Project", DF.FIREBASE.projectId),
    keyValue("Live listeners", "9 collections", "var(--toxic)")
  ));
  return wrap;
}

function settingsThemes(ctx) {
  const wrap = h("div", { class: "stack" });
  wrap.append(h("div", { class: "t-body", text: "Restyle the terminal. Semantic colours stay put in every theme — amber is always currency, green always success — so a warning never starts looking like a reward." }));
  const current = ctx.local("theme", "default");
  DF.THEMES.forEach((t) => {
    const selected = t.id === current;
    wrap.append(h("div", {
      class: "card card--click" + (selected ? " card--accent" : ""),
      onclick: () => { ctx.local("theme", t.id); ctx.applyTheme(t.id); ctx.rerender(); }
    },
      h("div", { class: "row" },
        h("div", {
          style: {
            width: "46px", height: "46px", borderRadius: "14px", flex: "none",
            background: `linear-gradient(160deg, ${t.ink4}, ${t.ink2})`,
            border: `1px solid ${t.line}`, position: "relative"
          }
        },
          h("span", { style: { position: "absolute", left: "6px", bottom: "6px", width: "10px", height: "10px", borderRadius: "50%", background: t.ember } }),
          h("span", { style: { position: "absolute", right: "6px", top: "6px", width: "10px", height: "10px", borderRadius: "50%", background: "#FFD54F" } })
        ),
        h("div", { style: { flex: "1" } },
          h("div", { class: "t-card-title", text: t.name }),
          h("div", { class: "t-body", style: { color: selected ? t.ember : null }, text: selected ? "Active" : "Click to apply" })
        ),
        selected ? statusPill("ACTIVE", "ember") : null
      )
    ));
  });
  return wrap;
}
