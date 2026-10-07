package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.gguf.LoadedSlotInfo
import com.example.ui.theme.BackgroundPitchBlack
import com.example.ui.theme.NeonPinkContainer
import com.example.ui.theme.NeonPinkLight
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceObsidianElevated
import com.example.ui.theme.TextPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun ModelManagerDialog(
  textSlot: LoadedSlotInfo,
  imageSlot: LoadedSlotInfo,
  onDismiss: () -> Unit,
  onPickGgufFile: (targetSlot: String) -> Unit,
  onSelectTextPreset: (name: String, arch: String, quant: String, size: String) -> Unit,
  onSelectImagePreset: (name: String, quant: String, size: String) -> Unit
) {
  var selectedTab by remember { mutableStateOf(0) } // 0 = Text LLM, 1 = Image Gen

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("بستن", color = NeonPinkPrimary)
      }
    },
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Memory,
            contentDescription = null,
            tint = NeonPinkPrimary,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "مدیریت اسلات‌های هوش مصنوعی (GGUF)",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
          )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
          Icon(Icons.Default.Close, contentDescription = "بستن", tint = TextSecondary)
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        Text(
          text = "قابلیت اجرای همزمان ۲ مدل آفلاین در حافظه گوشی: یک مدل متنی استدلالی و یک مدل تصویرساز.",
          color = TextSecondary,
          fontSize = 12.5.sp,
          lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Tabs
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = SurfaceObsidianElevated,
          contentColor = NeonPinkPrimary
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("اسلات ۱: مدل متنی", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("اسلات ۲: تصویرساز", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
          // Slot 1 (Text LLM)
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPinkPrimary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "مدل فعال متنی",
                  color = NeonPinkLight,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonPinkContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                  Text(text = "فعال در RAM", color = NeonPinkPrimary, fontSize = 10.5.sp)
                }
              }

              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = textSlot.modelName,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "کوانتیزاسیون: ${textSlot.quantization} • معماری: ${textSlot.architecture} • حجم: ${textSlot.sizeFormatted}",
                color = TextSecondary,
                fontSize = 11.5.sp
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Import Button
          Button(
            onClick = { onPickGgufFile("TEXT") },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("import_text_gguf_button"),
            colors = ButtonDefaults.buttonColors(containerColor = NeonPinkPrimary),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("بارگذاری فایل .gguf از حافظه گوشی (متنی)", fontSize = 13.sp)
          }

          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = "انتخاب از مدل‌های استاندارد بهینه:",
            color = TextPink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.height(6.dp))

          val textPresets = listOf(
            Triple("DeepSeek-R1-Distill-Qwen (7B)", "Q4_K_M", "4.2 GB"),
            Triple("Qwen2.5-7B-Instruct (High Quality)", "Q4_K_M", "4.4 GB"),
            Triple("Llama-3.2-3B-Instruct (Fast Mobile)", "Q4_K_M", "1.9 GB"),
            Triple("Gemma-2-2B-IT (Ultra Lightweight)", "Q4_0", "1.4 GB"),
            Triple("Mistral-7B-Instruct-v0.3", "Q4_K_M", "4.1 GB")
          )

          for (p in textPresets) {
            val isCurrent = textSlot.modelName.contains(p.first.substringBefore(" "))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isCurrent) NeonPinkContainer else SurfaceCard)
                .border(1.dp, if (isCurrent) NeonPinkPrimary else SurfaceCardBorder, RoundedCornerShape(8.dp))
                .clickable { onSelectTextPreset(p.first, "qwen2", p.second, p.third) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column {
                Text(text = p.first, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(text = "${p.second} • ${p.third}", color = TextSecondary, fontSize = 10.5.sp)
              }
              if (isCurrent) {
                Icon(Icons.Default.Check, contentDescription = "فعال", tint = NeonPinkPrimary, modifier = Modifier.size(16.dp))
              }
            }
          }
        } else {
          // Slot 2 (Image Generator)
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPinkPrimary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "مدل فعال تصویرساز آفلاین",
                  color = NeonPinkLight,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonPinkContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                  Text(text = "آماده سنتز محلی", color = NeonPinkPrimary, fontSize = 10.5.sp)
                }
              }

              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = imageSlot.modelName,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "کوانتیزاسیون: ${imageSlot.quantization} • معماری Diffusion • حجم: ${imageSlot.sizeFormatted}",
                color = TextSecondary,
                fontSize = 11.5.sp
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Import Button
          Button(
            onClick = { onPickGgufFile("IMAGE") },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("import_image_gguf_button"),
            colors = ButtonDefaults.buttonColors(containerColor = NeonPinkPrimary),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("بارگذاری مدل تصویرساز GGUF از حافظه گوشی", fontSize = 13.sp)
          }

          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = "انتخاب از مدل‌های تصویرساز پیش‌فرض:",
            color = TextPink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.height(6.dp))

          val imgPresets = listOf(
            Triple("StableDiffusion-GGUF / Neural Diffusion v2.1", "Q8_0", "1.8 GB"),
            Triple("SD-Turbo GGUF (Single Step Ultra Fast)", "Q4_K_M", "1.2 GB"),
            Triple("Flux-1-Schnell Mobile Edition", "Q4_0", "2.6 GB")
          )

          for (p in imgPresets) {
            val isCurrent = imageSlot.modelName.contains(p.first.substringBefore(" "))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isCurrent) NeonPinkContainer else SurfaceCard)
                .border(1.dp, if (isCurrent) NeonPinkPrimary else SurfaceCardBorder, RoundedCornerShape(8.dp))
                .clickable { onSelectImagePreset(p.first, p.second, p.third) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column {
                Text(text = p.first, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(text = "${p.second} • ${p.third}", color = TextSecondary, fontSize = 10.5.sp)
              }
              if (isCurrent) {
                Icon(Icons.Default.Check, contentDescription = "فعال", tint = NeonPinkPrimary, modifier = Modifier.size(16.dp))
              }
            }
          }
        }
      }
    },
    containerColor = BackgroundPitchBlack,
    shape = RoundedCornerShape(16.dp)
  )
}
