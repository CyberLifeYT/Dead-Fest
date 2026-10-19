/* =============================================================================
   Dead-Fest desktop — game database lookup
   -----------------------------------------------------------------------------
   A port of data/repository/TheGamesDb.kt. The service has been unreachable
   for a while, so every failure mode resolves to "no results" and the request
   screen offers manual entry instead. That is the same design decision the
   mobile app made, and it is why the feature works either way.
   ========================================================================== */
window.DF = window.DF || {};

DF.TheGamesDb = (function () {
  const BASE = "https://api.thegamesdb.net/v1.0";
  const TIMEOUT_MS = 8000;

  function apiKey() { return DF.THEGAMESDB_API_KEY || ""; }

  async function httpGet(url) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    try {
      const res = await fetch(url, { signal: controller.signal });
      if (!res.ok) return null;
      return await res.text();
    } catch (e) {
      return null;
    } finally {
      clearTimeout(timer);
    }
  }

  function parseGames(root) {
    const data = root && root.data;
    if (!data || !Array.isArray(data.games)) return [];
    const out = [];
    for (const g of data.games) {
      if (!g) continue;
      const id = g.id !== undefined && g.id !== null ? String(g.id) : "";
      const title = g.game_title || "";
      if (!id || !title) continue;
      out.push({
        id,
        title,
        coverUrl: null,
        platform: (g.platform && g.platform.name) || null,
        releaseDate: g.release_date || null
      });
    }
    return out;
  }

  function parseCovers(root) {
    const data = root && root.data;
    if (!data) return {};
    const image = root.image && root.image.base_url ? root.image.base_url.original : null;
    const baseUrl = image || root.image_base_url || null;
    const out = {};
    for (const key of Object.keys(data)) {
      const game = data[key];
      if (!game || !Array.isArray(game.boxart)) continue;
      for (const art of game.boxart) {
        if (!art || art.side !== "front") continue;
        const path = art.url;
        if (!path) continue;
        out[key] = path.startsWith("http")
          ? path
          : (baseUrl ? baseUrl.replace(/\/+$/, "") + "/" + path.replace(/^\/+/, "") : path);
        break;
      }
    }
    return out;
  }

  /**
   * Searches the database by title.
   * @returns {Promise<{items: Array, offline: boolean}>} offline means the lookup
   *          could not be completed, so the caller should offer manual entry.
   */
  async function search(query) {
    const key = apiKey();
    if (!key || !query || query.trim().length < 2) return { items: [], offline: !key };

    const url = `${BASE}/Games/ByGameName?apikey=${encodeURIComponent(key)}&name=${encodeURIComponent(query.trim())}` +
      `&fields=game_title,release_date,platform,overview`;
    const body = await httpGet(url);
    if (!body) return { items: [], offline: true };

    let parsed;
    try { parsed = JSON.parse(body); } catch (e) { return { items: [], offline: true }; }
    let games = parseGames(parsed);
    if (!games.length) return { items: [], offline: false };

    const ids = games.map((g) => g.id);
    const imgUrl = `${BASE}/Games/Images?apikey=${encodeURIComponent(key)}&games_id=${encodeURIComponent(ids.join(","))}`;
    const imgBody = await httpGet(imgUrl);
    if (imgBody) {
      try {
        const covers = parseCovers(JSON.parse(imgBody));
        games = games.map((g) => Object.assign({}, g, { coverUrl: covers[g.id] || null }));
      } catch (e) { /* art is optional */ }
    }
    return { items: games, offline: false };
  }

  return { search };
})();
