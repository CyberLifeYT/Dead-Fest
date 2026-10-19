/* =============================================================================
   Dead-Fest desktop — admin console
   Mirrors ui/screens/AdminScreen.kt: the same modules, the same actions.
   ========================================================================== */
window.DF = window.DF || {};
DF.Screens = DF.Screens || {};

const ADMIN_MODULES = [
  { key: "LIVE",         label: "Live",     accent: "var(--toxic)" },
  { key: "PLAYERS",      label: "Players",  accent: "var(--plasma)" },
  { key: "SHOP",         label: "Shop",     accent: "var(--amber)" },
  { key: "SCRATCH",      label: "Scratch",  accent: "var(--ember-glow)" },
  { key: "SECTORS",      label: "Sectors",  accent: "#CD7F32" },
  { key: "EVENT",        label: "Event",    accent: "var(--prestige)" },
  { key: "OVERLOAD",     label: "Overload", accent: "var(--amber-deep)" },
  { key: "WHEEL",        label: "Wheel",    accent: "#C8CBD1" },
  { key: "REQUESTS",     label: "Requests", accent: "var(--prestige)" },
  { key: "GLOBAL",       label: "Global",   accent: "var(--ember)" },
  { key: "BACKUP",       label: "Backup",   accent: "var(--ember)" },
  { key: "BROADCAST",    label: "Broadcast", accent: "var(--plasma)" }
];

DF.Screens.admin = {
  title: "ADMIN OVERRIDE",
  subtitle: "ROOT ACCESS // HANDLE WITH CARE",
  needs: ["user", "global", "users", "events", "gameRequests", "archives", "scratchPrizes"],
  render(ctx) {
    const user = ctx.state.user;
    // Defence in depth: the navigation hides this screen from non-admins, and the Firestore
    // rules refuse the writes, but the screen itself should also refuse to render.
    if (!user || user.admin !== true) {
      return h("div", { class: "stack" },
        emptyState("🔒", "ACCESS DENIED", "Administrator privileges are needed to access this sector.")
      );
    }

    const module = ctx.local("adminModule", "LIVE");
    const wrap = h("div", { class: "stack" });

    const chips = h("div", { class: "row row--wrap" });
    ADMIN_MODULES.forEach((m) => {
      chips.append(h("button", {
        class: "chip" + (m.key === module ? " on" : ""),
        text: m.label,
        style: m.key === module ? { background: m.accent + "22", borderColor: m.accent + "66", color: m.accent } : null,
        onclick: () => { ctx.local("adminModule", m.key); ctx.rerender(); }
      }));
    });
    wrap.append(chips);

    const panel = h("div", { class: "stack" });
    const mod = ADMIN_MODULES.find((m) => m.key === module);
    if (module === "LIVE") panel.append(adminLive(ctx));
    else if (module === "PLAYERS") panel.append(adminPlayers(ctx));
    else if (module === "SHOP") panel.append(adminShop(ctx));
    else if (module === "SCRATCH") panel.append(adminScratch(ctx));
    else if (module === "SECTORS") panel.append(adminSectors(ctx));
    else if (module === "EVENT") panel.append(adminEvent(ctx));
    else if (module === "OVERLOAD") panel.append(adminOverload(ctx));
    else if (module === "WHEEL") panel.append(adminWheel(ctx));
    else if (module === "REQUESTS") panel.append(adminRequests(ctx));
    else if (module === "GLOBAL") panel.append(adminGlobal(ctx));
    else if (module === "BACKUP") panel.append(adminBackup(ctx));
    else if (module === "BROADCAST") panel.append(adminBroadcast(ctx));
    wrap.append(panel);
    return wrap;
  }
};

