package com.deadfest.fcm

import com.deadfest.R
import com.deadfest.data.AppPrefs
import com.deadfest.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Pushes sent from the Firebase console arrive here and go out on the broadcasts channel. */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val n = remoteMessage.notification ?: return
        if (!AppPrefs(applicationContext).wastelandAlerts.value) return
        NotificationHelper.notifyBroadcast(
            applicationContext,
            n.title ?: getString(R.string.notif_broadcast_title),
            n.body ?: ""
        )
    }
}
