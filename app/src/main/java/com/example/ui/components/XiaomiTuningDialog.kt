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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hardware.HardwareTelemetry
import com.example.hardware.PerformanceMode
import com.example.hardware.XiaomiOptimizer
import com.example.ui.theme.BackgroundPitchBlack
import com.example.ui.theme.HyperOsOrange
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

@Composable
fun XiaomiTuningDialog(
  telemetry: HardwareTelemetry?,
  currentMode: PerformanceMode,
  onSelectMode: (PerformanceMode) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("تایید و بازگشت", color = NeonPinkPrimary)
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
            imageVector = Icons.Default.Speed,
            contentDescription = null,
            tint = HyperOsOrange,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "مرکز شتاب‌دهنده سخت‌افزاری Xiaomi",
            color = TextPrimary,
            fontSize = 16.sp,
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
        // Target Chipset Card (Redmi Note 14 Pro / HyperOS Engine)
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = SurfaceCard),
          border = androidx.compose.foundation.BorderStroke(1.dp, HyperOsOrange.copy(alpha = 0.5f)),
          shape = RoundedCornerShape(12.dp)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = telemetry?.brandName ?: "Xiaomi",
                color = HyperOsOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(HyperOsOrange.copy(alpha = 0.15f))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = telemetry?.hyperOsVersion ?: "HyperOS Engine",
                  color = HyperOsOrange,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = telemetry?.deviceModel ?: "Redmi Note 14 Pro",
              color = TextPrimary,
              fontSize = 14.5.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = XiaomiOptimizer.getChipsetRecommendation(),
              color = TextSecondary,
              fontSize = 11.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hardware Gauges (RAM, CPU, Battery)
        Text(
          text = "پایش سخت‌افزار در زمان واقعی (Telemetry):",
          color = TextPink,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))

        // RAM Bar
        if (telemetry != null) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(SurfaceCard)
              .padding(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("حافظه RAM آزاد:", color = TextSecondary, fontSize = 11.5.sp)
              Text(
                "${telemetry.availRamGb} GB از ${telemetry.totalRamGb} GB (${telemetry.ramUsagePercent}% در حال استفاده)",
                color = if (telemetry.ramUsagePercent > 80) PerformanceBeastRed else TurboActiveGreen,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
              progress = { telemetry.ramUsagePercent / 100f },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
              color = if (telemetry.ramUsagePercent > 80) PerformanceBeastRed else NeonPinkPrimary,
              trackColor = SurfaceCardBorder
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          // CPU & Thermals Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // CPU cores box
            Column(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceCard)
                .padding(10.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = NeonPinkPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("هسته‌های ARM64", color = TextSecondary, fontSize = 11.sp)
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text("${telemetry.cpuCores} هسته", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
              Text("${telemetry.performanceCores} قدرتمند + ${telemetry.efficiencyCores} کم‌مصرف", color = TextTertiary, fontSize = 10.sp)
            }

            // Temperature box
            Column(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceCard)
                .padding(10.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Thermostat, contentDescription = null, tint = TurboActiveGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("دمای باتری", color = TextSecondary, fontSize = 11.sp)
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text("${telemetry.batteryTempCelsius} °C", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
              Text("شارژ: ${telemetry.batteryLevel}%", color = TextTertiary, fontSize = 10.sp)
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Performance Mode Selector
        Text(
          text = "انتخاب پروفایل پردازشی موتور استنتاج:",
          color = TextPink,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))

        PerformanceMode.entries.forEach { mode ->
          val isSelected = mode == currentMode
          val modeColor = when (mode) {
            PerformanceMode.HYPER_TURBO -> PerformanceBeastRed
            PerformanceMode.BALANCED -> TurboActiveGreen
            PerformanceMode.ECO_BATTERY -> HyperOsOrange
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(if (isSelected) NeonPinkContainer else SurfaceCard)
              .border(1.dp, if (isSelected) NeonPinkPrimary else SurfaceCardBorder, RoundedCornerShape(10.dp))
              .clickable { onSelectMode(mode) }
              .padding(12.dp)
              .testTag("mode_${mode.name.lowercase()}"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(modeColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = mode.title,
                color = if (isSelected) NeonPinkLight else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = mode.description,
                color = TextSecondary,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
              )
            }
          }
        }
      }
    },
    containerColor = BackgroundPitchBlack,
    shape = RoundedCornerShape(16.dp)
  )
}
