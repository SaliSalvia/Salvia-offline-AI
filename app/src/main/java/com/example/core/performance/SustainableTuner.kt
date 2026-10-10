package com.example.core.performance

data class TuningBenchmarkResult(
  val threadCount: Int,
  val tokensPerSecond: Float,
  val thermalRiseRate: Float,
  val isSustainable: Boolean
)

data class SustainableProfile(
  val optimalThreads: Int,
  val optimalBatchSize: Int,
  val optimalContext: Int,
  /** Null until measured by a real inference benchmark on this device and model. */
  val peakTokensPerSec: Float?,
  /** Null until measured by a sustained inference benchmark on this device and model. */
  val sustainedTokensPerSec: Float?,
  val recommendedBackend: String
)

object SustainableTuner {
  /**
   * Returns conservative starting settings only. Performance numbers are deliberately not guessed;
   * the selected runtime/model combination must be benchmarked on the actual handset.
   */
  fun evaluateOptimalProfile(totalCores: Int, thermalTempCelsius: Float): SustainableProfile {
    val cores = totalCores.coerceAtLeast(1)
    val recommendedThreads = when {
      thermalTempCelsius >= 42f -> minOf(2, cores)
      cores >= 8 -> 4
      cores >= 4 -> 3
      else -> cores
    }.coerceAtLeast(1)

    return SustainableProfile(
      optimalThreads = recommendedThreads,
      optimalBatchSize = 128,
      optimalContext = 2048,
      peakTokensPerSec = null,
      sustainedTokensPerSec = null,
      recommendedBackend = "CPU/ARM64 به‌عنوان پیش‌فرض؛ GPU فقط پس از بنچمارک runtime"
    )
  }

  fun getAdvisorInsights(
    currentModelName: String,
    contextLength: Int,
    threads: Int,
    tempCelsius: Float?,
    availRamGb: Float
  ): List<String> = buildList {
    add("پروفایل فعلی: $currentModelName، context برابر $contextLength و پیشنهاد حداکثر $threads worker؛ runtime باید این تنظیم را واقعاً اعمال کند.")

    if (contextLength > 4096) {
      add("💡 context بالاتر از ۴۰۹۶ می‌تواند KV cache را به‌طور محسوسی افزایش دهد؛ مقدار را فقط بر اساس معماری مدل و RAM آزاد بالا ببرید.")
    } else {
      add("context فعلی محدود شده است؛ اندازهٔ واقعی KV cache به معماری مدل و تنظیمات runtime وابسته است.")
    }

    when {
      tempCelsius == null -> add("دمای باتری از سیستم دریافت نشد؛ شدت حرارتی Android را مبنای توقف ایمن قرار دهید.")
      tempCelsius >= 42f -> add("دمای باتری ${tempCelsius}°C است؛ برای ادامهٔ پایدار، بار کاری را کاهش دهید و وضعیت حرارتی Android را بررسی کنید.")
      else -> add("دمای اندازه‌گیری‌شده مربوط به باتری است (${tempCelsius}°C)، نه دمای مستقیم CPU/GPU.")
    }

    if (availRamGb < 3.0f) {
      add("⚠️ RAM آزاد گزارش‌شده ${availRamGb}GB است؛ بارگذاری مدل بزرگ یا context بلند پرریسک است.")
    } else {
      add("RAM آزاد گزارش‌شده ${availRamGb}GB است؛ این مقدار تضمین‌کنندهٔ نبود OOM نیست و باید همراه با تخمین runtime سنجیده شود.")
    }
  }
}
