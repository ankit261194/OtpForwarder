package com.example.otpforwarder

import android.content.Context
import org.json.JSONArray

object KeywordManager {
    private const val PREFS_NAME = "otp_prefs"
    private const val KEY_KEYWORDS = "custom_keywords"

    val DEFAULT_KEYWORDS = listOf("UPEGOV", "eDistrict", "eDistrict UP", "EDIST", "UPGOV", "EDISTRICT", "NICED", "OTP", "verification", "code", "One Time Password", "aadhar", "aadhaar", "gov")

    fun getKeywords(context: Context): List<String> {
        val prefs = PrefsHelper.getPrefs(context)
        val jsonString = prefs.getString(KEY_KEYWORDS, null) ?: return DEFAULT_KEYWORDS
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            if (list.isEmpty()) DEFAULT_KEYWORDS else list
        } catch (e: Exception) {
            DEFAULT_KEYWORDS
        }
    }

    fun addKeyword(context: Context, newKeyword: String): List<String> {
        val trimmed = newKeyword.trim()
        if (trimmed.isEmpty()) return getKeywords(context)

        val current = getKeywords(context).toMutableList()
        if (!current.any { it.equals(trimmed, ignoreCase = true) }) {
            current.add(trimmed)
            saveKeywords(context, current)
        }
        return current
    }

    fun removeKeyword(context: Context, keyword: String): List<String> {
        val current = getKeywords(context).toMutableList()
        current.removeAll { it.equals(keyword, ignoreCase = true) }
        val updated = if (current.isEmpty()) DEFAULT_KEYWORDS else current
        saveKeywords(context, updated)
        return updated
    }

    fun resetDefaults(context: Context): List<String> {
        saveKeywords(context, DEFAULT_KEYWORDS)
        return DEFAULT_KEYWORDS
    }

    private fun saveKeywords(context: Context, keywords: List<String>) {
        val prefs = PrefsHelper.getPrefs(context)
        val jsonArray = JSONArray()
        for (kw in keywords) {
            jsonArray.put(kw)
        }
        prefs.edit().putString(KEY_KEYWORDS, jsonArray.toString()).apply()
    }

    fun matches(context: Context, sender: String, body: String): Boolean {
        val keywords = getKeywords(context)
        val s = sender.trim()
        val b = body.trim()
        val keywordMatch = keywords.any { kw ->
            s.contains(kw, ignoreCase = true) || b.contains(kw, ignoreCase = true)
        }
        val otpPatternMatch = Regex("""\b\d{4,8}\b""").containsMatchIn(b) && 
                (b.contains("otp", ignoreCase = true) || b.contains("code", ignoreCase = true) || b.contains("verification", ignoreCase = true) || b.contains("password", ignoreCase = true) || b.contains("aavedan", ignoreCase = true) || b.contains("praman", ignoreCase = true))
        return keywordMatch || otpPatternMatch
    }
}
