package com.example.otpforwarder

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WhatsAppHelper {
    private const val TAG = "OtpForwarder_WhatsApp"

    suspend fun sendMessage(phone: String, apiKey: String, message: String): Pair<Boolean, String> {
        val cleanPhone = phone.replace("+", "").replace("-", "").replace(" ", "").trim()
        val cleanKey = apiKey.trim()

        if (cleanPhone.isEmpty() || cleanKey.isEmpty()) {
            return Pair(false, "Phone number ya API Key khali hai")
        }

        return withContext(Dispatchers.IO) {
            try {
                val encodedMsg = URLEncoder.encode(message, "UTF-8")
                val urlString = "https://api.callmebot.com/whatsapp.php?phone=$cleanPhone&text=$encodedMsg&apikey=$cleanKey"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "OtpForwarder/1.0")
                connection.connectTimeout = 12000
                connection.readTimeout = 12000

                val code = connection.responseCode
                Log.d(TAG, "CallMeBot response code: $code")
                if (code == 200) {
                    val resp = connection.inputStream.bufferedReader().use { it.readText() }
                    if (resp.contains("error", ignoreCase = true) || resp.contains("invalid", ignoreCase = true)) {
                        Pair(false, "WhatsApp Error: $resp")
                    } else {
                        Pair(true, "WhatsApp par message chala gaya!")
                    }
                } else {
                    val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    Pair(false, "Server Error: $err")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending WhatsApp message", e)
                Pair(false, e.localizedMessage ?: "Network connection error")
            }
        }
    }
}
