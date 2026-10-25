# STRICT SYSTEM RULES FOR OTPFORWARDER PROJECT

> [!CRITICAL]
> **DO NOT MODIFY OR DELETE EXISTING WORKING SETTINGS WITHOUT EXPLICIT PERMISSION FROM THE USER.**

### 1. FROZEN CONFIGURATION (NEVER CHANGE AUTOMATICALLY):
* **Telegram Bot Token:** `8680515171:AAFAV9QowNvEJxl42mcFtMUhXhN6BuqPVSk`
* **Telegram Channel ID:** `-1004470768686`
* **Telegram Personal Chat ID:** `291058055`
* **Core Forwarding Mechanism:**
  - `OtpNotificationListener.kt` (NotificationListenerService) MUST NEVER BE REMOVED. It is required to capture Google Messages notifications on modern Android 13/14.
  - `SmsReceiver.kt` (BroadcastReceiver) serves as secondary fallback.
  - Filter keywords: `UPEGOV`, `AD-UPEGOV-S`, `AX-UPEGOV-S`, `eDistrict`, `eDistrict UP`.
* **Signing Keystore:** `app/release.jks` with alias `releaseKey` and password `password123`.

### 2. PROTOCOL FOR ANY FUTURE EDITS:
1. When the user requests a change or new feature, ALWAYS inform the user of what will be added BEFORE modifying files.
2. NEVER remove or overwrite the working Notification Listener or Telegram credentials.
3. If code changes fail, revert immediately to git tag `v1.0-STABLE` or restore from `OtpForwarder_BACKUP_STABLE_V1`.
