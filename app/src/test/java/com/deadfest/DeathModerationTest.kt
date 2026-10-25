package com.deadfest

import com.deadfest.domain.CountChange
import com.deadfest.domain.DeathEvidenceStatus
import com.deadfest.domain.DeathEventType
import com.deadfest.domain.DeathGames
import com.deadfest.domain.DeathModeration
import com.deadfest.domain.EvidenceState
import com.deadfest.domain.ModerationAction
import com.deadfest.domain.ModerationPlan
import com.deadfest.domain.ModerationProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Death moderation against an in-memory ledger that applies plans exactly as the repository does
 * (record fields + DeathModeration.newDeathCounts), with real before/after balances.
 *
 * The same guarantees are enforced server-side and tested against the Firestore emulator in
 * tools/rules-tests (the "death evidence" tests).
 */
class DeathModerationTest {

    private data class Player(var coins: Long, val deaths: MutableMap<String, Long> = mutableMapOf())

    /** A tiny stand-in for Firestore: one evidence record and the players, updated all-or-nothing. */
    private class Ledger(var record: EvidenceState, val players: MutableMap<String, Player>) {
        var now = 1_000L

        fun run(action: ModerationAction, failCommit: Boolean = false): ModerationPlan {
            val plan = DeathModeration.plan(record, action, "admin", "Admin", now++)
            if (plan !is ModerationPlan.Apply) return plan
            // Stage everything, then commit at once, the way a transaction does.
            val counts = DeathModeration.newDeathCounts(plan.counts) { uid, sector -> players.getValue(uid).deaths[sector] ?: 0L }
            val f = plan.fields
            val next = record.copy(
                status = f["status"] as? String ?: record.status,
                counted = f["counted"] as? Boolean ?: record.counted,
                countedSector = f["countedSector"] as? String ?: record.countedSector,
                subjectUid = f["subjectUid"] as? String ?: record.subjectUid,
                gameId = f["gameId"] as? String ?: record.gameId,
                eventType = f["eventType"] as? String ?: record.eventType,
                playerLabel = f["playerLabel"] as? String ?: record.playerLabel,
                updatedAt = f["updatedAt"] as Long
            )
            if (failCommit) throw IllegalStateException("network dropped mid-commit")
            counts.forEach { (key, value) -> players.getValue(key.first).deaths[key.second] = value }
            record = next
            return plan
        }
    }

    private lateinit var ledger: Ledger
    private val sector = "Dying Light 2"

    @Before fun setUp() {
        ledger = Ledger(
            EvidenceState(
                status = DeathEvidenceStatus.PENDING, counted = false, countedSector = "", subjectUid = "owner",
                submitterUid = "owner", gameId = "dying_light_2", eventType = DeathEventType.SELF, playerLabel = "",
                updatedAt = 500L
            ),
            mutableMapOf("owner" to Player(coins = 777, deaths = mutableMapOf(sector to 4L)), "mate" to Player(coins = 50))
        )
    }

    private fun coins(uid: String = "owner") = ledger.players.getValue(uid).coins
    private fun deaths(uid: String = "owner", s: String = sector) = ledger.players.getValue(uid).deaths[s] ?: 0L

    @Test fun verifyingCountsOnceAndLeavesCoinsUnchanged() {
        val before = coins()
        ledger.run(ModerationAction.Verify(sector))
        assertEquals(before, coins())
        assertEquals(5L, deaths())
        assertEquals(DeathEvidenceStatus.VERIFIED, ledger.record.status)
        assertTrue(ledger.record.counted)
    }

    @Test fun repeatedVerifyRequestsCannotDoubleCount() {
        ledger.run(ModerationAction.Verify(sector))
        repeat(5) {
            val again = ledger.run(ModerationAction.Verify(sector))
            assertEquals(ModerationPlan.NoOp(ModerationProblem.ALREADY_VERIFIED), again)
        }
        assertEquals(5L, deaths())
        assertEquals(777L, coins())
    }

    @Test fun rejectingAPendingDeathChangesNothingButTheStatus() {
        ledger.run(ModerationAction.Reject("Loading screen"))
        assertEquals(777L, coins())
        assertEquals(4L, deaths())
        assertEquals(DeathEvidenceStatus.REJECTED, ledger.record.status)
    }

    @Test fun rejectingACountedDeathTakesItOffWithoutTouchingCoins() {
        ledger.run(ModerationAction.Verify(sector))
        ledger.run(ModerationAction.Reject("Duplicate"))
        assertEquals(4L, deaths())
        assertEquals(777L, coins())
        assertTrue(!ledger.record.counted)
    }

    @Test fun removingACountedDeathLeavesTheBalanceUnchanged() {
        ledger.run(ModerationAction.Verify(sector))
        ledger.run(ModerationAction.Remove("Wrong player"))
        assertEquals(4L, deaths())
        assertEquals(777L, coins())
        assertEquals(DeathEvidenceStatus.REMOVED, ledger.record.status)
        assertEquals(ModerationPlan.NoOp(ModerationProblem.ALREADY_REMOVED), ledger.run(ModerationAction.Remove("again")))
        assertEquals(4L, deaths())
    }

