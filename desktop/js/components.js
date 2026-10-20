/* =============================================================================
   Dead-Fest desktop — shared components
   -----------------------------------------------------------------------------
   A port of ui/components/Common.kt. Every screen is built from these, so a
   status pill, a hero panel or a stat tile looks identical wherever it appears —
   which is exactly how the phone app keeps its five themes coherent.
   ========================================================================== */
window.DF = window.DF || {};

/* ---- currency ------------------------------------------------------------- */
function coinIcon(size) {
  const px = size || 16;
  return h("span", {
    class: "coin",
    style: { width: px + "px", height: px + "px", fontSize: Math.round(px * 0.6) + "px" },
    text: "$"
  });
}

function coinBadge(coins) {
  return h("span", { class: "coin-badge", "aria-label": fmt(coins) + " coins" },
    coinIcon(16),
    h("span", { text: fmt(coins) })
  );
}

function priceTag(price, affordable) {
  return h("span", {
    class: "price-tag" + (affordable === false ? " price-tag--off" : ""),
    "aria-label": fmt(price) + " coins"
  }, coinIcon(14), h("span", { text: fmt(price) }));
}

/* ---- badges --------------------------------------------------------------- */
function levelBadge(level, prestige) {
  const wrap = h("span", { class: "row", style: { gap: "4px" } },
    h("span", { class: "level-badge", text: "LV " + level })
  );
  if (prestige > 0) wrap.append(h("span", { class: "prestige-badge", text: "★ P" + prestige }));
  return wrap;
}

function rankBadge(rank) {
  if (!rank) return null;
  if (rank <= 3) return h("div", { class: "rank rank--" + rank, text: String(rank) });
  return h("div", { class: "rank rank--plain", text: "#" + rank });
}

function pendingSpinsChip(count) {
  return h("div", { class: "pending-chip" },
    h("div", { class: "pending-chip-n", text: String(count) }),
    h("div", { class: "pending-chip-l", text: "PENDING" }),
    h("div", { class: "pending-chip-l", text: "SPINS" })
  );
}

/* ---- experience ----------------------------------------------------------- */
function xpBar(xp, level, cfg) {
  const config = cfg || DF.Economy.DEFAULT_XP;
  const maxed = level >= (config.maxLevel || 50);
  const need = DF.Economy.xpForLevel(level, config);
  const progress = DF.Economy.xpProgress(xp, level, config);
  return h("div", { class: "stack stack--tight", style: { gap: "4px" } },
    h("div", { class: "row row--between" },
      h("div", { class: "t-micro", text: "XP" }),
      h("div", { class: "t-micro", style: { color: maxed ? "var(--amber)" : "var(--text-mid)" }, text: maxed ? "MAX LEVEL · PRESTIGE READY" : `${fmt(xp)} / ${fmt(need)}` })
    ),
    h("div", { class: "bar", role: "progressbar", "aria-valuenow": String(Math.round(progress * 100)), "aria-valuemin": "0", "aria-valuemax": "100" },
      h("i", { style: { width: Math.round(progress * 100) + "%", background: maxed ? "var(--amber)" : "var(--ember)" } })
    )
  );
}

/* ---- layout primitives ---------------------------------------------------- */
function miniStat(label, value, color) {
  return h("div", { class: "mini" },
    h("div", { class: "mini-k", text: label }),
    h("div", { class: "mini-v", style: color ? { color } : null, text: value })
  );
}

function surfaceCard(children, opts) {
  const o = opts || {};
  const props = {
    class: "card" + (o.accent ? " card--accent-" + o.accent : "") + (o.onclick ? " card--click" : ""),
    style: Object.assign({ padding: o.padding || "var(--md)" }, o.style || {})
  };
  if (o.onclick) props.onclick = o.onclick;
  return h("div", props, ...(Array.isArray(children) ? children : [children]));
}

function heroPanel(opts) {
  const accent = opts.accent || "var(--ember)";
  const wrap = h("div", {
    class: "hero" + (opts.onclick ? " card--click" : ""),
    style: Object.assign({ borderColor: "color-mix(in srgb, " + accent + " 45%, transparent)", background: "linear-gradient(160deg, color-mix(in srgb, " + accent + " 16%, transparent) 0%, color-mix(in srgb, " + accent + " 2%, transparent) 45%, transparent 100%), var(--ink2)" }, opts.style || {}),
    onclick: opts.onclick
  },
    opts.icon ? h("div", { class: "hero-mark", text: opts.icon }) : null,
    h("div", { class: "row", style: { gap: "var(--sm)", alignItems: "flex-start" } },
      opts.icon ? h("div", { class: "tile", style: { "--tint": accent }, text: typeof opts.glyph === "string" ? opts.glyph : "" }) : null,
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "hero-eyebrow", style: { color: accent }, text: String(opts.eyebrow || "").toUpperCase() }),
        h("div", { class: "hero-title", text: opts.title })
      ),
      opts.badge || null
    ),
    opts.children || null
  );
  return wrap;
}

