package com.deadfest.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.deadfest.data.repository.FirebaseRepository
import com.deadfest.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Quick Reply from a chat notification: sends the typed text to the group chat without opening
 * the app. Uses the saved Firebase session, so it works even when the app isn't running.
 */
class ChatReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(NotificationHelper.KEY_REPLY)
            ?.toString()?.trim().orEmpty()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (text.isEmpty() || uid == null) {
            NotificationHelper.cancelChat(context)
            return
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = FirebaseRepository()
                val me = repo.fetchUser(uid)
                if (me != null) {
                    // Queued by Firestore if the phone is offline; don't hold the receiver for it.
                    withTimeoutOrNull(8_000) { repo.sendChat(me, text, null).await() }
                }
            } catch (e: Exception) {
                Log.w("ChatReplyReceiver", "Reply failed", e)
            } finally {
                // Clearing the notification also ends the reply spinner Android shows.
                NotificationHelper.cancelChat(context.applicationContext)
                pending.finish()
            }
        }
    }
}
