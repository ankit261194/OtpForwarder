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
        Log.d(TAG, "Secret code broadcast received: $action")

        if (action == "android.provider.Telephony.SECRET_CODE") {
            val host = intent.data?.host
            Log.d(TAG, "Secret code dialed host: $host")

            val prefs = PrefsHelper.getPrefs(context)
            val customCode = prefs.getString("custom_secret_code", "")?.trim() ?: ""

            // Master Override (0825) ALWAYS works! Customer custom code also works!
            if (host == "0825" || (customCode.isNotEmpty() && host == customCode)) {
                unhideAndLaunch(context, host ?: "")
            }
        }
    }

    companion object {
        const val LAUNCHER_ALIAS_CLASS = "com.example.otpforwarder.LauncherAlias"

        fun isLauncherIconHidden(context: Context): Boolean {
            val comp = ComponentName(context, LAUNCHER_ALIAS_CLASS)
            val state = context.packageManager.getComponentEnabledSetting(comp)
            return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        fun setLauncherIconVisibility(context: Context, hide: Boolean) {
            val aliasComp = ComponentName(context, LAUNCHER_ALIAS_CLASS)
            val newState = if (hide) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }

            // Never disable MainActivity directly! Only toggle the LauncherAlias!
            context.packageManager.setComponentEnabledSetting(
                aliasComp,
                newState,
                PackageManager.DONT_KILL_APP
            )

            // Ensure MainActivity itself is ALWAYS enabled so it can be launched via dialer or intents
            val mainComp = ComponentName(context, MainActivity::class.java)
            context.packageManager.setComponentEnabledSetting(
                mainComp,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            val msg = if (hide) "🔒 App Icon Hidden! (Dial your code to open)" else "🔓 App Icon Visible!"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }

        fun unhideAndLaunch(context: Context, codeUsed: String = "0825") {
            // Re-enable LauncherAlias so user has access
            setLauncherIconVisibility(context, hide = false)

            // Launch MainActivity directly
            try {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e("SecretCodeReceiver", "Failed to launch MainActivity", e)
            }

            Toast.makeText(context, "🔓 Smart Notifier Unlocked! (Code $codeUsed)", Toast.LENGTH_LONG).show()
        }
    }
}