function adminLive(ctx) {
  const { state } = ctx;
  const users = state.users || [];
  const online = users.filter((u) => u.playerData && u.playerData.isOnline);
  const pending = users.filter((u) => u.playerData && u.playerData.pendingSpins > 0);
  const events = (state.events || []).slice(0, 25);
  const global = state.global;

  return h("div", { class: "stack" },
    h("div", { class: "grid-4" },
      statTile("ONLINE", String(online.length), "var(--toxic)"),
      statTile("SURVIVORS", String(users.length)),
      statTile("SPINS DUE", String(pending.reduce((a, u) => a + u.playerData.pendingSpins, 0)), "var(--ember)"),
      statTile("GLOBAL DEATHS", fmt(global ? global.grandTotal : 0))
    ),
    sectionLabel("Recent events"),
    h("div", { class: "card scroll-420" },
      events.length ? h("table", { class: "tbl" },
        h("thead", {}, h("tr", {}, h("th", { text: "CATEGORY" }), h("th", { text: "MESSAGE" }), h("th", { text: "WHEN" }))),
        h("tbody", {}, events.map((e) => h("tr", {},
          h("td", { class: "hi", text: e.category || "" }),
          h("td", { text: e.message || "" }),
          h("td", { text: timeAgo(e.timestamp && e.timestamp.toMillis ? e.timestamp.toMillis() : 0) })
        )))
      ) : emptyState("📡", "No events yet", "")
    )
  );
}

function adminPlayers(ctx) {
  const users = ctx.state.users || [];
  const query = ctx.local("adminPlayerQuery", "");
  const filtered = query ? users.filter((u) => handleOf(u).toLowerCase().indexOf(query.toLowerCase()) >= 0) : users;
  const grid = h("div", { class: "grid-2" });
  filtered.forEach((u) => {
    const pd = u.playerData || {};
    grid.append(h("div", { class: "list-row", onclick: () => ctx.openPlayerDetail(u) },
      avatar(u),
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "t-card-title", text: handleOf(u) }),
        h("div", { class: "t-body", text: `${fmt(pd.coins || 0)} coins · ${fmt(totalDeaths(u))} deaths` })
      ),
      u.admin ? statusPill("ADMIN", "ember") : null
    ));
  });
  return h("div", { class: "stack" },
    h("div", { class: "field", style: { maxWidth: "340px" } },
      h("input", { value: query, placeholder: "Find survivor...", "data-focus-key": "adminSearch", oninput: (e) => { ctx.local("adminPlayerQuery", e.target.value); ctx.rerender(); } })
    ),
    grid
  );
}

function adminShop(ctx) {
  const cfg = ctx.state.global ? ctx.state.global.economy : DF.Economy.DEFAULT_CONFIG;
  const table = h("table", { class: "tbl" },
    h("thead", {}, h("tr", {},
      h("th", { text: "ITEM" }), h("th", { text: "ENABLED" }), h("th", { text: "PRICE" }), h("th", { text: "REDUCTION" })
    ))
  );
  const tbody = h("tbody");
  DF.Economy.SHOP.forEach((item) => {
    const enabled = DF.Economy.enabledIn(item, cfg);
    const price = DF.Economy.priceIn(item, cfg);
    tbody.append(h("tr", {},
      h("td", { class: "hi", text: item.name }),
      h("td", {}, h("div", { class: "switch" + (enabled ? " on" : ""), onclick: () => ctx.setShopOverride(item.id, { enabled: !enabled }) })),
      h("td", {}, h("input", {
        value: String(price), style: { width: "110px", height: "32px" },
        onchange: (e) => ctx.setShopOverride(item.id, { price: parseInt(e.target.value, 10) || 0 })
      })),
      h("td", {}, item.tier > 0 ? h("input", {
        value: String(DF.Economy.reductionIn(item, cfg)), style: { width: "80px", height: "32px" },
        onchange: (e) => ctx.setShopOverride(item.id, { reductionPct: parseInt(e.target.value, 10) || 0 })
      }) : h("span", { class: "muted", text: "—" }))
    ));
  });
  table.append(tbody);
  return h("div", { class: "stack" },
    sectionLabel("Black Market items"),
    h("div", { class: "card scroll-420" }, table)
  );
}

