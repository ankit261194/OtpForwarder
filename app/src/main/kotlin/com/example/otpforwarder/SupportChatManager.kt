package com.example.otpforwarder

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SupportMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "USER" or "ADMIN"
    val text: String,
    val mediaName: String? = null,
    val mediaType: String? = null, // "IMAGE", "PDF", "VIDEO", "NONE"
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String
)

object SupportChatManager {
    private const val TAG = "SupportChatManager"
    private const val PREF_KEY_MESSAGES = "support_messages_json"
    private const val PREF_KEY_BLOCKED_DEVICES = "support_blocked_devices"

    fun getMessages(context: Context): List<SupportMessage> {
        val prefs = PrefsHelper.getPrefs(context)
        val jsonStr = prefs.getString(PREF_KEY_MESSAGES, "[]") ?: "[]"
        val list = mutableListOf<SupportMessage>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SupportMessage(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        sender = obj.optString("sender", "USER"),
                        text = obj.optString("text", ""),
                        mediaName = obj.optString("mediaName").takeIf { it.isNotEmpty() },
                        mediaType = obj.optString("mediaType").takeIf { it.isNotEmpty() },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        deviceId = obj.optString("deviceId", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing support messages", e)
        }
        return list
    }

    private fun saveMessages(context: Context, messages: List<SupportMessage>) {
        val prefs = PrefsHelper.getPrefs(context)
        val array = JSONArray()
        for (msg in messages) {
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("sender", msg.sender)
                put("text", msg.text)
                put("mediaName", msg.mediaName ?: "")
                put("mediaType", msg.mediaType ?: "")
                put("timestamp", msg.timestamp)
                put("deviceId", msg.deviceId)
            }
            array.put(obj)
        }
        prefs.edit().putString(PREF_KEY_MESSAGES, array.toString()).apply()
    }

    fun addMessage(context: Context, msg: SupportMessage) {
        val list = getMessages(context).toMutableList()
        list.add(msg)
        saveMessages(context, list)
    }

    fun deleteMessage(context: Context, messageId: String) {
        val list = getMessages(context).filter { it.id != messageId }
        saveMessages(context, list)
    }

    fun clearChat(context: Context) {
        saveMessages(context, emptyList())
    }

    fun isUserBlocked(context: Context, deviceId: String): Boolean {
        val prefs = PrefsHelper.getPrefs(context)
        val blockedSet = prefs.getStringSet(PREF_KEY_BLOCKED_DEVICES, emptySet()) ?: emptySet()
        return blockedSet.contains(deviceId.uppercase())
    }

    fun setUserBlocked(context: Context, deviceId: String, blocked: Boolean) {
        val prefs = PrefsHelper.getPrefs(context)
        val blockedSet = (prefs.getStringSet(PREF_KEY_BLOCKED_DEVICES, emptySet()) ?: emptySet()).toMutableSet()
        if (blocked) {
            blockedSet.add(deviceId.uppercase())
        } else {
            blockedSet.remove(deviceId.uppercase())
        }
        prefs.edit().putStringSet(PREF_KEY_BLOCKED_DEVICES, blockedSet).apply()
    }

    suspend fun sendCustomerMessage(
        context: Context,
        text: String,
        fileBytes: ByteArray? = null,
        fileName: String = "",
        mimeType: String = ""
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val deviceId = CloudflareLicenseManager.getDeviceId(context)
        if (isUserBlocked(context, deviceId)) {
            return@withContext Pair(false, "🚫 Aapka device support desk se blocked hai.")
        }

        if (text.isBlank() && (fileBytes == null || fileBytes.isEmpty())) {
            return@withContext Pair(false, "Khali message nahi bhej sakte.")
        }

        val mediaType = when {
            mimeType.startsWith("image/") -> "IMAGE"
            mimeType.contains("pdf") -> "PDF"
            mimeType.startsWith("video/") -> "VIDEO"
            fileBytes != null -> "DOCUMENT"
            else -> null
        }

        val msg = SupportMessage(
            sender = "USER",
            text = text,
            mediaName = fileName.takeIf { it.isNotEmpty() },
            mediaType = mediaType,
            deviceId = deviceId
        )
        addMessage(context, msg)

        // Build Telegram alert caption
        val telegramCaption = buildString {
            append("📩 Smart Notifier Support Ticket\n\n")
            append("📱 Device ID: $deviceId\n")
            if (fileName.isNotEmpty()) append("📎 Attachment: $fileName ($mediaType)\n")
            if (text.isNotEmpty()) append("💬 Message: $text\n")
            append("⏰ Time: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        }

        val isPhoto = mimeType.startsWith("image/")
        val tgRes = TelegramHelper.broadcastSupportMessage(
            message = telegramCaption,
            fileBytes = fileBytes,
            fileName = fileName,
            mimeType = mimeType,
            isPhoto = isPhoto
        )

        Pair(true, if (tgRes.first) "Message sent to Admin!" else "Message saved locally.")
    }

    suspend fun sendAdminReply(
        context: Context,
        targetDeviceId: String,
        text: String,
        fileBytes: ByteArray? = null,
        fileName: String = "",
        mimeType: String = ""
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val mediaType = when {
            mimeType.startsWith("image/") -> "IMAGE"
            mimeType.contains("pdf") -> "PDF"
            mimeType.startsWith("video/") -> "VIDEO"
            fileBytes != null -> "DOCUMENT"
            else -> null
        }

        val msg = SupportMessage(
            sender = "ADMIN",
            text = text,
            mediaName = fileName.takeIf { it.isNotEmpty() },
            mediaType = mediaType,
            deviceId = targetDeviceId
        )
        addMessage(context, msg)
        Pair(true, "Reply sent to customer!")
    }
}
