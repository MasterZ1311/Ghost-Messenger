package org.ghostmessenger.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ghostmessenger.data.local.entities.MessageEntity
import org.ghostmessenger.ui.theme.GhostColors
import org.webrtc.DataChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val dataChannelState by viewModel.dataChannelState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = GhostColors.BgObsidian,
        topBar = {
            ChatTopBar(
                userCode = viewModel.recipientUserCode,
                dataChannelState = dataChannelState,
                onBack = onNavigateBack
            )
        },
        bottomBar = {
            ChatInputBar(
                input = uiState.messageInput,
                isSending = uiState.isSending,
                onInputChange = { viewModel.updateInput(it) },
                onSend = { viewModel.sendMessage() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Security Protocol Banner
            SecurityBanner()

            // Message History List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }

            if (uiState.sendError != null) {
                Text(
                    text = uiState.sendError ?: "",
                    color = GhostColors.WarningRed,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    userCode: String,
    dataChannelState: DataChannel.State,
    onBack: () -> Unit
) {
    Surface(
        color = GhostColors.SurfaceSlate,
        border = BorderStroke(0.5.dp, GhostColors.BorderGlow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GhostColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userCode,
                    color = GhostColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))

                val isP2P = dataChannelState == DataChannel.State.OPEN
                val routeText = if (isP2P) "DIRECT P2P ENCRYPTED" else "RELAY FALLBACK"
                val routeColor = if (isP2P) GhostColors.NeonCyan else GhostColors.TextMuted

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(routeColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = routeText,
                        color = routeColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GhostColors.CardGlass)
            .border(BorderStroke(0.5.dp, GhostColors.BorderGlow))
            .padding(vertical = 6.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🔒 E2E Encrypted (Signal Protocol // Curve25519 Double Ratchet)",
            color = GhostColors.TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val isOutgoing = message.isOutgoing
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val bg = if (isOutgoing) GhostColors.CardGlass else GhostColors.SurfaceSlate
    val borderColor = if (isOutgoing) GhostColors.GhostGreen else GhostColors.NeonCyan
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bg, RoundedCornerShape(10.dp))
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = message.content,
                    color = GhostColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        color = GhostColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val statusSymbol = when (message.status) {
                            MessageEntity.STATUS_DELIVERED, MessageEntity.STATUS_READ -> "✓✓"
                            MessageEntity.STATUS_SENT -> "✓"
                            MessageEntity.STATUS_SENDING -> "⋯"
                            else -> "!"
                        }
                        Text(
                            text = statusSymbol,
                            color = if (message.status == MessageEntity.STATUS_DELIVERED) GhostColors.NeonCyan else GhostColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = GhostColors.SurfaceSlate,
        border = BorderStroke(0.5.dp, GhostColors.BorderGlow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Type an encrypted message...", color = GhostColors.TextMuted, fontSize = 13.sp)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = GhostColors.BgObsidian,
                    unfocusedContainerColor = GhostColors.BgObsidian,
                    focusedBorderColor = GhostColors.GhostGreen,
                    unfocusedBorderColor = GhostColors.BorderGlow,
                    focusedTextColor = GhostColors.TextPrimary,
                    unfocusedTextColor = GhostColors.TextPrimary
                ),
                shape = RoundedCornerShape(20.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank() && !isSending) onSend() })
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSend,
                enabled = input.isNotBlank() && !isSending,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (input.isNotBlank() && !isSending) GhostColors.GhostGreen else GhostColors.CardGlass,
                        CircleShape
                    )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        color = GhostColors.BgObsidian,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (input.isNotBlank()) GhostColors.BgObsidian else GhostColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
