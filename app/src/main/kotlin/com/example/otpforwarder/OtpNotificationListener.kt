package com.example.otpforwarder

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OtpNotificationListener : NotificationListenerService() {
    private val TAG = "OtpForwarder"

    companion object {
        private var lastForwardedText: String = ""
        private var lastForwardedTime: Long = 0L

        fun shouldForward(text: String): Boolean {
            val now = System.currentTimeMillis()
            if (text == lastForwardedText && (now - lastForwardedTime) < 30000) {
                return false // Duplicate within 30 seconds
            }
            lastForwardedText = text
            lastForwardedTime = now
            return true
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val prefs = getSharedPreferences("otp_prefs", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("is_enabled", true)
        if (!isEnabled) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val fullContent = if (bigText.isNotEmpty()) bigText else text

        Log.d(TAG, "Notification received from pkg: ${sbn.packageName}, Title: $title, Text: $fullContent")

        // Match UPEGOV or eDistrict
        val isTarget = title.contains("UPEGOV", ignoreCase = true) ||
                fullContent.contains("eDistrict", ignoreCase = true) ||
                fullContent.contains("UPEGOV", ignoreCase = true) ||
                (fullContent.contains("OTP", ignoreCase = true) && title.contains("GOV", ignoreCase = true))

        if (isTarget) {
            if (!shouldForward(fullContent)) {
                Log.d(TAG, "Skipping duplicate notification")
                return
            }

            val otpRegex = Regex("""\b\d{4,8}\b""")
            val matchedOtp = otpRegex.find(fullContent)?.value

            val alertText = buildString {
                append("🔔 *eDistrict UP OTP Alert*\n\n")
                if (matchedOtp != null) {
                    append("🔑 *OTP Code: $matchedOtp*\n\n")
                }
                append("📩 *Message:* $fullContent\n")
                append("👤 *Sender:* $title")
            }

            val botToken = prefs.getString("bot_token", TelegramHelper.DEFAULT_BOT_TOKEN) ?: TelegramHelper.DEFAULT_BOT_TOKEN
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    TelegramHelper.broadcastOtp(botToken, alertText)
                    Log.d(TAG, "Successfully forwarded OTP from NotificationListener!")
                } catch (e: Exception) {
                    Log.e(TAG, "Error broadcasting from NotificationListener", e)
                }
            }
        }
    }
}