function alertBanner(icon, title, message, accent, onclick) {
  const tint = accent || "var(--ember)";
  return h("div", {
    class: "banner card--click",
    style: { borderColor: "color-mix(in srgb, " + tint + " 70%, transparent)", background: "linear-gradient(90deg, color-mix(in srgb, " + tint + " 10%, transparent), transparent 70%), var(--ink2)", cursor: onclick ? "pointer" : "default" },
    onclick
  },
    h("div", { class: "banner-ico", style: { color: tint }, text: icon }),
    h("div", { style: { flex: "1", minWidth: "0" } },
      h("div", { class: "t-card-title", style: { fontSize: "14px" }, text: title }),
      h("div", { class: "t-body", text: message })
    )
  );
}

function noticeCard(icon, title, message) {
  return h("div", { class: "notice" },
    h("div", { class: "notice-ico", text: icon }),
    h("div", { class: "notice-title", text: String(title).toUpperCase() }),
    h("div", { class: "t-body", style: { textAlign: "center" }, text: message })
  );
}

function emptyStateCard(icon, title, message, action) {
  return h("div", { class: "empty-card" },
    h("div", { class: "empty-ico", text: icon }),
    h("div", { class: "t-card-title", text: title }),
    message ? h("div", { class: "t-body", style: { textAlign: "center" }, text: message }) : null,
    action || null
  );
}

function pillChip(text, selected, onclick, accent) {
  const tint = accent || "var(--ember)";
  return h("button", {
    class: "chip" + (selected ? " chip--on" : ""),
    type: "button",
    style: selected ? { background: "color-mix(in srgb, " + tint + " 18%, transparent)", borderColor: "color-mix(in srgb, " + tint + " 70%, transparent)", color: tint } : null,
    "aria-pressed": selected ? "true" : "false",
    text: String(text).toUpperCase(),
    onclick
  });
}

function primaryAction(text, onclick, enabled, accent) {
  return h("button", {
    class: "btn btn--full btn--tall" + (enabled === false ? " btn--off" : ""),
    style: enabled === false ? null : (accent ? { background: accent } : null),
    disabled: enabled === false,
    text: String(text).toUpperCase(),
    onclick: enabled === false ? null : onclick
  });
}

function ghostAction(text, onclick, accent, enabled) {
  const tint = accent || "var(--text-mid)";
  return h("button", {
    class: "btn btn--ghost",
    style: { borderColor: "color-mix(in srgb, " + tint + " 45%, transparent)", color: tint },
    disabled: enabled === false,
    text: String(text).toUpperCase(),
    onclick: enabled === false ? null : onclick
  });
}

function itemEmblem(icon, accent, size) {
  const px = size || 54;
  const tint = accent || "var(--ember)";
  return h("div", {
    class: "hex",
    style: {
      width: px + "px", height: px + "px",
      background: "linear-gradient(180deg, color-mix(in srgb, " + tint + " 34%, transparent), color-mix(in srgb, " + tint + " 6%, transparent)), var(--ink3)",
      borderColor: "color-mix(in srgb, " + tint + " 70%, transparent)",
      fontSize: Math.round(px * 0.44) + "px",
      color: tint
    },
    text: icon
  });
}

function proportionBar(fraction, accent) {
  const pct = Math.max(0, Math.min(1, fraction || 0)) * 100;
  return h("div", { class: "prop" },
    h("i", { style: { width: pct + "%", background: accent || "var(--ember)" } })
  );
}

