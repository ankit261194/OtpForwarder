package com.example.otpforwarder

import android.app.Notification
import android.content.Context
import android.os.PowerManager
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

        // Acquire Partial WakeLock to force CPU and network to stay awake in deep sleep
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OtpForwarder:NotificationWakeLock"
        )
        try {
            wakeLock?.acquire(20000) // Keep awake for up to 20 seconds
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock in NotificationListener", e)
        }

        // Dispatch via central engine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wasForwarded = OtpDispatcher.dispatch(applicationContext, title, fullContent)
                
                // Feature: Auto-Clear SMS Notification (Removes notification popup from screen upon forwarding)
                if (wasForwarded) {
                    val prefs = PrefsHelper.getPrefs(applicationContext)
                    val autoClear = prefs.getBoolean("auto_clear_sms_notification", false)
                    if (autoClear) {
                        try {
                            cancelNotification(sbn.key)
                            Log.d(TAG, "Successfully auto-cleared notification for ${sbn.packageName} (${sbn.key})")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to auto-clear notification: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in NotificationListener dispatch", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (ignored: Exception) {}
            }
        }
    }
}
