package com.deadfest

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale

class DeadFestWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // goAsync keeps the receiver alive until Firestore answers; otherwise the
        // system may kill it first and the widget never refreshes.
        val pending = goAsync()
        var remaining = appWidgetIds.size
        if (remaining == 0) {
            pending.finish()
            return
        }
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId) {
                if (--remaining == 0) pending.finish()
            }
        }
    }

    companion object {
        /** Online = app open with a fresh heartbeat (same rule as in the app: domain.Presence). */
        /** Calls back exactly once: the count, or null if the query failed. */
        private fun countOnline(firestore: FirebaseFirestore, onResult: (Int?) -> Unit) {
            firestore.collection("users").whereEqualTo("playerData.isOnline", true).get()
                .addOnSuccessListener { snap ->
                    val now = System.currentTimeMillis()
                    onResult(snap.documents.count { d ->
                        val seen = when (val v = d.get("playerData.lastSeen")) {
                            is com.google.firebase.Timestamp -> v.toDate().time
                            is Number -> v.toLong()
                            else -> 0L
                        }
                        com.deadfest.domain.Presence.isOnline(true, seen, now)
                    })
                }
                .addOnFailureListener { onResult(null) }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            onDone: () -> Unit = {}
        ) {
            val views = RemoteViews(context.packageName, R.layout.deadfest_widget_layout)

            // Set up click intent to open MainActivity
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_grand_total, pendingIntent)

            // Fetch latest global state from Firestore
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("gameState").document("global").get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val grandTotal = document.getLong("grandTotal") ?: 0L
                        val sectorOverload = document.getBoolean("sectorOverload") ?: false
                        val overloadedSector = document.getString("overloadedSector") ?: ""

                        val formattedTotal = NumberFormat.getNumberInstance(Locale.getDefault()).format(grandTotal)
                        views.setTextViewText(R.id.widget_grand_total, formattedTotal)
                        if (sectorOverload) {
                            views.setTextViewText(R.id.widget_overload_status, "OVERLOAD")
                            views.setTextColor(R.id.widget_overload_status, 0xFFFF0055.toInt())
                            val sectorText = if (overloadedSector.isNotBlank()) overloadedSector else "ACTIVE"
                            views.setTextViewText(R.id.widget_sector_name, sectorText)
                        } else {
                            views.setTextViewText(R.id.widget_overload_status, "ONLINE")
                            views.setTextColor(R.id.widget_overload_status, 0xFF00FF66.toInt())
                            views.setTextViewText(R.id.widget_sector_name, if (overloadedSector.isNotBlank()) overloadedSector else "STABLE")
                        }

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                    // Online count second; the receiver stays alive (goAsync) until it lands.
                    countOnline(firestore) { count ->
                        if (count != null) {
                            views.setTextViewText(R.id.widget_online_count, count.toString())
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                        onDone()
                    }
                }
                .addOnFailureListener {
                    views.setTextViewText(R.id.widget_grand_total, "OFFLINE")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    onDone()
                }
        }
    }
}
