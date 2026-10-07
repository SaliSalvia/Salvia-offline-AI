package com.example.gguf

data class GgufMetadata(
  val isValid: Boolean,
  val version: Int = 0,
  val tensorCount: Long = 0L,
  val kvCount: Long = 0L,
  val architecture: String = "unknown",
  val modelName: String = "Unknown GGUF",
  val quantizationType: String = "Unknown",
  val contextLength: Int = 4096,
  val embeddingLength: Int = 4096,
  val blockCount: Int = 28,
  val headCount: Int = 16,
  val fileSizeFormatted: String = "0 MB",
  val rawKv: Map<String, String> = emptyMap(),
  val errorMessage: String? = null
)

enum class QuantType(val displayName: String, val ramRequirementGb: Double) {
  Q2_K("Q2_K (Ultra Low RAM)", 2.2),
  Q3_K_M("Q3_K_M (3-bit Medium)", 3.1),
  Q4_0("Q4_0 (Standard 4-bit)", 3.8),
  Q4_K_M("Q4_K_M (Recommended Balanced)", 4.2),
  Q5_K_M("Q5_K_M (High Fidelity 5-bit)", 5.1),
  Q6_K("Q6_K (Near Lossless 6-bit)", 6.0),
  Q8_0("Q8_0 (8-bit High Quality)", 7.8),
  F16("F16 (Half Precision Float)", 14.2),
  UNKNOWN("Custom Quantization", 4.0)
}
