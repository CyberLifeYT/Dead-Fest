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

/** Avatar: uses the survivor's chosen colour, or a neutral default. */
function avatar(user, size) {
  const data = (user && user.playerData) || {};
  const color = data.color || "#4ce07f";
  const label = ((user && (user.username || user.displayName)) || "?").trim().charAt(0).toUpperCase() || "?";
  return h("div", {
    class: "avatar" + (size === "lg" ? " avatar--lg" : size === "sm" ? " avatar--sm" : ""),
    style: { background: color },
    text: label
  });
}

/** The display name a survivor should be shown under. */
function handleOf(user) {
  if (!user) return "—";
  return (user.username && user.username.trim()) ||
    (user.displayName && user.displayName.trim()) ||
    ("SURVIVOR-" + String(user.uid || "").slice(0, 4).toUpperCase());
}

/** Human wording for a Black Market outcome. */
function shopOutcomeMessage(outcome) {
  switch (outcome) {
    case "INSUFFICIENT_FUNDS": return "Not enough coins.";
    case "ALREADY_ACTIVE": return "You already have that.";
    case "TIER_LOCKED": return "You need the previous insulation tier first.";
    case "BLOCKED_BY_SHIELD": return "Their shield absorbed the curse.";
    case "NO_DEATHS": return "You have no deaths to remove.";
    case "TARGET_MISSING": return "Pick a target first.";
    case "DISABLED": return "That item is disabled.";
    default: return "That did not work.";
  }
}
