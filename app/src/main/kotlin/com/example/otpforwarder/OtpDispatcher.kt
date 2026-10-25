package com.example.otpforwarder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OtpDispatcher {
    private const val TAG = "OtpDispatcher"
    private const val PREFS_NAME = "otp_prefs"

    private var lastForwardedText: String = ""
    private var lastForwardedTime: Long = 0L

    @Synchronized
    fun shouldProcess(text: String): Boolean {
        val now = System.currentTimeMillis()
        if (text == lastForwardedText && (now - lastForwardedTime) < 30000) {
            return false // Deduplicate within 30 seconds
        }
        lastForwardedText = text
        lastForwardedTime = now
        return true
    }

    suspend fun dispatch(context: Context, sender: String, fullMessage: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isMasterEnabled = prefs.getBoolean("is_enabled", true)
        if (!isMasterEnabled) {
            Log.d(TAG, "Master forwarding switch is OFF")
            return
        }

        // Match against dynamic keywords
        if (!KeywordManager.matches(context, sender, fullMessage)) {
            Log.d(TAG, "Message does not match any keyword. Sender: $sender")
            return
        }

        // Deduplication
        if (!shouldProcess(fullMessage)) {
            Log.d(TAG, "Duplicate message ignored")
            return
        }

        // Extract OTP
        val otpRegex = Regex("""\b\d{4,8}\b""")
        val matchedOtp = otpRegex.find(fullMessage)?.value ?: "OTP"

        val alertText = buildString {
            append("🔔 eDistrict UP OTP Alert\n\n")
            if (matchedOtp != "OTP") {
                append("🔑 OTP Code: $matchedOtp\n\n")
            }
            append("📩 Message: $fullMessage\n")
            append("👤 Sender: $sender")
        }

        var telegramStatus = "Disabled"
        var whatsappStatus = "Disabled"
        var smsStatus = "Disabled"

        withContext(Dispatchers.IO) {
            // 1. Telegram
            val isTelegramEnabled = prefs.getBoolean("is_telegram_enabled", true)
            if (isTelegramEnabled) {
                val botToken = prefs.getString("bot_token", TelegramHelper.DEFAULT_BOT_TOKEN) ?: TelegramHelper.DEFAULT_BOT_TOKEN
                val chatId = prefs.getString("chat_id", TelegramHelper.DEFAULT_CHANNEL_ID) ?: TelegramHelper.DEFAULT_CHANNEL_ID
                
                try {
                    val res = TelegramHelper.sendMessage(botToken, chatId, alertText)
                    // Also backup to personal user chat
                    TelegramHelper.sendMessage(botToken, TelegramHelper.DEFAULT_USER_ID, alertText)
                    telegramStatus = if (res.first) "Sent" else "Failed"
                } catch (e: Exception) {
                    telegramStatus = "Error"
                }
            }

            // 2. WhatsApp
            val isWhatsAppEnabled = prefs.getBoolean("is_whatsapp_enabled", false)
            if (isWhatsAppEnabled) {
                val waPhone = prefs.getString("wa_phone", "") ?: ""
                val waKey = prefs.getString("wa_api_key", "") ?: ""
                if (waPhone.isNotEmpty() && waKey.isNotEmpty()) {
                    try {
                        val res = WhatsAppHelper.sendMessage(waPhone, waKey, alertText)
                        whatsappStatus = if (res.first) "Sent" else "Failed"
                    } catch (e: Exception) {
                        whatsappStatus = "Error"
                    }
                }
            }

            // 3. Direct SMS
            val isSmsEnabled = prefs.getBoolean("is_sms_enabled", false)
            if (isSmsEnabled) {
                val smsPhone = prefs.getString("sms_phone", "") ?: ""
                if (smsPhone.isNotEmpty()) {
                    try {
                        val res = SmsHelper.sendSms(context, smsPhone, alertText)
                        smsStatus = if (res.first) "Sent" else "Failed"
                    } catch (e: Exception) {
                        smsStatus = "Error"
                    }
                }
            }

            // Save History Log
            HistoryManager.addEntry(
                context = context,
                sender = sender,
                otp = matchedOtp,
                fullMessage = fullMessage,
                telegramStatus = telegramStatus,
                whatsappStatus = whatsappStatus,
                smsStatus = smsStatus
            )

            // Show Local Notification
            showLocalNotification(context, "OTP: $matchedOtp Forwarded!", alertText)
        }
    }

    private fun showLocalNotification(context: Context, title: String, content: String) {
        try {
            val channelId = "otp_alert_channel"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "OTP Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Notification error", e)
        }
    }
}
