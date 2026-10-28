package com.example.core.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resource states are advisory controls for the app's own work. Android and device firmware
 * remain responsible for protecting the handset from unsafe temperatures and power conditions.
 */
enum class GuardResourceState(val titleFa: String, val levelCode: Int) {
  NORMAL("عادی", 0),
  CAUTION("احتیاط", 1),
  HARD_STOP("توقف ایمن", 2),
  RESUMABLE("آماده ادامه", 3)
}

data class ResourceSnapshot(
  val ramUsagePctOfBudget: Int,
  val ramState: GuardResourceState,
  /** App process CPU use as a percentage of total device CPU capacity; not system-wide CPU load. */
  val cpuSustainedPct: Int,
  val cpuState: GuardResourceState,
  val thermalState: GuardResourceState,
  /** Battery temperature reported by Android, not CPU/GPU temperature. */
  val batteryTempCelsius: Float?,
  val thermalSystemStatus: String,
  val isAiExecutionAllowed: Boolean,
  val activeConstraintReason: String? = null,
  val safeAiRamBudgetMb: Int,
  /** Process PSS includes UI and runtime memory; it is not a model-only measurement. */
  val appProcessPssMb: Int
)

/**
 * Conservative guard for this app's inference jobs.
 * It uses Android's thermal severity, system-wide RAM pressure and process CPU time. CPU
 * utilization alone never stops inference: sustained CPU use is expected during generation.
 * Process PSS is still reported, but mmap-backed model pages are reclaimable and must not be
 * treated as a hard memory limit.
 */
