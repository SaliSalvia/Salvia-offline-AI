package com.example.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import com.example.core.performance.CpuTopologyDetector
import java.util.Locale

// These profiles only select app-side scheduling preferences. Android/HyperOS retain control of
// CPU affinity, clocks, GPU power and thermal protection.
enum class PerformanceMode(val title: String, val description: String, val threadsMultiplier: Float) {
  HYPER_TURBO("عملکرد حداکثریِ ایمن", "بیشترین موازی‌سازی برنامه؛ محدود به گارد حافظه و حرارت اندروید", 1.0f),
  BALANCED("متعادل", "تعادل بین پاسخ‌گویی، مصرف باتری و گرما", 0.7f),
  ECO_BATTERY("کم‌مصرف", "موازی‌سازی کمتر برای کاهش مصرف و گرمای تولیدشده", 0.4f)
}

data class HardwareTelemetry(
  val isXiaomiDevice: Boolean,
  val deviceModel: String,
  val brandName: String,
  val hyperOsVersion: String,
  val cpuCores: Int,
  /** Zero means the public system interfaces did not reveal the CPU cluster topology. */
  val performanceCores: Int,
  val efficiencyCores: Int,
  val totalRamGb: Float,
  val availRamGb: Float,
  val ramUsagePercent: Int,
  /** -1 means unavailable. */
  val batteryLevel: Int,
  /** Battery temperature, not CPU/GPU temperature. */
  val batteryTempCelsius: Float?,
  val isCharging: Boolean,
  val chipsetName: String
)

object XiaomiOptimizer {
  fun isXiaomiFamily(): Boolean {
    val brand = Build.BRAND.lowercase(Locale.ROOT)
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    return brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ||
      manufacturer.contains("xiaomi") || manufacturer.contains("redmi")
  }

  fun getChipsetRecommendation(): String {
    val detectedSoc = detectSocName()
    return if (isXiaomiFamily()) {
      "دستگاه Xiaomi/Redmi شناسایی شد؛ SoC گزارش‌شده توسط Android: $detectedSoc. کنترل کلاک و pinning در اختیار HyperOS است."
    } else {
      "SoC گزارش‌شده توسط Android: $detectedSoc. پروفایل محافظه‌کارانهٔ عمومی فعال است."
    }
  }

  fun getHyperOsVersion(): String {
    val version = getSystemProperty("ro.mi.os.version.name")
    if (version.isNotBlank()) return "HyperOS $version"
    val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
    if (miuiVersion.isNotBlank()) return "MIUI $miuiVersion"
    return "نسخهٔ HyperOS نامشخص"
  }

  private fun getSystemProperty(key: String): String = try {
    val clazz = Class.forName("android.os.SystemProperties")
    val getMethod = clazz.getMethod("get", String::class.java)
    (getMethod.invoke(null, key) as? String).orEmpty()
  } catch (_: Exception) {
    ""
  }

  private fun detectSocName(): String {
    val systemSoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
        .filter(String::isNotBlank)
        .joinToString(" ")
    } else {
      ""
    }
    return systemSoc.ifBlank { Build.HARDWARE.takeIf(String::isNotBlank) ?: "نامشخص" }
  }

  fun getTelemetry(context: Context): HardwareTelemetry {
    val appContext = context.applicationContext
    val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager?.getMemoryInfo(memInfo)

    val totalRamGb = memInfo.totalMem / (1024f * 1024f * 1024f)
    val availRamGb = memInfo.availMem / (1024f * 1024f * 1024f)
    val ramPercent = if (memInfo.totalMem > 0L) {
      (((memInfo.totalMem - memInfo.availMem).toFloat() / memInfo.totalMem) * 100f).toInt().coerceIn(0, 100)
    } else {
      0
    }

    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val topology = CpuTopologyDetector.detect(cores)
    val batteryStatus: Intent? = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val batteryPct = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt().coerceIn(0, 100) else -1
    val tempRaw = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
    val batteryTempC = if (tempRaw >= 0) tempRaw / 10f else null
    val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

    return HardwareTelemetry(
      isXiaomiDevice = isXiaomiFamily(),
      deviceModel = Build.MODEL.takeIf(String::isNotBlank) ?: "Android device",
      brandName = Build.BRAND.takeIf(String::isNotBlank)?.replaceFirstChar { it.uppercase() } ?: "Unknown",
      hyperOsVersion = getHyperOsVersion(),
      cpuCores = cores,
      performanceCores = topology.performanceCores,
      efficiencyCores = topology.efficiencyCores,
      totalRamGb = (totalRamGb * 10).toInt() / 10f,
      availRamGb = (availRamGb * 10).toInt() / 10f,
      ramUsagePercent = ramPercent,
      batteryLevel = batteryPct,
      batteryTempCelsius = batteryTempC,
      isCharging = isCharging,
      chipsetName = detectSocName()
    )
  }

  /** Suggested app worker count only; it does not pin threads to big or little CPU cores. */
  fun getOptimalThreadCount(mode: PerformanceMode): Int {
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    return when (mode) {
      PerformanceMode.HYPER_TURBO -> minOf(6, cores)
      PerformanceMode.BALANCED -> minOf(4, cores)
      PerformanceMode.ECO_BATTERY -> minOf(2, cores)
    }.coerceAtLeast(1)
  }

  /** Must only be called from a worker thread; never raise the Android UI thread priority. */
  fun applyThreadPriority(mode: PerformanceMode) {
    try {
      val priority = when (mode) {
        PerformanceMode.HYPER_TURBO, PerformanceMode.BALANCED -> Process.THREAD_PRIORITY_DEFAULT
        PerformanceMode.ECO_BATTERY -> Process.THREAD_PRIORITY_BACKGROUND
      }
      Process.setThreadPriority(priority)
    } catch (_: Exception) {
      // Scheduling is controlled by Android/HyperOS; failure is non-fatal.
    }
  }
}
