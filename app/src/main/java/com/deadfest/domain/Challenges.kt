package com.deadfest.domain

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.random.Random

/*
 * Weekly challenges (v2.1.1). Every survivor gets the same four challenges each week, picked
 * from a pool by the ISO week number, so the group competes on the same goals. Progress is
 * counted per metric on the profile and resets when the week rolls over (Monday 00:00 local).
 */

/** Things the app counts for challenges. [key] is what's stored in `playerData.weeklyStats`. */
enum class ChallengeMetric(val key: String) {
    SPINS("spins"),
    SCRATCHES("scratches"),
    PURCHASES("purchases"),
    CURSES("curses"),
    SURVIVED_DAYS("survivedDays"),
    RATIONS("rations"),
    CHATS("chats"),
    IMAGES("images"),
    COMMENTS("comments"),
    REACTIONS("reactions")
}

data class ChallengeDef(
    val id: String,
    val title: String,
    val metric: ChallengeMetric,
    val target: Long,
    val coins: Long,
    val xp: Long
)

object Challenges {
    const val PER_WEEK = 4

    val POOL: List<ChallengeDef> = listOf(
        ChallengeDef("spin_10", "Spin the Wheel 10 times", ChallengeMetric.SPINS, 10, 120, 60),
        ChallengeDef("spin_25", "Spin the Wheel 25 times", ChallengeMetric.SPINS, 25, 300, 150),
        ChallengeDef("scratch_5", "Scratch 5 cards", ChallengeMetric.SCRATCHES, 5, 100, 50),
        ChallengeDef("buy_3", "Make 3 Black Market purchases", ChallengeMetric.PURCHASES, 3, 100, 50),
        ChallengeDef("curse_2", "Land 2 curses", ChallengeMetric.CURSES, 2, 150, 75),
        ChallengeDef("survive_3", "Survive 3 days (claim rations without dying)", ChallengeMetric.SURVIVED_DAYS, 3, 250, 120),
        ChallengeDef("rations_5", "Claim your rations 5 days", ChallengeMetric.RATIONS, 5, 150, 60),
        ChallengeDef("chat_15", "Send 15 chat messages", ChallengeMetric.CHATS, 15, 80, 40),
        ChallengeDef("image_2", "Share 2 images", ChallengeMetric.IMAGES, 2, 100, 50),
        ChallengeDef("comment_5", "Comment on 5 images", ChallengeMetric.COMMENTS, 5, 80, 40),
        ChallengeDef("react_10", "React to 10 images", ChallengeMetric.REACTIONS, 10, 60, 30)
    )

    /** Monday-start ISO week calendar in the phone's time zone. */
    private fun isoCalendar(millis: Long): GregorianCalendar = GregorianCalendar(Locale.ROOT).apply {
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
        timeInMillis = millis
    }

    /** "2026-W43": the ISO week [millis] falls in. */
    fun weekKey(millis: Long): String {
        val c = isoCalendar(millis)
        return String.format(Locale.US, "%d-W%02d", c.weekYear, c.get(Calendar.WEEK_OF_YEAR))
    }

    /** Next Monday 00:00 after [millis]: when this week's challenges reset. */
    fun weekEndsAt(millis: Long): Long = isoCalendar(millis).apply {
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= millis) add(Calendar.WEEK_OF_YEAR, 1)
    }.timeInMillis

    /** This week's challenges: the same four for everyone, never two on the same metric. */
    fun forWeek(weekKey: String): List<ChallengeDef> {
        val rnd = Random(weekKey.hashCode())
        val picked = mutableListOf<ChallengeDef>()
        for (c in POOL.shuffled(rnd)) {
            if (picked.none { it.metric == c.metric }) picked += c
            if (picked.size == PER_WEEK) break
        }
        return picked
    }

    fun byId(id: String): ChallengeDef? = POOL.firstOrNull { it.id == id }

    /** Progress for [c] given this week's stats, capped at the target. */
    fun progress(c: ChallengeDef, stats: Map<String, Long>): Long = (stats[c.metric.key] ?: 0L).coerceAtMost(c.target)

    /** Finished-but-unclaimed challenges this week. */
    fun claimable(weekKey: String, storedWeek: String, stats: Map<String, Long>, claimed: List<String>): List<ChallengeDef> =
        if (storedWeek != weekKey) emptyList()
        else forWeek(weekKey).filter { it.id !in claimed && (stats[it.metric.key] ?: 0L) >= it.target }
}
