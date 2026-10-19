package com.deadfest

import com.deadfest.data.repository.TheGamesDbClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the game-database response parsing.
 *
 * The service is an external dependency that has changed shape before and is currently
 * unreachable, so the parser is written defensively: a missing or renamed field must cost one row,
 * never the whole search. These tests pin the documented v1.0 shape and confirm the failure mode
 * for anything unexpected is "empty list", which the request screen handles as manual entry.
 */
class TheGamesDbParseTest {

    private val client = TheGamesDbClient(apiKey = "test-key")

    // ------------------------------------------------------------ search

    @Test fun parsesTheDocumentedSearchShape() {
        val json = """
            {
              "code": 200,
              "status": "Success",
              "data": {
                "count": 2,
                "games": [
                  {
                    "id": 1,
                    "game_title": "Dead Island",
                    "release_date": "2011-09-06",
                    "platform": { "id": 1, "name": "Xbox 360" }
                  },
                  {
                    "id": 2,
                    "game_title": "Dead Island 2",
                    "release_date": "",
                    "platform": { "id": 2, "name": "PC" }
                  }
                ]
              }
            }
        """.trimIndent()

        val games = client.parseGames(org.json.JSONObject(json))

        assertEquals(2, games.size)
        assertEquals("1", games[0].id)
        assertEquals("Dead Island", games[0].title)
        assertEquals("Xbox 360", games[0].platform)
        assertEquals("2011-09-06", games[0].releaseDate)
        assertEquals("Dead Island 2", games[1].title)
        // An empty release date is simply absent, not an empty string in the row.
        assertNull(games[1].releaseDate)
    }

    @Test fun aMissingGameStillLeavesTheOthers() {
        // One malformed entry must not cost the whole search.
        val json = """
            { "data": { "games": [
              { "id": 1, "game_title": "Good Game" },
              { "id": 2 },
              { "game_title": "No Id" },
              { "id": 3, "game_title": "Also Good" }
            ] } }
        """.trimIndent()

        val games = client.parseGames(org.json.JSONObject(json))

        assertEquals(2, games.size)
        assertEquals("Good Game", games[0].title)
        assertEquals("Also Good", games[1].title)
    }

    @Test fun missingOrMalformedPayloadYieldsNothingRatherThanThrowing() {
        // Every one of these is a shape the API could plausibly return instead of games.
        assertEquals(emptyList<Any>(), client.parseGames(org.json.JSONObject("{}")))
        assertEquals(emptyList<Any>(), client.parseGames(org.json.JSONObject("""{"data":{}}""")))
        assertEquals(emptyList<Any>(), client.parseGames(org.json.JSONObject("""{"data":{"games":null}}""")))
        assertEquals(emptyList<Any>(), client.parseGames(org.json.JSONObject("""{"data":{"games":[]}}""")))
    }

    @Test fun aGameWithNoTitleIsSkipped() {
        // A title is what the picker renders, so a row without one is useless.
        val json = """{ "data": { "games": [ { "id": 7, "game_title": "" } ] } }"""
        assertEquals(emptyList<Any>(), client.parseGames(org.json.JSONObject(json)))
    }

    // ------------------------------------------------------------ covers

    @Test fun parsesBoxartAndJoinsItToTheBaseUrl() {
        val json = """
            {
              "data": {
                "1": {
                  "boxart": [
                    { "side": "front", "url": "/platforms/boxart/abc.png" },
                    { "side": "back", "url": "/platforms/boxart/abc-back.png" }
                  ]
                }
              },
              "image": { "base_url": { "original": "https://cdn.example.test/images/original" } }
            }
        """.trimIndent()

        val covers = client.parseCovers(org.json.JSONObject(json))

        // Only the front art, and joined to the base URL the payload itself supplied.
        assertEquals(1, covers.size)
        assertEquals("https://cdn.example.test/images/original/platforms/boxart/abc.png", covers["1"])
    }

    @Test fun acceptsABaseUrlAtTheRoot() {
        // The base URL has appeared at the top level in some API versions.
        val json = """
            {
              "data": { "5": { "boxart": [ { "side": "front", "url": "box/5.png" } ] } },
              "image_base_url": "https://cdn.example.test/"
            }
        """.trimIndent()

        val covers = client.parseCovers(org.json.JSONObject(json))
        assertEquals("https://cdn.example.test/box/5.png", covers["5"])
    }

    @Test fun anAlreadyAbsoluteUrlIsLeftAlone() {
        val json = """
            {
              "data": { "9": { "boxart": [ { "side": "front", "url": "https://x.test/a.png" } ] } },
              "image": { "base_url": { "original": "https://cdn.example.test/" } }
            }
        """.trimIndent()

        val covers = client.parseCovers(org.json.JSONObject(json))
        assertEquals("https://x.test/a.png", covers["9"])
    }

    @Test fun gamesWithoutFrontArtGetNoCover() {
        val json = """
            { "data": { "3": { "boxart": [ { "side": "back", "url": "/b.png" } ] } } }
        """.trimIndent()

        assertEquals(emptyMap<String, String>(), client.parseCovers(org.json.JSONObject(json)))
    }

    // ------------------------------------------------------------ resilience

    @Test fun theClientToleratesAnEmptyKey() {
        // No key configured is a supported outcome (the request screen falls back to manual entry),
        // so constructing a client with one must be safe.
        TheGamesDbClient(apiKey = "")
        TheGamesDbClient(apiKey = "k")
    }
}
