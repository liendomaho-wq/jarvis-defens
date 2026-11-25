package com.example.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.bus.JarvisStateBus
import com.example.bus.JarvisSystemEvent

class JarvisNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "JarvisNotification"
        var instance: JarvisNotificationListenerService? = null
            private set

        private val activeReplyActions = mutableMapOf<String, Notification.Action>()

        fun replyToLastNotification(context: Context, replyText: String): Boolean {
            val lastEntry = activeReplyActions.entries.lastOrNull() ?: return false
            val action = lastEntry.value
            return sendReply(context, action, replyText)
        }

        private fun sendReply(context: Context, action: Notification.Action, replyText: String): Boolean {
            val remoteInputs = action.remoteInputs ?: return false
            val intent = Intent()
            val bundle = Bundle()
            for (input in remoteInputs) {
                bundle.putCharSequence(input.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
            return try {
                action.actionIntent.send(context, 0, intent)
                Log.i(TAG, "Reply sent successfully via RemoteInput: $replyText")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending reply via RemoteInput", e)
                false
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Jarvis Notification Listener connected.")
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
        Log.i(TAG, "Jarvis Notification Listener disconnected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: ""
        val isTargetApp = pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("mms") || pkg.contains("messaging")
        if (!isTargetApp) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        // Extract inline reply action if present
        val actions = sbn.notification?.actions
        val replyAction = actions?.firstOrNull { act ->
            act.remoteInputs?.isNotEmpty() == true
        }

        val notificationKey = sbn.key ?: pkg
        if (replyAction != null) {
            activeReplyActions[notificationKey] = replyAction
        }

        JarvisStateBus.postEvent(
            JarvisSystemEvent.NotificationReceived(
                appPackage = pkg,
                sender = title,
                messageText = text,
                canReply = replyAction != null
            )
        )
    }
}
