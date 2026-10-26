package com.example.otpforwarder

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

object PrefsHelper {
    private const val PREFS_NAME = "otp_prefs"

    fun getPrefs(context: Context): SharedPreferences {
        val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val deviceContext = context.createDeviceProtectedStorageContext()
            try {
                // Automatically migrate existing preferences from credential-protected storage if needed
                deviceContext.moveSharedPreferencesFrom(context, PREFS_NAME)
            } catch (ignored: Exception) {
            }
            deviceContext
        } else {
            context
        }
        return storageContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
