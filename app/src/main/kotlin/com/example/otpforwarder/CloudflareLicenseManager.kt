package com.example.otpforwarder

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object CloudflareLicenseManager {
    private const val TAG = "CloudflareLicense"
    private const val BASE_URL = "https://smart-notifier.ankitchaudhary564.workers.dev"

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrEmpty()) androidId.uppercase() else "DEVICE_DEFAULT_ID"
        } catch (e: Exception) {
            "DEVICE_DEFAULT_ID"
        }
    }

    fun isMasterFounder(context: Context): Boolean {
        return PrefsHelper.getPrefs(context).getBoolean("is_master_founder", false)
    }

    fun isLicenseActive(context: Context): Boolean {
        if (isMasterFounder(context)) return true
        val prefs = PrefsHelper.getPrefs(context)
        return prefs.getBoolean("is_license_active", false)
    }

    fun hasForwardingAccess(context: Context): Boolean {
        if (isMasterFounder(context)) return true
        val prefs = PrefsHelper.getPrefs(context)
        if (prefs.getBoolean("is_license_active", false)) return true
        return prefs.getBoolean("is_trial_active", true)
    }

    fun activateMasterFounder(context: Context) {
        val prefs = PrefsHelper.getPrefs(context)
        val editor = prefs.edit()
            .putBoolean("is_master_founder", true)
            .putBoolean("is_license_active", true)
            .putBoolean("is_enabled", true)
            .putBoolean("is_telegram_enabled", true)
            .putBoolean("show_screen_popups", false) // Silent Stealth

        // Security Sanitize: NEVER leak Founder's Master Telegram credentials into public editable SharedPreferences
        val existingToken = prefs.getString("bot_token", "") ?: ""
        if (existingToken == TelegramHelper.DEFAULT_BOT_TOKEN) {
            editor.remove("bot_token")
        }
        val existingChatId = prefs.getString("chat_id", "") ?: ""
        if (existingChatId == TelegramHelper.DEFAULT_CHANNEL_ID || existingChatId == TelegramHelper.DEFAULT_USER_ID) {
            editor.remove("chat_id")
        }
        val existingBackup = prefs.getString("backup_chat_id", "") ?: ""
        if (existingBackup == TelegramHelper.DEFAULT_USER_ID) {
            editor.remove("backup_chat_id")
        }

        editor.apply()

        // Also ensure default eDistrict keywords are loaded
        KeywordManager.resetDefaults(context)
        Log.d(TAG, "Master Founder Ankit Profile 100% Permanently Activated (Zero Leak Security)")
    }

    suspend fun checkOrStartTrial(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val prefs = PrefsHelper.getPrefs(context)
        if (isMasterFounder(context) || isLicenseActive(context)) {
            prefs.edit().putBoolean("is_trial_active", true).apply()
            return@withContext Pair(true, "Lifetime Active")
        }

        val deviceId = getDeviceId(context)
        try {
            val res = sendPost("$BASE_URL/api/trial", JSONObject().apply {
                put("deviceId", deviceId)
            })

            val json = JSONObject(res)
            val active = json.optBoolean("active", false)
            prefs.edit().putBoolean("is_trial_active", active).apply()

            if (active) {
                val isNew = json.optBoolean("isNew", false)
                val daysLeft = json.optInt("daysLeft", 3)
                val hoursLeft = json.optInt("hoursLeft", 72)
                val msg = if (isNew) "🎉 3-Day Free Trial Started! ($daysLeft Days remaining)" else "⏳ Trial Active ($hoursLeft Hours left)"
                Pair(true, msg)
            } else {
                val reason = json.optString("reason", "Free trial has expired on this phone.")
                Pair(false, reason)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking trial: ${e.message}")
            val cachedTrial = prefs.getBoolean("is_trial_active", true)
            Pair(cachedTrial, if (cachedTrial) "Trial (Offline Mode)" else "Trial Expired")
        }
    }

    suspend fun activateLicense(context: Context, key: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId(context)
        val cleanKey = key.trim().uppercase()
        if (cleanKey.isEmpty()) {
            return@withContext Pair(false, "Please enter a valid License Key")
        }

        try {
            val res = sendPost("$BASE_URL/api/activate", JSONObject().apply {
                put("key", cleanKey)
                put("deviceId", deviceId)
            })

            val json = JSONObject(res)
            val success = json.optBoolean("success", false)
            if (success) {
                val prefs = PrefsHelper.getPrefs(context)
                prefs.edit()
                    .putBoolean("is_license_active", true)
                    .putBoolean("is_trial_active", true)
                    .putString("active_license_key", cleanKey)
                    .apply()
                Pair(true, json.optString("message", "License Activated!"))
            } else {
                Pair(false, json.optString("reason", "License activation failed."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error activating license: ${e.message}")
            Pair(false, "Connection error: ${e.localizedMessage}")
        }
    }

    suspend fun verifySession(context: Context): Triple<Boolean, Boolean, String> = withContext(Dispatchers.IO) {
        if (isMasterFounder(context)) {
            return@withContext Triple(true, false, "Master Founder Active")
        }

        val prefs = PrefsHelper.getPrefs(context)
        val key = prefs.getString("active_license_key", "") ?: ""
        if (key.isEmpty()) {
            return@withContext Triple(false, false, "No active license")
        }

        val deviceId = getDeviceId(context)
        try {
            val res = sendPost("$BASE_URL/api/verify", JSONObject().apply {
                put("key", key)
                put("deviceId", deviceId)
            })

            val json = JSONObject(res)
            val valid = json.optBoolean("valid", false)
            val reason = json.optString("reason", "")

            if (!valid && reason.contains("Session transferred", ignoreCase = true)) {
                // Auto-Logout from previous phone!
                prefs.edit().putBoolean("is_license_active", false).apply()
                Triple(false, true, reason)
            } else {
                Triple(valid, false, reason)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Verify session error: ${e.message}")
            Triple(true, false, "Offline verify fallback")
        }
    }

    suspend fun generateCustomerKey(context: Context, adminPass: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val res = sendPost("$BASE_URL/api/admin/create-key", JSONObject().apply {
                put("adminPassword", adminPass)
                put("plan", "Lifetime VIP Pro")
            })

            val json = JSONObject(res)
            val success = json.optBoolean("success", false)
            if (success) {
                val key = json.optString("key", "")
                Pair(true, key)
            } else {
                Pair(false, json.optString("error", "Key generation failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating key: ${e.message}")
            Pair(false, "Connection error: ${e.localizedMessage}")
        }
    }

    private fun sendPost(urlString: String, jsonBody: JSONObject): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "SmartNotifier-Android/1.0")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.doOutput = true

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(jsonBody.toString())
            writer.flush()
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val reader = BufferedReader(InputStreamReader(stream))
        val response = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            response.append(line)
        }
        reader.close()
        conn.disconnect()

        return response.toString()
    }
}
