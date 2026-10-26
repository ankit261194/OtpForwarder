package com.example.otpforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val body = sms.messageBody ?: ""
            val sender = sms.originatingAddress ?: ""
            Log.d(TAG, "SMS Received from $sender: $body")

            val pendingResult = goAsync()

            // Acquire Partial WakeLock to force CPU and network to stay awake in deep sleep
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "OtpForwarder:SmsWakeLock"
            )
            try {
                wakeLock?.acquire(20000) // Keep awake for up to 20 seconds
            } catch (e: Exception) {
                Log.e(TAG, "Error acquiring WakeLock", e)
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    OtpDispatcher.dispatch(context, sender, body)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in SmsReceiver dispatch", e)
                } finally {
                    try {
                        if (wakeLock?.isHeld == true) {
                            wakeLock.release()
                        }
                    } catch (ignored: Exception) {}
                    pendingResult.finish()
                }
            }
        }
    }
}
