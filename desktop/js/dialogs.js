/* =============================================================================
   Dead-Fest desktop — result dialogs
   -----------------------------------------------------------------------------
   A port of ui/components/ResultDialogs.kt. The phone answers a death, a ration
   claim and a level-up with a framed result panel rather than a passing message,
   and the desktop shows the same panel, in the same order, with the same wording.
   ========================================================================== */
window.DF = window.DF || {};

DF.Dialogs = (function () {
  const queue = [];
  let showing = false;

  function panel(accent, heading, content) {
    return h("div", { class: "result", style: { "--accent": accent } },
      heading ? h("div", { class: "result-head", text: heading }) : null,
      content
    );
  }

  /** A line with an icon, mirroring ReportLine. */
  function reportLine(icon, text, color) {
    return h("div", { class: "result-line row", style: { gap: "var(--xs)", alignItems: "flex-start", color: color || "var(--text-hi)" } },
      h("span", { style: { width: "18px", flex: "none", textAlign: "center" }, text: icon }),
      h("span", { style: { flex: "1" }, text: text })
    );
  }

  function enqueue(spec) {
    queue.push(spec);
    pump();
  }

  function pump() {
    if (showing || !queue.length) return;
    const spec = queue.shift();
    showing = true;
    openModal({
      title: null,
      body: spec.body,
      wide: !!spec.wide,
      actions: [
        h("button", {
          class: "btn btn--full btn--tall",
          text: spec.button || "ACKNOWLEDGED",
          onclick: () => closeModal()
        })
      ],
      onClose: () => {
        showing = false;
        if (spec.onClose) spec.onClose();
        setTimeout(pump, 0);
      }
    });
    if (spec.after) spec.after();
  }

  /* ---- death report ------------------------------------------------------- */
  /**
   * @param result  what DF.Data.recordDeath resolved with
   * @param reward  promise for survivors paid (>=0), or -1 on failure; null while pending
   */
  function deathReport(result, reward, cfg) {
    const o = result.outcome;
    const tax = o.tax;
    const lines = [];

    lines.push(h("div", { class: "result-hero" },
      h("div", { class: "result-big", text: `${fmt(tax.finalTax)} coins lost` }),
      h("div", { class: "t-body", text: `Died in ${result.game} · ${tax.tier} tier · balance ${fmt(o.coinsBefore)} → ${fmt(o.coinsAfter)}` })
    ));
    lines.push(h("div", { class: "divider" }));

    if (tax.insulationSaved > 0) {
      lines.push(reportLine("🏦", `TAX REDUCED: Insulation Tier ${DF.Economy.roman(tax.insulationTier)} saved you ${fmt(tax.insulationSaved)} coins`, "var(--amber)"));
    }
    if (tax.insuranceUsed) {
      lines.push(reportLine("🛡", `INSURANCE CLAIMED: ${tax.insurancePct}% of tax absorbed (${fmt(tax.insuranceSaved)} coins)`, "var(--plasma)"));
    }
    lines.push(reportLine("🎰", `+${o.spinsAdded} pending spin(s) · ${o.pendingAfter} total`, "var(--amber)"));

    if (o.levelBefore < 2 && o.levelAfter >= 2) {
      lines.push(reportLine("💸", "BANKRUPTCY LEVEL 2: clear your penalty spins to escape", "var(--ember)"));
    } else if (o.levelBefore === 0 && o.levelAfter === 1) {
      lines.push(reportLine("💸", "BANKRUPTCY LEVEL 1: survival payouts halved", "var(--ember)"));
    }
    if (result.xp) {
      lines.push(reportLine("★", `+${fmt(result.xp.gained)} XP`, "var(--text-mid)"));
    }

    const rewardLine = h("div");
    lines.push(rewardLine);

    function setReward(paid) {
      rewardLine.replaceChildren();
      if (paid === null || paid === undefined) {
        rewardLine.append(reportLine("👥", "Paying the Mutual Survival Reward...", "var(--text-hi)"));
      } else if (paid < 0) {
        rewardLine.append(reportLine("👥", "Mutual Survival Reward couldn't be paid out", "var(--text-hi)"));
      } else if (paid > 0) {
        rewardLine.append(reportLine("👥", `${paid} survivor(s) collected ${fmt((cfg || DF.Economy.DEFAULT_CONFIG).survivalReward)} coins from your fall`, "var(--text-hi)"));
      }
    }

    enqueue({
      body: panel("var(--ember)", "CASUALTY TAX", h("div", { class: "stack" }, ...lines)),
      button: "ACKNOWLEDGED",
      after() {
        setReward(null);
        if (reward && reward.then) {
          reward.then((paid) => setReward(typeof paid === "number" ? paid : 0)).catch(() => setReward(-1));
        } else {
          setReward(0);
        }
      }
    });
  }

  /* ---- daily rations ------------------------------------------------------ */
  function dailyClaim(result) {
    const lines = [
      h("div", { class: "row", style: { gap: "var(--sm)" } },
        h("div", { class: "tile", style: { "--tint": "var(--toxic)" }, text: "📦" }),
        h("div", { style: { flex: "1" } },
          h("div", { class: "result-head", style: { textAlign: "left", marginBottom: "2px", color: "var(--toxic)" }, text: "RATIONS RECEIVED" }),
          h("div", { class: "result-big", style: { fontSize: "24px" }, text: `+${fmt(result.amount)} coins` })
        )
      ),
      h("div", { class: "t-body", text: `New balance: ${fmt(result.newBalance)} coins` })
    ];
    if (result.upgraded) lines.push(reportLine("⬆", "Ration Upgrade active", "var(--amber)"));
    if (result.survivalBonusXp > 0) lines.push(reportLine("🛡", `Survived a full day without dying: +${result.survivalBonusXp} XP`, "var(--plasma)"));

    enqueue({
      body: panel("var(--toxic)", null, h("div", { class: "stack" }, ...lines)),
      button: "COLLECT"
    });
  }

  /* ---- level up ----------------------------------------------------------- */
  function levelUp(level, gained, rewards) {
    const unlocked = (rewards || []).filter((r) => gained.indexOf(r.level) >= 0);
    const content = h("div", { class: "stack" });

    content.append(h("div", { class: "result-head", style: { letterSpacing: "3px", color: "var(--amber)", marginBottom: "2px" }, text: "LEVEL UP" }));
    content.append(h("div", { style: { display: "grid", placeItems: "center", padding: "var(--sm) 0" } },
      h("div", {
        style: {
          width: "96px", height: "96px", borderRadius: "50%",
          display: "grid", placeItems: "center",
          background: "rgba(255, 213, 79, 0.12)",
          border: "1px solid rgba(255, 213, 79, 0.5)",
          color: "var(--amber)", fontSize: "42px", fontWeight: "900"
        },
        text: String(level)
      })
    ));

    if (!unlocked.length) {
      content.append(h("div", { class: "t-body", style: { textAlign: "center" }, text: "Keep surviving. More rewards ahead." }));
    } else {
      content.append(sectionLabel("Unlocked"));
      unlocked.forEach((r) => {
        content.append(reportLine(r.type === "frame" ? "👤" : r.type === "title" ? "🎖" : "🎨", r.label, "var(--amber)"));
      });
      content.append(h("div", { class: "t-micro", text: "Equip it from Utility Hub → Progression." }));
    }

    enqueue({ body: panel("var(--amber)", null, content), button: "CONTINUE" });
  }

  /** Shows the level-up panel for any action that gained one. */
  function maybeLevelUp(xp) {
    if (!xp || !xp.levelsGained || !xp.levelsGained.length) return;
    const rewards = (DF.Data.state.levelRewards && DF.Data.state.levelRewards.length)
      ? DF.Data.state.levelRewards
      : DF.Economy.LEVEL_REWARDS;
    levelUp(xp.level, xp.levelsGained, rewards);
  }

  return { enqueue, deathReport, dailyClaim, levelUp, maybeLevelUp };
})();
