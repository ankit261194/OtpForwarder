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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
            Toast.makeText(this, "✅ SMS Permission Granted", Toast.LENGTH_SHORT).show()
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
                    OtpForwarderScreen(
                        onGrantSms = { requestAppPermissions() },
                        onOpenNotificationSettings = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            startActivity(intent)
                        }
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

fun isNotificationAccessGranted(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(pkgName)
}

@Composable
fun OtpForwarderScreen(onGrantSms: () -> Unit, onOpenNotificationSettings: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("otp_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var isEnabled by remember { mutableStateOf(prefs.getBoolean("is_enabled", true)) }
    var statusText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    val hasSmsPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECEIVE_SMS
    ) == PackageManager.PERMISSION_GRANTED

    var hasNotificationAccess by remember { mutableStateOf(isNotificationAccessGranted(context)) }

    // Refresh notification access check when screen appears
    LaunchedEffect(Unit) {
        hasNotificationAccess = isNotificationAccessGranted(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "eDistrict OTP Forwarder",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ON/OFF Switch Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isEnabled) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEnabled) "Status: ACTIVE (ON)" else "Status: STOPPED (OFF)",
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (isEnabled) "✅ Auto Forwarding ON hai" else "❌ Auto Forwarding band hai",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        isEnabled = checked
                        prefs.edit().putBoolean("is_enabled", checked).apply()
                    }
                )
            }
        }

        // Notification Access Card (CRITICAL FOR MODERN ANDROID)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (hasNotificationAccess) Color(0xFFE3F2FD) else Color(0xFFFFF3E0)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (hasNotificationAccess) "✅ Notification Listener: ON" else "⚡ Notification Access Enable Karein",
                    fontWeight = FontWeight.Bold,
                    color = if (hasNotificationAccess) Color(0xFF1565C0) else Color(0xFFE65100),
                    fontSize = 15.sp
                )
                Text(
                    text = if (hasNotificationAccess)
                        "Google Messages se aane wala OTP turant pakad kar Telegram bhejega."
                    else
                        "Naye phones direct SMS block karte hain. Isliye Notification Access chalu karna zaroori hai taaki OTP aate hi turant forward ho jaye.",
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                if (!hasNotificationAccess) {
                    Button(
                        onClick = onOpenNotificationSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                    ) {
                        Text("Notification Access Allow Karein")
                    }
                }
            }
        }

        // SMS Permission Card
        if (!hasSmsPermission) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SMS Permission",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Button(
                        onClick = onGrantSms,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                    ) {
                        Text("Allow")
                    }
                }
            }
        }

        // Test Message Button
        Button(
            onClick = {
                isSending = true
                statusText = "Sending test message..."
                scope.launch {
                    val res1 = TelegramHelper.sendMessage(
                        TelegramHelper.DEFAULT_BOT_TOKEN,
                        TelegramHelper.DEFAULT_CHANNEL_ID,
                        "🎉 Test Message: App aapke phone se successfully connect ho chuki hai!"
                    )
                    isSending = false
                    if (res1.first) {
                        statusText = "✅ SUCCESS! Telegram Channel me message pahunch gaya!"
                        Toast.makeText(context, "Channel me message gaya!", Toast.LENGTH_SHORT).show()
                    } else {
                        statusText = "❌ FAILED: ${res1.second}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isSending,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
        ) {
            Text(
                text = if (isSending) "Sending..." else "📲 Telegram Par Test Message Bhejein",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (statusText.startsWith("✅")) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
