package org.ghostmessenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Ghost Messenger Color Palette.
 * Dark Stealth / Cyberpunk Glassmorphism visual theme.
 */
object GhostColors {
    val BgObsidian   = Color(0xFF0A0A0A)  // Primary Background
    val SurfaceSlate = Color(0xFF141414)  // Surface Panels / AppBars
    val CardGlass    = Color(0xFF1E1E1E)  // Glass Cards & Bubble Base
    val BorderGlow   = Color(0xFF2E2E2E)  // Subtle Card Borders
    val GhostGreen   = Color(0xFF00FF41)  // Terminal Green / Primary Accent
    val NeonCyan     = Color(0xFF00E5FF)  // P2P Direct & Security Verified
    val WarningRed   = Color(0xFFFF3B30)  // Destruct Timers & Danger
    val TextPrimary  = Color(0xFFE0E0E0)  // Primary Text
    val TextMuted    = Color(0xFF8E8E93)  // Secondary Labels
}

private val GhostDarkColorScheme = darkColorScheme(
    primary = GhostColors.GhostGreen,
    secondary = GhostColors.NeonCyan,
    tertiary = GhostColors.WarningRed,
    background = GhostColors.BgObsidian,
    surface = GhostColors.SurfaceSlate,
    surfaceVariant = GhostColors.CardGlass,
    onPrimary = GhostColors.BgObsidian,
    onSecondary = GhostColors.BgObsidian,
    onBackground = GhostColors.TextPrimary,
    onSurface = GhostColors.TextPrimary,
    onSurfaceVariant = GhostColors.TextMuted,
    outline = GhostColors.BorderGlow,
    error = GhostColors.WarningRed,
    onError = Color.White,
)

@Composable
fun GhostMessengerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GhostDarkColorScheme,
        typography = GhostTypography,
        content = content
    )
}
