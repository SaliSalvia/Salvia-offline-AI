package com.example.core.model

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.files.FileInspector
import com.example.gguf.GgufParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class UniversalModelRegistry(private val context: Context) {

  private val _storedModels = MutableStateFlow<List<ModelMetadata>>(emptyList())
  val storedModels: StateFlow<List<ModelMetadata>> = _storedModels.asStateFlow()

  private val _loadedModels = MutableStateFlow<Map<ModelCapability, ModelMetadata>>(emptyMap())
  val loadedModels: StateFlow<Map<ModelCapability, ModelMetadata>> = _loadedModels.asStateFlow()

  init {
    initializeDefaultProfiles()
  }

  /**
   * Catalog of *suggested* models. Nothing here is installed on the device:
   * entries are recommendations the user can download elsewhere and import.
   * No fake "loaded" state and no invented benchmark numbers.
   */
  private fun initializeDefaultProfiles() {
    val presets = listOf(
      ModelMetadata(
        id = "preset_deepseek_r1_7b",
        fileName = "deepseek-r1-distill-qwen-7b.Q4_K_M.gguf",
        filePath = "",
        format = ModelFormat.GGUF,
        architecture = "qwen2",
        parameterCount = "7.6B",
        quantization = "Q4_K_M",
        contextLength = 4096,
        fileSizeFormatted = "4.35 GB",
        fileSizeBytes = 4670000000L,
        estimatedMemoryMb = 4900,
        capabilities = listOf(ModelCapability.TEXT, ModelCapability.CODE),
        compatibility = CompatibilityLevel.LIMITED,
        compatibilityReason = "پیشنهاد کاتالوگ: فایل روی دستگاه نیست. در صورت دانلود، برای رم‌های ۸ گیگابایتی با احتیاط و برای ۱۲ گیگابایتی مناسب است.",
        isLoaded = false,
        benchmarkTokPerSec = null
      ),
      ModelMetadata(
        id = "preset_qwen_3b",
        fileName = "qwen2.5-3b-instruct.Q4_K_M.gguf",
        filePath = "",
        format = ModelFormat.GGUF,
        architecture = "qwen2",
        parameterCount = "3.1B",
        quantization = "Q4_K_M",
        contextLength = 4096,
        fileSizeFormatted = "1.92 GB",
        fileSizeBytes = 2060000000L,
        estimatedMemoryMb = 2300,
        capabilities = listOf(ModelCapability.TEXT, ModelCapability.CODE),
        compatibility = CompatibilityLevel.READY,
        compatibilityReason = "پیشنهاد کاتالوگ: فایل روی دستگاه نیست. سبک‌ترین گزینهٔ پیشنهادی برای این گوشی؛ پس از دانلود و وارد کردن فایل GGUF اجرا می‌شود.",
        isLoaded = false,
        benchmarkTokPerSec = null
      ),
      ModelMetadata(
        id = "preset_vision_llama_32",
        fileName = "llama-3.2-11b-vision.Q4_0.gguf",
        filePath = "",
        format = ModelFormat.GGUF,
        architecture = "mllama",
        parameterCount = "11B",
        quantization = "Q4_0",
        contextLength = 2048,
        fileSizeFormatted = "6.80 GB",
        fileSizeBytes = 7300000000L,
        estimatedMemoryMb = 7500,
        capabilities = listOf(ModelCapability.VISION, ModelCapability.TEXT),
        compatibility = CompatibilityLevel.LIMITED,
        compatibilityReason = "پیشنهاد کاتالوگ: فایل روی دستگاه نیست. بخش متنی از طریق llama.cpp قابل اجراست؛ ورودی تصویر در این نسخه پشتیبانی نمی‌شود.",
        isLoaded = false,
        benchmarkTokPerSec = null
      ),
      ModelMetadata(
        id = "preset_sd_diffusion",
        fileName = "stable-diffusion-v1.5-mobile.mnn",
        filePath = "",
        format = ModelFormat.MNN,
        architecture = "diffusion",
        parameterCount = "860M",
        quantization = "FP16",
        contextLength = 77,
        fileSizeFormatted = "1.82 GB",
        fileSizeBytes = 1950000000L,
        estimatedMemoryMb = 2100,
        capabilities = listOf(ModelCapability.IMAGE_GENERATION),
        compatibility = CompatibilityLevel.UNSUPPORTED,
        compatibilityReason = "پیشنهاد کاتالوگ: موتور اجرای مدل‌های diffusion در این نسخه بسته‌بندی نشده است؛ فقط برای آشنایی فهرست شده.",
        isLoaded = false,
        benchmarkTokPerSec = null
      ),
      ModelMetadata(
        id = "preset_whisper_small",
        fileName = "whisper-small-q5_1.bin",
        filePath = "",
        format = ModelFormat.GGUF,
        architecture = "whisper",
        parameterCount = "244M",
        quantization = "Q5_1",
        contextLength = 448,
        fileSizeFormatted = "460 MB",
        fileSizeBytes = 482000000L,
        estimatedMemoryMb = 600,
        capabilities = listOf(ModelCapability.SPEECH_TO_TEXT),
        compatibility = CompatibilityLevel.REQUIRES_CONVERSION,
        compatibilityReason = "پیشنهاد کاتالوگ: موتور تبدیل صوت به متن در این نسخه اجرا نمی‌شود؛ فایلی بارگذاری نکنید.",
        isLoaded = false,
        benchmarkTokPerSec = null
      ),
      ModelMetadata(
        id = "preset_kokoro_tts",
        fileName = "kokoro-v0_19-mobile.onnx",
        filePath = "",
        format = ModelFormat.ONNX,
        architecture = "style_tts",
        parameterCount = "82M",
        quantization = "INT8",
        contextLength = 512,
        fileSizeFormatted = "95 MB",
        fileSizeBytes = 99000000L,
        estimatedMemoryMb = 180,
        capabilities = listOf(ModelCapability.TEXT_TO_SPEECH),
        compatibility = CompatibilityLevel.REQUIRES_CONVERSION,
        compatibilityReason = "پیشنهاد کاتالوگ: موتور تولید گفتار در این نسخه اجرا نمی‌شود؛ صدای خروجی فعلی از TTS سیستمی Android است.",
        isLoaded = false,
        benchmarkTokPerSec = null
      ),
      ModelMetadata(
        id = "preset_bge_m3_embedding",
        fileName = "bge-m3-multilingual.onnx",
        filePath = "",
        format = ModelFormat.ONNX,
        architecture = "bert",
        parameterCount = "130M",
        quantization = "INT8",
        contextLength = 8192,
        fileSizeFormatted = "280 MB",
        fileSizeBytes = 293000000L,
        estimatedMemoryMb = 350,
        capabilities = listOf(ModelCapability.EMBEDDING),
        compatibility = CompatibilityLevel.REQUIRES_CONVERSION,
        compatibilityReason = "پیشنهاد کاتالوگ: بازیابی RAG فعلی از بردار هش محلی استفاده می‌کند و به این مدل نیاز ندارد.",
        isLoaded = false,
        benchmarkTokPerSec = null
      )
    )

    _storedModels.value = presets
    _loadedModels.value = emptyMap()
  }

  suspend fun registerImportedFile(uri: Uri): ModelMetadata? = withContext(Dispatchers.IO) {
    var name = "unknown_model"
    var sizeBytes = 0L

    try {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
          if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
          if (sizeIdx != -1) sizeBytes = cursor.getLong(sizeIdx)
        }
      }
    } catch (_: Exception) {}

    val ext = name.substringAfterLast('.', "").lowercase()
    val sizeFormatted = FileInspector.formatBytes(sizeBytes)

    val format = when (ext) {
      "gguf" -> ModelFormat.GGUF
      "mnn" -> ModelFormat.MNN
      "onnx" -> ModelFormat.ONNX
      "tflite" -> ModelFormat.TFLITE
      "bin" -> if (name.contains("whisper")) ModelFormat.GGUF else ModelFormat.NCNN
      else -> ModelFormat.UNKNOWN
    }

    if (format == ModelFormat.UNKNOWN) {
      return@withContext null
    }

    // Safely parse GGUF metadata stream without loading whole file
    var arch = "arm64_neural"
    var quant = "Custom"
    var ctxLen = 4096
    if (format == ModelFormat.GGUF) {
      val parsed = GgufParser.parseFromUri(context, uri, sizeFormatted)
      if (parsed.isValid) {
        arch = parsed.architecture
        quant = parsed.quantizationType
        ctxLen = parsed.contextLength
      }
    }

    val estMemMb = maxOf(300, (sizeBytes / (1024 * 1024) * 1.15).toInt())
    val safety = evaluateMemorySafety(estMemMb)
    val compatLevel = when {
      safety == MemorySafetyStatus.CRITICAL -> CompatibilityLevel.UNSUPPORTED
      safety == MemorySafetyStatus.HIGH -> CompatibilityLevel.LIMITED
      else -> CompatibilityLevel.READY
    }

    val caps = when {
      name.contains("vision") || arch.contains("vision") || arch.contains("mllama") ->
        listOf(ModelCapability.VISION, ModelCapability.TEXT)
      name.contains("coder") || name.contains("deepseek-coder") ->
        listOf(ModelCapability.CODE, ModelCapability.TEXT)
      name.contains("diffusion") || format == ModelFormat.DIFFUSION || name.contains("sd") ->
        listOf(ModelCapability.IMAGE_GENERATION)
      name.contains("whisper") ->
        listOf(ModelCapability.SPEECH_TO_TEXT)
      name.contains("tts") || name.contains("kokoro") ->
        listOf(ModelCapability.TEXT_TO_SPEECH)
      name.contains("embed") || name.contains("bge") ->
        listOf(ModelCapability.EMBEDDING)
      else -> listOf(ModelCapability.TEXT)
    }

    val imported = ModelMetadata(
      id = "user_model_${System.currentTimeMillis()}",
      fileName = name,
      filePath = uri.toString(),
      format = format,
      architecture = arch,
      parameterCount = if (sizeBytes > 4000000000L) "7B" else if (sizeBytes > 1500000000L) "3B" else "Small",
      quantization = quant,
      contextLength = ctxLen,
      fileSizeFormatted = sizeFormatted,
      fileSizeBytes = sizeBytes,
      estimatedMemoryMb = estMemMb,
      capabilities = caps,
      compatibility = compatLevel,
      compatibilityReason = "ارزیابی بر اساس مشخصات حافظه گوشی (${safety.titleFa})",
      isLoaded = false
    )

    _storedModels.value = listOf(imported) + _storedModels.value
    imported
  }

  fun evaluateMemorySafety(modelMb: Int): MemorySafetyStatus {
    val actMan = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    actMan?.getMemoryInfo(memInfo)
    val availMb = (memInfo.availMem / (1024 * 1024)).toInt()

    return when {
      modelMb > availMb * 0.9 -> MemorySafetyStatus.CRITICAL
      modelMb > availMb * 0.75 -> MemorySafetyStatus.HIGH
      modelMb > availMb * 0.55 -> MemorySafetyStatus.WARNING
      else -> MemorySafetyStatus.SAFE
    }
  }

  suspend fun loadModel(modelId: String, capability: ModelCapability): Boolean = withContext(Dispatchers.IO) {
    val model = _storedModels.value.find { it.id == modelId } ?: return@withContext false
    // Catalog presets have no weights on this device; only imported files can load.
    if (model.filePath.isBlank() || !model.filePath.startsWith("content:")) {
      return@withContext false
    }
    val safety = evaluateMemorySafety(model.estimatedMemoryMb)
    if (!safety.isSafeToLoad) {
      return@withContext false
    }

    // Unload existing model on that capability slot if any
    val current = _loadedModels.value[capability]
    if (current != null) {
      unloadModel(current.id)
    }

    val updatedStored = _storedModels.value.map {
      if (it.id == modelId) it.copy(isLoaded = true) else it
    }
    _storedModels.value = updatedStored

    val newLoadedMap = _loadedModels.value.toMutableMap()
    newLoadedMap[capability] = model.copy(isLoaded = true)
    _loadedModels.value = newLoadedMap
    true
  }

  suspend fun unloadModel(modelId: String) = withContext(Dispatchers.IO) {
    val updatedStored = _storedModels.value.map {
      if (it.id == modelId) it.copy(isLoaded = false) else it
    }
    _storedModels.value = updatedStored

    val newLoadedMap = _loadedModels.value.toMutableMap()
    newLoadedMap.entries.removeIf { it.value.id == modelId }
    _loadedModels.value = newLoadedMap
  }

  fun getLoadedModelForCapability(capability: ModelCapability): ModelMetadata? {
    return _loadedModels.value[capability]
  }
}
