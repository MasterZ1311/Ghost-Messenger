package org.ghostmessenger.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ghostmessenger.ui.theme.GhostColors

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onVaultPurged: () -> Unit
) {
    val currentIdentity by viewModel.currentIdentity.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showMnemonic by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        containerColor = GhostColors.BgObsidian,
        topBar = {
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
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = GhostColors.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SECURITY & CONFIGURATION",
                        color = GhostColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Identity Section
            Text(
                text = "LOCAL CRYPTOGRAPHIC VAULT",
                color = GhostColors.GhostGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            currentIdentity?.let { identity ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GhostColors.CardGlass, RoundedCornerShape(8.dp))
                        .border(1.dp, GhostColors.BorderGlow, RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "USER CODE",
                                    color = GhostColors.TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = identity.userCode,
                                    color = GhostColors.GhostGreen,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(identity.userCode))
                                    Toast.makeText(context, "Copied UserCode", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = GhostColors.GhostGreen)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "REGISTRATION ID: ${identity.registrationId}",
                            color = GhostColors.TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (showMnemonic) {
                            Text(
                                text = "12-WORD MNEMONIC PHRASE:",
                                color = GhostColors.NeonCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = identity.mnemonicString,
                                color = GhostColors.TextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showMnemonic = false },
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, GhostColors.BorderGlow)
                            ) {
                                Text("HIDE MNEMONIC", color = GhostColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showMnemonic = true },
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, GhostColors.NeonCyan)
                            ) {
                                Text("REVEAL SECRET 12 WORDS", color = GhostColors.NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Signaling Server Configuration
            Text(
                text = "SIGNALING BROKER ENDPOINT",
                color = GhostColors.GhostGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.signalingUrlInput,
                onValueChange = { viewModel.updateSignalingUrlInput(it) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = GhostColors.SurfaceSlate,
                    unfocusedContainerColor = GhostColors.SurfaceSlate,
                    focusedBorderColor = GhostColors.GhostGreen,
                    unfocusedBorderColor = GhostColors.BorderGlow,
                    focusedTextColor = GhostColors.TextPrimary,
                    unfocusedTextColor = GhostColors.TextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.saveSignalingUrl()
                    Toast.makeText(context, "Signaling URL Saved", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GhostColors.SurfaceSlate,
                    contentColor = GhostColors.GhostGreen
                ),
                border = BorderStroke(1.dp, GhostColors.GhostGreen),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (uiState.isUrlSaved) "SAVED ✓" else "SAVE SERVER CONFIG",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Danger Zone
            Text(
                text = "DANGER ZONE",
                color = GhostColors.WarningRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.openPurgeDialog() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GhostColors.WarningRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "PURGE LOCAL VAULT & RESET",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }

        if (uiState.showPurgeDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.closePurgeDialog() },
                containerColor = GhostColors.SurfaceSlate,
                shape = RoundedCornerShape(12.dp),
                title = {
                    Text(
                        text = "PURGE ENTIRE VAULT?",
                        color = GhostColors.WarningRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                },
                text = {
                    Text(
                        text = "This will permanently destroy your cryptographic identity, erase all encrypted chat history, and disconnect active channels. This action is irreversible.",
                        color = GhostColors.TextPrimary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.purgeVault(onVaultPurged) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GhostColors.WarningRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("PURGE ALL DATA", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.closePurgeDialog() },
                        border = BorderStroke(1.dp, Color.Transparent),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("CANCEL", color = GhostColors.TextMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            )
        }
    }
}
