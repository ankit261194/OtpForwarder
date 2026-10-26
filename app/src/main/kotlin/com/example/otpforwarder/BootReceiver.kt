package com.example.otpforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Boot event received: $action (Direct Boot Active)")
        try {
            val prefs = PrefsHelper.getPrefs(context)
            val isEnabled = prefs.getBoolean("is_enabled", true)
            Log.d(TAG, "Direct Boot initialized successfully. Forwarder enabled: $isEnabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error in BootReceiver init", e)
        }
    }
}
