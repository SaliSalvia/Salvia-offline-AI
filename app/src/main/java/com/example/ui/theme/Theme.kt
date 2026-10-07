package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkPinkColorScheme = darkColorScheme(
  primary = NeonPinkPrimary,
  onPrimary = BackgroundPitchBlack,
  primaryContainer = NeonPinkContainer,
  onPrimaryContainer = NeonPinkLight,

  secondary = NeonPinkSecondary,
  onSecondary = BackgroundPitchBlack,
  secondaryContainer = SurfaceObsidianElevated,
  onSecondaryContainer = TextPrimary,

  tertiary = AccentPurple,
  onTertiary = BackgroundPitchBlack,

  background = BackgroundPitchBlack,
  onBackground = TextPrimary,

  surface = SurfaceObsidian,
  onSurface = TextPrimary,
  surfaceVariant = SurfaceObsidianElevated,
  onSurfaceVariant = TextSecondary,

  outline = SurfaceCardBorder,
  outlineVariant = ReasoningBorder
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to deep black-pink aesthetic
  dynamicColor: Boolean = false, // Keep intentional black-pink styling
  content: @Composable () -> Unit
) {
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
      }
    }
  }

  MaterialTheme(
    colorScheme = DarkPinkColorScheme,
    typography = Typography,
    content = content
  )
}
