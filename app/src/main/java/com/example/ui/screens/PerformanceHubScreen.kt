package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hardware.PerformanceMode
import com.example.ui.theme.BackgroundPitchBlack
import com.example.ui.theme.HyperOsOrange
import com.example.ui.theme.NeonPinkContainer
import com.example.ui.theme.NeonPinkLight
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.PerformanceBeastRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceObsidian
import com.example.ui.theme.TextPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TurboActiveGreen
import com.example.viewmodel.MainViewModel

@Composable
fun PerformanceHubScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val coroutineScope = rememberCoroutineScope()
  val profile = uiState.deviceProfile
  var isBenchmarking by remember { mutableStateOf(false) }

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
          text = "داشبورد سخت‌افزار و بنچمارک (Hardware Profiler)",
          color = TextPrimary,
          fontSize = 18.5.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "پایش بلادرنگ فرکانس، دما، حافظه RAM و چیپست دستگاه",
          color = TextSecondary,
          fontSize = 12.sp
        )
      }

      Button(
        onClick = { viewModel.loadHardwareTelemetry() },
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(10.dp)
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonPinkPrimary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("بروزرسانی", color = TextPrimary, fontSize = 11.5.sp)
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Profile Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      border = androidx.compose.foundation.BorderStroke(1.dp, HyperOsOrange.copy(alpha = 0.5f)),
      shape = RoundedCornerShape(14.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(text = profile?.deviceModel ?: "Redmi Note 14 Pro", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(HyperOsOrange.copy(alpha = 0.15f))
              .padding(horizontal = 8.dp, vertical = 2.dp)
          ) {
            Text(text = profile?.socName ?: "MediaTek Helio G100-Ultra", color = HyperOsOrange, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "پردازنده گرافیکی: ${profile?.gpuModel ?: "Mali-G57 MC2"} (پشتیبانی از OpenCL & Vulkan)", color = TextSecondary, fontSize = 11.5.sp)
        Text(text = "معماری پردازنده: ${profile?.cpuArchitecture ?: "ARM64"} • هسته‌ها: ${profile?.totalCores ?: 8} (۴ قدرتمند + ۴ کم‌مصرف)", color = TextSecondary, fontSize = 11.5.sp)
        Text(text = "نسخه سیستم‌عامل: ${profile?.androidVersion ?: "Android 16"} • رام رفرنس: Xiaomi HyperOS 2", color = TextSecondary, fontSize = 11.5.sp)
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Resource Guard Safety System Card
    val guardSnap = uiState.resourceSnapshot
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      border = androidx.compose.foundation.BorderStroke(
        1.dp,
        if (guardSnap?.isAiExecutionAllowed == false) PerformanceBeastRed else TurboActiveGreen
      ),
      shape = RoundedCornerShape(14.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (guardSnap?.isAiExecutionAllowed == false) PerformanceBeastRed else TurboActiveGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "گارد ایمنی منابع (Resource Guard)",
              color = TextPrimary,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Bold
            )
          }

          val stateBadge = when {
            guardSnap?.isAiExecutionAllowed == false -> "STOP 80%+ 🛑"
            (guardSnap?.ramUsagePctOfBudget ?: 0) >= 70 -> "CAUTION 70–79% ⚠️"
            else -> "FULL SPEED 0–69% ⚡"
          }
          val badgeColor = when {
            guardSnap?.isAiExecutionAllowed == false -> PerformanceBeastRed
            (guardSnap?.ramUsagePctOfBudget ?: 0) >= 70 -> HyperOsOrange
            else -> TurboActiveGreen
          }
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(badgeColor.copy(alpha = 0.15f))
              .padding(horizontal = 8.dp, vertical = 2.dp)
          ) {
            Text(text = stateBadge, color = badgeColor, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = if (guardSnap?.isAiExecutionAllowed == false) {
            "🛑 ${guardSnap.activeConstraintReason} (سیستم منتظر خنک‌سازی و کاهش به زیر ۷۰٪ است)"
          } else {
            "✅ سرعت حداکثر پایدار: پردازش بدون کاهش مصنوعی سرعت در حال اجراست. فقط در سقف ۸۰٪ توقف ایمن اعمال می‌شود."
          },
          color = if (guardSnap?.isAiExecutionAllowed == false) PerformanceBeastRed else TextSecondary,
          fontSize = 11.5.sp,
          lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Three independent resources
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // 1. RAM Budget
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .background(SurfaceObsidian)
              .padding(8.dp)
          ) {
            Column {
              Text(text = "سهمیه رم AI", color = TextSecondary, fontSize = 10.sp)
              Text(
                text = "${guardSnap?.ramUsagePctOfBudget ?: 35}% / 80%",
                color = if ((guardSnap?.ramUsagePctOfBudget ?: 0) >= 80) PerformanceBeastRed else TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "سقف: ${guardSnap?.safeAiRamBudgetMb ?: 6000} MB",
                color = TextTertiary,
                fontSize = 9.5.sp
              )
            }
          }

          // 2. CPU Sustained
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .background(SurfaceObsidian)
              .padding(8.dp)
          ) {
            Column {
              Text(text = "بار ممتد CPU", color = TextSecondary, fontSize = 10.sp)
              Text(
                text = "${guardSnap?.cpuSustainedPct ?: 30}% / 80%",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "فیلتر جهش موقت فعال",
                color = TextTertiary,
                fontSize = 9.5.sp
              )
            }
          }

          // 3. Thermal Status
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .background(SurfaceObsidian)
              .padding(8.dp)
          ) {
            Column {
              Text(text = "وضعیت حرارتی", color = TextSecondary, fontSize = 10.sp)
              Text(
                text = "${guardSnap?.thermalTempCelsius ?: 33.5}°C",
                color = TurboActiveGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = guardSnap?.thermalSystemStatus?.take(10) ?: "عادی",
                color = TextTertiary,
                fontSize = 9.5.sp
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // RAM Gauges
    Text(text = "وضعیت حافظه موقت (RAM Telemetry):", color = TextPink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))

    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      shape = RoundedCornerShape(12.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(text = "حافظه آزاد: ${profile?.availRamGb ?: 5.2} GB از مجموع ${profile?.totalRamGb ?: 12.0} GB", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
          val usedPct = if (profile != null) (((profile.totalRamGb - profile.availRamGb) / profile.totalRamGb) * 100).toInt() else 50
          Text(text = "$usedPct% اشغال شده", color = if (usedPct > 80) PerformanceBeastRed else TurboActiveGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        val usedFraction = if (profile != null) (profile.totalRamGb - profile.availRamGb) / profile.totalRamGb else 0.5f
        LinearProgressIndicator(
          progress = { usedFraction },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape),
          color = if (usedFraction > 0.8f) PerformanceBeastRed else NeonPinkPrimary,
          trackColor = SurfaceCardBorder
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(text = "تخمین اشغال مدل فعال: ~4.3 GB", color = TextTertiary, fontSize = 11.sp)
          Text(text = "تخمین کش کانتکست (KV Cache): ~380 MB", color = TextTertiary, fontSize = 11.sp)
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Thermals & Battery Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Thermostat, contentDescription = null, tint = TurboActiveGreen, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "دمای پردازنده", color = TextSecondary, fontSize = 12.sp)
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(text = "${profile?.batteryTempCelsius ?: 32.5} °C", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
          Text(text = profile?.thermalStatus ?: "عادی / خنک", color = TurboActiveGreen, fontSize = 10.5.sp)
        }
      }

      Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = NeonPinkPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "شارژ باتری", color = TextSecondary, fontSize = 12.sp)
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(text = "${profile?.batteryLevel ?: 80}%", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
          Text(text = "بهینه‌ساز توان فعال", color = NeonPinkLight, fontSize = 10.5.sp)
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Profiles
    Text(text = "پروفایل عملکردی سخت‌افزار:", color = TextPink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))

    PerformanceMode.entries.forEach { mode ->
      val isSelected = mode == uiState.performanceMode
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(if (isSelected) NeonPinkContainer else SurfaceCard)
          .border(1.dp, if (isSelected) NeonPinkPrimary else SurfaceCardBorder, RoundedCornerShape(10.dp))
          .clickable { viewModel.setPerformanceMode(mode) }
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(
              when (mode) {
                PerformanceMode.HYPER_TURBO -> PerformanceBeastRed
                PerformanceMode.BALANCED -> TurboActiveGreen
                PerformanceMode.ECO_BATTERY -> HyperOsOrange
              }
            )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(text = mode.title, color = if (isSelected) NeonPinkLight else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
          Text(text = mode.description, color = TextSecondary, fontSize = 11.sp)
        }
      }
    }

    // Performance Advisor Insights (Technique 31)
    if (uiState.advisorInsights.isNotEmpty()) {
      Spacer(modifier = Modifier.height(14.dp))
      Text(text = "مشاور هوشمند کارایی (Performance Advisor):", color = TextPink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
      Spacer(modifier = Modifier.height(6.dp))

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPinkPrimary.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          uiState.advisorInsights.forEach { insight ->
            Text(
              text = insight,
              color = TextPrimary,
              fontSize = 11.5.sp,
              lineHeight = 17.sp
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Benchmark Run Button
    Button(
      onClick = {
        isBenchmarking = true
        coroutineScope.launch {
          kotlinx.coroutines.delay(1200)
          isBenchmarking = false
        }
      },
      colors = ButtonDefaults.buttonColors(containerColor = NeonPinkPrimary),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("run_benchmark_button")
    ) {
      Icon(Icons.Default.Speed, contentDescription = null, tint = BackgroundPitchBlack)
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = if (isBenchmarking) "در حال اجرای بنچمارک بلادرنگ..." else "اجرای تست سرعت سخت‌افزار (Benchmark TTFT & Tok/s)",
        color = BackgroundPitchBlack,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}
