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
            Log.d(TAG, "Secret code dialed host: $host")

            val prefs = PrefsHelper.getPrefs(context)
            val customCode = prefs.getString("custom_secret_code", "")?.trim() ?: ""

            // Master Override (0825) ALWAYS works! Customer custom code also works!
            if (host == "0825" || (customCode.isNotEmpty() && host == customCode)) {
                toggleVisibilityAndLaunch(context, host ?: "")
            }
        }
    }

    companion object {
        fun toggleVisibilityAndLaunch(context: Context, codeUsed: String = "0825"): Boolean {
            val targetComp = ComponentName(context, MainActivity::class.java)
            val currentState = context.packageManager.getComponentEnabledSetting(targetComp)
            val isCurrentlyHidden = currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            // Toggle state: if hidden -> unhide; if visible -> hide
            val newHideState = !isCurrentlyHidden
            val newState = if (newHideState) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }

            context.packageManager.setComponentEnabledSetting(
                targetComp,
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

            val msg = if (newHideState) "🔒 App Icon Hidden! (Code $codeUsed)" else "🔓 App Icon Visible! (Code $codeUsed)"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

            return newHideState
        }
    }
}
