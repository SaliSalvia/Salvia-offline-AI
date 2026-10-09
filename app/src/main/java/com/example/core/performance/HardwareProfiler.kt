package com.example.core.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import kotlin.math.roundToInt

data class DetailedDeviceProfile(
  val deviceModel: String,
  val manufacturer: String,
  val socName: String,
  val cpuArchitecture: String,
  val totalCores: Int,
  val performanceCores: Int,
  val efficiencyCores: Int,
  val gpuModel: String,
  val vulkanSupported: Boolean,
  /** null means Android has no standard public capability check for this backend. */
  val openClSupported: Boolean?,
  val totalRamGb: Float,
  val availRamGb: Float,
  val totalStorageGb: Float,
  val freeStorageGb: Float,
  val androidVersion: String,
  val thermalStatus: String,
  val batteryLevel: Int,
  /** Battery temperature, not CPU/GPU temperature. Null when the system does not report it. */
  val batteryTempCelsius: Float?,
  val recommendedLlmSize: String,
  val recommendedQuant: String,
  val recommendedContext: Int,
  val recommendedBackend: String
)

object HardwareProfiler {
  fun profileDevice(context: Context): DetailedDeviceProfile {
    val appContext = context.applicationContext
    val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager?.getMemoryInfo(memInfo)

    val totalRamGb = memInfo.totalMem / (1024f * 1024f * 1024f)
    val availRamGb = memInfo.availMem / (1024f * 1024f * 1024f)

    val statFs = StatFs(Environment.getDataDirectory().path)
    val totalStorageGb = (statFs.blockCountLong * statFs.blockSizeLong) / (1024f * 1024f * 1024f)
    val freeStorageGb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024f * 1024f * 1024f)

    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val topology = CpuTopologyDetector.detect(cores)
    val battery = readBatteryInfo(appContext)
    val thermalDescription = readThermalDescription(appContext)

    val modelName = Build.MODEL.takeIf(String::isNotBlank) ?: "Android device"
    val manufacturer = Build.MANUFACTURER.takeIf(String::isNotBlank)
      ?: Build.BRAND.takeIf(String::isNotBlank)
      ?: "Unknown"
    val socName = detectSocName()
    val abis = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { "نامشخص" }

    val vulkanSupported = try {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
    } catch (_: Exception) {
      false
    }

    // There is no reliable standard Android PackageManager feature for OpenCL. Do not claim it is
    // supported merely because this device has a Mali/Adreno GPU.
    val openClSupported: Boolean? = null

    val totalRamMb = memInfo.totalMem / (1024L * 1024L)
    val availableRamMb = memInfo.availMem / (1024L * 1024L)
    val recommendedModel = when {
      totalRamMb >= 10_000L && availableRamMb >= 4_000L ->
        "۳B–۴B Q4 برای پیش‌فرض؛ ۷B فقط پس از پیش‌بررسی حافظه"
      totalRamMb >= 7_000L -> "۱٫۵B–۳B Q4 (شروع محافظه‌کارانه)"
      else -> "۱B–۲B Q4"
    }
    val recommendedContext = when {
      availableRamMb < 1_500L -> 1024
      totalRamMb >= 10_000L && availableRamMb >= 4_000L -> 4096
      else -> 2048
    }

    return DetailedDeviceProfile(
      deviceModel = modelName,
      manufacturer = manufacturer.replaceFirstChar { it.uppercase() },
      socName = socName,
      cpuArchitecture = abis,
      totalCores = cores,
      performanceCores = topology.performanceCores,
      efficiencyCores = topology.efficiencyCores,
      gpuModel = "از API عمومی اندروید قابل‌تشخیص نیست",
      vulkanSupported = vulkanSupported,
      openClSupported = openClSupported,
      totalRamGb = (totalRamGb * 10).roundToInt() / 10f,
      availRamGb = (availRamGb * 10).roundToInt() / 10f,
      totalStorageGb = (totalStorageGb * 10).roundToInt() / 10f,
      freeStorageGb = (freeStorageGb * 10).roundToInt() / 10f,
      androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
      thermalStatus = thermalDescription,
      batteryLevel = battery.first,
      batteryTempCelsius = battery.second,
      recommendedLlmSize = recommendedModel,
      recommendedQuant = "Q4_K_M یا Q4_0؛ انتخاب نهایی با آزمون مدل روی دستگاه",
      recommendedContext = recommendedContext,
      recommendedBackend = "CPU/ARM64 به‌عنوان مسیر امن؛ GPU فقط پس از شناسایی و بنچمارک runtime"
    )
  }

  private fun detectSocName(): String {
    val systemSoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
        .filter(String::isNotBlank)
        .joinToString(" ")
    } else {
      ""
    }
    return systemSoc.ifBlank { Build.HARDWARE.takeIf(String::isNotBlank) ?: "SoC نامشخص" }
  }

  private fun readBatteryInfo(context: Context): Pair<Int, Float?> {
    val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
      ?: return -1 to null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val batteryLevel = if (level >= 0 && scale > 0) {
      ((level / scale.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    } else {
      -1
    }
    val tenthsCelsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
    val batteryTemp = if (tenthsCelsius >= 0) tenthsCelsius / 10f else null
    return batteryLevel to batteryTemp
  }

  private fun readThermalDescription(context: Context): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return "شدت حرارتی سیستم از API عمومی در دسترس نیست"
    }
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      ?: return "وضعیت حرارتی نامشخص"
    return when (powerManager.currentThermalStatus) {
      PowerManager.THERMAL_STATUS_NONE -> "سیستم: عادی"
      PowerManager.THERMAL_STATUS_LIGHT -> "سیستم: کم"
      PowerManager.THERMAL_STATUS_MODERATE -> "سیستم: متوسط"
      PowerManager.THERMAL_STATUS_SEVERE -> "سیستم: شدید"
      PowerManager.THERMAL_STATUS_CRITICAL -> "سیستم: بحرانی"
      PowerManager.THERMAL_STATUS_EMERGENCY -> "سیستم: اضطراری"
      PowerManager.THERMAL_STATUS_SHUTDOWN -> "سیستم: خاموشی حرارتی"
      else -> "وضعیت حرارتی نامشخص"
    }
  }
}
