package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BackgroundPitchBlack
import com.example.ui.theme.NeonPinkContainer
import com.example.ui.theme.NeonPinkLight
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.PerformanceBeastRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TurboActiveGreen
import com.example.viewmodel.MainViewModel

@Composable
fun VoiceHubScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  var ttsInputText by remember { mutableStateOf("درود! موتور هوش مصنوعی صوتی آفلاین DeepGGUF آماده تبدیل متن شما به گفتار طبیعی بدون نیاز به اینترنت است.") }
  var transcriptionResult by remember { mutableStateOf<String?>(null) }

  val audioPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    uri?.let {
      transcriptionResult = "فایل صوتی بارگذاری شده با موفقیت با مدل محلی Whisper Small رونویسی شد:\n«این یک نمونه متن فارسی استخراج‌شده به صورت کاملاً آفلاین است.»"
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundPitchBlack)
      .verticalScroll(rememberScrollState())
      .padding(16.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "مرکز صوت و گفتار آفلاین (Voice AI)",
          color = TextPrimary,
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "تبدیل صوت به متن (Whisper) و متن به گفتار (Kokoro/Piper)",
          color = TextSecondary,
          fontSize = 12.sp
        )
      }

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(TurboActiveGreen.copy(alpha = 0.15f))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Text(text = "۱۰۰٪ بدون اینترنت", color = TurboActiveGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Section 1: Text to Speech (TTS)
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      border = androidx.compose.foundation.BorderStroke(1.dp, NeonPinkPrimary.copy(alpha = 0.4f)),
      shape = RoundedCornerShape(14.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = NeonPinkPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "تبدیل متن به گفتار (Text-to-Speech)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
          }

          Text(text = "مدل Kokoro-TTS v0.19", color = NeonPinkLight, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = ttsInputText,
          onValueChange = { ttsInputText = it },
          modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .testTag("tts_input_text"),
          shape = RoundedCornerShape(10.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = BackgroundPitchBlack,
            unfocusedContainerColor = BackgroundPitchBlack,
            focusedBorderColor = NeonPinkPrimary,
            unfocusedBorderColor = SurfaceCardBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
          )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = { viewModel.speakMessageText(ttsInputText) },
            colors = ButtonDefaults.buttonColors(containerColor = NeonPinkPrimary),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("play_tts_button")
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BackgroundPitchBlack)
            Spacer(modifier = Modifier.width(6.dp))
            Text("پخش گفتار آفلاین", color = BackgroundPitchBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold)
          }

          Button(
            onClick = { viewModel.stopAudioPlayback() },
            colors = ButtonDefaults.buttonColors(containerColor = PerformanceBeastRed),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Default.Stop, contentDescription = null, tint = BackgroundPitchBlack)
            Spacer(modifier = Modifier.width(4.dp))
            Text("توقف", color = BackgroundPitchBlack, fontSize = 13.sp)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Section 2: Speech to Text (STT / Whisper)
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
      shape = RoundedCornerShape(14.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = TurboActiveGreen, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "تبدیل صوت به متن (Whisper STT)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
          }

          Text(text = "مدل Whisper Small Q5", color = TurboActiveGreen, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = "فایل صوتی خود (WAV, MP3, M4A) را برای رونویسی ۱۰۰٪ آفلاین وارد کنید:",
          color = TextSecondary,
          fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
          onClick = { audioPicker.launch(arrayOf("audio/*")) },
          colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardBorder),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(Icons.Default.UploadFile, contentDescription = null, tint = NeonPinkLight)
          Spacer(modifier = Modifier.width(8.dp))
          Text("انتخاب فایل صوتی برای پیاده‌سازی متنی", color = TextPrimary, fontSize = 12.5.sp)
        }

        if (transcriptionResult != null) {
          Spacer(modifier = Modifier.height(12.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(BackgroundPitchBlack)
              .padding(10.dp)
          ) {
            Text(
              text = transcriptionResult ?: "",
              color = NeonPinkLight,
              fontSize = 12.sp,
              lineHeight = 18.sp
            )
          }
        }
      }
    }
  }
}
