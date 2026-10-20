/* =============================================================================
   Dead-Fest desktop — render smoke test
   -----------------------------------------------------------------------------
   Loads the real renderer in Electron, seeds the same shapes Firestore would
   deliver, then walks every screen, tab, sub-view and modal. Any thrown error
   or console error fails the run. This is how the desktop app is checked
   without signing into the live database.
   ========================================================================== */
const fs = require("fs");
const path = require("path");
const { app, BrowserWindow } = require("electron");

app.disableHardwareAcceleration();

const REPORT = path.join(process.env.LOCALAPPDATA || __dirname, "Temp", "opencode", "smoke-report.json");

const consoleErrors = [];
const pageErrors = [];
let win = null;

function finish(code, payload) {
  try { fs.writeFileSync(REPORT, JSON.stringify(payload, null, 2)); } catch (e) { /* best effort */ }
  console.log(JSON.stringify(payload, null, 2));
  if (win && !win.isDestroyed()) win.destroy();
  setTimeout(() => app.exit(code), 60);
}

app.whenReady().then(() => {
  win = new BrowserWindow({
    width: 1400,
    height: 900,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, "..", "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      backgroundThrottling: false
    }
  });

  win.webContents.on("console-message", (_event, level, message) => {
    if (level >= 2) consoleErrors.push(message);
  });
  win.webContents.on("render-process-gone", (_event, details) => {
    pageErrors.push("renderer gone: " + JSON.stringify(details));
  });
  win.webContents.on("did-fail-load", (_event, code, description) => {
    pageErrors.push("load failed: " + code + " " + description);
  });

  win.loadFile(path.join(__dirname, "..", "index.html"));

  win.webContents.once("did-finish-load", async () => {
    try {
      const report = await win.webContents.executeJavaScript(`(async () => {
        const out = { steps: [], dialogs: [], asyncErrors: [] };
        window.addEventListener("error", (e) => out.asyncErrors.push("error: " + e.message + " @ " + e.filename + ":" + e.lineno));
        window.addEventListener("unhandledrejection", (e) => {
          const r = e.reason;
          out.asyncErrors.push("rejection: " + (r && (r.stack || r.message) || String(r)));
        });

        const wait = (ms) => new Promise((r) => setTimeout(r, ms || 40));
        const now = Date.now();

        // Wait for Firebase Auth to finish restoring (there is no session, so this lands on
        // the login screen). Injecting before that callback would be wiped by its reset.
        const bootDeadline = Date.now() + 15000;
        while (!document.getElementById("login").classList.contains("show") && Date.now() < bootDeadline) {
          await wait(100);
        }
        out.booted = document.getElementById("login").classList.contains("show");

        function mockUser(uid, name, handle, extra) {
          return Object.assign({
            uid, displayName: name, username: handle, email: handle + "@deadfest.test", admin: false,
            playerData: {
              coins: 0, level: 1, xp: 0, games: {}, shield: false, luckyCoin: false, insurance: false,
              insulationTier: 0, pendingSpins: 0, bankruptcyLevel: 0, prestigeCount: 0,
              title: "Survivor", avatar: "💀", color: "#536DFE", avatarFrame: "none",
              dailyUpgradeOwned: false, lastDailyClaim: 0, lastDeathAt: 0, ownedTitles: [],
              lastSeen: now, isOnline: true
            }
          }, extra || {});
        }

        // ---- seed the data the screens read ---------------------------------
        const me = mockUser("u1", "Vex", "vex", {
          admin: true,
          playerData: {
            coins: 1234, level: 12, xp: 40,
            games: { "Elden Ring": { deaths: 3 }, "Hades": { deaths: 1 } },
            shield: true, luckyCoin: false, insurance: true, insulationTier: 2,
            pendingSpins: 1, bankruptcyLevel: 0, prestigeCount: 1,
            title: "SCAVENGER", avatar: "💀", color: "#4CE07F", avatarFrame: "rust",
            dailyUpgradeOwned: true, lastDailyClaim: 0, lastDeathAt: 0,
            ownedTitles: ["SCAVENGER", "WARLORD"], lastSeen: now, isOnline: true
          }
        });
        const others = [
          mockUser("u2", "Ash", "ash", { playerData: Object.assign({}, me.playerData, { coins: 4000, level: 30, prestigeCount: 2, title: "APOCALYPSE EMPEROR", avatarFrame: "inferno", games: { "Elden Ring": { deaths: 7 }, Hades: { deaths: 4 } } }) }),
          mockUser("u3", "Rook", "rook", { playerData: Object.assign({}, me.playerData, { coins: 90, level: 4, shield: false, pendingSpins: 3, bankruptcyLevel: 1, games: { Hades: { deaths: 6 } }, lastSeen: now - 600000, isOnline: false }) }),
          mockUser("u4", "Nyx", "nyx", { playerData: Object.assign({}, me.playerData, { coins: 2400, level: 19, prestigeCount: 0, title: "WARLORD", games: { "Deep Rock Galactic": { deaths: 2 } } }) }),
          mockUser("u5", "Sol", "sol", { playerData: Object.assign({}, me.playerData, { coins: 750, level: 8, games: {} }) })
        ];
        const users = [me].concat(others);

        const global = DF.Data.mapGlobal({
          exists: true,
          data: () => ({
            marketEnabled: true, wheelEnabled: true, reviveEnabled: true, lockdownActive: false,
            grandTotal: 987, games: ["Elden Ring", "Hades", "Risk of Rain 2", "Deep Rock Galactic"],
            lockedGames: ["Deep Rock Galactic"], sectorOverload: true, overloadedSector: "Hades",
            multiplier: 2, overloadEndsAt: now + 3600000, currentSeasonName: "Season 2",
            nextEventEnabled: true, nextEventName: "Dead-Fest: The Long Dark",
            nextEventStartsAt: now + 259200000, nextEventGames: ["Hades", "Elden Ring"],
            nextEventNotes: "Bring your worst.",
            latestVersionName: "2.1.0", latestVersionCode: 14,
            updateUrl: "https://example.com/a.apk", updateUrlR8: "https://example.com/b.apk",
            updateNotes: "Better.", forceSpinProtocol: true,
            dailyBase: 100, dailyUpgraded: 250, survivalReward: 10, insurancePct: 25, scratchCost: 25,
            xpConfig: { death: 25, survivalDay: 50, purchase: 10, spin: 5, scratch: 8, penaltySpin: 2, curveBase: 100, curveExponent: 1.35, maxLevel: 50 },
            wheelItems: [
              { id: "w1", label: "JACKPOT", value: "500", actionType: "give", color: "#FF4B4B", enabled: true, weight: 0.4 },
              { id: "w2", label: "Pocket", value: "50", actionType: "give", color: "#FFD54F", enabled: true, weight: 2 },
              { id: "w3", label: "Tax", value: "75", actionType: "take", color: "#4CE07F", enabled: true, weight: 1.6 },
              { id: "w4", label: "Big Tax", value: "150", actionType: "take", color: "#4DD0E1", enabled: true, weight: 1 },
              { id: "w5", label: "Shield", value: "", actionType: "give", color: "#B388FF", enabled: true, weight: 0.8 },
              { id: "w6", label: "Nothing", value: "", actionType: "give", color: "#FF8A5B", enabled: true, weight: 2.2 }
            ],
            shopConfig: {}
          })
        });

        DF.Data.state.user = me;
        DF.Data.state.users = users;
        DF.Data.state.global = global;
        DF.Data.state.events = [
          { id: "e1", category: "DEATH", message: "Rook died in Hades. Casualty tax: 32 coins.", timestamp: now - 120000 },
          { id: "e2", category: "WHEEL_SPIN", message: "Ash spun the Wheel of Fate and won 500 coins.", timestamp: now - 240000 },
          { id: "e3", category: "BLACK_MARKET", message: "Nyx bought a Double Curse.", timestamp: now - 360000 },
          { id: "e4", category: "WASTELAND_ALERT", message: "The next Dead-Fest starts Friday. Bring your worst.", timestamp: now - 600000 },
          { id: "e5", category: "DAILY", message: "Sol claimed 100 daily rations.", timestamp: now - 900000 }
        ];
        DF.Data.state.scratchPrizes = DF.Economy.SCRATCH_POOL.map((p, i) => Object.assign({ id: "p" + i }, p));
        DF.Data.state.levelRewards = [];
        DF.Data.state.gameRequests = [
          { id: "r1", name: "Lethal Company", coverUrl: "", requestedByName: "ASH", createdAt: now - 7200000, isApproved: false },
          { id: "r2", name: "Phasmophobia", coverUrl: "", requestedByName: "ROOK", createdAt: now - 172800000, isApproved: true, decidedByName: "VEX" }
        ];
        DF.Data.state.archives = [{
          id: "a1", name: "Season 1 — First Blood", timestamp: now - 86400000 * 30,
          totalDeaths: 42, totalCoins: 8123, grandTotalAtEnd: 42, survivorCount: 4,
          playerSnapshots: users.map((u, i) => ({
            uid: u.uid, displayName: u.displayName, avatar: u.playerData.avatar,
            avatarFrame: u.playerData.avatarFrame, color: u.playerData.color,
            coins: 5000 - i * 900, totalDeaths: 12 - i * 2,
            gameDeaths: { "Elden Ring": 8 - i, Hades: 4 - i > 0 ? 4 - i : 0 },
            level: 20 - i * 3, prestigeCount: i, title: u.playerData.title
          })),
          notes: "The first season."
        }];

        // a real JPEG thumbnail so the gallery and viewer use the same path as production
        const canvas = document.createElement("canvas");
        canvas.width = 240; canvas.height = 160;
        const g2 = canvas.getContext("2d");
        g2.fillStyle = "#1b1b22"; g2.fillRect(0, 0, 240, 160);
        g2.fillStyle = "#ff4b4b"; g2.font = "bold 28px sans-serif"; g2.fillText("DEAD-FEST", 24, 86);
        const blob = await new Promise((res) => canvas.toBlob(res, "image/jpeg", 0.8));
        const thumb = new Uint8Array(await blob.arrayBuffer());
        DF.Data.state.images = [{
          id: "i1", uploaderUid: "u1", uploaderName: "Vex", caption: "Test transmission",
          createdAt: now - 3600000, width: 240, height: 160, sizeBytes: thumb.length, thumb
        }];

        // avoid a live Firestore read just to exercise the viewer
        DF.Data.getImageData = () => Promise.resolve(thumb);

        const ctx = DF.App.ctx;
        document.getElementById("login").classList.remove("show");
        document.getElementById("login").replaceChildren();
        document.getElementById("app").style.display = "grid";

        const body = () => document.getElementById("screen-body");
        async function step(screen, localKey, localValue) {
          try {
            if (localKey) ctx.local(localKey, localValue);
            DF.App.go(screen);
            await wait(45);
            const el = body();
            out.steps.push({
              screen: screen + (localKey ? ":" + localValue : ""),
              nodes: el.childElementCount,
              chars: el.innerText.trim().length,
              title: (document.getElementById("screen-title") || {}).textContent || "",
              sample: el.innerText.trim().replace(/\\s+/g, " ").slice(0, 90)
            });
          } catch (e) {
            out.steps.push({ screen: screen + (localKey ? ":" + localValue : ""), failed: String(e && (e.stack || e.message || e)) });
          }
        }

        async function dialog(name, fn, closeAfter) {
          try {
            fn();
            await wait(30);
            const viewerEl = document.getElementById("viewer");
            const open = document.getElementById("modal-root").classList.contains("open") ||
              (viewerEl ? viewerEl.classList.contains("open") : false);
            const text = (document.getElementById("modal-root").innerText || "").trim().replace(/\\s+/g, " ").slice(0, 120);
            out.dialogs.push({ name, open, text });
            if (closeAfter !== false) {
              if (viewerEl && viewerEl.classList.contains("open")) DF.Viewer.close();
              else if (document.getElementById("modal-root").classList.contains("open")) closeModal();
              await wait(20);
            }
          } catch (e) {
            out.dialogs.push({ name, failed: String(e && (e.stack || e.message || e)) });
          }
        }

        await step("home");
        await step("players", "playersTab", "survivors");
        await step("players", "playersTab", "wealth");
        await step("players", "playersTab", "prowess");
        await step("shop", "shopTab", "all");
        await step("shop", "shopTab", "combat");
        await step("shop", "shopTab", "economy");
        await step("shop", "shopTab", "insulation");
        await step("shop", "shopTab", "titles");
        await step("wheel");
        ctx.local("wheelResult", { item: global.wheelItems[1], result: { delta: 100, luckyCoinUsed: true, penaltyCleared: false, pendingLeft: 0, bankruptcyLevel: 0 } });
        await step("wheel");
        ctx.local("wheelResult", null);
        await step("scratch");
        ctx.local("scratchPrize", { at: now - 5000, prize: DF.Economy.SCRATCH_POOL[4], coinsWon: 0, convertedItem: "Shield" });
        await step("scratch");
        ctx.local("scratchPrize", null);
        await step("progression");
        await step("more");
        await step("team");
        ctx.local("teamDetailUid", "u2");
        await step("team");
        ctx.local("teamDetailUid", null);
        await step("archives");
        ctx.local("archiveId", "a1");
        await step("archives");
        ctx.local("archiveId", null);
        await step("images");
        await step("intel");
        await step("requests");
        await step("settings", "settingsTab", "general");
        await step("settings", "settingsTab", "system");
        await step("settings", "settingsTab", "diagnostics");
        await step("settings", "settingsTab", "themes");
        for (const m of ["LIVE", "PLAYERS", "SHOP", "SCRATCH", "SECTORS", "EVENT", "OVERLOAD", "WHEEL", "REQUESTS", "GLOBAL", "BACKUP", "BROADCAST"]) {
          await step("admin", "adminModule", m);
        }

        await dialog("sector picker", () => ctx.pickSector(["Hades", "Elden Ring"], true, global));
        await dialog("player dossier", () => ctx.openDossier(users[1]));
        await dialog("admin player detail", () => ctx.openPlayerDetail(users[2]));
        await dialog("curse dialog", () => ctx.openCurse(DF.Economy.SHOP.find((i) => i.id === "double_curse")));
        await dialog("firmware dialog", () => ctx.openFirmwareDialog());
        await dialog("game request dialog", () => ctx.openRequestDialog());
        await dialog("level up", () => DF.Dialogs.levelUp(13, [13], [{ level: 13, type: "frame", label: "Test frame" }]));
        await dialog("daily rations", () => DF.Dialogs.dailyClaim({ amount: 100, newBalance: 1334, upgraded: true, survivalBonusXp: 10 }));
        await dialog("death report", () => DF.Dialogs.deathReport({
          game: "Hades",
          outcome: {
            tax: { finalTax: 42, tier: "Standard", insulationSaved: 8, insulationTier: 2, insuranceUsed: true, insurancePct: 25, insuranceSaved: 10 },
            coinsBefore: 1234, coinsAfter: 1192, spinsAdded: 1, pendingAfter: 2,
            levelBefore: 1, levelAfter: 2
          },
          xp: { gained: 25 }
        }, Promise.resolve(2), global.economy));
        await dialog("image viewer", () => DF.Viewer.open(DF.Data.state.images[0], ctx));
        await wait(80);

        /* ---- action wiring -------------------------------------------------
           The data layer needs a signed-in account, so these tests stub it and
           assert the screens call it with the right arguments and show the right
           readouts. Rendering was covered above. */
        out.actions = [];
        const recordAction = (name, ok, detail) => out.actions.push({ name, ok: !!ok, detail: detail === undefined ? "" : String(detail) });

        const realApplyWheel = DF.Data.applyWheelResult;
        const realScratch = DF.Data.scratchCard;
        const realShop = DF.Data.applyShopItem;
        const realDeath = DF.Data.recordDeath;
        const realReward = DF.Data.payMutualReward;
        const realLog = DF.Data.logEvent;
        const realHandle = DF.Data.changeUsername;
        const realGlobal = DF.Data.updateGlobalState;
        const realPrize = DF.Data.saveScratchPrize;
        DF.Data.logEvent = () => Promise.resolve();

        // wheel: 1.6 s performance spin, then an inline result
        try {
          let wheelCalls = 0;
          DF.Data.applyWheelResult = () => { wheelCalls++; return Promise.resolve({ delta: 50, luckyCoinUsed: false, penaltyCleared: false, pendingLeft: 0, bankruptcyLevel: 0, xp: null }); };
          ctx.applyPerformanceMode(true);
          ctx.local("wheelResult", null);
          ctx.local("wheelAngle", 0);
          DF.App.go("wheel");
          const wheelBtn = document.getElementById("wheel-btn");
          wheelBtn.click();
          const during = document.getElementById("wheel-state").textContent;
          await wait(2700);
          const bodyText = document.getElementById("screen-body").innerText;
          recordAction("wheel spin calls applyWheelResult", wheelCalls === 1, wheelCalls);
          recordAction("wheel shows in-progress state", during === "SPIN IN PROGRESS", during);
          recordAction("wheel inline result",
            bodyText.indexOf("SPIN COMPLETE") >= 0 && bodyText.indexOf("YOU WON 50 COINS") >= 0,
            "state=" + JSON.stringify(ctx.local("wheelResult")) + " text=" + bodyText.replace(/\\s+/g, " ").slice(-140));
          recordAction("wheel result did not open a dialog", !document.getElementById("modal-root").classList.contains("open"), "");
          ctx.applyPerformanceMode(false);
        } catch (e) { recordAction("wheel spin", false, e && (e.stack || e.message)); }
        DF.Data.applyWheelResult = realApplyWheel;

        // scratch: buy, foil wipes, prize stays on the card, no dialog
        try {
          DF.Data.scratchCard = () => Promise.resolve({ prize: { label: "100 Coins", type: "coins", value: "100", color: "#FFD54F" }, coinsWon: 100, xp: null });
          ctx.local("scratchPrize", null);
          ctx.local("scratchBusy", false);
          DF.App.go("scratch");
          const scratchBtn = document.getElementById("screen-body").querySelector("button.btn--tall");
          scratchBtn.click();
          await wait(500);
          const stored = ctx.local("scratchPrize");
          const foil = document.getElementById("scratch-foil");
          recordAction("scratch calls scratchCard", !!stored, stored && stored.prize && stored.prize.label);
          recordAction("scratch shows prize on card", document.getElementById("screen-body").innerText.indexOf("100 COINS") >= 0, document.getElementById("screen-body").innerText.replace(/\\s+/g, " ").slice(-100));
          recordAction("scratch foil is present", !!foil, "");
          await wait(1400);
          const foil2 = document.getElementById("scratch-foil");
          recordAction("scratch foil reveals", foil2 && foil2.classList.contains("gone"), foil2 ? foil2.className : "missing");
          recordAction("scratch result did not open a dialog", !document.getElementById("modal-root").classList.contains("open"), "");
        } catch (e) { recordAction("scratch", false, e && (e.stack || e.message)); }
        DF.Data.scratchCard = realScratch;

        // shop: a plain purchase goes through the confirmation, then the data layer
        try {
          let shopArgs = null;
          DF.Data.applyShopItem = (uid, item, economy, target, sector) => {
            shopArgs = { item: item.id, target: target || null, sector: sector || null };
            return Promise.resolve({ outcome: "APPLIED", xp: null });
          };
          ctx.local("shopTab", "all");
          DF.App.go("shop");
          const luckyCard = Array.from(document.querySelectorAll("#screen-body .item-card")).find((c) => c.innerText.indexOf("LUCKY COIN") >= 0);
          const buy = luckyCard && Array.from(luckyCard.querySelectorAll("button")).find((b) => b.textContent.trim() === "BUY");
          recordAction("shop has a buy button", !!buy, "");
          if (buy) {
            buy.click();
            await wait(40);
            const confirm = Array.from(document.querySelectorAll("#modal-root button")).find((b) => b.textContent.trim() === "BUY");
            recordAction("shop asks for confirmation", !!confirm, "");
            if (confirm) {
              confirm.click();
              await wait(80);
              recordAction("shop purchase reaches data layer", !!shopArgs && shopArgs.item === "lucky_coin", JSON.stringify(shopArgs));
              recordAction("shop outcome is a toast, not a dialog", !document.getElementById("modal-root").classList.contains("open"), "");
            }
          }
        } catch (e) { recordAction("shop purchase", false, e && (e.stack || e.message)); }
        DF.Data.applyShopItem = realShop;

        // death: pinned bar -> sector picker -> recordDeath -> result panel
        try {
          let deathSector = null;
          DF.Data.recordDeath = (uid, sector) => {
            deathSector = sector;
            return Promise.resolve({
              game: sector,
              outcome: {
                tax: { finalTax: 10, tier: "Standard", insulationSaved: 0, insulationTier: 0, insuranceUsed: false, insurancePct: 25, insuranceSaved: 0 },
                coinsBefore: 1000, coinsAfter: 990, spinsAdded: 1, pendingAfter: 1,
                levelBefore: 1, levelAfter: 1
              },
              xp: { gained: 25 }
            });
          };
          DF.Data.payMutualReward = () => Promise.resolve(1);
          ctx.local("shopTab", "all");
          DF.App.go("home");
          const bar = Array.from(document.querySelectorAll("#screen-body button")).find((b) => b.textContent.trim() === "RECORD DEATH");
          recordAction("home pinned bar present", !!bar, "");
          if (bar) {
            bar.click();
            await wait(40);
            const sector = Array.from(document.querySelectorAll("#modal-root .list-row")).find((r) => r.innerText.indexOf("HADES") >= 0);
            recordAction("sector picker lists open sectors", !!sector, "");
            if (sector) {
              sector.click();
              await wait(80);
              recordAction("death reaches data layer", deathSector === "Hades", deathSector);
              const resultText = document.getElementById("modal-root").innerText;
              recordAction("death shows casualty tax panel", resultText.indexOf("CASUALTY TAX") >= 0 && resultText.indexOf("10 coins lost") >= 0, "");
              closeModal();
              await wait(20);
            }
          }
        } catch (e) { recordAction("death flow", false, e && (e.stack || e.message)); }
        DF.Data.recordDeath = realDeath;
        DF.Data.payMutualReward = realReward;

        // settings: handle validation
        try {
          let savedHandle = null;
          DF.Data.changeUsername = (uid, name) => { savedHandle = name; return Promise.resolve({ outcome: "OK" }); };
          ctx.local("settingsTab", "general");
          DF.App.go("settings");
          const handle = document.querySelector("#screen-body input[data-focus-key='handle-input']");
          recordAction("settings has handle field", !!handle, "");
          if (handle) {
            handle.value = "ab";
            Array.from(document.querySelectorAll("#screen-body button")).find((b) => b.textContent.trim() === "SAVE HANDLE").click();
            await wait(30);
            const rejected = document.getElementById("screen-body").innerText.indexOf("3-20 characters") >= 0;
            recordAction("short handle is rejected", rejected && savedHandle === null, savedHandle);
            handle.value = "valid_handle";
            Array.from(document.querySelectorAll("#screen-body button")).find((b) => b.textContent.trim() === "SAVE HANDLE").click();
            await wait(30);
            recordAction("valid handle is saved", savedHandle === "valid_handle", savedHandle);
          }
        } catch (e) { recordAction("handle validation", false, e && (e.stack || e.message)); }
        DF.Data.changeUsername = realHandle;

        // themes + performance persist
        try {
          ctx.local("settingsTab", "themes");
          DF.App.go("settings");
          const cards = Array.from(document.querySelectorAll("#screen-body .card--click"));
          recordAction("all five themes listed", cards.length === 5, cards.length);
          if (cards.length === 5) {
            cards[3].click();
            await wait(20);
            recordAction("theme applies instantly", DF.Prefs.get("theme") === DF.THEMES[3].id, DF.Prefs.get("theme"));
          }
          ctx.applyTheme("default");
        } catch (e) { recordAction("themes", false, e && (e.stack || e.message)); }

        try {
          ctx.local("settingsTab", "system");
          DF.App.go("settings");
          const perfRow = Array.from(document.querySelectorAll("#screen-body .setting-row")).find((r) => r.innerText.indexOf("Performance Mode") >= 0);
          const perfSwitch = perfRow && perfRow.querySelector(".switch");
          recordAction("system has performance switch", !!perfSwitch, "");
          if (perfSwitch) {
            const before = DF.Prefs.get("performanceMode") === true;
            perfSwitch.click();
            await wait(20);
            recordAction("performance mode toggles", (DF.Prefs.get("performanceMode") === true) !== before, DF.Prefs.get("performanceMode"));
            if ((DF.Prefs.get("performanceMode") === true) !== before) { document.querySelector("#screen-body .setting-row .switch").click(); }
          }
          ctx.applyPerformanceMode(false);
        } catch (e) { recordAction("performance toggle", false, e && (e.stack || e.message)); }

        // admin: add a prize through the dialog
        try {
          let prizeArgs = null;
          DF.Data.saveScratchPrize = (prize) => { prizeArgs = prize; return Promise.resolve(); };
          ctx.local("adminModule", "SCRATCH");
          DF.App.go("admin");
          const add = Array.from(document.querySelectorAll("#screen-body button")).find((b) => b.textContent.trim() === "ADD PRIZE");
          recordAction("admin scratch has add", !!add, "");
          if (add) {
            add.click();
            await wait(40);
            const label = document.querySelector("#modal-root input[data-focus-key='prizeLabel']");
            recordAction("prize dialog has label field", !!label, "");
            if (label) {
              label.value = "Test Prize";
              Array.from(document.querySelectorAll("#modal-root button")).find((b) => b.textContent.trim() === "SAVE").click();
              await wait(40);
              recordAction("prize save reaches data layer", !!prizeArgs && prizeArgs.label === "Test Prize", JSON.stringify(prizeArgs));
            }
          }
        } catch (e) { recordAction("admin prize", false, e && (e.stack || e.message)); }
        DF.Data.saveScratchPrize = realPrize;

        // admin: add a sector
        try {
          let globalArgs = null;
          DF.Data.updateGlobalState = (map) => { globalArgs = map; return Promise.resolve(); };
          ctx.local("adminModule", "SECTORS");
          DF.App.go("admin");
          const sectorInput = document.querySelector("#screen-body input[data-focus-key='newSector']");
          recordAction("admin sectors has new field", !!sectorInput, "");
          if (sectorInput) {
            sectorInput.value = "The Void";
            sectorInput.dispatchEvent(new Event("input", { bubbles: true }));
            Array.from(document.querySelectorAll("#screen-body button")).find((b) => b.textContent.trim() === "ADD").click();
            await wait(40);
            const games = globalArgs && globalArgs.games;
            recordAction("sector add reaches data layer", !!games && games.indexOf("The Void") >= 0, JSON.stringify(globalArgs));
          }
        } catch (e) { recordAction("admin sector", false, e && (e.stack || e.message)); }
        DF.Data.updateGlobalState = realGlobal;

        // admin: wheel slice dialog opens with the ten swatches
        try {
          ctx.local("adminModule", "WHEEL");
          DF.App.go("admin");
          const edit = Array.from(document.querySelectorAll("#screen-body button")).find((b) => b.textContent.trim() === "EDIT");
          recordAction("wheel editor has edit buttons", !!edit, "");
          if (edit) {
            edit.click();
            await wait(40);
            const swatches = document.querySelectorAll("#modal-root button[aria-label^='#']");
            recordAction("wheel editor has ten swatches", swatches.length === 10, swatches.length);
            closeModal();
            await wait(20);
          }
        } catch (e) { recordAction("admin wheel editor", false, e && (e.stack || e.message)); }

        DF.Data.logEvent = realLog;

        out.htmlErrors = out.asyncErrors.slice();
        out.consoleTitle = document.title;
        out.appDisplay = document.getElementById("app").style.display;
        return out;
      })()`);

      const failures = report.steps.filter((s) => s.failed || s.nodes === 0 || s.chars === 0);
      const dialogFailures = report.dialogs.filter((d) => d.failed || !d.open);
      const actionFailures = (report.actions || []).filter((a) => !a.ok);
      const errorLines = report.asyncErrors || [];
      const ok = failures.length === 0 && dialogFailures.length === 0 && actionFailures.length === 0 && consoleErrors.length === 0 && errorLines.length === 0 && pageErrors.length === 0;
      finish(ok ? 0 : 1, {
        ok,
        steps: report.steps,
        dialogs: report.dialogs,
        actions: report.actions,
        consoleErrors,
        asyncErrors: errorLines,
        pageErrors
      });
    } catch (e) {
      finish(1, { ok: false, harnessError: String(e && (e.stack || e)), consoleErrors, pageErrors });
    }
  });
});

setTimeout(() => finish(1, { ok: false, timeout: true, consoleErrors, pageErrors }), 90000);
