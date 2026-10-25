package com.example.otpforwarder

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OtpNotificationListener : NotificationListenerService() {
    private val TAG = "OtpNotificationListener"

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (sbn.packageName == packageName) return // Ignore self-notifications to prevent loop

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val fullContent = if (bigText.isNotEmpty()) bigText else text

        Log.d(TAG, "Notification received from pkg: ${sbn.packageName}, Title: $title, Text: $fullContent")

        // Dispatch via central engine
        CoroutineScope(Dispatchers.IO).launch {
            OtpDispatcher.dispatch(applicationContext, title, fullContent)
        }
    }
}
