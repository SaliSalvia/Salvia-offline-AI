package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CompatibilityLevel
import com.example.core.model.ModelCapability
import com.example.core.model.ModelMetadata
import com.example.ui.theme.BackgroundPitchBlack
import com.example.ui.theme.NeonPinkContainer
import com.example.ui.theme.NeonPinkLight
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.PerformanceBeastRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceObsidian
import com.example.ui.theme.SurfaceObsidianElevated
import com.example.ui.theme.TextPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TurboActiveGreen
import com.example.viewmodel.MainViewModel

@Composable
fun ModelsHubScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  var selectedFilter by remember { mutableStateOf<ModelCapability?>(null) }

  val filePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    uri?.let { viewModel.attachFileUri(it) }
  }

  val filteredModels = if (selectedFilter == null) {
    uiState.storedModels
  } else {
    uiState.storedModels.filter { it.capabilities.contains(selectedFilter) }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundPitchBlack)
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
          text = "مدیریت مدل‌های محلی (AI Models)",
          color = TextPrimary,
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "تفکیک مدل‌های ذخیره‌شده روی حافظه و مدل‌های فعال در RAM",
          color = TextSecondary,
          fontSize = 12.sp
        )
      }

      Button(
        onClick = { filePicker.launch(arrayOf("*/*")) },
        colors = ButtonDefaults.buttonColors(containerColor = NeonPinkPrimary),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.testTag("import_model_hub_button")
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("وارد کردن مدل", fontSize = 12.sp)
      }
    }

    // Live weight-loading progress from the native loader (mmap read).
    val loadPercent = uiState.modelLoadPercent
    if (loadPercent != null) {
      Spacer(modifier = Modifier.height(10.dp))
      Column {
        Text(
          text = "در حال بارگذاری وزن‌های مدل: $loadPercent٪",
          color = NeonPinkLight,
          fontSize = 11.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
          progress = { loadPercent / 100f },
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .testTag("model_load_progress"),
          color = NeonPinkPrimary,
          trackColor = SurfaceCardBorder
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Filter Chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilterChipItem(
        label = "همه مدل‌ها (${uiState.storedModels.size})",
        isSelected = selectedFilter == null,
        onClick = { selectedFilter = null }
      )
      ModelCapability.entries.forEach { cap ->
        val count = uiState.storedModels.count { it.capabilities.contains(cap) }
        if (count > 0) {
          FilterChipItem(
            label = "${cap.labelFa} ($count)",
            isSelected = selectedFilter == cap,
            onClick = { selectedFilter = cap }
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // List of models
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      items(filteredModels) { model ->
        ModelCardItem(
          model = model,
          onLoad = {
            val primaryCap = model.capabilities.firstOrNull() ?: ModelCapability.TEXT
            viewModel.loadModelInRegistry(model.id, primaryCap)
          },
          onUnload = {
            viewModel.unloadModelInRegistry(model.id)
          }
        )
      }
    }
  }
}

@Composable
private fun FilterChipItem(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(if (isSelected) NeonPinkContainer else SurfaceCard)
      .border(1.dp, if (isSelected) NeonPinkPrimary else SurfaceCardBorder, RoundedCornerShape(12.dp))
      .clickable { onClick() }
      .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Text(
      text = label,
      color = if (isSelected) NeonPinkLight else TextSecondary,
      fontSize = 11.5.sp,
      fontWeight = FontWeight.Medium
    )
  }
}

@Composable
private fun ModelCardItem(
  model: ModelMetadata,
  onLoad: () -> Unit,
  onUnload: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (model.isLoaded) NeonPinkPrimary else SurfaceCardBorder
    ),
    shape = RoundedCornerShape(14.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      // Header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(NeonPinkPrimary.copy(alpha = 0.15f))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = model.format.displayName,
              color = NeonPinkPrimary,
              fontSize = 10.5.sp,
              fontWeight = FontWeight.Bold
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "${model.architecture} • ${model.parameterCount}",
            color = TextSecondary,
            fontSize = 11.sp
          )
        }

        // Status badge
        if (model.isLoaded) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(TurboActiveGreen.copy(alpha = 0.15f))
              .padding(horizontal = 8.dp, vertical = 2.dp)
          ) {
            Text(text = "در حال اجرا در RAM ⚡", color = TurboActiveGreen, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
          }
        } else {
          Text(
            text = "ذخیره در حافظه (Not Loaded)",
            color = TextTertiary,
            fontSize = 10.5.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = model.fileName,
        color = TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(4.dp))

      // Capabilities row
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        model.capabilities.forEach { cap ->
          Text(
            text = "#${cap.labelFa}",
            color = TextPink,
            fontSize = 10.5.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Specs
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "کوانتیزاسیون: ${model.quantization} • کانتکست: ${model.contextLength}",
          color = TextSecondary,
          fontSize = 11.sp
        )
        Text(
          text = "حجم: ${model.fileSizeFormatted} (تخمین رم: ${model.estimatedMemoryMb} MB)",
          color = TextSecondary,
          fontSize = 11.sp
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      // Compatibility banner
      Text(
        text = "${model.compatibility.titleFa}: ${model.compatibilityReason}",
        color = Color(model.compatibility.colorHex),
        fontSize = 11.sp
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Action row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (model.benchmarkTokPerSec != null) {
          Text(
            text = "بنچمارک ثبت‌شده: ${model.benchmarkTokPerSec} t/s",
            color = NeonPinkLight,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
          )
        } else {
          Spacer(modifier = Modifier.width(1.dp))
        }

        if (model.isLoaded) {
          OutlinedButton(
            onClick = onUnload,
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = PerformanceBeastRed, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("خروج از RAM", color = PerformanceBeastRed, fontSize = 11.sp)
          }
        } else {
          Button(
            onClick = onLoad,
            colors = ButtonDefaults.buttonColors(containerColor = NeonPinkPrimary),
            shape = RoundedCornerShape(8.dp)
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BackgroundPitchBlack, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("بارگذاری در RAM", color = BackgroundPitchBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
