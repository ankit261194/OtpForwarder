# eDistrict OTP Forwarder (Android)

An automated 24/7 OTP Forwarder Android app that captures eDistrict UP login OTP SMS and notifications, and forwards them instantly via Telegram, WhatsApp, or Direct Phone SMS.

## 📥 Direct Download (Phone ke liye 1-Click Link)
Click here to download the latest APK directly on your phone:
👉 **[Download eDistrict OTP Forwarder APK](https://github.com/ankit261194/OtpForwarder/raw/main/release_apk/eDistrict_OTP_Forwarder.apk)**

---

## 🌟 Key Features

### 1. 📢 Multi-Channel Forwarding
- **Dual Telegram (Telegram 1 & Telegram 2)**:
  - **Telegram 1 (Primary)**: Verified Bot (`@AnkitEdistrictBot`) & Channel (`-1004470768686`).
  - **Telegram 2 (Secondary)**: Add a 2nd bot or 2nd channel/user with its own test button.
- **WhatsApp**: Direct message forwarding via CallMeBot gateway API.
- **Direct Phone SMS**: Forward directly to another mobile number via device SIM card (`SmsManager`).

### 2. 🛡️ Child & Security Protection
- **Anti-Uninstall Lock (Device Administrator)**: Protects the app from being uninstalled by children. Once activated, Android blocks the uninstall button completely.
- **Hide App Icon**: Hides the app from the home screen and app drawer.
- **Secret Deep-Link Launch**: Open the app even when hidden by entering `otp://open` in Chrome browser.

### 3. 🏷️ Dynamic Keyword Management
- Edit, add, or delete sender IDs and keywords directly on your phone without touching a laptop.
- Includes a **Live Message Match Simulator** to test if a message will be forwarded.

### 4. 📜 Real-time OTP History
- Stores up to 30 past OTP events with timestamp, sender, OTP code, and live delivery status badges (Telegram 1, Telegram 2, WhatsApp, SMS).
- One-tap manual refresh and clear history options.
