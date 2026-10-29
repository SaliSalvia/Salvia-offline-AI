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
import com.example.ui.theme.AccentCyan
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
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "مدیریت مدل‌های محلی",
          color = TextPrimary,
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "مدل‌های ذخیره‌شده و مدل‌های فعال در RAM",
          color = TextSecondary,
          fontSize = 11.5.sp
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Button(
        onClick = { filePicker.launch(arrayOf("*/*")) },
        colors = ButtonDefaults.buttonColors(containerColor = NeonPinkPrimary),
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.testTag("import_model_hub_button")
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("افزودن", color = BackgroundPitchBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    val loadedCount = uiState.storedModels.count { it.isLoaded }
    val freeRam = uiState.deviceProfile?.availRamGb
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      ModelStatPill(
        label = "ذخیره‌شده",
        value = "${uiState.storedModels.size}",
        accent = NeonPinkPrimary,
        modifier = Modifier.weight(1f)
      )
      ModelStatPill(
        label = "فعال در RAM",
        value = "$loadedCount",
        accent = TurboActiveGreen,
        modifier = Modifier.weight(1f)
      )
      ModelStatPill(
        label = "RAM آزاد",
        value = freeRam?.let { "$it GB" } ?: "—",
        accent = AccentCyan,
        modifier = Modifier.weight(1f)
      )
    }

    // Honest ggml backend report (CPU-only build -> "cpu"; Vulkan build shows
    // the real GPU device). Never imply acceleration that is not compiled in.
    Text(
      text = "بک‌اند استنتاج: \${uiState.inferenceBackendLabel}",
      color = TextTertiary,
      fontSize = 10.5.sp
    )

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
      if (filteredModels.isEmpty()) {
        item(key = "empty_models") {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 36.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(NeonPinkPrimary.copy(alpha = 0.12f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.Memory,
                contentDescription = null,
                tint = NeonPinkPrimary,
                modifier = Modifier.size(26.dp)
              )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = if (uiState.storedModels.isEmpty()) "هنوز مدلی اضافه نشده" else "مدلی با این فیلتر پیدا نشد",
              color = TextPrimary,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "فایل .gguf را از حافظهٔ گوشی انتخاب کنید؛ همان فایل مستقیماً با llama.cpp اجرا می‌شود.",
              color = TextSecondary,
              fontSize = 11.5.sp,
              lineHeight = 18.sp,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
              onClick = { filePicker.launch(arrayOf("*/*")) },
              shape = RoundedCornerShape(10.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, NeonPinkPrimary.copy(alpha = 0.6f))
            ) {
              Icon(Icons.Default.Add, contentDescription = null, tint = NeonPinkPrimary, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(5.dp))
              Text("انتخاب فایل GGUF", color = NeonPinkPrimary, fontSize = 12.sp)
            }
          }
        }
      }

      items(
        items = filteredModels,
        key = { it.id },
        contentType = { "model" }
      ) { model ->
        ModelCardItem(
          model = model,
          onLoad = {
            // A llama.cpp GGUF can serve its text path even when it also advertises
            // code or vision capabilities; do not reject it just because the list
            // happens to start with a non-text capability.
            val primaryCap = when {
              model.capabilities.contains(ModelCapability.TEXT) -> ModelCapability.TEXT
              model.capabilities.contains(ModelCapability.CODE) -> ModelCapability.CODE
              else -> model.capabilities.firstOrNull() ?: ModelCapability.TEXT
            }
            viewModel.loadModelInRegistry(model.id, primaryCap)
          },
          onUnload = {
            viewModel.unloadModelInRegistry(model.id)
          },
          modifier = Modifier.animateItem()
        )
      }
    }
  }
}

@Composable
private fun ModelStatPill(
  label: String,
  value: String,
  accent: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(SurfaceObsidianElevated)
      .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp)
  ) {
    Text(text = value, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    Text(text = label, color = TextTertiary, fontSize = 9.5.sp, maxLines = 1)
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
  onUnload: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
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
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically
        ) {
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
            fontSize = 11.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
          )
        }
        Spacer(modifier = Modifier.width(6.dp))

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

      // Capabilities
      Text(
        text = model.capabilities.joinToString("   ") { "#${it.labelFa}" },
          color = TextPink,
          fontSize = 10.5.sp,
          maxLines = 2,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

      Spacer(modifier = Modifier.height(8.dp))

      // Specs stacked instead of squeezed side-by-side; long Persian labels and
      // multi-gigabyte sizes no longer collide on narrow phones.
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "کوانتیزاسیون: ${model.quantization} • کانتکست: ${model.contextLength}",
          color = TextSecondary,
          fontSize = 11.sp
        )
        Text(
          text = "حجم: ${model.fileSizeFormatted} • تخمین رم: ${model.estimatedMemoryMb} MB",
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
            text = "${model.benchmarkTokPerSec} t/s",
            color = NeonPinkLight,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
          )
        } else {
          Spacer(modifier = Modifier.width(1.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))

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
