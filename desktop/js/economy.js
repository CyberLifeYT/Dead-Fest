/* =============================================================================
   Dead-Fest desktop — game rules
   -----------------------------------------------------------------------------
   A direct port of app/src/main/java/com/deadfest/domain/Economy.kt and the
   catalog models. The mobile app and this client must agree on every number:
   the same tax, the same bankruptcy transitions, the same XP curve. If a rule
   changes in Kotlin it has to change here too.

   Pure functions only — no Firebase, no DOM.
   ========================================================================== */
window.DF = window.DF || {};

DF.Economy = (function () {
  const DEFAULT_XP = {
    death: 10, survivalDay: 25, purchase: 5, spin: 3, scratch: 3, penaltySpin: 5,
    curveBase: 100, curveExponent: 1.5, maxLevel: 50
  };

  const DEFAULT_CONFIG = {
    dailyBase: 50, dailyUpgraded: 100, survivalReward: 10, insurancePct: 50,
    insulationEnabled: true, scratchEnabled: true, scratchCost: 25,
    shop: {}, xp: DEFAULT_XP
  };

  /* ---- config from the live global document (mirrors parseEconomy) --------- */
  function parseConfig(doc) {
    const d = doc || {};
    const base = DEFAULT_CONFIG;
    const dx = base.xp;

    const shop = {};
    const rawShop = d.shopConfig;
    if (rawShop && typeof rawShop === "object") {
      for (const key of Object.keys(rawShop)) {
        const m = rawShop[key] || {};
        shop[key] = {
          enabled: typeof m.enabled === "boolean" ? m.enabled : null,
          price: typeof m.price === "number" ? m.price : null,
          reductionPct: typeof m.reductionPct === "number" ? m.reductionPct : null
        };
      }
    }

    const x = d.xpConfig && typeof d.xpConfig === "object" ? d.xpConfig : {};
    const num = (v, fallback) => (typeof v === "number" && isFinite(v) ? v : fallback);

    return {
      dailyBase: num(d.dailyBase, base.dailyBase),
      dailyUpgraded: num(d.dailyUpgraded, base.dailyUpgraded),
      survivalReward: num(d.survivalReward, base.survivalReward),
      insurancePct: Math.min(100, Math.max(0, num(d.insurancePct, base.insurancePct))),
      insulationEnabled: typeof d.insulationEnabled === "boolean" ? d.insulationEnabled : base.insulationEnabled,
      scratchEnabled: typeof d.scratchEnabled === "boolean" ? d.scratchEnabled : base.scratchEnabled,
      scratchCost: num(d.scratchCost, base.scratchCost),
      shop,
      xp: {
        death: num(x.death, dx.death),
        survivalDay: num(x.survivalDay, dx.survivalDay),
        purchase: num(x.purchase, dx.purchase),
        spin: num(x.spin, dx.spin),
        scratch: num(x.scratch, dx.scratch),
        penaltySpin: num(x.penaltySpin, dx.penaltySpin),
        curveBase: num(x.curveBase, dx.curveBase) > 0 ? num(x.curveBase, dx.curveBase) : dx.curveBase,
        curveExponent: num(x.curveExponent, dx.curveExponent) > 0 ? num(x.curveExponent, dx.curveExponent) : dx.curveExponent,
        maxLevel: Math.max(2, num(x.maxLevel, dx.maxLevel))
      }
    };
  }

  /* ---- weighted RNG (Weighted.kt) ------------------------------------------ */
  function weightedPick(weights) {
    if (!weights || !weights.length) return -1;
    const safe = weights.map((w) => (isFinite(w) ? Math.max(0, w) : 0));
    const total = safe.reduce((a, b) => a + b, 0);
    if (total <= 0) return Math.floor(Math.random() * safe.length);
    let roll = Math.random() * total;
    for (let i = 0; i < safe.length; i++) {
      if (roll < safe[i]) return i;
      roll -= safe[i];
    }
    return safe.length - 1;
  }

  function weightedChances(weights) {
    const safe = (weights || []).map((w) => Math.max(0, w));
    const total = safe.reduce((a, b) => a + b, 0);
    return total <= 0 ? safe.map(() => 0) : safe.map((w) => (w / total) * 100);
  }

  /* ---- casualty tax (CasualtyTax.kt) -------------------------------------- */
  const TAX_TIERS = [
    { label: "ELITE", rate: 0.30, flat: null, min: 5000 },
    { label: "HIGH",  rate: 0.20, flat: null, min: 1000 },
    { label: "MID",   rate: 0.15, flat: null, min: 500 },
    { label: "BASE",  rate: null,  flat: 25,   min: -Infinity }
  ];

  function taxTierFor(coins) {
    if (coins > 5000) return TAX_TIERS[0];
    if (coins > 1000) return TAX_TIERS[1];
    if (coins > 500) return TAX_TIERS[2];
    return TAX_TIERS[3];
  }

  function casualtyTax(balance, insulationTier, insulationReductionPct, insuranceActive, insurancePct) {
    const coins = Math.max(0, balance || 0);
    const tier = taxTierFor(coins);
    const baseTax = tier.rate !== null ? Math.round(coins * tier.rate) : tier.flat;
    const reduction = insulationTier > 0 ? Math.min(100, Math.max(0, insulationReductionPct || 0)) : 0;
    const afterInsulation = Math.round(baseTax * (1 - reduction / 100));
    const insuranceUsed = !!insuranceActive && afterInsulation > 0;
    const pct = Math.min(100, Math.max(0, insurancePct || 0));
    const insuranceSaved = insuranceUsed ? Math.round((afterInsulation * pct) / 100) : 0;
    const due = Math.max(0, afterInsulation - insuranceSaved);
    return {
      tier: tier.label,
      baseTax,
      insulationTier: insulationTier || 0,
      insulationReductionPct: reduction,
      insulationSaved: baseTax - afterInsulation,
      afterInsulation,
      insuranceUsed,
      insurancePct: insuranceUsed ? pct : 0,
      insuranceSaved,
      finalTax: Math.min(due, coins)
    };
  }

  function formatPct(p) {
    return p % 1 === 0 ? String(p) : p.toFixed(1);
  }

  function effectiveRateLabel(balance, insulationReductionPct) {
    const tier = taxTierFor(balance);
    const factor = 1 - Math.min(100, Math.max(0, insulationReductionPct || 0)) / 100;
    if (tier.rate !== null) return `${formatPct(tier.rate * 100 * factor)}% of balance`;
    return `flat ${Math.round(tier.flat * factor)} coins`;
  }

  /* ---- bankruptcy (Bankruptcy.kt) ----------------------------------------- */
  const BANKRUPTCY = { NONE: 0, LEVEL_1: 1, LEVEL_2: 2 };

  function afterBalanceChange(level, coins, pendingSpins) {
    if (level <= BANKRUPTCY.NONE && coins <= 0) return BANKRUPTCY.LEVEL_1;
    if (level === BANKRUPTCY.LEVEL_1 && coins > 0) return BANKRUPTCY.NONE;
    if (level >= BANKRUPTCY.LEVEL_2 && coins > 0 && pendingSpins <= 0) return BANKRUPTCY.NONE;
    return Math.min(BANKRUPTCY.LEVEL_2, Math.max(0, level));
  }

  function payoutFor(level, reward) {
    return level >= BANKRUPTCY.LEVEL_1 ? Math.floor(reward / 2) : reward;
  }

  /* ---- death rules (DeathRules.kt) --------------------------------------- */
  function resolveDeath(opts) {
    const coins = opts.coins || 0;
    const bankruptcyLevel = opts.bankruptcyLevel || 0;
    const pendingSpins = opts.pendingSpins || 0;
    const forceSpinProtocol = opts.forceSpinProtocol !== false;

    const wasBankrupt = bankruptcyLevel >= BANKRUPTCY.LEVEL_1;
    let spinsAdded = forceSpinProtocol ? 1 : 0;
    let level = Math.min(BANKRUPTCY.LEVEL_2, Math.max(BANKRUPTCY.NONE, bankruptcyLevel));
    if (wasBankrupt) {
      level = BANKRUPTCY.LEVEL_2;
      if (forceSpinProtocol) spinsAdded += 2;
    }
    const pending = Math.max(0, pendingSpins) + spinsAdded;
    const tax = casualtyTax(
      coins,
      opts.insulationTier || 0,
      opts.insulationReductionPct || 0,
      !!opts.insuranceActive,
      opts.insurancePct || 0
    );
    const after = Math.max(0, coins - tax.finalTax);
    level = afterBalanceChange(level, after, pending);
    return {
      tax, coinsBefore: coins, coinsAfter: after,
      levelBefore: bankruptcyLevel, levelAfter: level,
      pendingBefore: pendingSpins, pendingAfter: pending, spinsAdded
    };
  }

  /* ---- XP & levels (Leveling.kt) ------------------------------------------ */
  function xpForLevel(n, cfg) {
    const c = cfg || DEFAULT_XP;
    return Math.max(1, Math.round(c.curveBase * Math.pow(Math.max(1, n), c.curveExponent)));
  }

  function addXp(xp, level, amount, cfg) {
    const c = cfg || DEFAULT_XP;
    let curXp = Math.max(0, xp || 0) + Math.max(0, amount || 0);
    let curLevel = Math.min(Math.max(1, c.maxLevel), Math.max(1, level || 1));
    const gained = [];
    while (curLevel < c.maxLevel) {
      const need = xpForLevel(curLevel, c);
      if (curXp < need) break;
      curXp -= need;
      curLevel++;
      gained.push(curLevel);
    }
    return { gained: Math.max(0, amount || 0), xp: curXp, level: curLevel, levelsGained: gained };
  }

  function xpProgress(xp, level, cfg) {
    const c = cfg || DEFAULT_XP;
    if (level >= c.maxLevel) return 1;
    return Math.min(1, Math.max(0, (xp || 0) / xpForLevel(level, c)));
  }

  function canPrestige(level, cfg) {
    return level >= (cfg || DEFAULT_XP).maxLevel;
  }

  /* ---- daily rations (DailyClock.kt) -------------------------------------- */
  function startOfDay(millis) {
    const d = new Date(millis);
    d.setHours(0, 0, 0, 0);
    return d.getTime();
  }

  function nextReset(now) {
    const d = new Date(startOfDay(now));
    d.setDate(d.getDate() + 1);
    return d.getTime();
  }

  function canClaimDaily(lastClaim, now) {
    return !lastClaim || lastClaim < startOfDay(now);
  }

  function dailyCountdown(now) {
    const left = Math.max(0, Math.floor((nextReset(now) - now) / 1000));
    const hh = String(Math.floor(left / 3600)).padStart(2, "0");
    const mm = String(Math.floor((left % 3600) / 60)).padStart(2, "0");
    const ss = String(left % 60).padStart(2, "0");
    return `${hh}:${mm}:${ss}`;
  }

  /* ---- Black Market catalog (ShopItem.kt) --------------------------------- */
  const SHOP = [
    { id: "shield",        name: "SHIELD",              desc: "Blocks the next curse aimed at you", price: 50,   category: "COMBAT",   logName: "a Shield",      curseDeaths: 0, oneTime: false, tier: 0, requiresTier: 0, reduction: 0 },
    { id: "curse",         name: "CURSE",               desc: "Adds 1 death to a target",             price: 100,  category: "COMBAT",   logName: "a Curse",       curseDeaths: 1, oneTime: false, tier: 0, requiresTier: 0, reduction: 0 },
    { id: "double_curse",  name: "DOUBLE CURSE",        desc: "Adds 2 deaths to a target",            price: 200,  category: "COMBAT",   logName: "a Double Curse",curseDeaths: 2, oneTime: false, tier: 0, requiresTier: 0, reduction: 0 },
    { id: "revive",        name: "REVIVE",              desc: "Removes 1 death from you",             price: 150,  category: "COMBAT",   logName: "a Revive",      curseDeaths: 0, oneTime: false, tier: 0, requiresTier: 0, reduction: 0 },
    { id: "lucky_coin",    name: "LUCKY COIN",          desc: "Doubles your next Wheel win",          price: 80,   category: "ECONOMY",  logName: "a Lucky Coin",  curseDeaths: 0, oneTime: false, tier: 0, requiresTier: 0, reduction: 0 },
    { id: "insurance",     name: "INSURANCE POLICY",    desc: "Absorbs part of your next casualty tax. One use.", price: 150, category: "ECONOMY", logName: "an Insurance Policy", curseDeaths: 0, oneTime: false, tier: 0, requiresTier: 0, reduction: 0 },
    { id: "ration_upgrade",name: "RATION UPGRADE",      desc: "Permanently raises your daily rations", price: 750, category: "ECONOMY",  logName: "the Ration Upgrade", curseDeaths: 0, oneTime: true, tier: 0, requiresTier: 0, reduction: 0 },
    { id: "insulation_1",  name: "INSULATED LEDGER",    desc: "Wealth Insulation Tier I",              price: 1500, category: "INSULATION", logName: "Insulated Ledger", curseDeaths: 0, oneTime: true, tier: 1, requiresTier: 0, reduction: 10 },
    { id: "insulation_2",  name: "OFFSHORE TERMINAL",   desc: "Wealth Insulation Tier II",             price: 5000, category: "INSULATION", logName: "Offshore Terminal", curseDeaths: 0, oneTime: true, tier: 2, requiresTier: 1, reduction: 25 },
    { id: "insulation_3",  name: "BLACK VAULT PROTOCOL",desc: "Wealth Insulation Tier III",            price: 15000,category: "INSULATION", logName: "Black Vault Protocol", curseDeaths: 0, oneTime: true, tier: 3, requiresTier: 2, reduction: 50 },
    { id: "insulation_4",  name: "GHOST ECONOMY",       desc: "Wealth Insulation Tier IV",             price: 50000,category: "INSULATION", logName: "Ghost Economy", curseDeaths: 0, oneTime: true, tier: 4, requiresTier: 3, reduction: 100 }
  ];

  const ROMAN = { 1: "I", 2: "II", 3: "III", 4: "IV" };

  function priceIn(item, cfg) {
    const override = cfg && cfg.shop ? cfg.shop[item.id] : null;
    if (override && typeof override.price === "number") return Math.max(0, override.price);
    return item.price;
  }

  function enabledIn(item, cfg) {
    const override = cfg && cfg.shop ? cfg.shop[item.id] : null;
    const enabled = override && typeof override.enabled === "boolean" ? override.enabled : true;
    return enabled && (!item.tier || (cfg && cfg.insulationEnabled !== false));
  }

  function reductionIn(item, cfg) {
    const override = cfg && cfg.shop ? cfg.shop[item.id] : null;
    if (override && typeof override.reductionPct === "number") return Math.min(100, Math.max(0, override.reductionPct));
    return item.reduction || 0;
  }

  function insulationReduction(tier, cfg) {
    const item = SHOP.find((s) => s.tier === tier && tier > 0);
    return item ? reductionIn(item, cfg) : 0;
  }

  function roman(tier) { return ROMAN[tier] || "-"; }

  /* ---- level rewards (LevelRewardCatalog) --------------------------------- */
  const LEVEL_REWARDS = [
    { level: 2,  rewardId: "color_toxic",      type: "color", label: "Toxic Green badge",   value: "#4CE07F" },
    { level: 3,  rewardId: "frame_rust",       type: "frame", label: "Rust frame",          value: "rust" },
    { level: 5,  rewardId: "title_scavenger",  type: "title", label: "Title: SCAVENGER",    value: "SCAVENGER" },
    { level: 7,  rewardId: "color_cyan",       type: "color", label: "Neon Cyan badge",     value: "#4DD0E1" },
    { level: 10, rewardId: "frame_neon",       type: "frame", label: "Neon frame",          value: "neon" },
    { level: 12, rewardId: "title_ranger",     type: "title", label: "Title: WASTELAND RANGER", value: "WASTELAND RANGER" },
    { level: 15, rewardId: "color_orange",     type: "color", label: "Blood Orange badge",  value: "#FF7043" },
    { level: 20, rewardId: "frame_toxic",      type: "frame", label: "Toxic frame",         value: "toxic" },
    { level: 25, rewardId: "title_defier",     type: "title", label: "Title: DEATH DEFIER", value: "DEATH DEFIER" },
    { level: 30, rewardId: "color_violet",     type: "color", label: "Royal Violet badge",  value: "#B388FF" },
    { level: 35, rewardId: "frame_gold",       type: "frame", label: "Gold frame",          value: "gold" },
    { level: 40, rewardId: "title_unkillable", type: "title", label: "Title: UNKILLABLE",   value: "UNKILLABLE" },
    { level: 45, rewardId: "color_gold",       type: "color", label: "Molten Gold badge",   value: "#FFD54F" },
    { level: 50, rewardId: "frame_inferno",    type: "frame", label: "Inferno frame",       value: "inferno" }
  ];

  function rewardUnlocked(reward, level, prestigeCount) {
    return prestigeCount > 0 || level >= reward.level;
  }

  /* ---- scratch pool (ScratchPrize.kt) ------------------------------------- */
  const SCRATCH_POOL = [
    { id: "", label: "Dud",          weight: 4.0,  value: "0",   type: "none",       color: "#546E7A", enabled: true },
    { id: "", label: "Pocket Change",weight: 3.0,  value: "15",  type: "coins",      color: "#7CB342", enabled: true },
    { id: "", label: "Break Even",   weight: 2.0,  value: "25",  type: "coins",      color: "#039BE5", enabled: true },
    { id: "", label: "Supply Cache", weight: 1.35, value: "60",  type: "coins",      color: "#00897B", enabled: true },
    { id: "", label: "Free Shield",  weight: 0.5,  value: "",    type: "shield",     color: "#4DD0E1", enabled: true },
    { id: "", label: "Lucky Coin",   weight: 0.5,  value: "",    type: "lucky_coin", color: "#FDD835", enabled: true },
    { id: "", label: "JACKPOT",      weight: 0.25, value: "300", type: "coins",      color: "#FB8C00", enabled: true }
  ];

  /* ---- wheel slice helpers (WheelItem.kt) -------------------------------- */
  function coinAmount(item) {
    const n = parseInt(String(item.value || "").trim(), 10);
    return Number.isFinite(n) && n !== 0 ? n : null;
  }

  function isTake(item) {
    return String(item.actionType || "").toLowerCase() === "take";
  }

  return {
    DEFAULT_CONFIG, DEFAULT_XP,
    parseConfig,
    weightedPick, weightedChances,
    taxTierFor, casualtyTax, effectiveRateLabel, formatPct,
    BANKRUPTCY, afterBalanceChange, payoutFor, resolveDeath,
    xpForLevel, addXp, xpProgress, canPrestige,
    startOfDay, nextReset, canClaimDaily, dailyCountdown,
    SHOP, priceIn, enabledIn, reductionIn, insulationReduction, roman,
    LEVEL_REWARDS, rewardUnlocked,
    SCRATCH_POOL, coinAmount, isTake
  };
})();
