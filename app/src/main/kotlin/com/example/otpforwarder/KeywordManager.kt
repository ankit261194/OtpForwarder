package com.example.otpforwarder

import android.content.Context
import org.json.JSONArray

object KeywordManager {
    private const val PREFS_NAME = "otp_prefs"
    private const val KEY_KEYWORDS = "custom_keywords"

    val DEFAULT_KEYWORDS = listOf("UPEGOV", "eDistrict", "eDistrict UP")

    fun getKeywords(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    private fun saveKeywords(context: Context, keywords: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (kw in keywords) {
            jsonArray.put(kw)
        }
        prefs.edit().putString(KEY_KEYWORDS, jsonArray.toString()).apply()
    }

    fun matches(context: Context, sender: String, body: String): Boolean {
        val keywords = getKeywords(context)
        return keywords.any { kw ->
            sender.contains(kw, ignoreCase = true) || body.contains(kw, ignoreCase = true)
        } || (body.contains("OTP", ignoreCase = true) && sender.contains("GOV", ignoreCase = true))
    }
}
