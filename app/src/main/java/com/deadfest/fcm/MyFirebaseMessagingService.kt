package com.deadfest.fcm

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.deadfest.util.NotificationHelper

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.notification?.let {
            val title = it.title ?: "Dead-Fest Wasteland"
            val body = it.body ?: ""
            checkAndSendNotification(title, body, "pref_wasteland_alerts")
        }
    }

    override fun onNewToken(token: String) {
        // Handle new token registration if needed
    }

    private fun checkAndSendNotification(title: String, body: String, prefKey: String) {
        val prefs = getSharedPreferences("deadfest_optimizations", MODE_PRIVATE)
        val enabled = prefs.getBoolean(prefKey, true)
        if (enabled) {
            NotificationHelper.showNotification(applicationContext, title, body)
        }
    }
}