class ResourceGuard(
  private val context: Context,
  private val scope: CoroutineScope
) {
  companion object {
    const val THRESHOLD_CAUTION = 70
    const val THRESHOLD_HARD_STOP = 80
    private const val MONITOR_INTERVAL_MS = 1_000L
    private const val MIN_FREE_RAM_RESERVE_MB = 1_024
  }

  private val appContext = context.applicationContext
  private val actManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
  private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
  private val initialMemInfo = ActivityManager.MemoryInfo().also { actManager?.getMemoryInfo(it) }
  private val initialSafeAiRamBudgetMb = calculateSafeAiRamBudgetMb(initialMemInfo)
  private val initialProcessPssMb = readProcessPssMb()
  private val initialAvailableMb = (initialMemInfo.availMem / (1024L * 1024L)).toInt()
  // Report system RAM pressure, not PSS / budget. PSS includes file-backed mmap pages
  // that Android can reclaim, so it is a poor reason to hard-stop a large GGUF model.
  private val initialRamUsagePct = systemRamUsedPct(initialMemInfo)
  private val initialRamState = when {
    initialMemInfo.lowMemory || initialSafeAiRamBudgetMb <= 0 ||
      initialAvailableMb < MIN_FREE_RAM_RESERVE_MB / 2 -> GuardResourceState.HARD_STOP
    initialAvailableMb < MIN_FREE_RAM_RESERVE_MB -> GuardResourceState.CAUTION
    else -> GuardResourceState.NORMAL
  }

  private val _snapshot = MutableStateFlow(
    ResourceSnapshot(
      ramUsagePctOfBudget = initialRamUsagePct,
      ramState = initialRamState,
      cpuSustainedPct = 0,
      cpuState = GuardResourceState.NORMAL,
      thermalState = GuardResourceState.CAUTION,
      batteryTempCelsius = null,
      thermalSystemStatus = "در انتظار دادهٔ سیستم",
      isAiExecutionAllowed = initialRamState != GuardResourceState.HARD_STOP,
      activeConstraintReason = if (initialRamState == GuardResourceState.HARD_STOP) {
        "حافظهٔ آزاد سیستم برای اجرای ایمن کافی نیست"
      } else {
        null
      },
      safeAiRamBudgetMb = initialSafeAiRamBudgetMb,
      appProcessPssMb = initialProcessPssMb
    )
  )
  val snapshot: StateFlow<ResourceSnapshot> = _snapshot.asStateFlow()

  private var monitorJob: Job? = null
  private var previousProcessCpuMs: Long? = null
  private var previousElapsedMs: Long? = null
  private var isHardStopped = false
  private var wasInRamCaution = false
  private val stateMutex = Mutex()

  var onPreventiveCleanupRequested: (() -> Unit)? = null
  var onEmergencyAiStopRequested: ((reason: String) -> Unit)? = null
  var onAiResumeAllowed: (() -> Unit)? = null

  init {
    startDecoupledMonitor()
  }

  private fun startDecoupledMonitor() {
    monitorJob?.cancel()
    monitorJob = scope.launch(Dispatchers.IO) {
      while (isActive) {
        evaluateSystemState()
        delay(MONITOR_INTERVAL_MS)
      }
    }
  }

  private suspend fun evaluateSystemState() = stateMutex.withLock {
    val memInfo = ActivityManager.MemoryInfo()
    actManager?.getMemoryInfo(memInfo)

    val totalMb = (memInfo.totalMem / (1024L * 1024L)).toInt()
    val availableMb = (memInfo.availMem / (1024L * 1024L)).toInt()
    val safeBudgetMb = calculateSafeAiRamBudgetMb(memInfo)
    val processPssMb = readProcessPssMb()
    val systemUsedPct = if (totalMb > 0) {
      (((totalMb - availableMb).toFloat() / totalMb) * 100f).toInt().coerceIn(0, 100)
    } else {
      100
    }

    // A loaded GGUF is memory-mapped: most of its resident PSS is file-backed and
    // reclaimable. Hard-stop only on real system pressure (lowMemory / critically
    // low free RAM), and use system-wide usage for the caution state.
    val ramState = when {
      memInfo.lowMemory || safeBudgetMb <= 0 || availableMb < MIN_FREE_RAM_RESERVE_MB / 2 ->
        GuardResourceState.HARD_STOP
      availableMb < MIN_FREE_RAM_RESERVE_MB || systemUsedPct >= THRESHOLD_HARD_STOP ->
        GuardResourceState.CAUTION
      else -> GuardResourceState.NORMAL
    }

    val cpuPct = sampleProcessCpuUsagePct()
    val cpuState = if (cpuPct >= THRESHOLD_CAUTION) GuardResourceState.CAUTION else GuardResourceState.NORMAL
    val thermal = evaluateThermalStatus()

    // Do not stop a healthy inference just because its CPU use is high. Stop only for critical
    // Android thermal severity or a real memory pressure / low-memory signal.
    val shouldStop = ramState == GuardResourceState.HARD_STOP || thermal.first == GuardResourceState.HARD_STOP
    val canResume = availableMb >= MIN_FREE_RAM_RESERVE_MB &&
      !memInfo.lowMemory && thermal.first != GuardResourceState.HARD_STOP

    var constraintMsg: String? = null
    if (shouldStop && !isHardStopped) {
      isHardStopped = true
      constraintMsg = when {
        ramState == GuardResourceState.HARD_STOP -> "فشار واقعی حافظه یا هشدار کمبود حافظهٔ اندروید"
        else -> "وضعیت حرارتی بحرانی گزارش‌شده توسط اندروید"
      }
      onEmergencyAiStopRequested?.invoke(constraintMsg)
    } else if (isHardStopped && canResume) {
      isHardStopped = false
      onAiResumeAllowed?.invoke()
    }

    if (ramState == GuardResourceState.CAUTION && !wasInRamCaution) {
      onPreventiveCleanupRequested?.invoke()
    }
    wasInRamCaution = ramState == GuardResourceState.CAUTION

    _snapshot.value = ResourceSnapshot(
      ramUsagePctOfBudget = systemUsedPct,
      ramState = ramState,
      cpuSustainedPct = cpuPct,
      cpuState = cpuState,
      thermalState = thermal.first,
      batteryTempCelsius = thermal.third,
      thermalSystemStatus = thermal.second,
      isAiExecutionAllowed = !isHardStopped,
      activeConstraintReason = if (isHardStopped) constraintMsg ?: "منابع دستگاه هنوز به محدودهٔ ایمن بازنگشته‌اند" else null,
      safeAiRamBudgetMb = safeBudgetMb,
      appProcessPssMb = processPssMb
    )
  }

  /**
   * Preflight check for model load. llama.cpp mmaps GGUF weights on Android, so the
   * multi-gigabyte weight file is file-backed and reclaimable; only KV cache, compute
   * workspace and the safety margin need guaranteed anonymous memory.
   */
  fun canSafelyLoadModel(
    modelWeightsMb: Int,
    contextTokens: Int,
    weightsAreMmap: Boolean = true,
    loadedModelWeightsMb: Int = 0
  ): Pair<Boolean, String> {
    if (modelWeightsMb <= 0 || contextTokens <= 0) {
      return false to "مشخصات اندازه یا context مدل معتبر نیست."
    }

    val memInfo = ActivityManager.MemoryInfo()
    actManager?.getMemoryInfo(memInfo)
    val totalMb = (memInfo.totalMem / (1024L * 1024L)).toInt()
    val availableMb = (memInfo.availMem / (1024L * 1024L)).toInt()
    val safeBudgetMb = calculateSafeAiRamBudgetMb(memInfo)
    val currentPssMb = readProcessPssMb()

    // Qwen3-8B-class models use roughly 0.28 MB/token of FP16 KV cache. A generic 0.12
    // estimate under-counted that by more than 2x and caused late decode failures.
    val kvCacheEstimateMb = maxOf(64, (contextTokens * 0.28f).toInt())
    val runtimeWorkspaceMb = if (weightsAreMmap) 384 else 640
    val safetyMarginMb = 512
    val anonymousWeightsMb = if (weightsAreMmap) 0 else modelWeightsMb
    val incrementalRequiredMb = anonymousWeightsMb + kvCacheEstimateMb + runtimeWorkspaceMb + safetyMarginMb

    // When switching models, native loading frees the current one first. Exclude its
    // mapped pages so the preflight does not reject a replacement that actually fits.
    val reclaimableMb = loadedModelWeightsMb.coerceIn(0, currentPssMb)
    val baselinePssMb = (currentPssMb - reclaimableMb).coerceAtLeast(0)
    val remainingAppBudgetMb = (safeBudgetMb - baselinePssMb).coerceAtLeast(0)
    val remainingPhysicalMb = (availableMb - MIN_FREE_RAM_RESERVE_MB).coerceAtLeast(0)
    val allowedIncrementMb = minOf(remainingAppBudgetMb, remainingPhysicalMb)

    val modelLargerThanUsableRam = modelWeightsMb > (totalMb * 0.85f).toInt()

    return when {
      memInfo.lowMemory -> false to "بارگذاری رد شد: اندروید هشدار کمبود حافظه (lowMemory) می‌دهد."
      modelLargerThanUsableRam -> false to
        "بارگذاری رد شد: حجم فایل مدل (${modelWeightsMb}MB) از سقف امن RAM این دستگاه بیشتر است."
      incrementalRequiredMb > allowedIncrementMb -> false to
        "بارگذاری رد شد: برای KV/workspace حدود ${incrementalRequiredMb}MB لازم است، اما پس از رزرو ایمنی فقط ${allowedIncrementMb}MB آزاد است. چند برنامه را ببندید یا context را کم کنید."
      weightsAreMmap && modelWeightsMb.toFloat() > totalMb * 0.55f -> true to
        "مدل بزرگ است؛ با mmap بارگذاری می‌شود و ممکن است روی این دستگاه کند یا تحت فشار حافظه باشد."
      else -> true to ""
    }
  }

  private fun calculateSafeAiRamBudgetMb(memInfo: ActivityManager.MemoryInfo = ActivityManager.MemoryInfo()): Int {
    val manager = actManager ?: return 0
    if (memInfo.totalMem <= 0L) manager.getMemoryInfo(memInfo)

    val totalMb = (memInfo.totalMem / (1024L * 1024L)).toInt()
    if (totalMb <= 0) return 0

    // Reserve at least 2GB and 30% of RAM for Android, other apps and transient allocations.
    val systemReserveMb = maxOf(2_048, (totalMb * 0.30f).toInt())
    return (totalMb - systemReserveMb).coerceAtLeast(0)
  }

  private fun systemRamUsedPct(memInfo: ActivityManager.MemoryInfo): Int {
    val totalMb = (memInfo.totalMem / (1024L * 1024L)).toInt()
    val availableMb = (memInfo.availMem / (1024L * 1024L)).toInt()
    if (totalMb <= 0) return 100
    return (((totalMb - availableMb).toFloat() / totalMb) * 100f).toInt().coerceIn(0, 100)
  }

  private fun readProcessPssMb(): Int = try {
    (Debug.getPss() / 1_024L).toInt().coerceAtLeast(0)
  } catch (_: Exception) {
    0
  }

  /** Percent of the device's total CPU capacity consumed by this app process. */
  private fun sampleProcessCpuUsagePct(): Int {
    val nowElapsed = SystemClock.elapsedRealtime()
    val nowCpu = Process.getElapsedCpuTime()
    val oldElapsed = previousElapsedMs
    val oldCpu = previousProcessCpuMs
    previousElapsedMs = nowElapsed
    previousProcessCpuMs = nowCpu

    if (oldElapsed == null || oldCpu == null || nowElapsed <= oldElapsed) return 0
    val elapsedDelta = nowElapsed - oldElapsed
    val cpuDelta = (nowCpu - oldCpu).coerceAtLeast(0L)
    val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    return ((cpuDelta.toDouble() / (elapsedDelta * coreCount)) * 100.0).toInt().coerceIn(0, 100)
  }

  /** Returns official Android thermal severity and the separately measured battery temperature. */
  private fun evaluateThermalStatus(): Triple<GuardResourceState, String, Float?> {
    val batteryTemp = readBatteryTemperatureCelsius()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
      return when (powerManager.currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE -> Triple(GuardResourceState.NORMAL, "وضعیت حرارتی سیستم: عادی", batteryTemp)
        PowerManager.THERMAL_STATUS_LIGHT -> Triple(GuardResourceState.NORMAL, "وضعیت حرارتی سیستم: کم", batteryTemp)
        PowerManager.THERMAL_STATUS_MODERATE -> Triple(GuardResourceState.CAUTION, "وضعیت حرارتی سیستم: متوسط", batteryTemp)
        PowerManager.THERMAL_STATUS_SEVERE -> Triple(GuardResourceState.HARD_STOP, "وضعیت حرارتی سیستم: شدید؛ تولید متوقف شد", batteryTemp)
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN -> Triple(GuardResourceState.HARD_STOP, "هشدار حرارتی بحرانی اندروید", batteryTemp)
        else -> Triple(GuardResourceState.CAUTION, "وضعیت حرارتی نامشخص", batteryTemp)
      }
    }

    // On older Android versions there is no public thermal-severity API. Use battery temperature
    // only as a conservative fallback, and label it as battery temperature rather than CPU temp.
    return when {
      batteryTemp == null -> Triple(GuardResourceState.CAUTION, "پایش حرارتی سیستم در این نسخه در دسترس نیست", null)
      batteryTemp >= 46f -> Triple(GuardResourceState.HARD_STOP, "دمای باتری بالا؛ تولید متوقف شد", batteryTemp)
      batteryTemp >= 42f -> Triple(GuardResourceState.CAUTION, "دمای باتری بالا رفته است", batteryTemp)
      else -> Triple(GuardResourceState.NORMAL, "دمای باتری عادی؛ API حرارتی سیستم در دسترس نیست", batteryTemp)
    }
  }

  private fun readBatteryTemperatureCelsius(): Float? {
    val status: Intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val tenthsCelsius = status.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
    return if (tenthsCelsius >= 0) tenthsCelsius / 10f else null
  }
}
