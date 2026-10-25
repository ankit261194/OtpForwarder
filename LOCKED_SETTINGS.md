# 🔒 LOCKED WORKING SETTINGS (DO NOT MODIFY WITHOUT USER CONSENT)

> **STABLE VERSION:** v1.0-STABLE  
> **DATE LOCKED:** 2026-10-25 23:13:00  
> **STATUS:** VERIFIED WORKING ON PHYSICAL DEVICE

---

## 1. Frozen Telegram Credentials
* **Bot Username:** `@AnkitEdistrictBot`
* **Bot Token:** `8680515171:AAFAV9QowNvEJxl42mcFtMUhXhN6BuqPVSk` *(LOCKED)*
* **Telegram Channel Name:** `eDistrict OTP.`
* **Telegram Channel ID:** `-1004470768686` *(LOCKED)*
* **User Direct Chat ID:** `291058055` *(LOCKED)*

---

## 2. Core Architecture Rules
* **NotificationListenerService (`OtpNotificationListener.kt`):**
  - **MANDATORY:** Must never be removed. Android 13/14 drops direct `SMS_RECEIVED` on modern OEM skins (OxygenOS/Realme UI/MIUI), but `NotificationListenerService` reads Google Messages OTP notifications 100% reliably.
  - **Deduplication Window:** 30 seconds (`shouldForward(text)`).
* **BroadcastReceiver (`SmsReceiver.kt`):**
  - Secondary fallback with `goAsync()` and `pendingResult.finish()`.
* **Target Filter Rules:**
  - Sender contains: `UPEGOV` (covers `AD-UPEGOV-S`, `AX-UPEGOV-S`, `BZ-UPEGOV`, etc.)
  - Body contains: `eDistrict`, `eDistrict UP`, `UPEGOV`, or `OTP` from GOV.
  - Regex for OTP: `\b\d{4,8}\b` (extracts the exact 6-digit code).
* **Do NOT Re-add ForegroundService:**
  - Standard `ForegroundService` with `specialUse` on Android 14 causes `MissingForegroundServiceTypeException` and crashes the app. Keep it lightweight without background FGS crashes.

---

## 3. Signing Keystore
* **Keystore File:** `app/release.jks` *(LOCKED)*
* **Key Alias:** `releaseKey`
* **Store Password:** `password123`
* **Key Password:** `password123`

---

## 4. Backup Locations
1. **Frozen Source Code Clone:**  
   `C:\Users\hp\.gemini\antigravity\scratch\OtpForwarder_BACKUP_STABLE_V1`
2. **Git Tag:**  
   `v1.0-STABLE`
3. **Read-Only Verified APKs:**  
   - `C:\Users\hp\Downloads\eDistrict_OTP_Forwarder_SAFE_BACKUP.apk`
   - `C:\Users\hp\Desktop\eDistrict_OTP_Forwarder_SAFE_BACKUP.apk`
   - `C:\Users\hp\.gemini\antigravity\scratch\OtpForwarder_BACKUP_STABLE_V1\eDistrict_OTP_Forwarder.apk`

---
*Any future modification request must preserve all values in this file!*
