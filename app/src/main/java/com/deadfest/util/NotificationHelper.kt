package com.deadfest.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.deadfest.MainActivity
import com.deadfest.R
import com.deadfest.data.model.AlertItem
import com.deadfest.data.model.ChatMessage
import com.deadfest.notifications.ChatReplyReceiver

/**
 * Every system notification the app shows (v2.1.2).
 *
 * - **Channels**: each kind of notification has its own channel, so survivors can mute chat but
 *   keep curses, or silence level-ups, from Android's own notification settings.
 * - **Status-bar icon**: a monochrome skull ([R.drawable.ic_stat_deadfest]); the coloured
 *   launcher icon rendered as a white blob.
 * - **Deep links**: tapping opens the screen it's about (alerts, chat, progression, challenges,
 *   shop, settings) via [EXTRA_ROUTE].
 * - **Grouping**: alerts and progress notifications bundle under one summary instead of flooding
 *   the tray.
 * - **Chat**: conversation style with a quick Reply action that sends without opening the app.
 */
object NotificationHelper {
    const val EXTRA_ROUTE = "com.deadfest.extra.ROUTE"

    // Channel ids are permanent: renaming one resets every survivor's settings for it.
    const val CH_ALERTS = "deadfest_alerts"
    const val CH_CHAT = "deadfest_chat"
    const val CH_PROGRESS = "deadfest_progress"
    /** The original channel, kept so existing broadcast settings carry over. */
    const val CH_BROADCASTS = "deadfest_wasteland_channel"
    const val CH_UPDATES = "deadfest_updates"
    const val CH_MARKET = "deadfest_market"

    private const val GROUP_ALERTS = "com.deadfest.group.ALERTS"
    private const val GROUP_PROGRESS = "com.deadfest.group.PROGRESS"
    private const val ID_ALERTS_SUMMARY = 1001
    private const val ID_PROGRESS_SUMMARY = 1002
    const val ID_CHAT = 2001
    private const val ID_UPDATE = 3001
    private const val ID_FLASH = 3002

