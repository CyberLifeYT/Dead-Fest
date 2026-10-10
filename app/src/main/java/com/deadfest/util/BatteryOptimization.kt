package com.deadfest.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery optimisation / background restriction helpers.
 *
 * Dead-Fest doesn't run a background service (presence is "online while the app is open"),
 * but aggressive battery savers delay or drop push notifications and OTA downloads. Being
 * exempt keeps broadcasts, level-ups and updates arriving on time.
 */
object BatteryOptimization {

    /** True when Android won't apply Doze/App Standby battery optimisation to Dead-Fest. */
    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Android 9+: the user restricted background usage for this app in system settings. */
    fun isBackgroundRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.isBackgroundRestricted
    }

    /**
     * Opens the system "allow unrestricted battery" prompt for Dead-Fest. Falls back to the
     * battery optimisation list, then to the app's settings page, on phones that block it.
     * @return false if no settings screen could be opened at all
     */
    @SuppressLint("BatteryLife") // Sideloaded friends-and-family app; not distributed via Play.
    fun requestExemption(context: Context): Boolean {
        val pkg = context.packageName
        val attempts = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            appSettingsIntent(context)
        )
        return attempts.any { tryStart(context, it) }
    }

    /** The app's system settings page (where "Background usage" restrictions are lifted). */
    fun openAppSettings(context: Context): Boolean = tryStart(context, appSettingsIntent(context))

    private fun appSettingsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
