package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Translate
import com.example.core.localization.AppLanguage
import com.example.core.localization.AppStrings
import com.example.ui.theme.BackgroundPitchBlack
import com.example.ui.theme.NeonPinkContainer
import com.example.ui.theme.NeonPinkLight
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TurboActiveGreen
import com.example.viewmodel.MainViewModel

@Composable
fun SettingsHubScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current

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
          text = AppStrings.settingsTitle(uiState.appLanguage),
          color = TextPrimary,
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = AppStrings.settingsSubtitle(uiState.appLanguage),
          color = TextSecondary,
          fontSize = 12.sp
        )
      }

      Icon(Icons.Default.Settings, contentDescription = null, tint = NeonPinkPrimary)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 🌐 Language Selection Card (Bilingual English & Persian)
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("language_settings_card"),
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      border = androidx.compose.foundation.BorderStroke(1.dp, NeonPinkPrimary.copy(alpha = 0.5f)),
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
                .size(38.dp)
                .clip(CircleShape)
                .background(NeonPinkPrimary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.Translate,
                contentDescription = null,
                tint = NeonPinkPrimary,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = AppStrings.languageSettingTitle(uiState.appLanguage),
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = AppStrings.languageSettingSubtitle(uiState.appLanguage),
                color = TextSecondary,
                fontSize = 11.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Persian (FA) Option
          val isFa = uiState.appLanguage == AppLanguage.FA
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (isFa) NeonPinkContainer else BackgroundPitchBlack)
              .border(
                1.dp,
                if (isFa) NeonPinkPrimary else SurfaceCardBorder,
                RoundedCornerShape(10.dp)
              )
              .clickable {
                viewModel.setLanguage(AppLanguage.FA)
                Toast.makeText(context, "زبان به فارسی تغییر یافت", Toast.LENGTH_SHORT).show()
              }
              .padding(vertical = 10.dp, horizontal = 12.dp)
              .testTag("language_option_fa"),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("🇮🇷", fontSize = 16.sp)
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "فارسی (RTL)",
                color = if (isFa) NeonPinkLight else TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (isFa) FontWeight.Bold else FontWeight.Normal
              )
            }
          }

          // English (EN) Option
          val isEn = uiState.appLanguage == AppLanguage.EN
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (isEn) NeonPinkContainer else BackgroundPitchBlack)
              .border(
                1.dp,
                if (isEn) NeonPinkPrimary else SurfaceCardBorder,
                RoundedCornerShape(10.dp)
              )
              .clickable {
                viewModel.setLanguage(AppLanguage.EN)
                Toast.makeText(context, "Language switched to English", Toast.LENGTH_SHORT).show()
              }
              .padding(vertical = 10.dp, horizontal = 12.dp)
              .testTag("language_option_en"),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("🇬🇧", fontSize = 16.sp)
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "English (LTR)",
                color = if (isEn) NeonPinkLight else TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (isEn) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Airplane Mode Test Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      border = androidx.compose.foundation.BorderStroke(1.dp, if (uiState.airplaneModeTestEnabled) TurboActiveGreen else SurfaceCardBorder),
      shape = RoundedCornerShape(14.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(if (uiState.airplaneModeTestEnabled) TurboActiveGreen.copy(alpha = 0.2f) else NeonPinkPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.AirplanemodeActive,
              contentDescription = null,
              tint = if (uiState.airplaneModeTestEnabled) TurboActiveGreen else NeonPinkPrimary,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(text = "حالت تست هواپیما (Airplane Mode Test)", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Text(
              text = if (uiState.airplaneModeTestEnabled) "تست فعال: تمام دسترسی‌های شبکه قطع و هوش مصنوعی کاملاً آفلاین اجرا می‌شود." else "برای اثبات عدم نیاز به اینترنت فعال کنید.",
              color = TextSecondary,
              fontSize = 11.sp
            )
          }
        }

        Switch(
          checked = uiState.airplaneModeTestEnabled,
          onCheckedChange = { viewModel.toggleAirplaneModeTest() },
          colors = SwitchDefaults.colors(
            checkedThumbColor = BackgroundPitchBlack,
            checkedTrackColor = TurboActiveGreen,
            uncheckedThumbColor = TextTertiary,
            uncheckedTrackColor = SurfaceCardBorder
          ),
          modifier = Modifier.testTag("airplane_mode_switch")
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // User Experience Level
    Text(text = "سطح تجربه کاربری (User Experience Mode):", color = TextPink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))

    val userModes = listOf(
      Triple("SIMPLE", "حالت ساده", "فقط مدل را انتخاب کنید، تمام تنظیمات و رزولوشن کامپیوتر خودکار مدیریت می‌شود."),
      Triple("BALANCED", "حالت بهینه (پیش‌فرض)", "تعادل خودکار فرکانس و رم با پشتیبانی از Auto Performance."),
      Triple("ADVANCED", "حالت پیشرفته", "دسترسی آزاد به تنظیم تعداد هسته‌ها، گام‌های انتشار و محدودیت‌های دمایی.")
    )

    userModes.forEach { (modeKey, title, desc) ->
      val isSelected = uiState.userModeLevel == modeKey
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(if (isSelected) NeonPinkContainer else SurfaceCard)
          .border(1.dp, if (isSelected) NeonPinkPrimary else SurfaceCardBorder, RoundedCornerShape(10.dp))
          .clickable { viewModel.setUserModeLevel(modeKey) }
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = title, color = if (isSelected) NeonPinkLight else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
          Text(text = desc, color = TextSecondary, fontSize = 11.sp)
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Network Firewall Guard Banner
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      border = androidx.compose.foundation.BorderStroke(1.dp, TurboActiveGreen.copy(alpha = 0.5f)),
      shape = RoundedCornerShape(14.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Security, contentDescription = null, tint = TurboActiveGreen, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(text = "دیواره آتش شبکه (Network Firewall Guard)", color = TurboActiveGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "موتورهای استنتاج محلی هیچ‌گونه API راه دور، URL یا تله‌متری مخفی ندارند. داده‌های متنی، تصاویر و اسناد شما در دیتابیس داخلی گوشی باقی می‌مانند.",
          color = TextSecondary,
          fontSize = 11.5.sp,
          lineHeight = 17.sp
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Storage and Cache Cleanup
    Text(text = "مدیریت کش و حافظه:", color = TextPink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))

    Button(
      onClick = {
        viewModel.clearRagIndex()
        Toast.makeText(context, "تمام فایل‌های موقت و کش‌های وکتور با موفقیت پاک شدند", Toast.LENGTH_SHORT).show()
      },
      colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
      border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Icon(Icons.Default.CleaningServices, contentDescription = null, tint = NeonPinkLight)
      Spacer(modifier = Modifier.width(8.dp))
      Text("پاکسازی کش‌های موقت و وکتورهای RAG", color = TextPrimary, fontSize = 12.5.sp)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // About box
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(SurfaceCard)
        .padding(12.dp)
    ) {
      Column {
        Text(text = "Universal Offline AI Hub v2.0", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        Text(text = "طراحی اختصاصی برای Redmi Note 14 Pro (Helio G100-Ultra / Mali-G57 MC2)", color = TextSecondary, fontSize = 11.sp)
        Text(text = "موتورهای هماهنگ: llama.cpp ARM64, MNN Mobile, ONNX Runtime, Whisper STT, Kokoro TTS", color = TextTertiary, fontSize = 10.5.sp)
      }
    }
  }
}
