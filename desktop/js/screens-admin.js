/* =============================================================================
   Dead-Fest desktop — admin console
   -----------------------------------------------------------------------------
   A port of AdminScreen.kt and AdminEconomyScreen.kt: the same twelve modules,
   the same writes, and the same destructive confirmations. Everything here is
   additionally gated by the Firestore rules, so a non-admin cannot use these
   controls even if they somehow reach the screen.
   ========================================================================== */
window.DF = window.DF || {};
DF.Screens = DF.Screens || {};

(function () {
  const L = (ctx, key, fallback) => {
    const v = ctx.local(key);
    return v === undefined || v === null ? fallback : v;
  };
  const cfg = (ctx) => (ctx.state.global && ctx.state.global.economy) || DF.Economy.DEFAULT_CONFIG;
  const world = (ctx) => ctx.state.global || {};

  function savedLabel(ms) {
    if (!ms) return "UNKNOWN DATE";
    const d = new Date(ms);
    const two = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())} ${two(d.getHours())}:${two(d.getMinutes())}`;
  }

  function overloadRemaining(endsAt) {
    if (!endsAt) return "no time limit";
    const left = Math.max(0, endsAt - Date.now());
    const mins = Math.floor(left / 60000);
    if (mins >= 60) return `${Math.floor(mins / 60)}h ${mins % 60}m left`;
    if (mins >= 1) return `${mins}m left`;
    return `${Math.max(0, Math.floor(left / 1000))}s left`;
  }

  const MODULES = [
    { key: "LIVE", label: "Live", accent: "var(--toxic)" },
    { key: "PLAYERS", label: "Players", accent: "var(--plasma)" },
    { key: "SHOP", label: "Shop", accent: "var(--amber)" },
    { key: "SCRATCH", label: "Scratch", accent: "var(--ember-glow)" },
    { key: "SECTORS", label: "Sectors", accent: "#CD7F32" },
    { key: "EVENT", label: "Event", accent: "var(--prestige)" },
    { key: "OVERLOAD", label: "Overload", accent: "var(--amber-deep)" },
    { key: "WHEEL", label: "Wheel", accent: "#C8CBD1" },
    { key: "REQUESTS", label: "Requests", accent: "var(--prestige)" },
    { key: "GLOBAL", label: "Global", accent: "var(--ember)" },
    { key: "BACKUP", label: "Backup", accent: "var(--ember)" },
    { key: "BROADCAST", label: "Broadcast", accent: "var(--plasma)" }
  ];

  const SWATCHES = ["#FF4B4B", "#FFD54F", "#4CE07F", "#4DD0E1", "#B388FF", "#FF8A5B", "#FB8C00", "#7CB342", "#039BE5", "#EC407A"];

  /* ---- shared controls ---------------------------------------------------- */
  function field(label, input, hint) {
    return h("div", { class: "field" },
      h("label", { text: label }),
      input,
      hint ? h("div", { class: "hint", text: hint }) : null
    );
  }

  function numberInput(value, onSave, width) {
    return h("input", {
      type: "number",
      value: String(value),
      style: width ? { width } : { width: "110px", height: "32px" },
      onchange: (e) => onSave(parseFloat(e.target.value) || 0)
    });
  }

  function textPrompt(title, label, value, onSave, hint) {
    const input = h("input", { value: value || "", "data-focus-key": "prompt" });
    openModal({
      title,
      body: field(label, input, hint),
      actions: [
        h("button", { class: "btn btn--ghost", text: "CANCEL", onclick: () => closeModal() }),
        h("button", {
          class: "btn", text: "SAVE",
          onclick: () => {
            const next = input.value.trim();
            if (!next) { toast("That can't be empty.", true); return; }
            closeModal();
            onSave(next);
          }
        })
      ]
    });
    setTimeout(() => input.focus(), 40);
  }

  function swatchRow(selected, onPick) {
    const row = h("div", { class: "row row--wrap", style: { gap: "6px" } });
    SWATCHES.forEach((color) => {
      row.append(h("button", {
        type: "button",
        "aria-label": color,
        style: {
          width: "26px", height: "26px", borderRadius: "50%",
          background: color,
          border: selected === color ? "2px solid #fff" : "2px solid rgba(0,0,0,0.4)",
          cursor: "pointer",
          boxShadow: selected === color ? "0 0 0 2px rgba(255,255,255,0.25)" : "none"
        },
        onclick: () => onPick(color)
      }));
    });
    return row;
  }

  function eventMillis(timestamp) {
    if (!timestamp) return 0;
    if (typeof timestamp === "number") return timestamp;
    if (typeof timestamp.toMillis === "function") return timestamp.toMillis();
    return 0;
  }

  /* ---- screen ------------------------------------------------------------- */
  DF.Screens.admin = {
    title: "ADMIN OVERRIDE",
    subtitle: "ROOT ACCESS // HANDLE WITH CARE",
    pill() { return h("span", { class: "pill pill--ember", text: "ADMIN" }); },
    render(ctx) {
      const me = ctx.state.user;
      if (!me || me.admin !== true) {
        return h("div", { class: "stack" },
          emptyState("🔒", "ACCESS DENIED", "Administrator privileges are needed to access this sector.")
        );
      }

      const module = L(ctx, "adminModule", "LIVE");
      const wrap = h("div", { class: "stack", style: { gap: "var(--md)" } });
      const chips = h("div", { class: "tabbar" });
      MODULES.forEach((m) => {
        chips.append(h("button", {
          class: "chip" + (m.key === module ? " on" : ""),
          text: m.label,
          style: m.key === module ? { background: m.accent + "22", borderColor: m.accent + "66", color: m.accent } : null,
          onclick: () => { ctx.local("adminModule", m.key); ctx.rerender(); }
        }));
      });
      wrap.append(chips);

      const panel = h("div", { class: "stack" });
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

  /* ---- live --------------------------------------------------------------- */
  function adminLive(ctx) {
    const state = ctx.state;
    const users = state.users || [];
    const online = users.filter((u) => isOnlineNow(u));
    const pending = users.reduce((a, u) => a + (((u.playerData || {}).pendingSpins) || 0), 0);
    const global = state.global || {};
    const events = (state.events || []).slice(0, 30);

    return h("div", { class: "stack", style: { gap: "var(--md)" } },
      h("div", { class: "grid-4" },
        statTile("ONLINE", String(online.length), "var(--toxic)"),
        statTile("SURVIVORS", String(users.length)),
        statTile("SPINS DUE", String(pending), "var(--ember)"),
        statTile("GLOBAL DEATHS", fmt(global.grandTotal || 0))
      ),
      global.lockdownActive
        ? alertBanner("🔒", "LOCKDOWN IS ACTIVE", "Deaths, shop purchases and interaction are frozen.", "var(--ember)")
        : null,
      sectionLabel("Recent Events"),
      h("div", { class: "card scroll-420" },
        events.length ? h("table", { class: "tbl" },
          h("thead", {}, h("tr", {}, h("th", { text: "CATEGORY" }), h("th", { text: "MESSAGE" }), h("th", { text: "WHEN" }))),
          h("tbody", {}, events.map((e) => h("tr", {},
            h("td", { class: "hi", text: e.category || "" }),
            h("td", { text: e.message || "" }),
            h("td", { text: timeAgo(eventMillis(e.timestamp)) })
          )))
        ) : emptyState("📡", "No events yet", "")
      )
    );
  }

  /* ---- players ------------------------------------------------------------ */
  function adminPlayers(ctx) {
    const users = ctx.state.users || [];
    const query = L(ctx, "adminPlayerQuery", "");
    const filtered = query
      ? users.filter((u) => (displayNameOf(u) + " " + handleOf(u)).toLowerCase().indexOf(query.toLowerCase()) >= 0)
      : users;
    const grid = h("div", { class: "grid-2" });
    filtered.forEach((u) => {
      const p = u.playerData || {};
      grid.append(h("div", { class: "list-row card--click", onclick: () => ctx.openPlayerDetail(u) },
        avatar(u, 40),
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-card-title", text: displayNameOf(u) }),
          h("div", { class: "t-micro", text: `@${handleOf(u)} · ${fmt(p.coins || 0)} COINS · ${fmt(totalDeaths(u))} DEATHS` })
        ),
        u.admin ? statusPill("ADMIN", "ember") : null
      ));
    });
    return h("div", { class: "stack" },
      field("Search", h("input", {
        value: query,
        placeholder: "Find survivor...",
        "data-focus-key": "adminSearch",
        oninput: (e) => { ctx.local("adminPlayerQuery", e.target.value); ctx.rerender(); }
      }), `${filtered.length} of ${users.length} survivors`),
      grid
    );
  }

  /* ---- shop --------------------------------------------------------------- */
  function adminShop(ctx) {
    const economy = cfg(ctx);
    const table = h("table", { class: "tbl" },
      h("thead", {}, h("tr", {},
        h("th", { text: "ITEM" }), h("th", { text: "ENABLED" }), h("th", { text: "PRICE" }), h("th", { text: "REDUCTION" })
      ))
    );
    const tbody = h("tbody");
    DF.Economy.SHOP.forEach((item) => {
      const enabled = DF.Economy.enabledIn(item, economy);
      const price = DF.Economy.priceIn(item, economy);
      tbody.append(h("tr", {},
        h("td", { class: "hi", text: item.name }),
        h("td", {}, h("div", { class: "switch" + (enabled ? " on" : ""), onclick: () => ctx.setShopOverride(item.id, { enabled: !enabled }) })),
        h("td", {}, numberInput(price, (v) => ctx.setShopOverride(item.id, { price: Math.max(0, Math.round(v)) }))),
        h("td", {}, item.tier > 0
          ? numberInput(DF.Economy.reductionIn(item, economy), (v) => ctx.setShopOverride(item.id, { reductionPct: Math.max(0, Math.min(100, Math.round(v))) }), "80px")
          : h("span", { class: "muted", text: "—" }))
      ));
    });
    table.append(tbody);
    return h("div", { class: "stack" },
      sectionLabel("Black Market Items"),
      h("div", { class: "card scroll-420" }, table),
      h("div", { class: "t-micro", text: "PRICES AND REDUCTIONS APPLY TO BOTH CLIENTS IMMEDIATELY." })
    );
  }

  /* ---- scratch ------------------------------------------------------------ */
  function prizeDialog(ctx, prize) {
    let color = prize.color || SWATCHES[0];
    const label = h("input", { value: prize.label || "", "data-focus-key": "prizeLabel" });
    const type = h("select", {},
      ...[["coins", "Coins"], ["shield", "Shield"], ["lucky_coin", "Lucky Coin"], ["revive", "Revive"], ["none", "Nothing"]]
        .map(([v, l]) => h("option", { value: v, selected: (prize.type || "coins") === v, text: l }))
    );
    const value = h("input", { value: String(prize.value || ""), placeholder: "Amount or blank" });
    const weight = h("input", { type: "number", value: String(prize.weight || 1) });
    const enabled = h("input", { type: "checkbox" });
    enabled.checked = prize.enabled !== false;
    const swatches = h("div");
    const paint = () => swatches.replaceChildren(swatchRow(color, (c) => { color = c; paint(); }));
    paint();

    const body = h("div", { class: "stack" },
      field("Label", label),
      field("Type", type),
      field("Value", value),
      field("Weight", weight, "Higher weight = more likely."),
      field("Colour", swatches),
      h("div", { class: "row" }, h("label", { text: "Enabled" }), enabled)
    );

    openModal({
      title: prize.id ? "EDIT PRIZE" : "ADD PRIZE",
      body,
      wide: true,
      actions: [
        h("button", { class: "btn btn--ghost", text: "CANCEL", onclick: () => closeModal() }),
        h("button", {
          class: "btn", text: "SAVE",
          onclick: () => {
            if (!label.value.trim()) { toast("Give the prize a label.", true); return; }
            ctx.setScratchPrize({
              id: prize.id || "",
              label: label.value.trim(),
              type: type.value,
              value: value.value.trim(),
              weight: Math.max(0, parseFloat(weight.value) || 0),
              color,
              enabled: enabled.checked
            });
            closeModal();
          }
        })
      ]
    });
  }

  function adminScratch(ctx) {
    const prizes = ctx.state.scratchPrizes || [];
    const root = h("div", { class: "stack" });
    root.append(h("div", { class: "row row--between" },
      sectionLabel("Prize Pool"),
      h("button", { class: "btn btn--sm", text: "ADD PRIZE", onclick: () => prizeDialog(ctx, { type: "coins", weight: 1, color: SWATCHES[0], enabled: true }) })
    ));

    if (!prizes.length) {
      root.append(emptyStateCard("🎟", "NO PRIZES LOADED",
        "Scratch cards have nothing to give until a pool exists.",
        h("button", { class: "btn", text: "LOAD DEFAULT POOL", onclick: () => ctx.loadDefaultPrizes() })));
      return root;
    }

    const total = prizes.reduce((a, p) => a + ((p.weight || 0) > 0 ? p.weight : 0), 0);
    const list = h("div", { class: "stack stack--tight" });
    prizes.forEach((p) => {
      const chance = total > 0 ? ((p.weight || 0) / total) * 100 : 0;
      list.append(h("div", { class: "list-row static" },
        h("span", { style: { width: "12px", height: "12px", borderRadius: "50%", background: p.color || "var(--ink4)", flex: "none" } }),
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "row", style: { gap: "6px" } },
            h("div", { class: "t-card-title", style: { fontSize: "13px" }, text: p.label || "Untitled" }),
            p.enabled === false ? statusPill("OFF", "low") : null
          ),
          h("div", { class: "t-micro", text: `${String(p.type || "none").toUpperCase()} · ${p.value || "—"} · W ${p.weight || 0} · ${Math.round(chance)}%` })
        ),
        h("button", { class: "btn btn--ghost btn--sm", text: "EDIT", onclick: () => prizeDialog(ctx, p) }),
        h("button", {
          class: "btn btn--ember btn--sm", text: "DELETE",
          onclick: () => confirmDialog({
            title: "DELETE PRIZE?",
            message: `"${p.label}" will be removed from every scratch card.`,
            confirmText: "DELETE",
            destructive: true,
            onConfirm: () => ctx.deleteScratchPrize(p)
          })
        })
      ));
    });
    root.append(h("div", { class: "card" }, list));
    return root;
  }

  /* ---- sectors ------------------------------------------------------------ */
  function adminSectors(ctx) {
    const global = ctx.state.global || {};
    const games = global.games || [];
    const locked = global.lockedGames || [];
    const list = h("div", { class: "stack stack--tight" });

    games.forEach((sector, index) => {
      const isLocked = locked.indexOf(sector) >= 0;
      list.append(h("div", { class: "list-row static" },
        h("span", { class: "t-micro", text: String(index + 1).padStart(2, "0") }),
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-card-title", text: sector }),
          h("div", { class: "t-body", text: isLocked ? "Locked — deaths cannot be recorded here" : "Active" })
        ),
        statusPill(isLocked ? "LOCKED" : "ACTIVE", isLocked ? null : "toxic"),
        h("button", { class: "btn btn--ghost btn--sm", text: "↑", title: "Move up", onclick: () => ctx.moveSector(sector, -1), disabled: index === 0 }),
        h("button", { class: "btn btn--ghost btn--sm", text: "↓", title: "Move down", onclick: () => ctx.moveSector(sector, 1), disabled: index === games.length - 1 }),
        h("button", {
          class: "btn btn--ghost btn--sm", text: "RENAME",
          onclick: () => textPrompt("RENAME SECTOR", "Sector name", sector, (next) => ctx.renameSector(sector, next))
        }),
        h("button", { class: "btn btn--ghost btn--sm", text: isLocked ? "UNLOCK" : "LOCK", onclick: () => ctx.toggleSectorLock(sector) }),
        h("button", {
          class: "btn btn--ember btn--sm", text: "REMOVE",
          onclick: () => confirmDialog({
            title: "REMOVE SECTOR?",
            message: `"${sector}" will disappear from both clients. Player death records are kept.`,
            confirmText: "REMOVE",
            destructive: true,
            onConfirm: () => ctx.removeSector(sector)
          })
        })
      ));
    });

    const newSector = h("input", {
      value: L(ctx, "newSector", ""),
      placeholder: "New sector designation",
      "data-focus-key": "newSector",
      oninput: (e) => ctx.local("newSector", e.target.value)
    });

    return h("div", { class: "stack" },
      h("div", { class: "row", style: { maxWidth: "520px", alignItems: "flex-end" } },
        h("div", { style: { flex: "1" } }, field("Add a sector", newSector)),
        h("button", {
          class: "btn", text: "ADD",
          onclick: () => {
            const name = (ctx.local("newSector") || "").trim();
            if (!name) { toast("Type a sector name first.", true); return; }
            if (games.some((g) => g.toLowerCase() === name.toLowerCase())) { toast("That sector already exists.", true); return; }
            ctx.local("newSector", "");
            ctx.setGlobal({ games: games.concat([name]) });
          }
        })
      ),
      sectionLabel(`Sectors · ${games.length}`),
      list
    );
  }

  /* ---- event -------------------------------------------------------------- */
  function toLocalInput(ms) {
    if (!ms) return "";
    const d = new Date(ms);
    const two = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())}T${two(d.getHours())}:${two(d.getMinutes())}`;
  }

  function adminEvent(ctx) {
    const ev = world(ctx);
    const start = h("input", { type: "datetime-local", value: toLocalInput(ev.nextEventStartsAt || 0) });
    start.addEventListener("change", () => {
      const ms = start.value ? new Date(start.value).getTime() : 0;
      ctx.setGlobal({ nextEventStartsAt: isFinite(ms) ? ms : 0 });
    });

    const name = h("input", { value: ev.nextEventName || "", onchange: (e) => ctx.setGlobal({ nextEventName: e.target.value }) });
    const notes = h("textarea", { onchange: (e) => ctx.setGlobal({ nextEventNotes: e.target.value }) }, ev.nextEventNotes || "");

    return h("div", { class: "stack" },
      sectionLabel("Next Dead-Fest"),
      h("div", { class: "card stack" },
        field("Event name", name),
        field("Starts at", start, ev.nextEventStartsAt ? eventTimeLabel(ev.nextEventStartsAt) : "No time set yet."),
        field("Notes", notes),
        h("div", { class: "row row--between" },
          h("div", { class: "t-body", text: ev.nextEventEnabled ? "The intel panel is visible to every survivor." : "The intel panel is hidden." }),
          h("button", {
            class: "btn btn--sm",
            text: ev.nextEventEnabled ? "DISABLE PANEL" : "ENABLE PANEL",
            onclick: () => ctx.setGlobal({ nextEventEnabled: !ev.nextEventEnabled })
          })
        )
      ),
      sectionLabel("Game Line-Up"),
      h("div", { class: "card" },
        h("div", { class: "t-body", text: (ev.nextEventGames || []).length ? (ev.nextEventGames || []).join(" · ") : "No games queued." }),
        h("div", { class: "row row--wrap", style: { marginTop: "var(--sm)" } },
          (ev.games || []).map((g) => h("button", {
            class: "chip" + ((ev.nextEventGames || []).indexOf(g) >= 0 ? " on" : ""),
            text: ((ev.nextEventGames || []).indexOf(g) >= 0 ? "✓ " : "+ ") + g,
            onclick: () => ctx.queueEventGame(g)
          }))
        )
      )
    );
  }

  /* ---- overload ----------------------------------------------------------- */
  function adminOverload(ctx) {
    const global = ctx.state.global || {};
    const games = (global.games || []).filter((g) => (global.lockedGames || []).indexOf(g) < 0);
    const multiplier = h("input", { type: "number", value: String(global.multiplier || 2), min: "1", step: "0.1" });
    const duration = h("select", {},
      h("option", { value: "0", text: "No time limit" }),
      h("option", { value: "3600000", text: "1 hour" }),
      h("option", { value: "21600000", text: "6 hours" }),
      h("option", { value: "86400000", text: "24 hours" })
    );

    function start(sector) {
      const hours = parseInt(duration.value, 10) || 0;
      ctx.setGlobal({
        sectorOverload: true,
        overloadedSector: sector,
        multiplier: Math.max(1, parseFloat(multiplier.value) || 2),
        overloadEndsAt: hours > 0 ? Date.now() + hours : 0
      });
    }

    return h("div", { class: "stack" },
      sectionLabel("Overload Status"),
      h("div", { class: "card" },
        global.sectorOverload
          ? h("div", { class: "stack" },
              h("div", { class: "t-card-title amber", text: `OVERLOAD ACTIVE: ${String(global.overloadedSector || "").toUpperCase()} ×${global.multiplier}` }),
              h("div", { class: "t-body", text: global.overloadEndsAt ? `Ends ${eventTimeLabel(global.overloadEndsAt)} · ${overloadRemaining(global.overloadEndsAt)}` : "No time limit set." }),
              h("button", {
                class: "btn btn--ember btn--sm", style: { marginTop: "var(--sm)" }, text: "TERMINATE",
                onclick: () => ctx.setGlobal({ sectorOverload: false, overloadedSector: "", overloadEndsAt: 0 })
              })
            )
          : h("div", { class: "t-body", text: "No sector is overloaded." })
      ),
      sectionLabel("Initiate Overload"),
      h("div", { class: "card stack" },
        h("div", { class: "row row--wrap" },
          field("Multiplier", multiplier, "Applied to survival payouts in the sector."),
          field("Duration", duration)
        ),
        h("div", { class: "row row--wrap" },
          games.length
            ? games.map((g) => h("button", { class: "chip", text: g, onclick: () => start(g) }))
            : h("div", { class: "t-body", text: "Every sector is locked." })
        )
      )
    );
  }

  /* ---- wheel -------------------------------------------------------------- */
  function sliceDialog(ctx, slice) {
    let color = slice.color || SWATCHES[0];
    const label = h("input", { value: slice.label || "", "data-focus-key": "sliceLabel" });
    const value = h("input", { value: slice.value || "", placeholder: "Coins, or blank" });
    const action = h("select", {},
      h("option", { value: "give", selected: (slice.actionType || "give") === "give", text: "Give coins" }),
      h("option", { value: "take", selected: slice.actionType === "take", text: "Take coins" })
    );
    const weight = h("input", { type: "number", value: String(slice.weight || 1) });
    const enabled = h("input", { type: "checkbox" });
    enabled.checked = slice.enabled !== false;
    const swatches = h("div");
    const paint = () => swatches.replaceChildren(swatchRow(color, (c) => { color = c; paint(); }));
    paint();

    openModal({
      title: slice.id ? "EDIT SLICE" : "ADD SLICE",
      body: h("div", { class: "stack" },
        field("Label", label),
        field("Value", value, "Leave blank for a prize with no coin value."),
        field("Action", action),
        field("Weight", weight, "Higher weight = a bigger slice."),
        field("Colour", swatches),
        h("div", { class: "row" }, h("label", { text: "Enabled" }), enabled)
      ),
      wide: true,
      actions: [
        h("button", { class: "btn btn--ghost", text: "CANCEL", onclick: () => closeModal() }),
        h("button", {
          class: "btn", text: "SAVE",
          onclick: () => {
            if (!label.value.trim()) { toast("Give the slice a label.", true); return; }
            ctx.saveWheelSlice({
              id: slice.id || "",
              label: label.value.trim(),
              value: value.value.trim(),
              actionType: action.value,
              weight: Math.max(0, parseFloat(weight.value) || 0),
              color,
              enabled: enabled.checked
            });
            closeModal();
          }
        })
      ]
    });
  }

  function adminWheel(ctx) {
    const global = ctx.state.global || {};
    const items = global.wheelItems || [];
    const list = h("div", { class: "stack stack--tight" });
    items.forEach((item) => {
      list.append(h("div", { class: "list-row static" },
        h("span", { style: { width: "14px", height: "14px", borderRadius: "50%", background: item.color, flex: "none" } }),
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-card-title", text: item.label }),
          h("div", { class: "t-body", text: `${item.actionType === "take" ? "TAKE" : "GIVE"} ${item.value || "—"} · WEIGHT ${item.weight}` })
        ),
        h("div", { class: "switch" + (item.enabled !== false ? " on" : ""), onclick: () => ctx.updateWheelItem(item.id, { enabled: item.enabled === false }) }),
        h("button", { class: "btn btn--ghost btn--sm", text: "EDIT", onclick: () => sliceDialog(ctx, item) }),
        h("button", {
          class: "btn btn--ember btn--sm", text: "DELETE",
          onclick: () => confirmDialog({
            title: "DELETE SLICE?",
            message: `"${item.label}" will be removed from the wheel.`,
            confirmText: "DELETE",
            destructive: true,
            onConfirm: () => ctx.deleteWheelSlice(item.id)
          })
        })
      ));
    });

    return h("div", { class: "stack" },
      h("div", { class: "row row--between" },
        sectionLabel("Wheel Slices"),
        h("button", { class: "btn btn--sm", text: "ADD SLICE", onclick: () => sliceDialog(ctx, { actionType: "give", weight: 1, color: SWATCHES[0], enabled: true }) })
      ),
      items.length ? list : emptyStateCard("🎡", "WHEEL IS EMPTY", "Add slices before survivors can spin.")
    );
  }

  /* ---- requests ----------------------------------------------------------- */
  function adminRequests(ctx) {
    const requests = ctx.state.gameRequests || [];
    const pending = requests.filter((r) => r.isApproved !== true);
    const approved = requests.filter((r) => r.isApproved === true);

    const rows = (list, isApproved) => h("div", { class: "stack stack--tight" },
      list.length ? list.map((r) => h("div", { class: "list-row static" },
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "t-card-title", text: r.name || "Untitled" }),
          h("div", { class: "t-body", text: "Asked by " + (r.requestedByName || "Survivor") + (r.decidedByName ? " · decided by " + r.decidedByName : "") })
        ),
        statusPill(isApproved ? "APPROVED" : "PENDING", isApproved ? "toxic" : "amber"),
        isApproved ? null : h("button", { class: "btn btn--sm", text: "APPROVE", onclick: () => ctx.approveRequest(r.id) }),
        h("button", { class: "btn btn--ember btn--sm", text: "REMOVE", onclick: () => ctx.removeRequest(r.id) })
      )) : h("div", { class: "t-body", text: isApproved ? "Nothing approved yet." : "The queue is clear." })
    );

    return h("div", { class: "stack" },
      sectionLabel(`Pending Requests · ${pending.length}`),
      rows(pending, false),
      sectionLabel(`Approved Games · ${approved.length}`),
      rows(approved, true)
    );
  }

  /* ---- global ------------------------------------------------------------- */
  function adminGlobal(ctx) {
    const global = ctx.state.global || {};
    const economy = cfg(ctx);
    const sw = (title, subtitle, checked, onChange, danger) => h("div", { class: "row", style: { padding: "var(--xs) 0" } },
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "t-card-title", style: { fontSize: "13px", color: danger && checked ? "var(--ember)" : null }, text: title }),
        h("div", { class: "t-body", text: subtitle })
      ),
      h("div", { class: "switch" + (checked ? " on" : ""), onclick: onChange })
    );

    const xp = economy.xp;
    const xpRow = (label, key, value) => h("div", { class: "row row--between", style: { padding: "6px 0" } },
      h("div", { class: "t-body", text: label }),
      numberInput(value, (v) => ctx.setXpConfig(key, Math.max(0, v)), "100px")
    );

    return h("div", { class: "stack", style: { gap: "var(--md)" } },
      sectionLabel("System Switches"),
      h("div", { class: "card" },
        sw("Black Market access", "Enable or disable shop purchases", global.marketEnabled !== false, () => ctx.setGlobal({ marketEnabled: global.marketEnabled === false })),
        h("div", { class: "divider" }),
        sw("Wheel of Fate", "Enable or disable wheel spinning", global.wheelEnabled !== false, () => ctx.setGlobal({ wheelEnabled: global.wheelEnabled === false })),
        h("div", { class: "divider" }),
        sw("Revives", "Allow survivors to buy Revives", global.reviveEnabled !== false, () => ctx.setGlobal({ reviveEnabled: global.reviveEnabled === false })),
        h("div", { class: "divider" }),
        sw("Scratch cards", "Enable the scratch card screen and shop entry", economy.scratchEnabled !== false, () => ctx.setEconomyValue("scratchEnabled", economy.scratchEnabled === false)),
        h("div", { class: "divider" }),
        sw("Insulation", "Allow wealth insulation purchases", economy.insulationEnabled !== false, () => ctx.setEconomyValue("insulationEnabled", economy.insulationEnabled === false)),
        h("div", { class: "divider" }),
        sw("Lockdown protocol", "Freeze core features and death recording", global.lockdownActive === true, () => ctx.setGlobal({ lockdownActive: global.lockdownActive !== true }), true),
        h("div", { class: "divider" }),
        sw("Forced Spin Protocol", "Every death adds a penalty spin to the Wheel of Fate", global.forceSpinProtocol !== false, () => ctx.setGlobal({ forceSpinProtocol: global.forceSpinProtocol === false }))
      ),

      sectionLabel("Economy"),
      h("div", { class: "card" },
        h("div", { class: "row row--between", style: { padding: "6px 0" } },
          h("div", { class: "t-body", text: "Daily base ration" }),
          numberInput(economy.dailyBase, (v) => ctx.setEconomyValue("dailyBase", Math.max(0, Math.round(v))), "110px")
        ),
        h("div", { class: "row row--between", style: { padding: "6px 0" } },
          h("div", { class: "t-body", text: "Upgraded daily ration" }),
          numberInput(economy.dailyUpgraded, (v) => ctx.setEconomyValue("dailyUpgraded", Math.max(0, Math.round(v))), "110px")
        ),
        h("div", { class: "row row--between", style: { padding: "6px 0" } },
          h("div", { class: "t-body", text: "Mutual Survival Reward" }),
          numberInput(economy.survivalReward, (v) => ctx.setEconomyValue("survivalReward", Math.max(0, Math.round(v))), "110px")
        ),
        h("div", { class: "row row--between", style: { padding: "6px 0" } },
          h("div", { class: "t-body", text: "Insurance cover (%)" }),
          numberInput(economy.insurancePct, (v) => ctx.setEconomyValue("insurancePct", Math.max(0, Math.min(100, Math.round(v)))), "110px")
        ),
        h("div", { class: "row row--between", style: { padding: "6px 0" } },
          h("div", { class: "t-body", text: "Scratch card cost" }),
          numberInput(economy.scratchCost, (v) => ctx.setEconomyValue("scratchCost", Math.max(0, Math.round(v))), "110px")
        )
      ),

      sectionLabel("XP Configuration"),
      h("div", { class: "card" },
        xpRow("Logging a death", "death", xp.death),
        xpRow("Surviving a full day", "survivalDay", xp.survivalDay),
        xpRow("Black Market purchase", "purchase", xp.purchase),
        xpRow("Wheel spin", "spin", xp.spin),
        xpRow("Scratch card", "scratch", xp.scratch),
        xpRow("Penalty spin", "penaltySpin", xp.penaltySpin),
        h("hr", { class: "rule" }),
        xpRow("Curve base", "curveBase", xp.curveBase),
        h("div", { class: "row row--between", style: { padding: "6px 0" } },
          h("div", { class: "t-body", text: "Curve exponent" }),
          numberInput(xp.curveExponent, (v) => ctx.setXpConfig("curveExponent", Math.max(1, v)), "100px")
        ),
        h("div", { class: "row row--between", style: { padding: "6px 0" } },
          h("div", { class: "t-body", text: "Max level" }),
          numberInput(xp.maxLevel, (v) => ctx.setXpConfig("maxLevel", Math.max(2, Math.round(v))), "100px")
        )
      ),

      sectionLabel("Publish Firmware"),
      h("div", { class: "card" },
        h("div", { class: "t-body", text: `Live: v${global.latestVersionName || "—"} (build ${global.latestVersionCode || 0})` }),
        h("button", { class: "btn btn--sm", style: { marginTop: "var(--sm)" }, text: "PUBLISH FIRMWARE", onclick: () => ctx.openFirmwareDialog() })
      )
    );
  }

  /* ---- backup ------------------------------------------------------------- */
  function adminBackup(ctx) {
    const archives = ctx.state.archives || [];
    const nameInput = h("input", {
      value: L(ctx, "archiveName", ""),
      placeholder: "Season 3 — The Long Dark",
      "data-focus-key": "archiveName",
      oninput: (e) => ctx.local("archiveName", e.target.value)
    });

    return h("div", { class: "stack" },
      sectionLabel("Archive A Season"),
      h("div", { class: "card stack" },
        field("Season name", nameInput),
        h("div", { class: "t-code", style: { fontSize: "11px", color: "var(--ember)" },
          text: "Archiving snapshots every survivor, then resets coins to " + fmt(DF.STARTING_COINS) + ", clears penalty spins and zeroes every sector." }),
        h("div", null, h("button", {
          class: "btn", text: "ARCHIVE AND RESET",
          onclick: () => {
            const name = (ctx.local("archiveName") || "").trim();
            if (!name) { toast("Name the season first.", true); return; }
            confirmDialog({
              title: "ARCHIVE THIS SEASON?",
              message: `"${name}" will be saved, then every survivor's coins reset to ${fmt(DF.STARTING_COINS)} and all deaths to zero. This cannot be undone.`,
              confirmText: "ARCHIVE",
              destructive: true,
              onConfirm: () => ctx.createArchive()
            });
          }
        }))
      ),
      sectionLabel(`Past Seasons · ${archives.length}`),
      h("div", { class: "card" },
        archives.length
          ? h("div", { class: "stack stack--tight" }, archives.map((a) => h("div", { class: "row" },
              h("div", { style: { flex: "1", minWidth: "0" } },
                h("div", { class: "t-card-title", text: a.name }),
                h("div", { class: "t-micro", text: `${savedLabel(a.timestamp)} · ${fmt(a.totalDeaths)} DEATHS · ${fmt(a.totalCoins)} COINS SAVED` })
              ),
              h("button", {
                class: "btn btn--ghost btn--sm", text: "RESTORE",
                onclick: () => ctx.restoreArchive(a)
              })
            )))
          : h("div", { class: "t-body", text: "No seasons archived." })
      )
    );
  }

  /* ---- broadcast ---------------------------------------------------------- */
  function adminBroadcast(ctx) {
    const MAX = 280;
    const text = L(ctx, "broadcastText", "");
    const area = h("textarea", {
      maxLength: String(MAX),
      placeholder: "What does the wasteland need to know?",
      "data-focus-key": "broadcast",
      oninput: (e) => {
        ctx.local("broadcastText", e.target.value);
        counter.textContent = `${e.target.value.length}/${MAX}`;
      }
    }, text);
    const counter = h("div", { class: "hint", text: `${text.length}/${MAX}` });

    return h("div", { class: "stack" },
      sectionLabel("Broadcast"),
      h("div", { class: "card stack" },
        field("Message", area, "Lands in every survivor's live feed. Max 280 characters."),
        counter,
        h("div", null, h("button", {
          class: "btn", text: "SEND BROADCAST",
          onclick: () => {
            const message = (ctx.local("broadcastText") || "").trim();
            if (!message) { toast("Write something first.", true); return; }
            if (message.length > MAX) { toast(`Keep it under ${MAX} characters.`, true); return; }
            ctx.local("broadcastText", "");
            ctx.sendBroadcast();
          }
        }))
      ),
      sectionLabel("Recent Transmissions"),
      h("div", { class: "card stack stack--tight" },
        (ctx.state.events || []).filter((e) => e.category === "WASTELAND_ALERT").slice(0, 8).map((e) => h("div", { class: "row" },
          h("div", { style: { flex: "1", minWidth: "0" }, class: "t-body", text: e.message }),
          h("div", { class: "t-micro", "data-live": "ago", "data-at": String(eventMillis(e.timestamp)), text: timeAgo(eventMillis(e.timestamp)) })
        )),
        (ctx.state.events || []).filter((e) => e.category === "WASTELAND_ALERT").length
          ? null
          : h("div", { class: "t-body", text: "No broadcasts yet." })
      )
    );
  }
})();
