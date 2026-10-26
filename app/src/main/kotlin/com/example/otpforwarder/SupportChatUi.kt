package com.example.otpforwarder

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = "attachment"
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
    } else if (uri.scheme == "file") {
        name = uri.lastPathSegment ?: "attachment"
    }
    return name
}

@Composable
fun SupportChatDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val deviceId = remember { CloudflareLicenseManager.getDeviceId(context) }
    var messagesList by remember { mutableStateOf(SupportChatManager.getMessages(context)) }
    var messageText by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedMimeType by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val isBlocked = remember { SupportChatManager.isUserBlocked(context, deviceId) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedMimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            selectedFileName = getFileNameFromUri(context, uri)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.size - 1)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0B1120),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Live Support & Help Desk",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color(0xFFA5B4FC)
                        )
                    }
                    Text("Direct Admin Chat (24/7 Sahayata)", fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
                Text(
                    "✕",
                    fontSize = 16.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.clickable { onDismiss() }.padding(4.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp, max = 460.dp)
            ) {
                // Info Banner
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "ℹ️ App me koi dikkat ho to yahan likhein ya screenshot bhejein. Admin turant sahayata karega.",
                        fontSize = 10.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chat Messages List
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    if (messagesList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Abhi koi message nahi hai.\nApni pareshani yahan likhkar bhejein.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messagesList) { msg ->
                                val isMe = msg.sender == "USER"
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                                ) {
                                    Surface(
                                        color = if (isMe) Color(0xFF4F46E5) else Color(0xFF1E293B),
                                        shape = RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isMe) 12.dp else 2.dp,
                                            bottomEnd = if (isMe) 2.dp else 12.dp
                                        ),
                                        border = BorderStroke(1.dp, if (isMe) Color(0xFF6366F1) else Color(0xFF334155))
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            if (!isMe) {
                                                Text("🛡️ Admin (Ankit)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                                            }
                                            if (!msg.mediaName.isNullOrEmpty()) {
                                                val icon = if (msg.mediaType == "IMAGE") "🖼️ Photo: " else "📄 Doc: "
                                                Text(
                                                    "$icon${msg.mediaName}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFFFDE047)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }
                                            if (msg.text.isNotEmpty()) {
                                                Text(msg.text, fontSize = 12.sp, color = Color.White)
                                            }
                                            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))
                                            Text(
                                                timeStr,
                                                fontSize = 8.sp,
                                                color = Color(0xFFCBD5E1).copy(alpha = 0.7f),
                                                modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Blocked or Active Input
                if (isBlocked) {
                    Surface(
                        color = Color(0xFF450A0A),
                        border = BorderStroke(1.dp, Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "🚫 Aapka device support desk se blocked hai. Naya message nahi bhej sakte.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFCA5A5),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    // Attachment preview tag if picked
                    if (selectedFileUri != null) {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("📎 $selectedFileName", fontSize = 10.sp, color = Color(0xFF67E8F9), modifier = Modifier.weight(1f))
                                Text(
                                    "✕",
                                    fontSize = 12.sp,
                                    color = Color.Red,
                                    modifier = Modifier.clickable {
                                        selectedFileUri = null
                                        selectedFileName = ""
                                        selectedMimeType = ""
                                    }.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    // Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // File picker button
                        Button(
                            onClick = { filePicker.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("📎", fontSize = 15.sp)
                        }

                        // Message text field
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Message likhein...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF6366F1),
                                unfocusedBorderColor = Color(0xFF334155)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                            singleLine = true
                        )

                        // Send button
                        Button(
                            onClick = {
                                if (messageText.isNotBlank() || selectedFileUri != null) {
                                    scope.launch {
                                        isSending = true
                                        var fileBytes: ByteArray? = null
                                        if (selectedFileUri != null) {
                                            try {
                                                fileBytes = context.contentResolver.openInputStream(selectedFileUri!!)?.use { it.readBytes() }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "File read error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }

                                        val res = SupportChatManager.sendCustomerMessage(
                                            context = context,
                                            text = messageText.trim(),
                                            fileBytes = fileBytes,
                                            fileName = selectedFileName,
                                            mimeType = selectedMimeType
                                        )

                                        if (res.first) {
                                            messageText = ""
                                            selectedFileUri = null
                                            selectedFileName = ""
                                            selectedMimeType = ""
                                            messagesList = SupportChatManager.getMessages(context)
                                            Toast.makeText(context, "✅ Message Sent!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "❌ ${res.second}", Toast.LENGTH_SHORT).show()
                                        }
                                        isSending = false
                                    }
                                }
                            },
                            enabled = !isSending && (messageText.isNotBlank() || selectedFileUri != null),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(if (isSending) "..." else "➤", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Close", fontSize = 11.sp)
            }
        }
    )
}

@Composable
fun AdminSupportDeskView(context: Context) {
    val scope = rememberCoroutineScope()
    var messagesList by remember { mutableStateOf(SupportChatManager.getMessages(context)) }
    var replyText by remember { mutableStateOf("") }
    var targetDeviceId by remember { mutableStateOf("") }
    var isSendingReply by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("💬 Customer Support Desk (Live Tickets)", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFFFBBF24))
        Text("Grahakon ke messages dekhein, reply karein, delete ya block karein:", fontSize = 10.sp, color = Color(0xFF94A3B8))
        Spacer(modifier = Modifier.height(8.dp))

        // Messages list
        if (messagesList.isEmpty()) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(
                    "Abhi koi naya support ticket nahi hai.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
            ) {
                LazyColumn(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(messagesList) { msg ->
                        val isBlocked = SupportChatManager.isUserBlocked(context, msg.deviceId)
                        Surface(
                            color = if (msg.sender == "USER") Color(0xFF1E293B) else Color(0xFF1E1B4B),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (msg.sender == "USER") Color(0xFF475569) else Color(0xFF4F46E5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        if (msg.sender == "USER") "👤 Customer (${msg.deviceId.take(8)}...)" else "👑 Admin (You)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.sender == "USER") Color(0xFF67E8F9) else Color(0xFFA5B4FC)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Delete Button
                                        Button(
                                            onClick = {
                                                SupportChatManager.deleteMessage(context, msg.id)
                                                messagesList = SupportChatManager.getMessages(context)
                                                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("🗑️ Del", fontSize = 9.sp)
                                        }

                                        // Block / Unblock Button
                                        if (msg.sender == "USER") {
                                            Button(
                                                onClick = {
                                                    val newBlocked = !isBlocked
                                                    SupportChatManager.setUserBlocked(context, msg.deviceId, newBlocked)
                                                    messagesList = SupportChatManager.getMessages(context)
                                                    Toast.makeText(context, if (newBlocked) "User Blocked" else "User Unblocked", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = if (isBlocked) Color(0xFF047857) else Color(0xFFDC2626)),
                                                shape = RoundedCornerShape(4.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(if (isBlocked) "Unblock" else "🚫 Block", fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }

                                if (!msg.mediaName.isNullOrEmpty()) {
                                    Text("📎 ${msg.mediaType}: ${msg.mediaName}", fontSize = 10.sp, color = Color(0xFFFDE047))
                                }
                                if (msg.text.isNotEmpty()) {
                                    Text(msg.text, fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Reply Box
        Text("Customer ko Reply Karein:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = targetDeviceId,
            onValueChange = { targetDeviceId = it },
            placeholder = { Text("Customer Device ID (default current device)", fontSize = 10.sp, color = Color(0xFF64748B)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = replyText,
                onValueChange = { replyText = it },
                placeholder = { Text("Reply message likhein...", fontSize = 10.sp, color = Color(0xFF64748B)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(44.dp),
                singleLine = true
            )

            Button(
                onClick = {
                    if (replyText.isNotBlank()) {
                        scope.launch {
                            isSendingReply = true
                            val devId = if (targetDeviceId.isNotBlank()) targetDeviceId.trim() else CloudflareLicenseManager.getDeviceId(context)
                            val res = SupportChatManager.sendAdminReply(context, devId, replyText.trim())
                            if (res.first) {
                                replyText = ""
                                messagesList = SupportChatManager.getMessages(context)
                                Toast.makeText(context, "✅ Reply Sent!", Toast.LENGTH_SHORT).show()
                            }
                            isSendingReply = false
                        }
                    }
                },
                enabled = !isSendingReply && replyText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(if (isSendingReply) "..." else "Reply", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Clear All Chat Button
        Button(
            onClick = {
                SupportChatManager.clearChat(context)
                messagesList = emptyList()
                Toast.makeText(context, "Chat Cleared", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().height(36.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            Text("🧹 Clear All Support Chat History", fontSize = 10.sp)
        }
    }
}
