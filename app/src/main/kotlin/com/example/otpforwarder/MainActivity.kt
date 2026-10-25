package com.example.otpforwarder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
                    MainScreenContent(
                        onOpenNotificationSettings = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            startActivity(intent)
                        },
                        onRequestPermissions = { requestAppPermissions() }
                    )
                }
            }
        }
    }

    private fun requestAppPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS
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

@Composable
fun MainScreenContent(
    onOpenNotificationSettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("otp_prefs", Context.MODE_PRIVATE) }
    var selectedTab by remember { mutableStateOf(0) }
    var isMasterEnabled by remember { mutableStateOf(prefs.getBoolean("is_enabled", true)) }
    var hasNotificationAccess by remember { mutableStateOf(checkNotificationAccess(context)) }

    LaunchedEffect(Unit) {
        hasNotificationAccess = checkNotificationAccess(context)
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
    val prefs = remember { context.getSharedPreferences("otp_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    // Telegram State
    var isTelegramEnabled by remember { mutableStateOf(prefs.getBoolean("is_telegram_enabled", true)) }
    var botToken by remember { mutableStateOf(prefs.getString("bot_token", TelegramHelper.DEFAULT_BOT_TOKEN) ?: "") }
    var chatId by remember { mutableStateOf(prefs.getString("chat_id", TelegramHelper.DEFAULT_CHANNEL_ID) ?: "") }
    var telegramStatus by remember { mutableStateOf("") }

    // WhatsApp State
    var isWhatsAppEnabled by remember { mutableStateOf(prefs.getBoolean("is_whatsapp_enabled", false)) }
    var waPhone by remember { mutableStateOf(prefs.getString("wa_phone", "") ?: "") }
    var waApiKey by remember { mutableStateOf(prefs.getString("wa_api_key", "") ?: "") }
    var waStatus by remember { mutableStateOf("") }

    // Direct SMS State
    var isSmsEnabled by remember { mutableStateOf(prefs.getBoolean("is_sms_enabled", false)) }
    var smsPhone by remember { mutableStateOf(prefs.getString("sms_phone", "") ?: "") }
    var smsStatus by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 1. Telegram Card
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
                        Text("📢 Telegram Forwarding", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                        Text("Channel ya Bot par message bhejein", fontSize = 11.sp, color = Color.DarkGray)
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
                        label = { Text("Bot Token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chatId,
                        onValueChange = { chatId = it },
                        label = { Text("Channel / Chat ID (e.g. -100...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            telegramStatus = "Sending test..."
                            scope.launch {
                                val res = TelegramHelper.sendMessage(
                                    botToken.trim(),
                                    chatId.trim(),
                                    "🎉 Test Message: Telegram forwarding test successful!"
                                )
                                telegramStatus = if (res.first) "✅ Telegram Par Test Message Chala Gaya!" else "❌ ${res.second}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Telegram")
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

        // 2. WhatsApp Card
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
                        Text("💬 WhatsApp Forwarding", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B5E20))
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test WhatsApp")
                    }
                    if (waStatus.isNotEmpty()) {
                        Text(
                            text = waStatus,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (waStatus.startsWith("✅")) Color(0xFF1B5E20) else Color(0xFFC62828),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // 3. Direct SMS Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
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
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = smsPhone,
                        onValueChange = { smsPhone = it },
                        label = { Text("Friend's Mobile No. (10 Digits)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
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

        // Save All Settings Button
        Button(
            onClick = {
                prefs.edit()
                    .putBoolean("is_telegram_enabled", isTelegramEnabled)
                    .putString("bot_token", botToken.trim())
                    .putString("chat_id", chatId.trim())
                    .putBoolean("is_whatsapp_enabled", isWhatsAppEnabled)
                    .putString("wa_phone", waPhone.trim())
                    .putString("wa_api_key", waApiKey.trim())
                    .putBoolean("is_sms_enabled", isSmsEnabled)
                    .putString("sms_phone", smsPhone.trim())
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
    var keywordsList by remember { mutableStateOf(KeywordManager.getKeywords(context)) }
    var newKeywordText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Current Active Rules (${keywordsList.size}):",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(keywordsList) { kw ->
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
    }
}

@Composable
fun HistoryTab(context: Context) {
    var historyList by remember { mutableStateOf(HistoryManager.getHistory(context)) }

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

        Spacer(modifier = Modifier.height(10.dp))

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Abhi tak koi OTP forward nahi hua hai.\nJaise hi koi OTP aayega, uska khata yahan dikhega.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                                StatusBadge(label = "Telegram", status = item.telegramStatus)
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
