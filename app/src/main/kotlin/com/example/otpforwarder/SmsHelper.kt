package com.example.otpforwarder

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log

object SmsHelper {
    private const val TAG = "OtpForwarder_Sms"

    @SuppressLint("MissingPermission")
    fun sendSms(context: Context, phoneNumber: String, message: String): Pair<Boolean, String> {
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isEmpty()) {
            return Pair(false, "Mobile number khali hai")
        }

        val prefs = PrefsHelper.getPrefs(context)
        val preferredSlot = prefs.getInt("sms_sim_slot", -1) // -1 = default, 0 = SIM 1, 1 = SIM 2

        return try {
            var targetSmsManager: SmsManager? = null

            if (preferredSlot >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                try {
                    val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    val subList = subManager?.activeSubscriptionInfoList
                    val targetInfo = subList?.firstOrNull { it.simSlotIndex == preferredSlot }
                    if (targetInfo != null) {
                        targetSmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.getSystemService(SmsManager::class.java)?.createForSubscriptionId(targetInfo.subscriptionId)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsManager.getSmsManagerForSubscriptionId(targetInfo.subscriptionId)
                        }
                        Log.d(TAG, "Selected SIM slot $preferredSlot (SubId: ${targetInfo.subscriptionId}) for SMS")
                    } else {
                        Log.w(TAG, "SIM slot $preferredSlot not found in active subscriptions, using default.")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not resolve subscription SMS manager: ${e.message}")
                }
            }

            val smsManager = targetSmsManager ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (message.length > 160) {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanNumber, null, message, null, null)
            }

            val simLabel = when (preferredSlot) {
                0 -> " (via SIM 1)"
                1 -> " (via SIM 2)"
                else -> ""
            }
            Log.d(TAG, "Direct SMS sent to $cleanNumber$simLabel")
            Pair(true, "SMS sent to $cleanNumber$simLabel")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
            Pair(false, e.localizedMessage ?: "Failed to send SMS")
        }
    }
}
