package com.deadfest.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.deadfest.BuildConfig
import com.deadfest.R
import com.deadfest.data.AppPrefs
import com.deadfest.data.repository.FirebaseRepository
import com.deadfest.util.AppVisibility
import com.deadfest.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/**
 * Background check-in (v2.1.1): about every 15 minutes, while the app is closed, Android's own
 * scheduler (WorkManager) wakes this up for a few seconds to look for anything new — personal
 * alerts, chat messages, admin broadcasts, a flash sale, an app update — and shows it as a
 * normal notification.
 *
 * No server and no paid plan: it reads Firestore directly with the saved login. Android decides
 * the exact timing (it batches work to save battery), so notifications arrive within ~15 minutes
 * rather than instantly. Skipped while the app is open, since the app handles that live.
 */
class CheckInWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        if (AppVisibility.foreground) return Result.success()
        val ctx = applicationContext
        val prefs = AppPrefs(ctx)
        if (!prefs.backgroundSync.value) return Result.success()
        val repo = FirebaseRepository()
        return try {
            withTimeout(60_000) {
                checkAlerts(ctx, repo, prefs, uid)
                checkChat(ctx, repo, prefs, uid)
                checkGlobal(ctx, repo, prefs)
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Check-in failed; will try again next time", e)
            Result.retry()
        }
    }

    private suspend fun checkAlerts(ctx: Context, repo: FirebaseRepository, prefs: AppPrefs, uid: String) {
        if (!prefs.alertNotificationsEnabled) return
        val since = prefs.lastAlertNotifiedAt.takeIf { it > 0 } ?: (System.currentTimeMillis() - FIRST_RUN_WINDOW_MS)
        val fresh = repo.fetchAlertsSince(uid, since).filter { !it.read }
        if (fresh.isEmpty()) return
        prefs.lastAlertNotifiedAt = fresh.maxOf { it.createdAt }
        fresh.forEach { NotificationHelper.notifyAlert(ctx, it) }
    }

    private suspend fun checkChat(ctx: Context, repo: FirebaseRepository, prefs: AppPrefs, uid: String) {
        if (!prefs.chatNotifications.value) return
        val since = maxOf(prefs.lastChatNotifiedAt, prefs.lastChatSeenAt)
            .takeIf { it > 0 } ?: (System.currentTimeMillis() - FIRST_RUN_WINDOW_MS)
        val fresh = repo.fetchChatSince(since).filter { it.senderUid != uid }
        if (fresh.isEmpty()) return
        prefs.lastChatNotifiedAt = fresh.maxOf { it.createdAt }
        val myName = repo.fetchUser(uid)?.handle ?: ""
        NotificationHelper.notifyChat(ctx, fresh, myName)
    }

    private suspend fun checkGlobal(ctx: Context, repo: FirebaseRepository, prefs: AppPrefs) {
        val state = repo.fetchGlobalState()

        // App update published.
        if (state.latestVersionCode > BuildConfig.VERSION_CODE && state.latestVersionCode > prefs.lastUpdateNotifiedCode) {
            prefs.lastUpdateNotifiedCode = state.latestVersionCode
            NotificationHelper.notifyUpdate(
                ctx,
                ctx.getString(R.string.notif_update_title, state.latestVersionName),
                ctx.getString(R.string.notif_update_body)
            )
        }

        // Flash sale started.
        val sale = state.economy.flashSale
        val saleKey = "${sale.pct}:${sale.endsAt}"
        if (sale.isLive() && saleKey != prefs.lastFlashSaleKey && prefs.marketAlerts.value) {
            prefs.lastFlashSaleKey = saleKey
            NotificationHelper.notifyFlashSale(
                ctx,
                ctx.getString(R.string.notif_flash_title, sale.pct),
                ctx.getString(R.string.notif_flash_body)
            )
        }

        // Admin broadcasts posted in the app.
        if (prefs.wastelandAlerts.value) {
            val since = prefs.lastBroadcastNotifiedAt.takeIf { it > 0 } ?: (System.currentTimeMillis() - FIRST_RUN_WINDOW_MS)
            val broadcasts = repo.fetchEventsSince(since).filter { it.category == "ADMIN_BROADCAST" }
            if (broadcasts.isNotEmpty()) {
                prefs.lastBroadcastNotifiedAt = broadcasts.maxOf { it.timestamp?.toDate()?.time ?: 0L }
                broadcasts.forEach {
                    NotificationHelper.notifyBroadcast(ctx, ctx.getString(R.string.notif_broadcast_title), it.message ?: "")
                }
            } else if (prefs.lastBroadcastNotifiedAt == 0L) {
                prefs.lastBroadcastNotifiedAt = System.currentTimeMillis()
            }
        }
    }

    companion object {
        private const val TAG = "CheckInWorker"
        private const val WORK_NAME = "deadfest-checkin"
        /** On the very first check-in, only look back this far rather than the whole history. */
        private const val FIRST_RUN_WINDOW_MS = 60 * 60 * 1000L

        /** Starts (or keeps) the 15-minute check-in. Safe to call on every launch. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CheckInWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Schedules or cancels to match the setting and whether anyone is signed in. */
        fun sync(context: Context, enabled: Boolean, signedIn: Boolean) {
            if (enabled && signedIn) schedule(context) else cancel(context)
        }
    }
}