function adminScratch(ctx) {
  const prizes = (ctx.state.scratchPrizes && ctx.state.scratchPrizes.length) ? ctx.state.scratchPrizes : DF.Economy.SCRATCH_POOL;
  const table = h("table", { class: "tbl" },
    h("thead", {}, h("tr", {},
      h("th", { text: "LABEL" }), h("th", { text: "WEIGHT" }), h("th", { text: "VALUE" }), h("th", { text: "TYPE" }), h("th", { text: "" })
    ))
  );
  const tbody = h("tbody");
  prizes.forEach((p) => {
    tbody.append(h("tr", {},
      h("td", { class: "hi", text: p.label }),
      h("td", {}, h("input", { value: String(p.weight), style: { width: "90px", height: "32px" }, onchange: (e) => ctx.setScratchPrize(p, { weight: parseFloat(e.target.value) || 0 }) })),
      h("td", {}, h("input", { value: String(p.value || ""), style: { width: "90px", height: "32px" }, onchange: (e) => ctx.setScratchPrize(p, { value: e.target.value }) })),
      h("td", { text: p.type }),
      h("td", {}, h("button", { class: "btn btn--ember btn--sm", text: "DELETE", onclick: () => ctx.deleteScratchPrize(p) }))
    ));
  });
  table.append(tbody);
  return h("div", { class: "stack" },
    sectionLabel("Prize pool"),
    h("div", { class: "card scroll-420" }, table)
  );
}

function adminSectors(ctx) {
  const global = ctx.state.global;
  const games = global ? global.games : [];
  const locked = global ? global.lockedGames : [];
  const list = h("div", { class: "stack stack--tight" });
  games.forEach((sector) => {
    const isLocked = locked.indexOf(sector) >= 0;
    list.append(h("div", { class: "list-row static" },
      h("div", { style: { flex: "1" } },
        h("div", { class: "t-card-title", text: sector }),
        h("div", { class: "t-body", text: isLocked ? "Locked — deaths cannot be recorded here" : "Active" })
      ),
      statusPill(isLocked ? "LOCKED" : "ACTIVE", isLocked ? null : "toxic"),
      h("button", { class: "btn btn--ghost btn--sm", text: isLocked ? "UNLOCK" : "LOCK", onclick: () => ctx.toggleSectorLock(sector) }),
      h("button", { class: "btn btn--ember btn--sm", text: "REMOVE", onclick: () => ctx.removeSector(sector) })
    ));
  });
  return h("div", { class: "stack" },
    sectionLabel("Sectors"),
    h("div", { class: "row", style: { maxWidth: "420px" } },
      h("div", { class: "field", style: { flex: "1" } },
        h("input", { value: ctx.local("newSector", ""), placeholder: "New sector designation", "data-focus-key": "newSector", oninput: (e) => ctx.local("newSector", e.target.value) })
      ),
      h("button", { class: "btn", text: "ADD", onclick: () => ctx.addSector() })
    ),
    list
  );
}

function adminEvent(ctx) {
  const global = ctx.state.global;
  const ev = global || {};
  return h("div", { class: "stack" },
    sectionLabel("Next Dead-Fest"),
    h("div", { class: "card" },
      h("div", { class: "field" },
        h("label", { text: "Event name" }),
        h("input", { value: ev.nextEventName || "", onchange: (e) => ctx.setGlobal({ nextEventName: e.target.value }) })
      ),
      h("div", { class: "field", style: { marginTop: "10px" } },
        h("label", { text: "Start time (epoch millis)" }),
        h("input", { value: String(ev.nextEventStartsAt || 0), onchange: (e) => ctx.setGlobal({ nextEventStartsAt: parseInt(e.target.value, 10) || 0 }) })
      ),
      h("div", { class: "field", style: { marginTop: "10px" } },
        h("label", { text: "Notes" }),
        h("textarea", { onchange: (e) => ctx.setGlobal({ nextEventNotes: e.target.value }) }, ev.nextEventNotes || "")
      ),
      h("div", { style: { marginTop: "12px" } },
        h("button", {
          class: "btn btn--sm",
          text: ev.nextEventEnabled ? "DISABLE PANEL" : "ENABLE PANEL",
          onclick: () => ctx.setGlobal({ nextEventEnabled: !ev.nextEventEnabled })
        })
      )
    ),
    sectionLabel("Game line-up"),
    h("div", { class: "card" },
      h("div", { class: "t-body", text: (ev.nextEventGames || []).length ? (ev.nextEventGames || []).join(" · ") : "No games queued." }),
      h("div", { class: "row row--wrap", style: { marginTop: "12px" } },
        (ev.games || []).map((g) => h("button", {
          class: "chip",
          text: ((ev.nextEventGames || []).indexOf(g) >= 0 ? "✓ " : "+ ") + g,
          onclick: () => ctx.queueEventGame(g)
        }))
      )
    )
  );
}

