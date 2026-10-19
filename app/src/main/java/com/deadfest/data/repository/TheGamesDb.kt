package com.deadfest.data.repository

import com.deadfest.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One game as returned by the lookup: everything a picker row needs. */
data class GameInfo(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val platform: String?,
    val releaseDate: String?
)

/**
 * Client for the TheGamesDB public API (v1.0).
 *
 * A lookup is two calls: search by title, then fetch the box art for the hits. Both are
 * best-effort — a dead service, an unconfigured key or a phone in airplane mode all yield an empty
 * list rather than an exception, because the request flow has a manual-entry fallback and "this
 * database has nothing for that name" is a normal outcome, not a failure.
 *
 * **The key ships inside the APK.** It is a client-side key, so anyone who unpacks the build can
 * read it. Treat it as public, and scope or rotate it on the provider's dashboard if that ever
 * becomes possible.
 */
class TheGamesDbClient(private val apiKey: String = BuildConfig.THEGAMESDB_API_KEY) {

    companion object {
        private const val BASE = "https://api.thegamesdb.net/v1.0"
        private const val TIMEOUT_MS = 8_000
        private const val MAX_RESULTS = 8

        /** The configured key, so the UI can hide the search box entirely when there is none. */
        val configuredApiKey: String
            get() = BuildConfig.THEGAMESDB_API_KEY
    }

    /** Searches the database by title, returning at most [limit] hits with covers filled in. */
    suspend fun searchGames(query: String, limit: Int = MAX_RESULTS): List<GameInfo> =
        withContext(Dispatchers.IO) {
            val games = fetchGames(query)
            if (games.isEmpty()) return@withContext emptyList()
            val covers = fetchCovers(games.map { it.id })
            games.map { it.copy(coverUrl = covers[it.id]) }.take(limit)
        }

    private fun fetchGames(query: String): List<GameInfo> {
        val url = "$BASE/Games/ByGameName?apikey=${enc(apiKey)}&name=${enc(query)}" +
            "&fields=game_title,release_date,platform,overview"
        val body = httpGet(url) ?: return emptyList()
        return parseGames(JSONObject(body))
    }

    private fun fetchCovers(ids: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val url = "$BASE/Games/Images?apikey=${enc(apiKey)}&games_id=${enc(ids.joinToString(","))}"
        val body = httpGet(url) ?: return emptyMap()
        return parseCovers(JSONObject(body))
    }

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            // Any failure means "no results", which the caller handles as a normal outcome.
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /**
     * Pulls the game list out of the documented v1.0 payload.
     *
     * Written defensively: the service is an external dependency whose exact response shape has
     * drifted before, and a missing or renamed field must cost one row, not the whole search.
     */
    internal fun parseGames(root: JSONObject): List<GameInfo> {
        val data = root.optJSONObject("data") ?: return emptyList()
        val games = data.optJSONArray("games") ?: return emptyList()
        val out = ArrayList<GameInfo>(games.length())
        for (i in 0 until games.length()) {
            val g = games.optJSONObject(i) ?: continue
            val id = g.optString("id").takeIf { it.isNotBlank() } ?: continue
            val title = g.optString("game_title").takeIf { it.isNotBlank() } ?: continue
            val platform = g.optJSONObject("platform")?.optString("name")?.takeIf { it.isNotBlank() }
            out += GameInfo(
                id = id,
                title = title,
                coverUrl = null,
                platform = platform,
                releaseDate = g.optString("release_date").takeIf { it.isNotBlank() }
            )
        }
        return out
    }

    /**
     * Maps game id -> box art URL.
     *
     * The images endpoint returns art paths relative to a base URL carried in the same payload, so
     * both are read here and joined. Games with no front box art simply get no cover — the picker
     * row shows a placeholder.
     */
    internal fun parseCovers(root: JSONObject): Map<String, String> {
        val data = root.optJSONObject("data") ?: return emptyMap()
        // The base URL has moved between API versions; accept either location.
        val baseUrl = root.optJSONObject("image")?.optJSONObject("base_url")?.optString("original")
            ?.takeIf { it.isNotBlank() }
            ?: root.optString("image_base_url").takeIf { it.isNotBlank() }
        val out = HashMap<String, String>()
        for (key in data.keys()) {
            val game = data.optJSONObject(key) ?: continue
            val boxart = game.optJSONArray("boxart") ?: continue
            for (i in 0 until boxart.length()) {
                val art = boxart.optJSONObject(i) ?: continue
                if (art.optString("side") != "front") continue
                val path = art.optString("url").takeIf { it.isNotBlank() } ?: continue
                out[key] = when {
                    path.startsWith("http") -> path
                    baseUrl.isNullOrBlank() -> path
                    else -> baseUrl.trimEnd('/') + "/" + path.removePrefix("/")
                }
                break
            }
        }
        return out
    }
}
