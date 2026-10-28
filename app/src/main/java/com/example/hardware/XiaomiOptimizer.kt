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

/**
 * Device-specific starting point. Threads are split deliberately: generation is
 * memory-bandwidth bound, so it uses four workers on this 2+6 CPU instead of all eight;
 * prompt ingestion can use two more workers because it is throughput bound. Android still
 * owns scheduling, so this is a worker count rather than a CPU-affinity pin.
 * These are conservative recommendations, not measured tokens/sec numbers.
 */
data class DeviceTuningProfile(
  val isTargetDevice: Boolean,
  val deviceLabel: String,
  val generationThreads: Int,
  val promptThreads: Int,
  val contextTokens: Int,
  val recommendedModel: String,
  val notes: List<String>
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

  /**
   * Generation threads for this profile. More threads than physical big cores often make
   * generation slower and hotter because llama.cpp is memory-bandwidth bound. The Redmi
   * Note 14 Pro 4G (Helio G100-Ultra / 2×A76 + 6×A55) therefore uses 4 threads for Turbo.
   *
   * This only sets llama.cpp's worker count; Android/HyperOS still own frequency and affinity.
   */
  fun getOptimalThreadCount(mode: PerformanceMode): Int {
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val topology = CpuTopologyDetector.detect(cores)
    // Unknown topology must not turn into "use every core": that is the slow, hot case.
    val bigCores = if (topology.isKnown) {
      topology.performanceCores
    } else {
      (cores / 4).coerceAtLeast(1)
    }
    val ceiling = (bigCores * 2).coerceIn(2, 6).coerceAtMost(cores)
    val threads = when (mode) {
      PerformanceMode.HYPER_TURBO -> ceiling
      PerformanceMode.BALANCED -> (ceiling * 3) / 4
      PerformanceMode.ECO_BATTERY -> ceiling / 2
    }
    return threads.coerceIn(1, cores)
  }

  /**
   * Detects the Redmi Note 14 Pro 4G (and its rebadged Helio G99 siblings) from public
   * build fields. No benchmark number is invented here; the UI only shows tuning choices.
   */
  fun getDeviceTuningProfile(mode: PerformanceMode): DeviceTuningProfile {
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val generationThreads = getOptimalThreadCount(mode)
    val promptThreads = (generationThreads + 2)
      .coerceAtMost(6)
      .coerceAtMost(cores)
      .coerceAtLeast(generationThreads)
    val isG100Family = isHelioG100Family()
    val modelName = Build.MODEL.takeIf(String::isNotBlank) ?: "Android device"

    return if (isG100Family) {
      DeviceTuningProfile(
        isTargetDevice = true,
        deviceLabel = "$modelName • Helio G100-Ultra",
        generationThreads = generationThreads,
        promptThreads = promptThreads,
        contextTokens = 2048,
        recommendedModel = "۳B–۴B Q4_K_M برای پاسخ روان؛ ۸B Q3_K_L با mmap اجرا می‌شود اما محسوساً کندتر است.",
        notes = listOf(
          "HyperOS: Autostart را روشن و صرفه‌جویی باتری Sali-Localo را روی «بدون محدودیت» بگذارید.",
          "برای مدل ۸B، context را ۲۰۴۸ و حداکثر طول پاسخ را کوتاه نگه دارید.",
          "دمای باتری بالای ۴۲°C معمولاً یعنی throttling؛ بین پاسخ‌ها چند لحظه استراحت دهید."
        )
      )
    } else {
      DeviceTuningProfile(
        isTargetDevice = false,
        deviceLabel = "$modelName • ${detectSocName()}",
        generationThreads = generationThreads,
        promptThreads = promptThreads,
        contextTokens = 2048,
        recommendedModel = "با آزمون روی دستگاه انتخاب کنید؛ ۳B–۴B Q4 نقطهٔ شروع محافظه‌کارانه است.",
        notes = listOf("تعداد نخ تولید بر اساس هسته‌های فیزیکی شناسایی‌شده محدود شده است.")
      )
    }
  }

  private fun isHelioG100Family(): Boolean {
    val hardware = buildString {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        append(Build.SOC_MODEL).append(' ').append(Build.SOC_MANUFACTURER).append(' ')
      }
      append(Build.HARDWARE).append(' ').append(Build.BOARD).append(' ').append(Build.DEVICE)
    }.lowercase(Locale.ROOT)
    return hardware.contains("g100") || hardware.contains("g99") || hardware.contains("mt6789")
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
