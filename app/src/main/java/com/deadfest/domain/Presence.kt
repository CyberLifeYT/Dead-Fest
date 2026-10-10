package com.deadfest.domain

import com.deadfest.data.model.PlayerData

/**
 * Online / offline status.
 *
 * - The app marks a survivor ONLINE when it comes to the foreground and refreshes
 *   `playerData.lastSeen` (a server timestamp) every 60 seconds while it stays open.
 * - It marks them OFFLINE the moment the app leaves the screen.
 * - If the app is killed, crashes or loses signal before it can say goodbye, the heartbeat
 *   stops and the survivor drops to offline once [STALE_AFTER_MS] passes. No ghost "online"
 *   players, even without a server-side disconnect hook.
 */
object Presence {
    const val HEARTBEAT_MS = 60_000L
    /** Heartbeat interval plus slack for slow networks and clock skew. */
    const val STALE_AFTER_MS = 150_000L

    fun isOnline(isOnlineFlag: Boolean, lastSeenMs: Long, now: Long): Boolean =
        isOnlineFlag && lastSeenMs > 0 && now - lastSeenMs < STALE_AFTER_MS

    /** "just now", "5m ago", "3h ago", "2d ago" or "never". */
    fun lastSeenLabel(lastSeenMs: Long, now: Long): String {
        if (lastSeenMs <= 0) return "never"
        val mins = ((now - lastSeenMs) / 60_000).coerceAtLeast(0)
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 60 * 24 -> "${mins / 60}h ago"
            else -> "${mins / (60 * 24)}d ago"
        }
    }
}

fun PlayerData.onlineNow(now: Long = System.currentTimeMillis()): Boolean =
    Presence.isOnline(isOnline, lastSeenMs, now)
