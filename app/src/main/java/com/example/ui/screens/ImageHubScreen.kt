package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.BackgroundPitchBlack
import com.example.ui.theme.NeonPinkContainer
import com.example.ui.theme.NeonPinkLight
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.PerformanceBeastRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceObsidianElevated
import com.example.ui.theme.TextPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TurboActiveGreen
import com.example.viewmodel.MainViewModel
import java.io.File

@Composable
fun ImageHubScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current

  var promptText by remember { mutableStateOf("پرتره هوش مصنوعی با خطوط نئونی صورتی و پس‌زمینه سایبرپانک تاریک") }
  var selectedStyle by remember { mutableStateOf("Cyberpunk Dark Pink") }
  var steps by remember { mutableFloatStateOf(15f) }
  var lastGeneratedPath by remember { mutableStateOf<String?>(null) }

  val styles = listOf(
    "Cyberpunk Dark Pink",
    "Anime / Manga Style",
    "Photorealistic 8K",
    "Digital Art & 3D Render",
    "Isometric Vector"
  )

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
          text = "استودیوی تصویرساز آفلاین (Diffusion)",
          color = TextPrimary,
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "مبتنی بر مدل ${uiState.imageSlotModel.modelName} • ۱۰۰٪ محلی بدون اینترنت",
          color = TextSecondary,
          fontSize = 11.5.sp
        )
      }

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(NeonPinkContainer)
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Text(text = "Mali-G57 GPU", color = NeonPinkLight, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Prompt input
    OutlinedTextField(
      value = promptText,
      onValueChange = { promptText = it },
      placeholder = { Text("توصیف تصویر مورد نظر...", color = TextTertiary, fontSize = 13.sp) },
      modifier = Modifier
        .fillMaxWidth()
        .height(100.dp)
        .testTag("image_prompt_input"),
      shape = RoundedCornerShape(12.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = SurfaceCard,
        unfocusedContainerColor = SurfaceCard,
        focusedBorderColor = NeonPinkPrimary,
        unfocusedBorderColor = SurfaceCardBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
      ),
      maxLines = 3
    )

    Spacer(modifier = Modifier.height(14.dp))

    // Style selector chips
    Text(text = "انتخاب سبک هنری:", color = TextPink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(6.dp))
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      styles.forEach { style ->
        val isSelected = style == selectedStyle
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) NeonPinkContainer else SurfaceCard)
            .border(1.dp, if (isSelected) NeonPinkPrimary else SurfaceCardBorder, RoundedCornerShape(10.dp))
            .clickable { selectedStyle = style }
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Text(
            text = style,
            color = if (isSelected) NeonPinkLight else TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Steps slider
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(text = "تعداد مراحل U-Net Denoising Steps: ${steps.toInt()}", color = TextSecondary, fontSize = 12.sp)
      Text(text = if (steps > 18) "کیفیت بالا (کیفیت)" else "سریع و روان (Note 14 Pro)", color = NeonPinkLight, fontSize = 11.sp)
    }
    Slider(
      value = steps,
      onValueChange = { steps = it },
      valueRange = 8f..30f,
      steps = 21,
      colors = SliderDefaults.colors(
        thumbColor = NeonPinkPrimary,
        activeTrackColor = NeonPinkPrimary,
        inactiveTrackColor = SurfaceCardBorder
      )
    )

    Spacer(modifier = Modifier.height(14.dp))

    // Generation State / Action Button
    if (uiState.isGeneratingImage) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPinkPrimary),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NeonPinkPrimary, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "سنتز تصویر در فضای نهان (مرحله ${uiState.imageProgress?.step ?: 1} از ${uiState.imageProgress?.totalSteps ?: steps.toInt()})...",
              color = NeonPinkLight,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(text = uiState.imageProgress?.currentStage ?: "Denoising...", color = TextSecondary, fontSize = 11.5.sp)
          Spacer(modifier = Modifier.height(8.dp))
          LinearProgressIndicator(
            progress = { (uiState.imageProgress?.step?.toFloat() ?: 1f) / (uiState.imageProgress?.totalSteps?.toFloat() ?: steps) },
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(CircleShape),
            color = NeonPinkPrimary,
            trackColor = SurfaceCardBorder
          )
          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = { viewModel.stopGeneration() },
            colors = ButtonDefaults.buttonColors(containerColor = PerformanceBeastRed),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("توقف فرآیند تصویرسازی", fontSize = 12.sp)
          }
        }
      }
    } else {
      Button(
        onClick = {
          val activeSession = uiState.currentSession
          if (activeSession != null) {
            viewModel.executeOfflineImageGeneration(activeSession.id, promptText)
          } else {
            Toast.makeText(context, "لطفاً ابتدا یک گفت‌وگو ایجاد کنید", Toast.LENGTH_SHORT).show()
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = NeonPinkPrimary),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("generate_image_hub_button")
      ) {
        Icon(Icons.Default.Brush, contentDescription = null, tint = BackgroundPitchBlack)
        Spacer(modifier = Modifier.width(8.dp))
        Text("تولید آفلاین تصویر با هوش مصنوعی", color = BackgroundPitchBlack, fontSize = 14.sp, fontWeight = FontWeight.Bold)
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Display recent images from messages
    Text(text = "گالری تصاویر تولید شده محلی:", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(10.dp))

    val generatedMessages = uiState.messages.filter { !it.imageResultUri.isNullOrEmpty() }
    if (generatedMessages.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(140.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(SurfaceCard)
          .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
      ) {
        Text("تصویری تولید نشده است. پرامپت خود را بنویسید و دکمه را بزنید.", color = TextTertiary, fontSize = 12.sp)
      }
    } else {
      generatedMessages.takeLast(4).reversed().forEach { msg ->
        val file = File(msg.imageResultUri ?: "")
        if (file.exists()) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp)
          ) {
            Column {
              AsyncImage(
                model = file,
                contentDescription = "تصویر محلی",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(260.dp)
                  .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
              )
              Text(
                text = msg.content.take(80),
                color = TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier.padding(10.dp)
              )
            }
          }
        }
      }
    }
  }
}
