package com.deadfest.util

/**
 * Whether the app is on screen right now. Set by MainActivity; read by the background check-in
 * (which stands down while the app is open) and by chat notifications (none while you're reading
 * the chat).
 */
object AppVisibility {
    @Volatile var foreground: Boolean = false
    @Volatile var chatOpen: Boolean = false
}
