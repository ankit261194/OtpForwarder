package com.example.otpforwarder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class OtpHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val sender: String,
    val otp: String,
    val fullMessage: String,
    val telegramStatus: String, // e.g. "Sent", "Failed", "Disabled"
    val telegram2Status: String = "Disabled",
    val whatsappStatus: String,
    val smsStatus: String
)

object HistoryManager {
    private const val PREFS_NAME = "otp_prefs"
    private const val KEY_HISTORY = "history_log"
    private const val MAX_ITEMS = 30

    fun addEntry(
        context: Context,
        sender: String,
        otp: String,
        fullMessage: String,
        telegramStatus: String,
        telegram2Status: String,
        whatsappStatus: String,
        smsStatus: String
    ) {
        val timeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val timestamp = timeFormat.format(Date())

        val newItem = OtpHistoryItem(
            timestamp = timestamp,
            sender = sender,
            otp = otp,
            fullMessage = fullMessage,
            telegramStatus = telegramStatus,
            telegram2Status = telegram2Status,
            whatsappStatus = whatsappStatus,
            smsStatus = smsStatus
        )

        val currentList = getHistory(context).toMutableList()
        currentList.add(0, newItem) // Newest on top
        if (currentList.size > MAX_ITEMS) {
            currentList.subList(MAX_ITEMS, currentList.size).clear()
        }

        saveList(context, currentList)
    }

    fun getHistory(context: Context): List<OtpHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return emptyList()

        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<OtpHistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    OtpHistoryItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        timestamp = obj.optString("timestamp", ""),
                        sender = obj.optString("sender", ""),
                        otp = obj.optString("otp", ""),
                        fullMessage = obj.optString("fullMessage", ""),
                        telegramStatus = obj.optString("telegramStatus", "-"),
                        telegram2Status = obj.optString("telegram2Status", "Disabled"),
                        whatsappStatus = obj.optString("whatsappStatus", "-"),
                        smsStatus = obj.optString("smsStatus", "-")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveList(context: Context, list: List<OtpHistoryItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("sender", item.sender)
                put("otp", item.otp)
                put("fullMessage", item.fullMessage)
                put("telegramStatus", item.telegramStatus)
                put("telegram2Status", item.telegram2Status)
                put("whatsappStatus", item.whatsappStatus)
                put("smsStatus", item.smsStatus)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }
}