    @Test fun restoringARejectedDeathCountsItExactlyOnce() {
        ledger.run(ModerationAction.Verify(sector))
        ledger.run(ModerationAction.Reject("oops"))
        ledger.run(ModerationAction.Restore)
        assertEquals(5L, deaths())
        assertEquals(DeathEvidenceStatus.VERIFIED, ledger.record.status)
        repeat(3) { ledger.run(ModerationAction.Restore) }
        assertEquals(5L, deaths())
        assertEquals(777L, coins())
    }

    @Test fun restoringANeverVerifiedRecordReturnsItToPendingUncounted() {
        ledger.run(ModerationAction.Reject("blurry"))
        ledger.run(ModerationAction.Restore)
        assertEquals(DeathEvidenceStatus.PENDING, ledger.record.status)
        assertEquals(4L, deaths())
    }

    @Test fun correctionsToAnUncountedRecordCannotMakeRestoreCountIt() {
        ledger.run(ModerationAction.Correct(sector = "Dead Island 2"))
        ledger.run(ModerationAction.Remove("not a death"))
        ledger.run(ModerationAction.Restore)
        assertEquals(DeathEvidenceStatus.PENDING, ledger.record.status)
        assertEquals(4L, deaths())
    }

    @Test fun movingACountedDeathToAnotherSectorIsMinusOnePlusOne() {
        ledger.run(ModerationAction.Verify(sector))
        val plan = ledger.run(ModerationAction.Correct(gameId = "dead_island_2", sector = "Dead Island 2")) as ModerationPlan.Apply
        assertEquals(listOf(CountChange("owner", sector, -1), CountChange("owner", "Dead Island 2", +1)), plan.counts)
        assertEquals(4L, deaths())
        assertEquals(1L, deaths(s = "Dead Island 2"))
        assertEquals(777L, coins())
    }

    @Test fun linkingATeammateDeathCountsItForThemOnly() {
        ledger.record = ledger.record.copy(eventType = DeathEventType.TEAMMATE, subjectUid = "", playerLabel = "Mate")
        assertEquals(ModerationPlan.Invalid(ModerationProblem.NEED_PLAYER), ledger.run(ModerationAction.Verify(sector)))
        ledger.run(ModerationAction.Verify(sector, subjectUid = "mate"))
        assertEquals(1L, deaths("mate"))
        assertEquals(4L, deaths("owner"))
        assertEquals(50L, coins("mate"))
        assertEquals(777L, coins("owner"))
    }

    @Test fun aCountedDeathCannotBeUnlinkedFromItsPlayer() {
        ledger.run(ModerationAction.Verify(sector))
        assertEquals(ModerationPlan.Invalid(ModerationProblem.CANNOT_UNLINK_COUNTED), ledger.run(ModerationAction.Correct(subjectUid = "")))
    }

    @Test fun countsNeverGoNegative() {
        ledger.run(ModerationAction.Verify(sector))
        ledger.players.getValue("owner").deaths[sector] = 0L    // someone else zeroed it meanwhile
        ledger.run(ModerationAction.Reject("dup"))
        assertEquals(0L, deaths())
    }

    @Test fun aFailedCommitChangesNeitherTheRecordNorTheCount() {
        val before = ledger.record
        try {
            ledger.run(ModerationAction.Verify(sector), failCommit = true)
        } catch (_: IllegalStateException) {
        }
        assertEquals(before, ledger.record)
        assertEquals(4L, deaths())
        assertEquals(777L, coins())
    }

    @Test fun rejectAndRemoveNeedAReason() {
        assertEquals(ModerationPlan.Invalid(ModerationProblem.NEED_REASON), ledger.run(ModerationAction.Reject("  ")))
        assertEquals(ModerationPlan.Invalid(ModerationProblem.NEED_REASON), ledger.run(ModerationAction.Remove("")))
    }

    @Test fun badCorrectionsAreRefused() {
        assertEquals(ModerationPlan.Invalid(ModerationProblem.BAD_GAME), ledger.run(ModerationAction.Correct(gameId = "halo")))
        assertEquals(ModerationPlan.Invalid(ModerationProblem.BAD_TYPE), ledger.run(ModerationAction.Correct(eventType = "murder")))
        assertEquals(ModerationPlan.NoOp(ModerationProblem.NOTHING_TO_CHANGE), ledger.run(ModerationAction.Correct()))
    }

    @Test fun planWritesOnlyRecordFieldsAndDeathCountsNeverCoins() {
        val plan = ledger.run(ModerationAction.Verify(sector)) as ModerationPlan.Apply
        assertTrue(plan.fields.keys.none { it.contains("coin", ignoreCase = true) })
        assertTrue(plan.counts.all { it.delta == 1 || it.delta == -1 })
    }

    @Test fun everyDecisionGetsANewerTimestampForItsAuditEntry() {
        ledger.now = 100L   // the phone's clock is behind the record
        val plan = ledger.run(ModerationAction.Reject("x")) as ModerationPlan.Apply
        assertTrue(plan.at > 500L)
        assertEquals(plan.at, plan.fields["updatedAt"])
    }

    @Test fun defaultSectorMatchesTheConfiguredSectorName() {
        // The admin's own spelling of the sector wins, so the death lands in the existing sector.
        assertEquals("DYING LIGHT 2", DeathGames.defaultSector("dying_light_2", listOf("dead island 2", "DYING LIGHT 2")))
        assertEquals("Dying Light The Beast", DeathGames.defaultSector("dying_light_the_beast", listOf("Dying Light The Beast")))
        assertEquals("State of Decay 2", DeathGames.defaultSector("state_of_decay_2", emptyList()))
    }
}
