package com.example.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import java.io.File
import java.io.RandomAccessFile

enum class PerformanceMode(val title: String, val description: String, val threadsMultiplier: Float) {
  HYPER_TURBO("HyperOS Beast Turbo", "حداکثر سرعت و استفاده از هسته‌های قدرتمند (Cortex-A78 / X)", 1.0f),
  BALANCED("Balanced MIUI", "تعادل بین سرعت پردازش، مصرف باتری و دمای پردازنده", 0.7f),
  ECO_BATTERY("Eco Battery", "مصرف حداقل باتری با فرکانس کنترل‌شده و هسته‌های کم‌مصرف", 0.4f)
}

data class HardwareTelemetry(
  val isXiaomiDevice: Boolean,
  val deviceModel: String,
  val brandName: String,
  val hyperOsVersion: String,
  val cpuCores: Int,
  val performanceCores: Int,
  val efficiencyCores: Int,
  val totalRamGb: Float,
  val availRamGb: Float,
  val ramUsagePercent: Int,
  val batteryLevel: Int,
  val batteryTempCelsius: Float,
  val isCharging: Boolean,
  val chipsetName: String
)

object XiaomiOptimizer {

  fun isXiaomiFamily(): Boolean {
    val brand = Build.BRAND.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    return brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ||
           manufacturer.contains("xiaomi") || manufacturer.contains("redmi")
  }

  fun getChipsetRecommendation(): String {
    val model = Build.MODEL.lowercase()
    return when {
      model.contains("note 14") -> "بهینه‌سازی‌شده برای MediaTek Dimensity 7300-Ultra / Snapdragon 7s Gen 3"
      model.contains("note 13") -> "بهینه‌سازی‌شده برای Dimensity 7200-Ultra / Snapdragon 7s Gen 2"
      model.contains("poco") -> "پیکربندی اختصاصی هسته‌های توربو POCO Game Turbo"
      isXiaomiFamily() -> "پیکربندی ویژه Xiaomi HyperOS Thread Scheduler"
      else -> "بهینه‌سازی استاندارد ARM64 big.LITTLE"
    }
  }

  fun getHyperOsVersion(): String {
    return try {
      val miuiVer = getSystemProperty("ro.mi.os.version.name")
      if (miuiVer.isNotEmpty()) "HyperOS $miuiVer"
      else {
        val oldMiui = getSystemProperty("ro.miui.ui.version.name")
        if (oldMiui.isNotEmpty()) "MIUI $oldMiui" else "HyperOS Engine v2.0"
      }
    } catch (_: Exception) {
      "HyperOS Standard"
    }
  }

  private fun getSystemProperty(key: String): String {
    return try {
      val clazz = Class.forName("android.os.SystemProperties")
      val getMethod = clazz.getMethod("get", String::class.java)
      (getMethod.invoke(null, key) as? String) ?: ""
    } catch (_: Exception) {
      ""
    }
  }

  fun getTelemetry(context: Context): HardwareTelemetry {
    val actMan = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    actMan?.getMemoryInfo(memInfo)

    val totalRamGb = memInfo.totalMem / (1024f * 1024f * 1024f)
    val availRamGb = memInfo.availMem / (1024f * 1024f * 1024f)
    val ramPercent = (((totalRamGb - availRamGb) / totalRamGb) * 100).toInt().coerceIn(0, 100)

    val cores = Runtime.getRuntime().availableProcessors()
    val perfCores = maxOf(2, cores / 2)
    val effCores = maxOf(2, cores - perfCores)

    // Battery info
    val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
      context.registerReceiver(null, filter)
    }
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 80
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
    val batteryPct = if (scale > 0) ((level / scale.toFloat()) * 100).toInt() else 80
    val tempRaw = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 320) ?: 320
    val tempC = tempRaw / 10.0f
    val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                     status == BatteryManager.BATTERY_STATUS_FULL

    val detectedChipset = if (Build.HARDWARE.isNotEmpty()) Build.HARDWARE else "Octa-Core ARM64"

    return HardwareTelemetry(
      isXiaomiDevice = isXiaomiFamily(),
      deviceModel = if (Build.MODEL.isNullOrEmpty()) "Xiaomi Device" else Build.MODEL,
      brandName = Build.BRAND.replaceFirstChar { it.uppercase() },
      hyperOsVersion = getHyperOsVersion(),
      cpuCores = cores,
      performanceCores = perfCores,
      efficiencyCores = effCores,
      totalRamGb = (Math.round(totalRamGb * 10) / 10f),
      availRamGb = (Math.round(availRamGb * 10) / 10f),
      ramUsagePercent = ramPercent,
      batteryLevel = batteryPct,
      batteryTempCelsius = tempC,
      isCharging = isCharging,
      chipsetName = detectedChipset
    )
  }

  fun getOptimalThreadCount(mode: PerformanceMode): Int {
    val totalCores = Runtime.getRuntime().availableProcessors()
    return when (mode) {
      PerformanceMode.HYPER_TURBO -> totalCores.coerceIn(4, 8)
      PerformanceMode.BALANCED -> maxOf(2, (totalCores * 0.7f).toInt())
      PerformanceMode.ECO_BATTERY -> maxOf(1, (totalCores * 0.35f).toInt())
    }
  }

  fun applyThreadPriority(mode: PerformanceMode) {
    try {
      when (mode) {
        PerformanceMode.HYPER_TURBO -> {
          Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
        }
        PerformanceMode.BALANCED -> {
          Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
        }
        PerformanceMode.ECO_BATTERY -> {
          Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        }
      }
    } catch (_: Exception) {
      // Ignore if sandbox limits priority
    }
  }
}