function adminOverload(ctx) {
  const global = ctx.state.global;
  const games = global ? global.games.filter((g) => global.lockedGames.indexOf(g) < 0) : [];
  return h("div", { class: "stack" },
    sectionLabel("Overload status"),
    h("div", { class: "card" },
      global && global.sectorOverload
        ? h("div", { class: "t-card-title amber", text: `OVERLOAD ACTIVE: ${(global.overloadedSector || "").toUpperCase()} x${global.multiplier}` })
        : h("div", { class: "t-body", text: "No sector is overloaded." }),
      global && global.sectorOverload
        ? h("button", { class: "btn btn--ember btn--sm", style: { marginTop: "12px" }, text: "TERMINATE", onclick: () => ctx.setGlobal({ sectorOverload: false, overloadedSector: "", overloadEndsAt: 0 }) })
        : null
    ),
    sectionLabel("Initiate overload"),
    h("div", { class: "card" },
      h("div", { class: "row row--wrap" },
        games.map((g) => h("button", { class: "chip", text: g, onclick: () => ctx.setGlobal({ sectorOverload: true, overloadedSector: g, multiplier: 2, overloadEndsAt: 0 }) }))
      )
    )
  );
}

function adminWheel(ctx) {
  const global = ctx.state.global;
  const items = global ? global.wheelItems : [];
  const list = h("div", { class: "stack stack--tight" });
  items.forEach((item) => {
    list.append(h("div", { class: "list-row static" },
      h("span", { style: { width: "14px", height: "14px", borderRadius: "50%", background: item.color, flex: "none" } }),
      h("div", { style: { flex: "1" } },
        h("div", { class: "t-card-title", text: item.label }),
        h("div", { class: "t-body", text: `${item.actionType === "take" ? "TAKE" : "GIVE"} ${item.value || "-"} · w ${item.weight}` })
      ),
      h("div", { class: "switch" + (item.enabled !== false ? " on" : ""), onclick: () => ctx.updateWheelItem(item.id, { enabled: item.enabled === false }) }),
      h("button", { class: "btn btn--ember btn--sm", text: "DELETE", onclick: () => ctx.updateWheelItem(item.id, null) })
    ));
  });
  return h("div", { class: "stack" },
    sectionLabel("Wheel slices"),
    h("div", { class: "row", style: { maxWidth: "520px" } },
      h("div", { class: "field", style: { flex: "1" } },
        h("label", { text: "New slice" }),
        h("input", { value: ctx.local("newWheelLabel", ""), placeholder: "Label", "data-focus-key": "newWheel", oninput: (e) => ctx.local("newWheelLabel", e.target.value) })
      ),
      h("div", { class: "field", style: { width: "120px" } },
        h("label", { text: "Value" }),
        h("input", { value: ctx.local("newWheelValue", ""), oninput: (e) => ctx.local("newWheelValue", e.target.value) })
      ),
      h("div", { class: "field", style: { width: "110px" } },
        h("label", { text: "Weight" }),
        h("input", { value: ctx.local("newWheelWeight", "1"), oninput: (e) => ctx.local("newWheelWeight", e.target.value) })
      ),
      h("button", { class: "btn", text: "ADD", onclick: () => ctx.addWheelSlice() })
    ),
    list
  );
}

function adminRequests(ctx) {
  const requests = ctx.state.gameRequests || [];
  const pending = requests.filter((r) => r.status !== "approved");
  const approved = requests.filter((r) => r.status === "approved");
  const rows = (list, isApproved) => h("div", { class: "stack stack--tight" },
    list.length ? list.map((r) => h("div", { class: "list-row static" },
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "t-card-title", text: r.name }),
        h("div", { class: "t-body", text: "Asked by " + (r.requestedByName || "Survivor") + (r.decidedByName ? " · decided by " + r.decidedByName : "") })
      ),
      statusPill(isApproved ? "APPROVED" : "PENDING", isApproved ? "toxic" : "amber"),
      isApproved ? null : h("button", { class: "btn btn--sm", text: "APPROVE", onclick: () => ctx.approveRequest(r.id) }),
      h("button", { class: "btn btn--ember btn--sm", text: "REMOVE", onclick: () => ctx.removeRequest(r.id) })
    )) : h("div", { class: "t-body", text: isApproved ? "Nothing approved yet." : "The queue is clear." })
  );
  return h("div", { class: "stack" },
    sectionLabel(`Pending requests (${pending.length})`),
    rows(pending, false),
    sectionLabel(`Approved games (${approved.length})`),
    rows(approved, true)
  );
}

