package com.example.core.performance

import java.io.File

/**
 * Best-effort CPU cluster detector. Android does not expose big.LITTLE topology through a stable
 * public API, so unknown is preferable to presenting a guessed split as measured hardware data.
 */
data class CpuCoreTopology(
  val performanceCores: Int = 0,
  val efficiencyCores: Int = 0
) {
  val isKnown: Boolean
    get() = performanceCores > 0 && efficiencyCores > 0
}

object CpuTopologyDetector {
  fun detect(totalCores: Int): CpuCoreTopology {
    if (totalCores < 2) return CpuCoreTopology()

    val maxFrequencies = (0 until totalCores).map { core ->
      runCatching {
        File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq")
          .takeIf { it.canRead() }
          ?.readText()
          ?.trim()
          ?.toLongOrNull()
      }.getOrNull()
    }

    if (maxFrequencies.any { it == null }) return CpuCoreTopology()
    val frequencies = maxFrequencies.filterNotNull()
    val highest = frequencies.maxOrNull() ?: return CpuCoreTopology()
    if (frequencies.distinct().size < 2) return CpuCoreTopology()

    // Clock bins are only a topology hint. This does not identify core microarchitecture or
    // guarantee that the OS will schedule inference on a particular cluster.
    val performanceThreshold = highest.toDouble() * 0.90
    val performanceCount = frequencies.count { it.toDouble() >= performanceThreshold }
    if (performanceCount !in 1 until totalCores) return CpuCoreTopology()

    return CpuCoreTopology(
      performanceCores = performanceCount,
      efficiencyCores = totalCores - performanceCount
    )
  }
}
