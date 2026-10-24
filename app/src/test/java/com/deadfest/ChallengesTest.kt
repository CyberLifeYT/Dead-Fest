package com.deadfest

import com.deadfest.domain.ChallengeMetric
import com.deadfest.domain.Challenges
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class ChallengesTest {

    private fun millis(y: Int, m: Int, d: Int, h: Int = 12) =
        GregorianCalendar(y, m - 1, d, h, 0).timeInMillis

    @Test fun weekKeyFollowsIsoWeeks() {
        // 2026-10-24 is a Saturday in ISO week 43.
        assertEquals("2026-W43", Challenges.weekKey(millis(2026, 10, 24)))
        // Monday starts a new week; Sunday still belongs to the previous one.
        assertEquals("2026-W43", Challenges.weekKey(millis(2026, 10, 25)))
        assertEquals("2026-W44", Challenges.weekKey(millis(2026, 10, 26)))
        // ISO week-year: 1 Jan 2027 (a Friday) is still in 2026's last week.
        assertEquals("2026-W53", Challenges.weekKey(millis(2027, 1, 1)))
    }

    @Test fun weekEndsNextMondayAtMidnight() {
        val end = Challenges.weekEndsAt(millis(2026, 10, 24))
        val c = GregorianCalendar().apply { timeInMillis = end }
        assertEquals(Calendar.MONDAY, c.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(26, c.get(Calendar.DAY_OF_MONTH))
        // Exactly at Monday 00:00 the *next* Monday is the end.
        assertTrue(Challenges.weekEndsAt(end) > end)
    }

    @Test fun everyoneGetsTheSameFourDistinctChallenges() {
        val a = Challenges.forWeek("2026-W43")
        val b = Challenges.forWeek("2026-W43")
        assertEquals(a, b)
        assertEquals(Challenges.PER_WEEK, a.size)
        assertEquals("never two on the same metric", a.size, a.map { it.metric }.toSet().size)
    }

    @Test fun challengesRotateBetweenWeeks() {
        val weeks = (40..52).map { Challenges.forWeek("2026-W$it").map { c -> c.id }.toSet() }
        assertTrue("the line-up should change across weeks", weeks.toSet().size > 1)
    }

    @Test fun progressAndClaimable() {
        val week = "2026-W43"
        val c = Challenges.forWeek(week).first()
        val done = mapOf(c.metric.key to c.target + 5)
        assertEquals(c.target, Challenges.progress(c, done)) // capped
        assertTrue(c in Challenges.claimable(week, week, done, emptyList()))
        assertFalse(c in Challenges.claimable(week, week, done, listOf(c.id)))      // already claimed
        assertTrue(Challenges.claimable(week, "2026-W42", done, emptyList()).isEmpty()) // last week's stats
    }

    @Test fun poolIdsAreUniqueAndMetricsKnown() {
        assertEquals(Challenges.POOL.size, Challenges.POOL.map { it.id }.toSet().size)
        assertTrue(Challenges.POOL.all { it.metric in ChallengeMetric.entries && it.target > 0 && it.coins > 0 })
    }
}
