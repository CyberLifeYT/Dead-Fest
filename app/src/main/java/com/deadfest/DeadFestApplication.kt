package com.deadfest

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class DeadFestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.deadfest.util.I18n.init(this)
        // Log, then hand off to the system handler. Swallowing the crash (as before)
        // left the app frozen on a dead UI thread instead of letting it restart.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("DeadFestApp", "Uncaught exception in thread ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
        FirebaseApp.initializeApp(this)
        // Notification channels exist from the first launch, so they show up in Android's
        // settings (and can be muted) before the first notification ever arrives.
        com.deadfest.util.NotificationHelper.createChannels(this)
    }
}
