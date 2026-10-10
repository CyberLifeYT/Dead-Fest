package com.deadfest

import com.deadfest.domain.Presence
import org.junit.Assert.*
import org.junit.Test

class PresenceTest {
    private val now = 1_000_000_000L

    @Test fun onlineWithFreshHeartbeat() {
        assertTrue(Presence.isOnline(true, now - 30_000, now))
    }

    @Test fun offlineWhenAppLeftTheScreen() {
        assertFalse(Presence.isOnline(false, now - 5_000, now))
    }

    @Test fun killedAppGoesOfflineAfterHeartbeatStops() {
        // App was killed without saying goodbye: the flag is stuck on true, but the heartbeat is stale.
        assertFalse(Presence.isOnline(true, now - Presence.STALE_AFTER_MS - 1, now))
    }

    @Test fun neverHeartbeatIsOffline() {
        // Pre-2.0.5 clients set isOnline but never a heartbeat: no ghost "online" survivors.
        assertFalse(Presence.isOnline(true, 0, now))
    }

    @Test fun clockSkewAheadStillCountsAsOnline() {
        assertTrue(Presence.isOnline(true, now + 20_000, now))
    }

    @Test fun lastSeenLabels() {
        assertEquals("just now", Presence.lastSeenLabel(now - 20_000, now))
        assertEquals("5m ago", Presence.lastSeenLabel(now - 5 * 60_000, now))
        assertEquals("3h ago", Presence.lastSeenLabel(now - 3 * 3_600_000L, now))
        assertEquals("2d ago", Presence.lastSeenLabel(now - 2 * 86_400_000L, now))
        assertEquals("never", Presence.lastSeenLabel(0, now))
    }
}
