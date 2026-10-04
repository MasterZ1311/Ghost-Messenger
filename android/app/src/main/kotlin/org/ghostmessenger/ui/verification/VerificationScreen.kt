package org.ghostmessenger.ui.verification

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ghostmessenger.ui.theme.GhostColors

@Composable
fun VerificationScreen(
    viewModel: VerificationViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

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
                        text = "SAFETY NUMBER VERIFICATION",
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Shield Icon Header
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(GhostColors.CardGlass, CircleShape)
                    .border(
                        1.5.dp,
                        if (uiState.isVerified) GhostColors.NeonCyan else GhostColors.BorderGlow,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = "Security Shield",
                    tint = if (uiState.isVerified) GhostColors.NeonCyan else GhostColors.TextMuted,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = uiState.recipientUserCode,
                color = GhostColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Verified Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (uiState.isVerified) GhostColors.NeonCyan else GhostColors.TextMuted,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (uiState.isVerified) "VERIFIED SESSION" else "UNVERIFIED CONTACT",
                    color = if (uiState.isVerified) GhostColors.NeonCyan else GhostColors.TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Safety Number Display Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GhostColors.CardGlass, RoundedCornerShape(12.dp))
                    .border(1.dp, GhostColors.BorderGlow, RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CRYPTOGRAPHIC FINGERPRINT",
                        color = GhostColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (uiState.safetyNumber != null) {
                        val groups = uiState.safetyNumber!!.split(" ")
                        groups.forEach { group ->
                            Text(
                                text = group,
                                color = GhostColors.GhostGreen,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 6.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = uiState.statusMessage ?: "Session keys not yet established.",
                            color = GhostColors.WarningRed,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Explanation Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GhostColors.SurfaceSlate, RoundedCornerShape(8.dp))
                    .border(0.5.dp, GhostColors.BorderGlow, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Compare this 30-digit cryptographic fingerprint with your contact through an independent secure channel (such as in-person or a trusted voice call). If every digit matches, your end-to-end encryption is mathematically guaranteed against man-in-the-middle surveillance.",
                    color = GhostColors.TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Button
            if (uiState.hasRemoteSession) {
                Button(
                    onClick = { viewModel.toggleVerification() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isVerified) GhostColors.SurfaceSlate else GhostColors.GhostGreen,
                        contentColor = if (uiState.isVerified) GhostColors.TextPrimary else GhostColors.BgObsidian
                    ),
                    border = if (uiState.isVerified) BorderStroke(1.dp, GhostColors.BorderGlow) else null,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isVerified) "MARK AS UNVERIFIED" else "MARK AS VERIFIED",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
