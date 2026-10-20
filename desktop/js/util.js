/* =============================================================================
   Dead-Fest desktop — DOM helpers, formatting, toasts and modals
   ========================================================================== */

/** Create an element. `props` may include class/text/html/style/dataset/on* handlers. */
function h(tag, props, ...children) {
  const el = document.createElement(tag);
  if (props) {
    for (const [key, value] of Object.entries(props)) {
      if (value === null || value === undefined || value === false) continue;
      if (key === "class") el.className = value;
      else if (key === "text") el.textContent = value;
      else if (key === "html") el.innerHTML = value;
      else if (key === "style" && typeof value === "object") Object.assign(el.style, value);
      else if (key === "dataset") Object.assign(el.dataset, value);
      else if (key.startsWith("on") && typeof value === "function") el.addEventListener(key.slice(2).toLowerCase(), value);
      else if (key in el && key !== "list" && typeof value !== "object") el[key] = value;
      else el.setAttribute(key, value);
    }
  }
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

const $ = (sel, root) => (root || document).querySelector(sel);
const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

/** Thousands separator, matching the mobile app's formatNumber(). */
function fmt(n) {
  const v = Math.round(Number(n) || 0);
  return v.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

/** "2h ago" style. */
function timeAgo(ms) {
  if (!ms) return "never";
  const diff = Date.now() - ms;
  const s = Math.floor(diff / 1000);
  if (s < 60) return `${s}s ago`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  const d = Math.floor(h / 24);
  return `${d}d ago`;
}

/* ---- presence (mirrors domain/Presence.kt) ---------------------------------
   A survivor is online when the flag is set AND their last heartbeat is fresh. The staleness
   window is what stops a killed app from looking online forever: without it a crash would leave
   a ghost in the network until somebody died. */
DF.Presence = {
  HEARTBEAT_MS: 60000,
  STALE_AFTER_MS: 150000,
  isOnline(playerData, now) {
    const pd = playerData || {};
    const seen = pd.lastSeen || 0;
    return pd.isOnline === true && seen > 0 && (now || Date.now()) - seen < DF.Presence.STALE_AFTER_MS;
  },
  /** "just now", "5m ago", "3h ago", "2d ago" or "never". */
  lastSeenLabel(lastSeenMs, now) {
    if (!lastSeenMs || lastSeenMs <= 0) return "never";
    const mins = Math.max(0, Math.floor(((now || Date.now()) - lastSeenMs) / 60000));
    if (mins < 1) return "just now";
    if (mins < 60) return `${mins}m ago`;
    if (mins < 60 * 24) return `${Math.floor(mins / 60)}h ago`;
    return `${Math.floor(mins / (60 * 24))}d ago`;
  }
};

/** True when this survivor counts as online right now. */
function isOnlineNow(user, now) {
  return DF.Presence.isOnline(user && user.playerData, now);
}

/** The "seen" line under a survivor's name. */
function lastSeenLabel(user) {
  return DF.Presence.lastSeenLabel(user && user.playerData ? user.playerData.lastSeen : 0);
}

function dateStr(ms) {
  if (!ms) return "—";
  return new Date(ms).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

function escapeHtml(str) {
  return String(str === null || str === undefined ? "" : str)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

/* ---- toast ---------------------------------------------------------------- */
function toast(message, isError) {
  const root = $("#toast-root");
  const el = h("div", { class: "toast" + (isError ? " err" : ""), text: message });
  root.append(el);
  setTimeout(() => {
    el.style.transition = "opacity .25s ease";
    el.style.opacity = "0";
    setTimeout(() => el.remove(), 260);
  }, isError ? 4200 : 2800);
}

/* ---- modal ---------------------------------------------------------------- */
let modalCloser = null;

function openModal({ title, body, actions, onClose, wide }) {
  const root = $("#modal-root");
  const panel = h("div", { class: "modal", style: wide ? { width: "min(860px, 100%)" } : null });
  if (title) panel.append(h("div", { class: "modal-title", text: title }));
  if (body) panel.append(body);
  if (actions && actions.length) {
    panel.append(h("div", { class: "modal-actions" }, actions));
  }
  root.replaceChildren(panel);
  root.classList.add("open");
  modalCloser = () => {
    root.classList.remove("open");
    root.replaceChildren();
    modalCloser = null;
    if (onClose) onClose();
  };
  root.onclick = (e) => { if (e.target === root) modalCloser && modalCloser(); };
  return modalCloser;
}

function closeModal() { if (modalCloser) modalCloser(); }

function confirmDialog({ title, message, confirmText, destructive, onConfirm }) {
  const body = h("div", { class: "t-body", text: message });
  const actions = [
    h("button", { class: "btn btn--ghost", text: "CANCEL", onclick: () => closeModal() }),
    h("button", {
      class: "btn" + (destructive ? " btn--ember" : ""),
      text: confirmText || "CONFIRM",
      onclick: () => { closeModal(); onConfirm && onConfirm(); }
    })
  ];
  openModal({ title, body, actions });
}

/* ---- small builders ------------------------------------------------------- */
function sectionLabel(text, accent) {
  return h("div", { class: "t-section", style: accent ? { color: accent } : null, text });
}

function statTile(label, value, color) {
  return h("div", { class: "stat-tile" },
    h("div", { class: "stat-label", text: label }),
    h("div", { class: "stat-value", style: color ? { color } : null, text: value })
  );
}

function keyValue(k, v, vColor) {
  return h("div", { class: "kv" },
    h("div", { class: "kv-k", text: k }),
    h("div", { class: "kv-v", style: vColor ? { color: vColor } : null, text: v })
  );
}

function statusPill(text, kind) {
  return h("span", { class: "pill" + (kind ? " pill--" + kind : ""), text });
}

function emptyState(icon, title, message) {
  return h("div", { class: "empty" },
    h("div", { class: "empty-ico", text: icon }),
    h("div", { class: "t-card-title", text: title }),
    message ? h("div", { class: "t-body", text: message }) : null
  );
}

/** Avatar frames, mirroring AvatarFrames.brush() in Common.kt. */
const DF_AVATAR_FRAMES = {
  rust:     { label: "Rust",     css: "#CD7F32" },
  neon:     { label: "Neon",     css: "#FF4B4B" },
  toxic:    { label: "Toxic",    css: "#4CE07F" },
  gold:     { label: "Gold",     css: "linear-gradient(135deg,#FFD54F,#FFF9C4,#FFD54F,#FFA000,#FFD54F)" },
  inferno:  { label: "Inferno",  css: "linear-gradient(135deg,#FF4B4B,#FF8A5B,#FFD54F,#FF4B4B)" },
  prestige: { label: "Prestige", css: "linear-gradient(135deg,#B388FF,#4DD0E1,#FFD54F,#B388FF)" }
};

/**
 * Framed avatar: the survivor's emoji, their badge colour and — when they have
 * earned one — the frame brush around it. The online dot is the same green as
 * the phone app's, and it only appears when presence is actually fresh.
 */
function avatar(user, size, online) {
  const pd = (user && user.playerData) || {};
  const color = pd.color || "#536DFE";
  const frame = pd.avatarFrame || "none";
  const px = typeof size === "number" ? size : size === "xl" ? 64 : size === "lg" ? 56 : size === "sm" ? 28 : 40;
  const brush = DF_AVATAR_FRAMES[frame];
  const inner = h("div", {
    class: "avatar-core",
    style: {
      background: "color-mix(in srgb, " + color + " 18%, transparent)",
      color: color,
      fontSize: Math.round(px * 0.45) + "px"
    },
    text: pd.avatar || "💀"
  });
  const wrap = h("div", {
    class: "avatar-wrap",
    style: {
      width: px + "px",
      height: px + "px",
      borderRadius: "50%",
      flex: "none",
      padding: brush ? "3px" : "0",
      background: brush ? brush.css : "transparent",
      boxShadow: brush ? "none" : "0 0 0 1.5px " + color
    }
  }, inner);
  if (online === undefined ? isOnlineNow(user) : online) {
    const dot = Math.max(8, Math.round(px * 0.28));
    wrap.append(h("div", {
      class: "avatar-dot",
      style: { width: dot + "px", height: dot + "px" }
    }));
  }
  return wrap;
}

/** The name a survivor is listed under on the player network. */
function displayNameOf(user) {
  if (!user) return "Survivor";
  const name = (user.displayName || "").trim();
  return name || "Survivor";
}

/** The display name a survivor should be shown under in logs and requests. */
function handleOf(user) {
  if (!user) return "—";
  return (user.username && user.username.trim()) ||
    (user.displayName && user.displayName.trim()) ||
    ("SURVIVOR-" + String(user.uid || "").slice(0, 4).toUpperCase());
}

/** Total deaths across every sector. */
function totalDeaths(user) {
  const games = (user && user.playerData && user.playerData.games) || {};
  return Object.keys(games).reduce((sum, k) => sum + ((games[k] || {}).deaths || 0), 0);
}

/** Human wording for a Black Market failure, mirroring the phone's snackbars. */
function shopOutcomeMessage(item, outcome, target) {
  const name = (item && item.name) || "that item";
  const targetName = target ? displayNameOf(target) : "that survivor";
  switch (outcome) {
    case "INSUFFICIENT_FUNDS": return `Not enough coins for ${name}.`;
    case "ALREADY_ACTIVE":
      return item && item.oneTime
        ? `You already own ${name}.`
        : `You already have an active ${name.toLowerCase()}.`;
    case "TIER_LOCKED": return "Buy the previous insulation tier first.";
    case "BLOCKED_BY_SHIELD": return `${targetName}'s Shield blocked your curse. The Shield is gone now.`;
    case "NO_DEATHS": return "No deaths to revive. You're clean!";
    case "TARGET_MISSING": return "That survivor couldn't be found.";
    case "DISABLED": return `${name} is disabled by an admin.`;
    default: return "That did not work.";
  }
}

/** The snackbar shown after a successful purchase, mirroring MainViewModel.kt. */
function purchaseMessage(item, result, target) {
  const where = result && result.sector ? " in " + result.sector : "";
  const targetName = target ? displayNameOf(target) : "a survivor";
  if (item.curseDeaths > 0) return `Curse landed on ${targetName}${where}.`;
  if (item.id === "revive") return `Revived: 1 death removed${where}.`;
  if (item.id === "shield") return "Shield is up. The next curse on you will be blocked.";
  if (item.id === "lucky_coin") return "Lucky Coin armed. Your next Wheel win is doubled.";
  if (item.id === "insurance") {
    const pct = Math.round((result && result.insurancePct) || 25);
    return `Insurance armed: ${pct}% of your next casualty tax is covered.`;
  }
  if (item.id === "ration_upgrade") {
    const cfg = (DF.Data.state.global && DF.Data.state.global.economy) || DF.Economy.DEFAULT_CONFIG;
    return `Ration Upgrade active: ${fmt(cfg.dailyUpgraded)} coins every day.`;
  }
  if (item.tier > 0) return `WEALTH INSULATION UPGRADED: Tier ${DF.Economy.roman(item.tier)} active.`;
  return `Purchased ${item.name}.`;
}
