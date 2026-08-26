package org.ghostmessenger.ui.onboarding

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ghostmessenger.ui.theme.GhostColors

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onNavigateToHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = GhostColors.BgObsidian
    ) {
        when (uiState.step) {
            OnboardingStep.LANDING -> LandingStep(
                onGenerate = { viewModel.selectGenerateNew() },
                onRestore = { viewModel.selectRestore() }
            )
            OnboardingStep.GENERATE_NEW -> GenerateStep(
                words = uiState.generatedMnemonicWords,
                userCode = uiState.generatedIdentity?.userCode ?: "",
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                onBack = { viewModel.setStep(OnboardingStep.LANDING) },
                onConfirm = { viewModel.confirmGeneratedIdentity(onNavigateToHome) }
            )
            OnboardingStep.RESTORE_EXISTING -> RestoreStep(
                input = uiState.restoreInput,
                error = uiState.restoreError,
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                onInputChange = { viewModel.updateRestoreInput(it) },
                onBack = { viewModel.setStep(OnboardingStep.LANDING) },
                onConfirm = { viewModel.confirmRestoreIdentity(onNavigateToHome) }
            )
        }
    }
}

@Composable
private fun LandingStep(
    onGenerate: () -> Unit,
    onRestore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "👻",
            fontSize = 72.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "GHOST MESSENGER",
            color = GhostColors.GhostGreen,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ZERO-KNOWLEDGE // ANONYMOUS P2P",
            color = GhostColors.TextMuted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGenerate,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GhostColors.GhostGreen,
                contentColor = GhostColors.BgObsidian
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "GENERATE NEW IDENTITY",
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onRestore,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            border = BorderStroke(1.dp, GhostColors.BorderGlow),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = GhostColors.TextPrimary
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "RESTORE FROM 12 WORDS",
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun GenerateStep(
    words: List<String>,
    userCode: String,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SECRET RECOVERY VAULT",
            color = GhostColors.GhostGreen,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Write these 12 words down offline. There are no servers, no emails, and no recovery passwords.",
            color = GhostColors.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // UserCode Preview Badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GhostColors.SurfaceSlate, RoundedCornerShape(8.dp))
                .border(1.dp, GhostColors.NeonCyan, RoundedCornerShape(8.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "YOUR UNIQUE USER CODE",
                    color = GhostColors.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = userCode,
                    color = GhostColors.NeonCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Mnemonic Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(words) { index, word ->
                Box(
                    modifier = Modifier
                        .background(GhostColors.CardGlass, RoundedCornerShape(6.dp))
                        .border(1.dp, GhostColors.BorderGlow, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}. $word",
                        color = GhostColors.TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(words.joinToString(" ")))
                Toast.makeText(context, "Mnemonic copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, GhostColors.BorderGlow),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = "COPY MNEMONIC WORDS",
                color = GhostColors.TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                color = GhostColors.WarningRed,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onConfirm,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GhostColors.GhostGreen,
                contentColor = GhostColors.BgObsidian
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = GhostColors.BgObsidian, modifier = Modifier.height(20.dp).width(20.dp))
            } else {
                Text(
                    text = "ENTER THE SHADOWS",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color.Transparent),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "CANCEL",
                color = GhostColors.TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun RestoreStep(
    input: String,
    error: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onInputChange: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "RESTORE VAULT",
            color = GhostColors.GhostGreen,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter your 12-word mnemonic phrase separated by spaces to restore your deterministic identity.",
            color = GhostColors.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            placeholder = {
                Text(
                    text = "e.g. abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
                    color = GhostColors.TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GhostColors.SurfaceSlate,
                unfocusedContainerColor = GhostColors.SurfaceSlate,
                focusedBorderColor = if (error == null) GhostColors.GhostGreen else GhostColors.WarningRed,
                unfocusedBorderColor = if (error == null) GhostColors.BorderGlow else GhostColors.WarningRed,
                focusedTextColor = GhostColors.TextPrimary,
                unfocusedTextColor = GhostColors.TextPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (error == null && input.isNotBlank()) onConfirm() })
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = GhostColors.WarningRed,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = GhostColors.WarningRed,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onConfirm,
            enabled = error == null && input.isNotBlank() && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GhostColors.GhostGreen,
                contentColor = GhostColors.BgObsidian
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = GhostColors.BgObsidian, modifier = Modifier.height(20.dp).width(20.dp))
            } else {
                Text(
                    text = "RESTORE & CONNECT",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color.Transparent),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "BACK",
                color = GhostColors.TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
