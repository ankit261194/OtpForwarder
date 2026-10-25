package com.example.otpforwarder

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

object SmsHelper {
    private const val TAG = "OtpForwarder_Sms"

    fun sendSms(context: Context, phoneNumber: String, message: String): Pair<Boolean, String> {
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isEmpty()) {
            return Pair(false, "Mobile number khali hai")
        }

        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

            Log.d(TAG, "Direct SMS sent to $cleanNumber")
            Pair(true, "SMS sent to $cleanNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
            Pair(false, e.localizedMessage ?: "Failed to send SMS")
        }
    }
}