/* ---- penalty card (Common.kt PenaltyStatusCard) --------------------------- */
function penaltyStatusCard(player, onSpin) {
  const level = player.bankruptcyLevel || 0;
  const spins = player.pendingSpins || 0;
  if (level <= 0 && spins <= 0) return null;

  let title, message, accent, critical = false;
  if (level >= 2) {
    title = "BANKRUPTCY LEVEL 2";
    message = "Clear every pending spin and get your balance above 0 to escape. Survival payouts are halved.";
    accent = "var(--ember)";
    critical = true;
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
    style: {
      borderColor: "color-mix(in srgb, " + accent + " " + (critical ? "100" : "70") + "%, transparent)",
      borderWidth: critical ? "1.5px" : "1px",
      background: "linear-gradient(90deg, color-mix(in srgb, " + accent + " " + (critical ? "24" : "13") + "%, transparent), transparent 70%), var(--ink2)",
      cursor: "pointer"
    },
    onclick: onSpin
  },
    h("div", { class: "row" },
      h("div", { class: "tile", style: { "--tint": accent }, text: level > 0 ? "💸" : "🎰" }),
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "t-card-title", style: { color: critical ? accent : "var(--text-hi)" }, text: title }),
        h("div", { class: "t-body", text: message })
      ),
      spins > 0 ? pendingSpinsChip(spins) : null
    )
  );
}

/* ---- countdown (Common.kt CountdownTiles / NextEventHero) ----------------- */
function countdownTiles(target, showSeconds) {
  const cells = [
    { kind: "cd-days", label: "DAYS" },
    { kind: "cd-hrs", label: "HRS" },
    { kind: "cd-min", label: "MIN" }
  ];
  if (showSeconds !== false) cells.push({ kind: "cd-sec", label: "SEC", sec: true });
  return h("div", { class: "cd" }, ...cells.map((c) => h("div", { class: "cd-cell" + (c.sec ? " cd-cell--sec" : "") },
    h("div", { class: "cd-num" + (c.sec ? " amber" : ""), "data-live": c.kind, "data-at": String(target), text: "00" }),
    h("div", { class: "cd-lab", text: c.label })
  )));
}

function countdownStatusPill(target, now) {
  if (!target || target <= 0) return statusPill("TBA", "low");
  if ((now || Date.now()) >= target) return statusPill("LIVE NOW", "toxic");
  return statusPill("UPCOMING", "amber");
}

