package com.deadfest.domain

/**
 * The games the Xbox Death Detector supports (v2.1.3).
 *
 * The ids are shared with the detector (detector/deadfest_detector/games.py) and the Firestore
 * rules (`deathGames()`), so they must never change. [sectorName] is the default sector a verified
 * death is counted in; an admin can pick another sector when verifying.
 */
data class DeathGame(val id: String, val sectorName: String)

object DeathGames {
    val ALL = listOf(
        DeathGame("dying_light", "Dying Light"),
        DeathGame("dying_light_2", "Dying Light 2"),
        DeathGame("dying_light_the_beast", "Dying Light: The Beast"),
        DeathGame("world_war_z", "World War Z"),
        DeathGame("dead_island", "Dead Island"),
        DeathGame("dead_island_riptide", "Dead Island Riptide"),
        DeathGame("dead_island_2", "Dead Island 2"),
        DeathGame("state_of_decay_2", "State of Decay 2")
    )

    private val byId = ALL.associateBy { it.id }

    fun byId(id: String): DeathGame? = byId[id]

    fun name(id: String): String = byId[id]?.sectorName ?: id

    fun isKnown(id: String): Boolean = id in byId

    /**
     * The sector a death in [gameId] should count towards: the configured sector whose name
     * matches the game (ignoring case and punctuation), else the game's own name.
     */
    fun defaultSector(gameId: String, sectors: List<String>): String {
        val name = name(gameId)
        val key = normalize(name)
        return sectors.firstOrNull { normalize(it) == key } ?: name
    }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
