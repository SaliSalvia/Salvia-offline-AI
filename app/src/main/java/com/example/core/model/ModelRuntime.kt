package com.example.core.model

enum class ModelFormat(val displayName: String, val defaultExtension: String) {
  GGUF("GGUF (llama.cpp)", "gguf"),
  MNN("MNN Mobile", "mnn"),
  ONNX("ONNX Runtime", "onnx"),
  TFLITE("LiteRT / TFLite", "tflite"),
  NCNN("NCNN Vulkan", "bin"),
  DIFFUSION("Diffusion Package", "diff"),
  UNKNOWN("ناشناخته", "")
}

enum class ModelCapability(val labelFa: String, val icon: String) {
  TEXT("مدل متنی", "chat"),
  CODE("دستیار کدنویسی", "code"),
  VISION("بینایی چندوجهی", "visibility"),
  IMAGE_GENERATION("تولید تصویر", "brush"),
  SPEECH_TO_TEXT("تبدیل صوت به متن", "mic"),
  TEXT_TO_SPEECH("تبدیل متن به صوت", "volume_up"),
  OCR("تشخیص متن از تصویر", "document_scanner"),
  EMBEDDING("تولید بردار امبدینگ", "scatter_plot"),
  RERANKER("بازرتبه‌بندی محتوا", "sort")
}

enum class CompatibilityLevel(val titleFa: String, val colorHex: Long) {
  READY("سازگار و آماده اجرا ✅", 0xFF00E676),
  LIMITED("سازگار با محدودیت حافظه ⚠️", 0xFFFFB300),
  REQUIRES_CONVERSION("نیازمند تبدیل فرمت 🔄", 0xFF00E5FF),
  UNSUPPORTED("پشتیبانی نمی‌شود ❌", 0xFFFF1744)
}

enum class MemorySafetyStatus(val titleFa: String, val isSafeToLoad: Boolean) {
  SAFE("حافظه کاملاً امن (سبز)", true),
  WARNING("هشدار: اشغال بخش عمده رم", true),
  HIGH("خطر OOM: حافظه رم ناکافی است", false),
  CRITICAL("بسیار بحرانی: امکان کرش سیستم وجود دارد", false)
}

data class ModelMetadata(
  val id: String,
  val fileName: String,
  val filePath: String,
  val format: ModelFormat,
  val architecture: String,
  val parameterCount: String,
  val quantization: String,
  val contextLength: Int,
  val fileSizeFormatted: String,
  val fileSizeBytes: Long,
  val estimatedMemoryMb: Int,
  val capabilities: List<ModelCapability>,
  val compatibility: CompatibilityLevel,
  val compatibilityReason: String,
  val isLoaded: Boolean = false,
  val isFavorite: Boolean = false,
  val benchmarkTokPerSec: Float? = null
)

data class InferenceChunk(
  val deltaText: String,
  val isReasoning: Boolean,
  val currentTokensPerSec: Float,
  val elapsedMs: Long,
  val isCompleted: Boolean
)

interface ModelAdapter {
  val adapterName: String
  val supportedFormats: List<ModelFormat>

  fun isSupported(metadata: ModelMetadata): Boolean
  suspend fun estimateMemory(metadata: ModelMetadata): Int
  suspend fun load(metadata: ModelMetadata, contextLength: Int, threads: Int): Boolean
  suspend fun unload(metadata: ModelMetadata)
  suspend fun benchmark(metadata: ModelMetadata): Float
}
