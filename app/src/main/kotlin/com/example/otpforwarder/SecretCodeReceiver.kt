package com.example.otpforwarder

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast

class SecretCodeReceiver : BroadcastReceiver() {
    private val TAG = "SecretCodeReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Secret code broadcast received with action: $action")

        if (action == "android.provider.Telephony.SECRET_CODE") {
            val host = intent.data?.host
            Log.d(TAG, "Secret code host: $host")

            if (host == "0825") {
                toggleVisibilityAndLaunch(context)
            }
        }
    }

    companion object {
        fun toggleVisibilityAndLaunch(context: Context): Boolean {
            val alias = ComponentName(context, "com.example.otpforwarder.LauncherAlias")
            val currentState = context.packageManager.getComponentEnabledSetting(alias)
            val isCurrentlyHidden = currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            // Toggle state: if hidden -> unhide; if visible -> hide
            val newHideState = !isCurrentlyHidden
            val newState = if (newHideState) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }

            context.packageManager.setComponentEnabledSetting(
                alias,
                newState,
                PackageManager.DONT_KILL_APP
            )

            // Launch MainActivity directly
            try {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e("SecretCodeReceiver", "Failed to launch MainActivity", e)
            }

            val msg = if (newHideState) "🔒 App Icon Hidden! (Code 0825)" else "🔓 App Icon Unhidden! (Code 0825)"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

            return newHideState
        }
    }
}
