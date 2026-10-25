package com.example.otpforwarder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val TAG = "OtpForwarder"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences("otp_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("is_enabled", true)
        
        if (!isEnabled) {
            Log.d(TAG, "Service is OFF by user switch")
            return
        }

        val botToken = prefs.getString("bot_token", TelegramHelper.DEFAULT_BOT_TOKEN) ?: TelegramHelper.DEFAULT_BOT_TOKEN
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        for (sms in messages) {
            val body = sms.messageBody ?: ""
            val sender = sms.originatingAddress ?: ""
            Log.d(TAG, "Received SMS from $sender: $body")

            // Match eDistrict UP / UPEGOV
            val isTarget = sender.contains("UPEGOV", ignoreCase = true) ||
                    body.contains("eDistrict", ignoreCase = true) ||
                    body.contains("UPEGOV", ignoreCase = true) ||
                    (body.contains("OTP", ignoreCase = true) && body.contains("district", ignoreCase = true))

            if (isTarget) {
                if (!OtpNotificationListener.shouldForward(body)) {
                    Log.d(TAG, "Skipping duplicate SMS")
                    return
                }

                // Extract 4-8 digit OTP
                val otpRegex = Regex("""\b\d{4,8}\b""")
                val matchedOtp = otpRegex.find(body)?.value

                val alertText = buildString {
                    append("🔔 *eDistrict UP OTP Alert*\n\n")
                    if (matchedOtp != null) {
                        append("🔑 *OTP Code: $matchedOtp*\n\n")
                    }
                    append("📩 *Message:* $body\n")
                    append("👤 *Sender:* $sender")
                }

                // Show a quick local notification on the phone screen
                showLocalNotification(context, matchedOtp ?: "OTP Received", body)

                // goAsync ensures the background Telegram call completes
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        TelegramHelper.broadcastOtp(botToken, alertText)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to broadcast OTP", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
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
                .setContentTitle("✅ eDistrict OTP Forwarded!")
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
