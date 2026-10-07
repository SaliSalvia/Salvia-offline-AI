package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.core.localization.AppLanguage
import com.example.ui.screens.MainChatScreen
import com.example.ui.theme.BackgroundPitchBlack
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val uiState by viewModel.uiState.collectAsState()
      val layoutDirection = if (uiState.appLanguage == AppLanguage.FA) {
        LayoutDirection.Rtl
      } else {
        LayoutDirection.Ltr
      }

      MyApplicationTheme(darkTheme = true, dynamicColor = false) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundPitchBlack
          ) {
            MainChatScreen(viewModel = viewModel)
          }
        }
      }
    }
  }
}