function adminGlobal(ctx) {
  const global = ctx.state.global || {};
  const sw = (title, subtitle, checked, onChange, danger) => h("div", {},
    h("div", { class: "row" },
      h("div", { style: { flex: "1" } },
        h("div", { class: "t-card-title", style: { color: danger && checked ? "var(--ember)" : null }, text: title }),
        h("div", { class: "t-body", text: subtitle })
      ),
      h("div", { class: "switch" + (checked ? " on" : ""), onclick: onChange })
    )
  );
  return h("div", { class: "stack" },
    sectionLabel("System switches"),
    h("div", { class: "card" },
      sw("Black Market access", "Enable or disable shop purchases", global.marketEnabled !== false, () => ctx.setGlobal({ marketEnabled: global.marketEnabled === false })),
      h("div", { class: "divider" }),
      sw("Wheel of Fate", "Enable or disable wheel spinning", global.wheelEnabled !== false, () => ctx.setGlobal({ wheelEnabled: global.wheelEnabled === false })),
      h("div", { class: "divider" }),
      sw("Revives", "Allow survivors to buy Revives", global.reviveEnabled !== false, () => ctx.setGlobal({ reviveEnabled: global.reviveEnabled === false })),
      h("div", { class: "divider" }),
      sw("Lockdown protocol", "Freeze core features & death recording", global.lockdownActive === true, () => ctx.setGlobal({ lockdownActive: global.lockdownActive !== true }), true),
      h("div", { class: "divider" }),
      sw("Forced Spin Protocol", "Every death adds a penalty spin to the Wheel of Fate", global.forceSpinProtocol !== false, () => ctx.setGlobal({ forceSpinProtocol: global.forceSpinProtocol === false }))
    ),
    sectionLabel("Publish firmware"),
    h("div", { class: "card" },
      h("div", { class: "t-body", text: "Live: v" + (global.latestVersionName || "-") + " (build " + (global.latestVersionCode || 0) + ")" }),
      h("button", { class: "btn btn--sm", style: { marginTop: "12px" }, text: "PUBLISH FIRMWARE", onclick: () => ctx.openFirmwareDialog() })
    )
  );
}

function adminBackup(ctx) {
  const archives = ctx.state.archives || [];
  return h("div", { class: "stack" },
    sectionLabel("Archive a season"),
    h("div", { class: "card" },
      h("div", { class: "row", style: { maxWidth: "520px" } },
        h("div", { class: "field", style: { flex: "1" } },
          h("label", { text: "Season name" }),
          h("input", { value: ctx.local("archiveName", ""), "data-focus-key": "archiveName", oninput: (e) => ctx.local("archiveName", e.target.value) })
        ),
        h("button", { class: "btn", text: "ARCHIVE", onclick: () => ctx.createArchive() })
      )
    ),
    sectionLabel("Past seasons"),
    h("div", { class: "card" },
      archives.length
        ? h("div", { class: "stack stack--tight" }, archives.map((a) => h("div", { class: "row" },
            h("div", { style: { flex: "1" } },
              h("div", { class: "t-card-title", text: a.name }),
              h("div", { class: "t-body", text: dateStr(a.timestamp) })
            ),
            h("button", { class: "btn btn--ghost btn--sm", text: "RESTORE", onclick: () => ctx.restoreArchive(a) })
          )))
        : h("div", { class: "t-body", text: "No seasons archived." })
    )
  );
}

function adminBroadcast(ctx) {
  return h("div", { class: "stack" },
    sectionLabel("Broadcast"),
    h("div", { class: "card" },
      h("div", { class: "field" },
        h("label", { text: "Message" }),
        h("textarea", { value: ctx.local("broadcastText", ""), "data-focus-key": "broadcast", oninput: (e) => ctx.local("broadcastText", e.target.value) })
      ),
      h("button", { class: "btn btn--sm", style: { marginTop: "12px" }, text: "SEND BROADCAST", onclick: () => ctx.sendBroadcast() })
    )
  );
}