    const val KEY_REPLY = "com.deadfest.key.REPLY"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        fun ch(id: String, name: Int, desc: Int, importance: Int) =
            NotificationChannel(id, context.getString(name), importance).apply { description = context.getString(desc) }
        nm.createNotificationChannels(listOf(
            ch(CH_ALERTS, R.string.channel_alerts, R.string.channel_alerts_desc, NotificationManager.IMPORTANCE_HIGH),
            ch(CH_CHAT, R.string.channel_chat, R.string.channel_chat_desc, NotificationManager.IMPORTANCE_HIGH),
            ch(CH_PROGRESS, R.string.channel_progress, R.string.channel_progress_desc, NotificationManager.IMPORTANCE_DEFAULT),
            ch(CH_BROADCASTS, R.string.channel_broadcasts, R.string.channel_broadcasts_desc, NotificationManager.IMPORTANCE_DEFAULT),
            ch(CH_MARKET, R.string.channel_market, R.string.channel_market_desc, NotificationManager.IMPORTANCE_DEFAULT),
            ch(CH_UPDATES, R.string.channel_updates, R.string.channel_updates_desc, NotificationManager.IMPORTANCE_LOW)
        ))
    }

    // ------------------------------------------------------------------ public API

    /** A personal alert (cursed, shield blocked, image removed, admin change...). Opens Alerts. */
    fun notifyAlert(context: Context, alert: AlertItem) {
        val n = base(context, CH_ALERTS, alert.title, alert.body, route = "alerts")
            .setGroup(GROUP_ALERTS)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setWhen(alert.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis())
            .setShowWhen(true)
        post(context, alert.id.hashCode(), n)
        summarize(context, CH_ALERTS, GROUP_ALERTS, ID_ALERTS_SUMMARY, context.getString(R.string.notif_alerts_summary), "alerts")
    }

    /** Level-ups, prestige, achievements, finished challenges. */
    fun notifyProgress(context: Context, title: String, body: String, route: String = "progression") {
        val n = base(context, CH_PROGRESS, title, body, route).setGroup(GROUP_PROGRESS)
        post(context, (title + body).hashCode(), n)
        summarize(context, CH_PROGRESS, GROUP_PROGRESS, ID_PROGRESS_SUMMARY, context.getString(R.string.notif_progress_summary), route)
    }

    /** Admin broadcasts (in-app broadcasts and Firebase console pushes). */
    fun notifyBroadcast(context: Context, title: String, body: String) {
        post(context, (title + body).hashCode(), base(context, CH_BROADCASTS, title, body, route = "home"))
    }

    fun notifyFlashSale(context: Context, title: String, body: String) {
        post(context, ID_FLASH, base(context, CH_MARKET, title, body, route = "shop"))
    }

    fun notifyUpdate(context: Context, title: String, body: String) {
        post(context, ID_UPDATE, base(context, CH_UPDATES, title, body, route = "settings"))
    }

    /** New group-chat messages, conversation style, with a Reply action. */
    fun notifyChat(context: Context, messages: List<ChatMessage>, myName: String) {
        if (messages.isEmpty()) return
        val me = Person.Builder().setName(myName.ifBlank { context.getString(R.string.chat_you) }).build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(context.getString(R.string.chat_title))
            .setGroupConversation(true)
        messages.takeLast(6).forEach { m ->
            val sender = Person.Builder().setName(m.senderName).setKey(m.senderUid).build()
            val text = when {
                m.text.isNotBlank() -> m.text
                m.hasImage -> context.getString(R.string.chat_photo)
                else -> ""
            }
            style.addMessage(text, m.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(), sender)
        }

        val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel(context.getString(R.string.chat_hint)).build()
        val replyIntent = PendingIntent.getBroadcast(
            context, ID_CHAT,
            Intent(context, ChatReplyReceiver::class.java),
            // A reply action must be MUTABLE so Android can attach the typed text.
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )
        val reply = NotificationCompat.Action.Builder(R.drawable.ic_stat_deadfest, context.getString(R.string.notif_reply), replyIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()

        val n = NotificationCompat.Builder(context, CH_CHAT)
            .setSmallIcon(R.drawable.ic_stat_deadfest)
            .setColor(context.getColor(R.color.notification_accent))
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openApp(context, "chat"))
            .addAction(reply)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
        post(context, ID_CHAT, n)
    }

    fun cancelChat(context: Context) = NotificationManagerCompat.from(context).cancel(ID_CHAT)

    /** Back-compat for older call sites: treated as a progress notification. */
    fun showNotification(context: Context, title: String, message: String) = notifyProgress(context, title, message)

    // ------------------------------------------------------------------ internals

    private fun base(context: Context, channel: String, title: String, body: String, route: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_deadfest)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(context, route))
            .setAutoCancel(true)

    /** Opens MainActivity on [route]. Request code per route so the extras never get merged. */
    private fun openApp(context: Context, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(context, route.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        createChannels(context)
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied (Android 13+): nothing to show, and nothing to crash over.
        }
    }

    /** Collapses 2+ notifications of a group under one summary row. */
    private fun summarize(context: Context, channel: String, group: String, summaryId: Int, title: String, route: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val inGroup = nm.activeNotifications.filter { it.notification.group == group && it.id != summaryId }
        if (inGroup.size < 2) return
        val inbox = NotificationCompat.InboxStyle().setSummaryText(context.getString(R.string.notif_count, inGroup.size))
        inGroup.take(5).forEach { sbn ->
            sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.let { inbox.addLine(it) }
        }
        val summary = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_deadfest)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_count, inGroup.size))
            .setStyle(inbox)
            .setGroup(group)
            .setGroupSummary(true)
            .setContentIntent(openApp(context, route))
            .setAutoCancel(true)
        post(context, summaryId, summary)
    }
}