function eventTimeLabel(ms) {
  if (!ms || ms <= 0) return "Date to be announced";
  try {
    return new Date(ms).toLocaleString(undefined, { weekday: "short", day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
  } catch (e) {
    return new Date(ms).toISOString().slice(0, 16).replace("T", " ");
  }
}

function nextEventHero(global, now) {
  const target = global.nextEventStartsAt || 0;
  const live = target > 0 && (now || Date.now()) >= target;
  const season = (global.nextEventName || "").trim() || "NEXT DEAD-FEST";
  const children = h("div", { class: "stack", style: { marginTop: "var(--sm)" } });

  if (target <= 0) {
    children.append(h("div", { class: "t-body", text: "Start time hasn't been locked in yet." }));
  } else if (live) {
    children.append(h("div", { class: "t-body toxic", style: { fontWeight: "700", fontSize: "14px" }, text: "It's on. Good luck out there." }));
  } else {
    children.append(countdownTiles(target, !DF.Prefs.get("performanceMode")));
  }
  if (target > 0) {
    children.append(h("div", { class: "row", style: { gap: "6px", color: "var(--text-mid)" } },
      h("span", { text: "🗓" }),
      h("div", { class: "t-body", text: (live ? "Started " : "Starts ") + eventTimeLabel(target) })
    ));
  }
  if (global.nextEventGames && global.nextEventGames.length) {
    children.append(h("div", { class: "row", style: { gap: "6px" } },
      h("span", { class: "ember", text: "🎮" }),
      h("div", { class: "t-micro", text: "IN THE LINE-UP" })
    ));
    children.append(h("div", { class: "row row--wrap", style: { gap: "6px" } },
      ...global.nextEventGames.map((g) => h("span", { class: "lineup-pill", text: String(g).toUpperCase() }))
    ));
  }
  if ((global.nextEventNotes || "").trim()) {
    children.append(h("hr", { class: "rule" }));
    children.append(h("div", { class: "t-body", text: global.nextEventNotes }));
  }

  return heroPanel({
    eyebrow: "Next Dead-Fest",
    title: String(season).toUpperCase(),
    icon: "🎉",
    glyph: "🎉",
    accent: "var(--amber)",
    badge: countdownStatusPill(target, now),
    children
  });
}

/* ---- image viewer (ImagesScreen.kt ImageViewer) ---------------------------
   Full-screen, pinch/wheel zoom 1×–5×, drag to pan, reset when back at 1×.
   No swipe between images and no download button, matching the phone. */
DF.Viewer = (function () {
  let root = null;
  let state = null;

  function ensure() {
    if (root) return root;
    root = h("div", { id: "viewer" });
    document.body.append(root);
    return root;
  }

  function close() {
    if (!root) return;
    root.classList.remove("open");
    root.replaceChildren();
    state = null;
  }

  function open(image, ctx) {
    const host = ensure();
    const me = DF.Data.state.user;
    const canDelete = me && (image.uploaderUid === me.uid || me.admin === true);
    const scale = { value: 1, x: 0, y: 0 };
    const img = h("img", { alt: image.caption || "Shared image", draggable: "false" });
    const stage = h("div", { class: "viewer-stage" }, img);
    const loading = h("div", { class: "scanning" },
      h("div", { class: "spinner" }),
      h("div", { class: "t-micro", text: "DECRYPTING IMAGE" })
    );

    function apply() {
      img.style.transform = `translate(${scale.x}px, ${scale.y}px) scale(${scale.value})`;
    }

    function reset() { scale.value = 1; scale.x = 0; scale.y = 0; apply(); }

    stage.addEventListener("wheel", (e) => {
      e.preventDefault();
      const next = Math.max(1, Math.min(5, scale.value * (e.deltaY < 0 ? 1.12 : 0.89)));
      scale.value = next;
      if (next === 1) { scale.x = 0; scale.y = 0; }
      apply();
    }, { passive: false });

    let dragging = false;
    let startX = 0, startY = 0, baseX = 0, baseY = 0;
    stage.addEventListener("pointerdown", (e) => {
      if (scale.value <= 1) return;
      dragging = true;
      startX = e.clientX; startY = e.clientY;
      baseX = scale.x; baseY = scale.y;
      stage.setPointerCapture(e.pointerId);
      stage.style.cursor = "grabbing";
    });
    stage.addEventListener("pointermove", (e) => {
      if (!dragging) return;
      scale.x = baseX + (e.clientX - startX);
      scale.y = baseY + (e.clientY - startY);
      apply();
    });
    const stopDrag = () => { dragging = false; stage.style.cursor = "grab"; };
    stage.addEventListener("pointerup", stopDrag);
    stage.addEventListener("pointercancel", stopDrag);
    stage.addEventListener("dblclick", reset);

    const bar = h("div", { class: "viewer-bar" },
      h("button", { class: "btn btn--ghost btn--sm", text: "CLOSE", "aria-label": "Close", onclick: close }),
      h("div", { style: { flex: "1", minWidth: "0" } },
        h("div", { class: "t-card-title", text: image.uploaderName }),
        h("div", { class: "t-micro", "data-live": "since", "data-at": String(image.createdAt), text: DF.Presence.lastSeenLabel(image.createdAt) + ` · ${image.width}×${image.height}` })
      ),
      canDelete ? h("button", {
        class: "btn btn--ghost btn--sm ember",
        text: "DELETE",
        "aria-label": "Delete image by " + image.uploaderName,
        onclick: () => {
          confirmDialog({
            title: "DELETE IMAGE?",
            message: "This removes it for every survivor.",
            confirmText: "DELETE",
            destructive: true,
            onConfirm: () => {
              DF.Data.deleteImage(image.id).then(() => {
                toast("Image deleted");
                close();
              }).catch((e) => toast("Couldn't delete image: " + DF.App.friendlyError(e), true));
            }
          });
        }
      }) : null
    );

    const foot = image.caption
      ? h("div", { class: "viewer-foot", text: image.caption })
      : null;

    host.replaceChildren(bar, stage, loading);
    if (foot) host.append(foot);
    host.classList.add("open");

    const thumbUrl = DF.Media.objectUrl("thumb-" + image.id, image.thumb, "image/jpeg");
    img.src = thumbUrl;
    apply();

    DF.Data.getImageData(image.id).then((bytes) => {
      loading.remove();
      if (!bytes) {
        stage.replaceChildren(h("div", { class: "scanning" },
          h("div", { class: "t-card-title", text: "SIGNAL LOST" }),
          h("div", { class: "t-body", text: "Couldn't load the full image. Check your connection." })
        ));
        return;
      }
      img.src = DF.Media.objectUrl("full-" + image.id, bytes, "image/jpeg");
      apply();
    }).catch((e) => {
      loading.remove();
      stage.replaceChildren(h("div", { class: "scanning" },
        h("div", { class: "t-card-title", text: "SIGNAL LOST" }),
        h("div", { class: "t-body", text: "Couldn't load the full image. Check your connection." })
      ));
    });
  }

  return { open, close };
})();
