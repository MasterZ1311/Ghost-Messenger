package org.ghostmessenger.ui.home

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ghostmessenger.data.local.entities.ConversationEntity
import org.ghostmessenger.data.network.model.SignalingConnectionState
import org.ghostmessenger.ui.theme.GhostColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val currentIdentity by viewModel.currentIdentity.collectAsState()
    val signalingState by viewModel.signalingState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        containerColor = GhostColors.BgObsidian,
        topBar = {
            HomeTopBar(
                signalingState = signalingState,
                onSettingsClick = onNavigateToSettings
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddContactDialog() },
                containerColor = GhostColors.GhostGreen,
                contentColor = GhostColors.BgObsidian,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Encrypted Chat")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Identity Header Card
            currentIdentity?.let { identity ->
                IdentityCard(
                    userCode = identity.userCode,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(identity.userCode))
                        Toast.makeText(context, "UserCode copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Conversations List
            if (conversations.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(conversations, key = { it.userCode }) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = { onNavigateToChat(conversation.userCode) },
                            onDelete = { viewModel.deleteConversation(conversation.userCode) }
                        )
                    }
                }
            }
        }

        // Add Contact Dialog
        if (uiState.showAddContactDialog) {
            AddContactDialog(
                input = uiState.addContactInput,
                error = uiState.addContactError,
                onInputChange = { viewModel.updateAddContactInput(it) },
                onDismiss = { viewModel.closeAddContactDialog() },
                onConfirm = {
                    viewModel.addContact { targetUserCode ->
                        onNavigateToChat(targetUserCode)
                    }
                }
            )
        }
    }
}

@Composable
private fun HomeTopBar(
    signalingState: SignalingConnectionState,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = GhostColors.SurfaceSlate,
        border = BorderStroke(0.5.dp, GhostColors.BorderGlow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "👻", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GHOST",
                    color = GhostColors.GhostGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Connection Indicator
                val (statusColor, statusText) = when (signalingState) {
                    SignalingConnectionState.CONNECTED -> GhostColors.GhostGreen to "ONLINE"
                    SignalingConnectionState.CONNECTING -> GhostColors.NeonCyan to "CONNECTING"
                    SignalingConnectionState.DISCONNECTED -> GhostColors.WarningRed to "OFFLINE"
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.width(12.dp))

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = GhostColors.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityCard(
    userCode: String,
    onCopy: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(GhostColors.CardGlass, RoundedCornerShape(8.dp))
            .border(1.dp, GhostColors.BorderGlow, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "YOUR GHOST ADDRESS",
                    color = GhostColors.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = userCode,
                    color = GhostColors.GhostGreen,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .background(GhostColors.SurfaceSlate, RoundedCornerShape(6.dp))
                    .size(36.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy UserCode",
                    tint = GhostColors.GhostGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: ConversationEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(conversation.lastMessageTimestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar Badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(GhostColors.SurfaceSlate, RoundedCornerShape(8.dp))
                .border(1.dp, GhostColors.NeonCyan, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = conversation.userCode.take(2),
                color = GhostColors.NeonCyan,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = conversation.userCode,
                    color = GhostColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
                Text(
                    text = dateStr,
                    color = GhostColors.TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastMessage ?: "Encrypted Session Established",
                    color = GhostColors.TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(GhostColors.GhostGreen, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${conversation.unreadCount}",
                            color = GhostColors.BgObsidian,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete Conversation",
                tint = GhostColors.BorderGlow,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚡",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "NO ENCRYPTED CHANNELS",
            color = GhostColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap '+' to initiate an anonymous P2P session with a contact's UserCode.",
            color = GhostColors.TextMuted,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun AddContactDialog(
    input: String,
    error: String?,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GhostColors.SurfaceSlate,
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = "START ENCRYPTED CHAT",
                color = GhostColors.GhostGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter contact's 8-character UserCode (e.g. 5JKL-2P4X):",
                    color = GhostColors.TextMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("XXXX-XXXX", color = GhostColors.TextMuted, fontFamily = FontFamily.Monospace)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GhostColors.BgObsidian,
                        unfocusedContainerColor = GhostColors.BgObsidian,
                        focusedBorderColor = if (error == null) GhostColors.GhostGreen else GhostColors.WarningRed,
                        unfocusedBorderColor = if (error == null) GhostColors.BorderGlow else GhostColors.WarningRed,
                        focusedTextColor = GhostColors.TextPrimary,
                        unfocusedTextColor = GhostColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = GhostColors.WarningRed,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GhostColors.GhostGreen,
                    contentColor = GhostColors.BgObsidian
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("START CHAT", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, Color.Transparent),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("CANCEL", color = GhostColors.TextMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    )
}
