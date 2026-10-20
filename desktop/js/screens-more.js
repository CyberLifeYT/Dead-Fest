/* =============================================================================
   Dead-Fest desktop — secondary screens
   -----------------------------------------------------------------------------
   Utility Hub, Team Progress, Season Archives, Image Board, Intel, Game
   Requests and Settings. Where the phone shows a pushed screen with a back
   arrow, the desktop keeps the same header slot and puts the same button there.
   ========================================================================== */
window.DF = window.DF || {};
DF.Screens = DF.Screens || {};

(function () {
  const cfg = () => (DF.Data.state.global && DF.Data.state.global.economy) || DF.Economy.DEFAULT_CONFIG;
  const world = () => DF.Data.state.global || {};
  const user = () => DF.Data.state.user;
  const pd = () => (user() && user().playerData) || {};
  const myCoins = () => pd().coins || 0;
  const users = () => DF.Data.state.users || [];
  const L = (ctx, key, fallback) => {
    const v = ctx.local(key);
    return v === undefined || v === null ? fallback : v;
  };

  function savedLabel(ms) {
    if (!ms) return "UNKNOWN DATE";
    const d = new Date(ms);
    const two = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())} ${two(d.getHours())}:${two(d.getMinutes())}`;
  }

  function navRow(icon, title, subtitle, onclick, badge, tint) {
    return h("div", { class: "list-row card--click", onclick },
      itemEmblem(icon, tint || "var(--ember)", 40),
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "t-card-title", style: { fontSize: "14px" }, text: title }),
        h("div", { class: "t-body", text: subtitle })
      ),
      badge || h("span", { class: "t-micro", text: "→" })
    );
  }

  function backRow(ctx, label, to) {
    return h("div", null, ghostAction(label || "BACK", () => {
      ctx.local("teamDetailUid", null);
      ctx.local("archiveId", null);
      if (to) ctx.go(to); else ctx.rerender();
    }));
  }

  /* ---- utility hub -------------------------------------------------------- */
  DF.Screens.more = {
    title: "UTILITY HUB",
    subtitle: "Advanced Terminal Systems",
    pill() { return coinBadge(myCoins()); },
    render(ctx) {
      const me = user();
      const g = world();
      const p = pd();
      const root = h("div", { class: "stack", style: { gap: "var(--md)" } });

      if (me) {
        root.append(h("div", { class: "card", style: { padding: "var(--md)" } },
          h("div", { class: "row" },
            avatar(me, 54),
            h("div", { style: { flex: "1", minWidth: "0" } },
              h("div", { class: "t-card-title", text: displayNameOf(me) }),
              h("div", { class: "t-micro", text: "@" + handleOf(me) }),
              h("div", { class: "row", style: { gap: "6px", marginTop: "3px" } },
                levelBadge(p.level || 1, p.prestigeCount || 0),
                h("span", { class: "t-micro ember", text: String(p.title || "SURVIVOR").toUpperCase() })
              )
            ),
            h("div", { class: "right" },
              coinBadge(p.coins || 0),
              h("div", { class: "t-micro", style: { marginTop: "4px" }, text: `${totalDeaths(me)} DEATHS` })
            )
          )
        ));
      }

      root.append(sectionLabel("Network"));
      root.append(navRow("🏆", "TEAM PROGRESS", "Squad deaths, sector by sector", () => ctx.go("team"), null, "var(--amber)"));
      root.append(navRow("🖼", "IMAGE BOARD", "Transmissions from the wasteland", () => ctx.go("images"),
        (DF.Data.state.images || []).length ? statusPill(String((DF.Data.state.images || []).length), "plasma") : null, "var(--plasma)"));
      root.append(navRow("🎉", "INTEL", g.nextEventEnabled ? "Next Dead-Fest briefing" : "Event intel", () => ctx.go("intel"),
        g.nextEventEnabled ? statusPill("EVENT", "amber") : null, "var(--amber)"));

      root.append(sectionLabel("Career"));
      root.append(navRow("★", "PROGRESSION", `Level ${p.level || 1} · rewards and prestige`, () => ctx.go("progression"),
        statusPill(`LV ${p.level || 1}`, "ember"), "var(--ember)"));
      root.append(navRow("🗄", "SEASON ARCHIVES", "Every season that came before", () => ctx.go("archives"),
        (DF.Data.state.archives || []).length ? statusPill(String((DF.Data.state.archives || []).length), "prestige") : null, "var(--prestige)"));
      root.append(navRow("🎮", "REQUEST A GAME", "Put a game on the squad wishlist", () => ctx.openRequestDialog(), null, "var(--toxic)"));

      root.append(sectionLabel("System"));
      root.append(navRow("⚙", "SETTINGS", "Identity, firmware, themes and alerts", () => ctx.go("settings"), null, "var(--text-mid)"));
      root.append(navRow("🚪", "LOG OUT", "End this session on this terminal", () => ctx.logout(), null, "var(--ember)"));

      root.append(h("div", { class: "t-micro", style: { textAlign: "center", marginTop: "var(--sm)" },
        text: `DEAD-FEST DESKTOP v${ctx.appVersionName} · BUILD ${ctx.appVersion}` }));
      return root;
    }
  };

  /* ---- team progress ------------------------------------------------------ */
  function teamSummary() {
    const all = users();
    const deaths = all.reduce((sum, u) => sum + totalDeaths(u), 0);
    const coins = all.reduce((sum, u) => sum + ((u.playerData || {}).coins || 0), 0);
    return { deaths, coins, survivors: all.length };
  }

  function teamDetail(ctx, target) {
    const p = target.playerData || {};
    const games = p.games || {};
    const sectors = Object.keys(games)
      .map((name) => ({ name, deaths: (games[name] || {}).deaths || 0 }))
      .filter((s) => s.deaths > 0)
      .sort((a, b) => b.deaths - a.deaths)
      .slice(0, 8);
    const total = totalDeaths(target);
    const max = sectors.length ? sectors[0].deaths : 1;

    const root = h("div", { class: "stack", style: { gap: "var(--md)" } });
    root.append(backRow(ctx));

    root.append(h("div", { class: "card" },
      h("div", { class: "row" },
        avatar(target, 52),
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-card-title", text: displayNameOf(target) }),
          h("div", { class: "t-micro", text: "@" + handleOf(target) }),
          h("div", { class: "row", style: { gap: "6px", marginTop: "3px" } },
            levelBadge(p.level || 1, p.prestigeCount || 0),
            h("span", { class: "t-micro ember", text: String(p.title || "SURVIVOR").toUpperCase() })
          )
        )
      ),
      h("hr", { class: "rule" }),
      h("div", { class: "row row--between", style: { alignItems: "flex-end" } },
        h("div", null,
          h("div", { class: "t-micro", text: "TOTAL DEATHS" }),
          h("div", { class: "t-stat-hero ember", text: fmt(total) })
        ),
        h("div", { class: "right" },
          h("div", { class: "t-micro", text: "BANK" }),
          h("div", { class: "t-stat amber", text: fmt(p.coins || 0) })
        )
      )
    ));

    root.append(sectionLabel("Sector Breakdown"));
    if (!sectors.length) {
      root.append(emptyStateCard("🛡", "UNTOUCHED", "This survivor hasn't died in any sector yet."));
    } else {
      const card = h("div", { class: "card stack stack--tight" });
      sectors.forEach((s) => {
        card.append(h("div", null,
          h("div", { class: "row row--between" },
            h("div", { class: "t-body", text: String(s.name).toUpperCase() }),
            h("div", { class: "t-body ember", style: { fontWeight: "900" }, text: `${s.deaths}` })
          ),
          proportionBar(s.deaths / max, "var(--ember)")
        ));
      });
      if (Object.keys(games).length > 8) {
        card.append(h("div", { class: "t-micro", text: "SHOWING TOP 8 SECTORS" }));
      }
      root.append(card);
    }

    root.append(sectionLabel("Standing"));
    root.append(keyValue("Shield", p.shield ? "ACTIVE" : "NONE", p.shield ? "var(--plasma)" : "var(--text-low)"));
    root.append(keyValue("Insulation", p.insulationTier ? "TIER " + DF.Economy.roman(p.insulationTier) : "NONE", p.insulationTier ? "var(--amber)" : "var(--text-low)"));
    root.append(keyValue("Prestige", p.prestigeCount ? `★ ${p.prestigeCount}` : "NONE", p.prestigeCount ? "var(--prestige)" : "var(--text-low)"));
    root.append(keyValue("Status", isOnlineNow(target) ? "ONLINE" : `OFFLINE · ${lastSeenLabel(target)}`, isOnlineNow(target) ? "var(--toxic)" : "var(--text-low)"));
    return root;
  }

  DF.Screens.team = {
    title: "TEAM PROGRESS",
    subtitle: "LIVE SEASON & DEAD-FEST HISTORY",
    pill() { return statusPill(`${users().length} SURVIVORS`, "toxic"); },
    back(ctx) { return ghostAction("BACK", () => ctx.go("more")); },
    render(ctx) {
      const detailUid = ctx.local("teamDetailUid");
      if (detailUid) {
        const target = users().find((u) => u.uid === detailUid);
        if (target) return teamDetail(ctx, target);
        ctx.local("teamDetailUid", null);
      }

      const summary = teamSummary();
      const root = h("div", { class: "stack", style: { gap: "var(--md)" } });
      root.append(h("div", { class: "card" },
        h("div", { class: "row row--between" },
          h("div", null,
            h("div", { class: "t-micro", text: "SQUAD DEATHS" }),
            h("div", { class: "t-stat-hero ember", text: fmt(summary.deaths) })
          ),
          h("div", { class: "right" },
            h("div", { class: "t-micro", text: "SURVIVORS" }),
            h("div", { class: "t-stat", text: fmt(summary.survivors) })
          )
        ),
        h("hr", { class: "rule" }),
        h("div", { class: "t-body", text: `${fmt(summary.coins)} coins are still circulating in the squad.` })
      ));

      const sorted = users().slice().sort((a, b) =>
        totalDeaths(b) - totalDeaths(a) ||
        (((b.playerData || {}).coins || 0) - ((a.playerData || {}).coins || 0)));

      if (!sorted.length) {
        root.append(emptyStateCard("🏆", "NO SURVIVORS", "The squad roster is empty."));
        return root;
      }

      root.append(sectionLabel("Survivors"));
      const list = h("div", { class: "stack stack--tight" });
      sorted.forEach((u, i) => {
        const p = u.playerData || {};
        list.append(h("div", { class: "list-row card--click", onclick: () => { ctx.local("teamDetailUid", u.uid); ctx.rerender(); } },
          rankBadge(i + 1),
          avatar(u, 40),
          h("div", { style: { flex: "1", minWidth: "0" } },
            h("div", { class: "t-card-title", style: { fontSize: "14px" }, text: displayNameOf(u) }),
            h("div", { class: "row", style: { gap: "6px" } },
              levelBadge(p.level || 1, p.prestigeCount || 0),
              h("span", { class: "t-micro", text: `${fmt(p.coins || 0)} COINS` })
            )
          ),
          h("div", { class: "right" },
            h("div", { class: "t-stat ember", text: fmt(totalDeaths(u)) }),
            h("div", { class: "t-micro", text: "DEATHS" })
          )
        ));
      });
      root.append(list);
      return root;
    }
  };

  /* ---- season archives ---------------------------------------------------- */
  function archiveDetail(ctx, archive) {
    const root = h("div", { class: "stack", style: { gap: "var(--md)" } });
    root.append(backRow(ctx, "BACK TO ARCHIVES"));

    root.append(h("div", { class: "card card--prestige" },
      h("div", { class: "t-micro prestige", text: "SEASON RECORD" }),
      h("div", { class: "t-card-title", style: { fontSize: "20px" }, text: archive.name }),
      h("div", { class: "t-micro", style: { marginTop: "2px" }, text: `SAVED ${savedLabel(archive.timestamp)}` }),
      h("hr", { class: "rule" }),
      h("div", { class: "row row--between" },
        h("div", null,
          h("div", { class: "t-micro", text: "LOSSES" }),
          h("div", { class: "t-stat-hero ember", text: fmt(archive.totalCoins) })
        ),
        h("div", { class: "right" },
          h("div", { class: "t-micro", text: "DEATHS" }),
          h("div", { class: "t-stat", text: fmt(archive.totalDeaths) })
        )
      ),
      archive.notes ? h("div", { class: "t-body", style: { marginTop: "var(--sm)" }, text: archive.notes }) : null
    ));

    // Sector totals come from the snapshots, because the archive stores one record per survivor.
    const sectorTotals = {};
    (archive.playerSnapshots || []).forEach((snap) => {
      Object.keys(snap.gameDeaths || {}).forEach((sector) => {
        sectorTotals[sector] = (sectorTotals[sector] || 0) + (snap.gameDeaths[sector] || 0);
      });
    });
    const sectors = Object.keys(sectorTotals)
      .map((name) => ({ name, deaths: sectorTotals[name] }))
      .filter((s) => s.deaths > 0)
      .sort((a, b) => b.deaths - a.deaths);
    const max = sectors.length ? sectors[0].deaths : 1;

    if (sectors.length) {
      root.append(sectionLabel("Sector Breakdown"));
      const card = h("div", { class: "card stack stack--tight" });
      sectors.forEach((s) => {
        card.append(h("div", null,
          h("div", { class: "row row--between" },
            h("div", { class: "t-body", text: String(s.name).toUpperCase() }),
            h("div", { class: "t-body ember", style: { fontWeight: "900" }, text: `${s.deaths}` })
          ),
          proportionBar(s.deaths / max, "var(--ember)")
        ));
      });
      root.append(card);
    }

    const survivors = (archive.playerSnapshots || []).slice().sort((a, b) => (b.coins || 0) - (a.coins || 0));
    root.append(sectionLabel(`Survivors · ${survivors.length}`));
    if (!survivors.length) {
      root.append(emptyStateCard("🗄", "EMPTY RECORD", "This season was saved without survivor data."));
    } else {
      const list = h("div", { class: "stack stack--tight" });
      survivors.forEach((snap, i) => {
        list.append(h("div", { class: "list-row static" },
          rankBadge(i + 1),
          avatar({ playerData: { avatar: snap.avatar, color: snap.color, avatarFrame: snap.avatarFrame } }, 36, false),
          h("div", { style: { flex: "1", minWidth: "0" } },
            h("div", { class: "t-card-title", style: { fontSize: "13px" }, text: snap.displayName || "Survivor" }),
            h("div", { class: "t-micro", text: `${fmt(snap.totalDeaths || 0)} DEATHS · LV ${snap.level || 1}` })
          ),
          h("div", { class: "t-body amber", style: { fontWeight: "900" }, text: fmt(snap.coins || 0) })
        ));
      });
      root.append(list);
    }
    return root;
  }

  DF.Screens.archives = {
    title: "ARCHIVES HUB",
    subtitle: "WASTELAND HISTORICAL DATABASE",
    pill() { return statusPill(`${(DF.Data.state.archives || []).length} SEASONS`, "prestige"); },
    back(ctx) { return ghostAction("BACK", () => { ctx.local("archiveId", null); ctx.go("more"); }); },
    render(ctx) {
      const archiveId = ctx.local("archiveId");
      if (archiveId) {
        const archive = (DF.Data.state.archives || []).find((a) => a.id === archiveId);
        if (archive) return archiveDetail(ctx, archive);
        ctx.local("archiveId", null);
      }

      const archives = DF.Data.state.archives || [];
      const root = h("div", { class: "stack", style: { gap: "var(--md)" } });
      if (!archives.length) {
        root.append(emptyStateCard("🗄", "NO ARCHIVES YET", "When a season ends, an admin can save the wasteland here."));
        return root;
      }

      archives.forEach((a) => {
        root.append(h("div", { class: "card card--click", onclick: () => { ctx.local("archiveId", a.id); ctx.rerender(); } },
          h("div", { class: "row" },
            itemEmblem("🗄", "var(--prestige)", 44),
            h("div", { style: { flex: "1", minWidth: "0" } },
              h("div", { class: "t-card-title", text: a.name }),
              h("div", { class: "t-body", text: `${fmt(a.survivorCount || (a.playerSnapshots || []).length)} survivors · ${fmt(a.totalDeaths)} deaths` }),
              h("div", { class: "t-micro", text: `SAVED ${savedLabel(a.timestamp)}` })
            ),
            h("div", { class: "right" },
              h("div", { class: "t-stat ember", text: fmt(a.totalCoins) }),
              h("div", { class: "t-micro", text: "LOSSES" })
            )
          )
        ));
      });
      return root;
    }
  };

  /* ---- image board -------------------------------------------------------- */
  DF.Screens.images = {
    title: "IMAGES",
    subtitle: "SHARED WITH EVERY SURVIVOR",
    pill() { return statusPill(`${(DF.Data.state.images || []).length} POSTS`, "plasma"); },
    back(ctx) { return ghostAction("BACK", () => ctx.go("more")); },
    render(ctx) {
      const images = DF.Data.state.images || [];
      const processing = ctx.local("imageProcessing") === true;
      const uploading = ctx.local("imageUploading") === true;
      const root = h("div", { class: "stack", style: { gap: "var(--md)" } });

      root.append(h("div", { class: "card" },
        h("div", { class: "row" },
          itemEmblem("📷", "var(--plasma)"),
          h("div", { style: { flex: "1", minWidth: "0" } },
            h("div", { class: "t-card-title", text: "SHARE AN IMAGE" }),
            h("div", { class: "t-body", text: "Compressed on this terminal. Visible to every survivor." })
          ),
          h("button", {
            class: "btn btn--sm" + (processing || uploading ? " btn--off" : ""),
            disabled: processing || uploading,
            text: processing ? "COMPRESSING..." : uploading ? "POSTING..." : "PICK IMAGE",
            onclick: () => ctx.pickImage()
          })
        )
      ));

      if (!images.length) {
        root.append(emptyStateCard("🖼", "NO IMAGES YET", "Be the first to post a transmission from the wasteland."));
        return root;
      }

      const grid = h("div", { class: "gallery" });
      images.forEach((image) => {
        let thumb = null;
        try { thumb = DF.Media.objectUrl("thumb-" + image.id, image.thumb, "image/jpeg"); } catch (e) { thumb = null; }
        grid.append(h("div", {
          class: "gallery-tile",
          style: { padding: "4px", background: "var(--ink2)", border: "1px solid var(--ink-line)", borderRadius: "var(--r-lg)", cursor: "pointer" },
          onclick: () => ctx.openViewer(image)
        },
          h("div", { class: "gallery-thumb" }, thumb ? h("img", { src: thumb, alt: image.caption || "Survivor image", loading: "lazy" }) : h("span", { text: "💀" })),
          h("div", { class: "t-micro", style: { marginTop: "5px", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }, text: image.uploaderName }),
          h("div", { class: "row row--between" },
            h("div", { class: "t-micro", style: { fontSize: "8px" }, "data-live": "ago", "data-at": String(image.createdAt), text: timeAgo(image.createdAt) }),
            image.caption ? h("div", { class: "t-micro", style: { fontSize: "8px" }, text: "💬" }) : null
          )
        ));
      });
      root.append(grid);
      return root;
    }
  };

  /* ---- intel -------------------------------------------------------------- */
  DF.Screens.intel = {
    title: "SURVIVAL INTEL",
    get subtitle() { return `CLASSIFIED TERMINAL DATA // V${DF.APP_VERSION.name}`; },
    pill() {
      const g = world();
      return g.nextEventEnabled ? statusPill("EVENT LIVE" , "amber") : statusPill("NO EVENT", "low");
    },
    back(ctx) { return ghostAction("BACK", () => ctx.go("more")); },
    render(ctx) {
      const g = world();
      const root = h("div", { class: "stack", style: { gap: "var(--md)" } });

      if (!g.nextEventEnabled) {
        root.append(noticeCard("📡", "NO ACTIVE EVENT",
          "There's no Dead-Fest on the calendar right now. Check back after the next announcement."));
      } else {
        root.append(nextEventHero(g, Date.now()));
      }

      root.append(sectionLabel("House Rules"));
      root.append(h("div", { class: "card stack stack--tight" },
        h("div", { class: "t-body", text: "Every death is logged against the sector it happened in. Dying is the whole point — the scoreboard is a ledger of falls, not wins." }),
        h("hr", { class: "rule" }),
        h("div", { class: "t-body", text: "The casualty tax scales with your bank. The richer you are, the more a death costs." }),
        h("hr", { class: "rule" }),
        h("div", { class: "t-body", text: "Insulation shrinks the tax permanently. Insurance absorbs one hit. Shields block one curse." })
      ));

      const leaderboard = users().slice().sort((a, b) => totalDeaths(b) - totalDeaths(a)).slice(0, 5);
      if (leaderboard.length) {
        root.append(sectionLabel("Most Fallen"));
        const list = h("div", { class: "stack stack--tight" });
        leaderboard.forEach((u, i) => {
          list.append(h("div", { class: "list-row static" },
            rankBadge(i + 1),
            avatar(u, 34),
            h("div", { style: { flex: "1", minWidth: "0" }, class: "t-body hi", text: displayNameOf(u) }),
            h("div", { class: "t-body ember", style: { fontWeight: "900" }, text: `${totalDeaths(u)}` })
          ));
        });
        root.append(list);
      }
      return root;
    }
  };

  /* ---- game requests ------------------------------------------------------ */
  DF.Screens.requests = {
    title: "REQUEST GAME",
    subtitle: "SUGGEST A SECTOR",
    pill() {
      const pending = (DF.Data.state.gameRequests || []).filter((r) => r.isApproved !== true).length;
      return statusPill(`${pending} PENDING`, pending ? "amber" : "low");
    },
    back(ctx) { return ghostAction("BACK", () => ctx.go("more")); },
    render(ctx) {
      const requests = DF.Data.state.gameRequests || [];
      const root = h("div", { class: "stack", style: { gap: "var(--md)" } });

      root.append(h("div", { class: "card" },
        h("div", { class: "row" },
          itemEmblem("🎮", "var(--toxic)"),
          h("div", { style: { flex: "1", minWidth: "0" } },
            h("div", { class: "t-card-title", text: "WANT SOMETHING IN THE LINE-UP?" }),
            h("div", { class: "t-body", text: "Send it to the admins. They decide what makes the next Dead-Fest." })
          ),
          h("button", { class: "btn btn--sm", text: "REQUEST", onclick: () => ctx.openRequestDialog() })
        )
      ));

      if (!requests.length) {
        root.append(emptyStateCard("📭", "NO REQUESTS YET", "The wishlist is empty. Be the first to ask."));
        return root;
      }

      const list = h("div", { class: "stack stack--tight" });
      requests.forEach((r) => {
        const cover = (r.coverUrl || "").trim();
        list.append(h("div", { class: "list-row static" },
          cover
            ? h("div", { class: "cover-thumb" }, h("img", { src: cover, alt: "", onerror: "this.style.display='none'" }))
            : itemEmblem("🎮", "var(--text-low)", 40),
          h("div", { style: { flex: "1", minWidth: "0" } },
            h("div", { class: "t-card-title", style: { fontSize: "14px" }, text: r.name || "Untitled" }),
            h("div", { class: "t-micro", text: `${(r.requestedByName || "SURVIVOR").toUpperCase()} · ${r.createdAt ? savedLabel(r.createdAt) : "—"}` })
          ),
          r.isApproved === true ? statusPill("APPROVED", "toxic") : statusPill("PENDING", "amber")
        ));
      });
      root.append(list);
      return root;
    }
  };

  /* ---- settings ----------------------------------------------------------- */
  function switchRow(label, description, on, onToggle, disabled) {
    return h("div", { class: "row row--between setting-row" + (disabled ? " setting-row--off" : "") },
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "t-card-title", style: { fontSize: "13px" }, text: label }),
        h("div", { class: "t-body", text: description })
      ),
      h("button", {
        class: "switch" + (on ? " on" : ""),
        type: "button",
        disabled: !!disabled,
        "aria-label": label,
        "aria-pressed": on ? "true" : "false",
        onclick: disabled ? null : onToggle
      })
    );
  }

  function variantRow(label, value, selected, onClick) {
    return h("div", { class: "radio-row" + (selected ? " on" : ""), onclick: onClick },
      h("div", { class: "radio-dot" + (selected ? " on" : "") }),
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "t-body hi", style: { fontWeight: "700" }, text: label })
      )
    );
  }

  function settingsGeneral(ctx) {
    const g = world();
    const u = user();
    const p = pd();
    const variant = ctx.updateVariant();
    const latestName = g.latestVersionName || "";
    const latestCode = g.latestVersionCode || 0;
    const newer = latestCode > ctx.appVersion;
    const url = ctx.updateUrlFor(variant, g);

    const root = h("div", { class: "stack", style: { gap: "var(--md)" } });

    /* firmware */
    const firmware = heroPanel({
      eyebrow: "Firmware",
      title: `Dead-Fest v${ctx.appVersionName}`,
      glyph: "📱",
      accent: "var(--plasma)",
      badge: statusPill(`BUILD ${ctx.appVersion}`, "plasma"),
      children: h("div", { class: "stack", style: { marginTop: "var(--sm)" } },
        h("div", { class: "t-micro", text: "UPDATE CHANNEL" }),
        variantRow("R8 · Smaller download", "R8", variant === "R8", () => { ctx.prefs.set("updateVariant", "R8"); ctx.rerender(); }),
        variantRow("Standard · Full build", "Standard", variant === "Standard", () => { ctx.prefs.set("updateVariant", "Standard"); ctx.rerender(); }),
        newer && g.updateNotes ? h("div", { class: "t-body", text: g.updateNotes }) : null,
        h("button", {
          class: "btn btn--full btn--tall",
          text: newer && latestName ? `INSTALL v${latestName} · ${variant}` : "CHECK FOR UPDATES",
          onclick: () => ctx.checkUpdate()
        }),
        h("div", { class: "t-micro", text: `PHONE BUILD ${latestCode ? "v" + latestName + " (" + latestCode + ")" : "NOT PUBLISHED"}` })
      )
    });
    root.append(firmware);

    /* identity */
    const handleInput = h("input", {
      value: u ? (u.username || "") : "",
      placeholder: "survivor_handle",
      maxLength: "20",
      "data-focus-key": "handle-input",
      autocomplete: "off"
    });
    const error = h("div", { class: "t-micro ember" });
    const hint = h("div", { class: "hint", text: "3-20 characters. Letters, numbers and underscores." });

    function save() {
      const value = handleInput.value.trim();
      error.textContent = "";
      if (!/^[A-Za-z0-9_]{3,20}$/.test(value)) {
        error.textContent = "Handles are 3-20 characters: letters, numbers and underscores only.";
        return;
      }
      ctx.saveHandle(value);
    }
    handleInput.addEventListener("keydown", (e) => { if (e.key === "Enter") save(); });

    root.append(h("div", { class: "card stack" },
      h("div", { class: "t-micro", text: "IDENTITY" }),
      h("div", { class: "field" },
        h("label", { text: "Unique handle" }),
        handleInput,
        hint,
        error
      ),
      h("div", { class: "row row--between" },
        h("div", { class: "t-micro", text: `DISPLAY NAME · ${displayNameOf(u).toUpperCase()}` }),
        h("button", { class: "btn btn--sm", text: "SAVE HANDLE", onclick: save })
      )
    ));

    /* discord */
    root.append(h("div", { class: "card" },
      h("div", { class: "row" },
        itemEmblem("💬", "var(--prestige)"),
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-card-title", text: "SURVIVOR DISCORD" }),
          h("div", { class: "t-body", text: "Organise games, argue about curses, post clips." })
        ),
        h("button", { class: "btn btn--ghost btn--sm", text: "JOIN", onclick: () => window.open("https://discord.gg/rYq5WywRBG", "_blank") })
      )
    ));

    /* account */
    root.append(h("div", { class: "card stack stack--tight" },
      h("div", { class: "t-micro", text: "ACCOUNT" }),
      keyValue("Email", (u && u.email) || "—"),
      keyValue("Handle", "@" + handleOf(u)),
      keyValue("Survivor since", u && u.createdAt ? savedLabel(u.createdAt) : "—"),
      keyValue("Coins", fmt(p.coins || 0), "var(--amber)"),
      h("div", { style: { marginTop: "var(--sm)" } },
        h("button", { class: "btn btn--ghost btn--full btn--ember", text: "LOG OUT", onclick: () => ctx.logout() })
      )
    ));
    return root;
  }

  function settingsSystem(ctx) {
    const prefs = ctx.prefs;
    const root = h("div", { class: "stack", style: { gap: "var(--md)" } });

    root.append(h("div", { class: "card stack" },
      h("div", { class: "t-micro", text: "PERFORMANCE" }),
      switchRow("Performance Mode", "Turns off transitions and shortens the wheel spin. Useful on older hardware.",
        prefs.get("performanceMode") === true, () => {
          const next = !(prefs.get("performanceMode") === true);
          ctx.applyPerformanceMode(next);
          ctx.rerender();
        }),
      h("hr", { class: "rule" }),
      h("div", { class: "row row--between setting-row setting-row--off" },
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-card-title", style: { fontSize: "13px" }, text: "Battery optimisation" }),
          h("div", { class: "t-body", text: "Not applicable on a desktop terminal — the app never runs in the background here." })
        ),
        statusPill("N/A", "low")
      )
    ));

    root.append(h("div", { class: "card stack" },
      h("div", { class: "t-micro", text: "LIVE FEED ALERTS" }),
      switchRow("Wheel alerts", "Show wheel results in the command center feed.",
        prefs.get("wheelAlerts") !== false, () => {
          prefs.set("wheelAlerts", !(prefs.get("wheelAlerts") !== false));
          ctx.rerender();
        }),
      switchRow("Market alerts", "Show Black Market purchases in the feed.",
        prefs.get("marketAlerts") !== false, () => {
          prefs.set("marketAlerts", !(prefs.get("marketAlerts") !== false));
          ctx.rerender();
        }),
      switchRow("Wasteland alerts", "Show deaths, sign-ins and everything else.",
        prefs.get("wastelandAlerts") !== false, () => {
          prefs.set("wastelandAlerts", !(prefs.get("wastelandAlerts") !== false));
          ctx.rerender();
        })
    ));

    return root;
  }

  function settingsDiagnostics(ctx) {
    const u = user();
    const g = world();
    const rows = [
      ["App version", `Dead-Fest ${ctx.appVersionName} (${ctx.appVersion})`],
      ["Platform", "Windows desktop"],
      ["Electron", window.electronVersion || "—"],
      ["User agent", navigator.userAgent],
      ["Firebase project", DF.FIREBASE.projectId],
      ["Signed in as", (u && u.email) || "—"],
      ["UID", (u && u.uid) || "—"],
      ["Admin", u && u.admin ? "YES" : "NO"],
      ["Firestore", DF.Data.currentUser() ? "CONNECTED" : "OFFLINE"],
      ["Survivors", String(users().length)],
      ["Events loaded", String((DF.Data.state.events || []).length)],
      ["Images loaded", String((DF.Data.state.images || []).length)],
      ["Global doc", g.marketEnabled !== undefined ? "LOADED" : "MISSING"]
    ];
    const root = h("div", { class: "stack", style: { gap: "var(--md)" } });
    const card = h("div", { class: "card stack stack--tight" });
    rows.forEach(([k, v]) => card.append(keyValue(k, v)));
    root.append(card);

    root.append(h("div", { class: "row", style: { gap: "var(--sm)" } },
      h("button", {
        class: "btn btn--ghost btn--full",
        text: "COPY DIAGNOSTICS",
        onclick: () => {
          const text = rows.map(([k, v]) => `${k}: ${v}`).join("\n");
          if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(() => toast("Diagnostics copied.")).catch(() => toast("Couldn't copy diagnostics.", true));
          } else {
            toast("Clipboard is unavailable.", true);
          }
        }
      }),
      h("button", {
        class: "btn btn--ghost btn--full",
        text: "RELOAD APP",
        onclick: () => window.location.reload()
      })
    ));

    root.append(h("div", { class: "card" },
      h("div", { class: "t-micro", text: "ABOUT THIS BUILD" }),
      h("div", { class: "t-body", style: { marginTop: "6px" },
        text: "The desktop client talks to the same Firebase project as the phone app: the same accounts, the same survivors, the same coins and the same event log. Nothing is stored locally except your theme and alert preferences." })
    ));
    return root;
  }

  function settingsThemes(ctx) {
    const current = DF.Prefs.get("theme");
    const root = h("div", { class: "stack", style: { gap: "var(--md)" } });
    DF.THEMES.forEach((theme) => {
      const selected = current === theme.id;
      root.append(h("div", {
        class: "card card--click",
        style: { borderColor: selected ? theme.ember : "var(--ink-line)" },
        onclick: () => { ctx.applyTheme(theme.id); ctx.rerender(); }
      },
        h("div", { class: "row" },
          h("div", { style: { display: "flex", gap: "4px", flex: "none" } },
            [theme.ink0, theme.ink3, theme.ember, theme.glow].map((hex) => h("span", {
              style: { width: "22px", height: "42px", borderRadius: "5px", background: hex, border: "1px solid rgba(255,255,255,0.08)" }
            }))
          ),
          h("div", { style: { flex: "1", minWidth: "0" } },
            h("div", { class: "t-card-title", text: theme.name }),
            h("div", { class: "t-micro", text: theme.id.toUpperCase() })
          ),
          selected ? statusPill("ACTIVE", "toxic") : h("span", { class: "t-micro", text: "APPLY" })
        )
      ));
    });
    return root;
  }

  DF.Screens.settings = {
    title: "TERMINAL CONFIG",
    subtitle: "System Operations & Personalization",
    pill() { return statusPill(`v${DF.APP_VERSION.name}`, "low"); },
    render(ctx) {
      const tab = L(ctx, "settingsTab", "general");
      const tabs = h("div", { class: "tabbar" });
      [["general", "General"], ["system", "System"], ["diagnostics", "Diagnostics"], ["themes", "Themes"]].forEach(([key, label]) => {
        tabs.append(pillChip(label, tab === key, () => { ctx.local("settingsTab", key); ctx.rerender(); }));
      });
      const root = h("div", { class: "stack", style: { gap: "var(--md)" } }, tabs);
      if (tab === "system") root.append(settingsSystem(ctx));
      else if (tab === "diagnostics") root.append(settingsDiagnostics(ctx));
      else if (tab === "themes") root.append(settingsThemes(ctx));
      else root.append(settingsGeneral(ctx));
      return root;
    }
  };
})();
