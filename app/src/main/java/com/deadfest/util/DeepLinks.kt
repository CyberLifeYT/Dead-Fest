package com.deadfest.util

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Screen requested by a tapped notification. MainActivity fills it from the launch intent (or
 * onNewIntent when the app is already open); the navigation host consumes it and clears it.
 */
object DeepLinks {
    /** Routes a notification may open. Anything else is ignored. */
    private val ALLOWED = setOf("home", "alerts", "chat", "progression", "challenges", "shop", "settings", "images")

    val route = MutableStateFlow<String?>(null)

    fun handle(intent: Intent?) {
        val r = intent?.getStringExtra(NotificationHelper.EXTRA_ROUTE) ?: return
        if (r in ALLOWED) route.value = r
        intent.removeExtra(NotificationHelper.EXTRA_ROUTE)
    }
}
