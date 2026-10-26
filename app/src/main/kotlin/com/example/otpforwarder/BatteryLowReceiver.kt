package com.example.otpforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BatteryLowReceiver : BroadcastReceiver() {
    private val TAG = "BatteryLowReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prefs = PrefsHelper.getPrefs(context)

        if (action == Intent.ACTION_BATTERY_OKAY) {
            // Battery is charged back up, reset alert flag for next discharge cycle
            prefs.edit().putBoolean("battery_alert_sent", false).apply()
            Log.d(TAG, "Battery level OKAY. Alert flag reset.")
            return
        }

        if (action == Intent.ACTION_BATTERY_LOW) {
            val isAlertEnabled = prefs.getBoolean("low_battery_alert_enabled", false)
            if (!isAlertEnabled) {
                Log.d(TAG, "Battery LOW received but low_battery_alert_enabled is false.")
                return
            }

            val alreadySent = prefs.getBoolean("battery_alert_sent", false)
            if (alreadySent) {
                Log.d(TAG, "Battery alert already sent for current discharge cycle. Skipping.")
                return
            }

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 15

            val alertMsg = "⚠️ Alert: Forwarding Phone Ki Battery ${batteryPct}% Reh Gayi Hai! Kripya phone ko charger par lagayein."

            prefs.edit().putBoolean("battery_alert_sent", true).apply()
            Log.d(TAG, "Sending Low Battery alert ($batteryPct%) to Telegram...")

            CoroutineScope(Dispatchers.IO).launch {
                // Send to Telegram 1 (Primary)
                val isTg1Enabled = prefs.getBoolean("is_telegram_enabled", true)
                if (isTg1Enabled) {
                    val tokenFromPrefs = prefs.getString("bot_token", null)?.trim()
                    val botToken = if (!tokenFromPrefs.isNullOrEmpty()) tokenFromPrefs else TelegramHelper.DEFAULT_BOT_TOKEN
                    val chatFromPrefs = prefs.getString("chat_id", null)?.trim()
                    val chatId = if (!chatFromPrefs.isNullOrEmpty()) chatFromPrefs else TelegramHelper.DEFAULT_CHANNEL_ID
                    try {
                        TelegramHelper.sendMessage(botToken, chatId, alertMsg)
                        TelegramHelper.sendMessage(botToken, TelegramHelper.DEFAULT_USER_ID, alertMsg)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send battery alert to TG1", e)
                    }
                }

                // Send to Telegram 2 (Secondary) if enabled
                val isTg2Enabled = prefs.getBoolean("is_telegram_2_enabled", false)
                if (isTg2Enabled) {
                    val token2 = prefs.getString("bot_token_2", "")?.trim() ?: ""
                    val chat2 = prefs.getString("chat_id_2", "")?.trim() ?: ""
                    if (token2.isNotEmpty() && chat2.isNotEmpty()) {
                        try {
                            TelegramHelper.sendMessage(token2, chat2, alertMsg)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send battery alert to TG2", e)
                        }
                    }
                }

                // Log in History
                HistoryManager.addEntry(
                    context = context,
                    sender = "Battery Monitor",
                    otp = "${batteryPct}%",
                    fullMessage = alertMsg,
                    telegramStatus = if (isTg1Enabled) "Sent" else "Disabled",
                    telegram2Status = if (isTg2Enabled) "Sent" else "Disabled",
                    whatsappStatus = "Disabled",
                    smsStatus = "Disabled"
                )
            }
        }
    }
}
