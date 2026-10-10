package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.core.localization.AppLanguage
import com.example.core.llm.GenerationParams
import com.example.core.llm.GenerationParamsStore
import com.example.core.llm.NativeLlamaBackend
import com.example.core.llm.RealLlmEngine
import com.example.core.model.CompatibilityLevel
import com.example.core.model.ModelCapability
import com.example.core.model.ModelMetadata
import com.example.core.model.UniversalModelRegistry
import com.example.core.performance.ContextOptimizer
import com.example.core.performance.DetailedDeviceProfile
import com.example.core.performance.HardwareProfiler
import com.example.core.performance.ModelLruCache
import com.example.core.performance.ResourceGuard
import com.example.core.performance.ResourceSnapshot
import com.example.core.performance.SustainableTuner
import com.example.core.performance.TokenBatcher
import com.example.core.rag.LocalRagEngine
import com.example.core.rag.RagIndexSummary
import com.example.core.rag.RetrievalResult
import com.example.core.router.AiRouter
import com.example.core.router.TargetEngine
import com.example.core.voice.AudioEngine
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import com.example.data.ChatSessionEntity
import com.example.files.AttachedFile
import com.example.files.FileInspector
import com.example.generator.GenerationProgress
import com.example.generator.OfflineImageEngine
import com.example.gguf.GgufInferenceEngine
import com.example.gguf.GgufMetadata
import com.example.gguf.GgufParser
import com.example.gguf.LoadedSlotInfo
import com.example.hardware.HardwareTelemetry
import com.example.hardware.PerformanceMode
import com.example.hardware.XiaomiOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen(val titleFa: String) {
  CHAT("گفت‌وگو"),
  MODELS("مدل‌ها"),
  FILES("اسناد و RAG"),
  IMAGE("تصویرساز"),
  VOICE("صوت و گفتار"),
  PERFORMANCE("سخت‌افزار"),
  SETTINGS("تنظیمات")
}

data class ChatUiState(
  val currentScreen: AppScreen = AppScreen.CHAT,
  val sessions: List<ChatSessionEntity> = emptyList(),
  val currentSession: ChatSessionEntity? = null,
  val messages: List<ChatMessageEntity> = emptyList(),
  val isStreaming: Boolean = false,
  val liveThinking: String = "",
  val liveAnswer: String = "",
  val liveThinkingDurationMs: Long = 0L,
  val liveTokensPerSecond: Float = 0f,
  val attachedFiles: List<AttachedFile> = emptyList(),
  val textSlotModel: LoadedSlotInfo = GgufInferenceEngine.DEFAULT_TEXT_SLOT,
  val imageSlotModel: LoadedSlotInfo = GgufInferenceEngine.DEFAULT_IMAGE_SLOT,
  val activeMode: String = "TEXT", // "TEXT" or "IMAGE"
  val performanceMode: PerformanceMode = PerformanceMode.HYPER_TURBO,
  val telemetry: HardwareTelemetry? = null,
  val deviceProfile: DetailedDeviceProfile? = null,
  val storedModels: List<ModelMetadata> = emptyList(),
  val loadedModelsMap: Map<ModelCapability, ModelMetadata> = emptyMap(),
  val isGeneratingImage: Boolean = false,
  val imageProgress: GenerationProgress? = null,
  val activeZipDetail: AttachedFile? = null,
  val showModelManager: Boolean = false,
  val showXiaomiTuning: Boolean = false,
  val showDrawer: Boolean = false,
  val statusNotice: String? = null,
  val ragSummary: RagIndexSummary = RagIndexSummary(0, 0, emptyList()),
  val ragResults: List<RetrievalResult> = emptyList(),
  val isSpeakingAudio: Boolean = false,
  val airplaneModeTestEnabled: Boolean = false,
  val userModeLevel: String = "BALANCED", // SIMPLE, BALANCED, ADVANCED
  val routerNotice: String? = null,
  val advisorInsights: List<String> = emptyList(),
  val activeContextTokens: Int = 4096,
  val resourceSnapshot: ResourceSnapshot? = null,
  val generationParams: GenerationParams = GenerationParams(),
  val appLanguage: AppLanguage = AppLanguage.FA
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val prefs = application.getSharedPreferences("deepgguf_prefs", Context.MODE_PRIVATE)
  private val db = AppDatabase.getDatabase(application)
  private val chatDao = db.chatDao()

  val modelRegistry = UniversalModelRegistry(application)
  val ragEngine = LocalRagEngine()
  val audioEngine = AudioEngine(application)
  val modelLruCache = ModelLruCache(maxLoadedModelsCount = 2, maxTotalMemoryMb = 8000)
  val resourceGuard = ResourceGuard(application, viewModelScope)

  // Real offline inference (llama.cpp). PSS is sampled around model loads so the
  // app can report honest memory numbers instead of guesses.
  val llmEngine = RealLlmEngine(NativeLlamaBackend(), pssReader = { android.os.Debug.getPss() })
  private val genParamsStore = GenerationParamsStore(prefs)

  private val _uiState = MutableStateFlow(ChatUiState())
  val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

  // Token Batcher with 30ms window to reduce UI recompositions and maintain silky 120Hz responsiveness
  private val tokenBatcher = TokenBatcher(viewModelScope, batchIntervalMs = 30L) { _, accumulated ->
    _uiState.value = _uiState.value.copy(
      liveAnswer = accumulated
    )
  }

  private var activeGenerationJob: Job? = null
  private var activeImageGenerationJob: Job? = null
  private var imageGenerationToken: Long = 0L
  private var messagesObservationJob: Job? = null

  init {
    val savedLang = prefs.getString("app_language", "FA") ?: "FA"
    val initialLang = if (savedLang == "EN") AppLanguage.EN else AppLanguage.FA
    _uiState.value = _uiState.value.copy(
      appLanguage = initialLang,
      generationParams = genParamsStore.load()
    )

    loadHardwareTelemetry()
    observeSessions()
    observeRegistry()
    setupResourceGuard()
  }

  private fun setupResourceGuard() {
    viewModelScope.launch {
      resourceGuard.snapshot.collectLatest { snap ->
        _uiState.value = _uiState.value.copy(resourceSnapshot = snap)
      }
    }

    resourceGuard.onPreventiveCleanupRequested = {
      viewModelScope.launch(Dispatchers.IO) {
        // 70–79% Caution range: Keep full speed, but drop unused models & trim caches in background
        modelLruCache.clear()
      }
    }

    resourceGuard.onEmergencyAiStopRequested = { reason ->
      viewModelScope.launch(Dispatchers.Main) {
        stopGeneration()
        _uiState.value = _uiState.value.copy(
          statusNotice = "AI Paused — Resource limit reached: $reason (در حال خنک‌سازی)"
        )
      }
    }

    resourceGuard.onAiResumeAllowed = {
      viewModelScope.launch(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(
          statusNotice = "منابع به زیر ۷۰٪ بازگشتند (Hysteresis Safe). هوش مصنوعی مجدداً آماده است."
        )
      }
    }
  }

  fun switchScreen(screen: AppScreen) {
    _uiState.value = _uiState.value.copy(currentScreen = screen)
  }

  fun loadHardwareTelemetry() {
    viewModelScope.launch(Dispatchers.IO) {
      val telem = XiaomiOptimizer.getTelemetry(getApplication())
      val profile = HardwareProfiler.profileDevice(getApplication())
      val insights = SustainableTuner.getAdvisorInsights(
        currentModelName = _uiState.value.textSlotModel.modelName,
        contextLength = _uiState.value.activeContextTokens,
        threads = XiaomiOptimizer.getOptimalThreadCount(_uiState.value.performanceMode),
        tempCelsius = telem.batteryTempCelsius,
        availRamGb = telem.availRamGb
      )
      withContext(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(
          telemetry = telem,
          deviceProfile = profile,
          advisorInsights = insights
        )
      }
    }
  }

  private fun observeRegistry() {
    viewModelScope.launch {
      modelRegistry.storedModels.collectLatest { list ->
        _uiState.value = _uiState.value.copy(storedModels = list)
      }
    }
    viewModelScope.launch {
      modelRegistry.loadedModels.collectLatest { map ->
        _uiState.value = _uiState.value.copy(loadedModelsMap = map)
      }
    }
  }

  private fun observeSessions() {
    viewModelScope.launch {
      chatDao.getAllSessions().collectLatest { sessionList ->
        if (sessionList.isEmpty()) {
          createNewSession("گفت‌وگوی آفلاین ۱")
        } else {
          val currentId = _uiState.value.currentSession?.id
          val selected = sessionList.find { it.id == currentId } ?: sessionList.first()
          _uiState.value = _uiState.value.copy(
            sessions = sessionList,
            currentSession = selected
          )
          observeMessagesForSession(selected.id)
        }
      }
    }
  }

  fun createNewSession(title: String = "گفت‌وگوی جدید") {
    viewModelScope.launch(Dispatchers.IO) {
      val newSession = ChatSessionEntity(
        title = title,
        activeModelName = _uiState.value.textSlotModel.modelName
      )
      val newId = chatDao.insertSession(newSession)
      val created = chatDao.getSessionById(newId)
      withContext(Dispatchers.Main) {
        if (created != null) {
          _uiState.value = _uiState.value.copy(currentSession = created, attachedFiles = emptyList())
          observeMessagesForSession(newId)
        }
      }
    }
  }

  fun selectSession(session: ChatSessionEntity) {
    _uiState.value = _uiState.value.copy(currentSession = session, attachedFiles = emptyList())
    observeMessagesForSession(session.id)
  }

  fun deleteSession(sessionId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      chatDao.deleteSession(sessionId)
    }
  }

  private fun observeMessagesForSession(sessionId: Long) {
    messagesObservationJob?.cancel()
    messagesObservationJob = viewModelScope.launch {
      chatDao.getMessagesForSession(sessionId).collectLatest { messageList ->
        _uiState.value = _uiState.value.copy(messages = messageList)
      }
    }
  }

  fun setPerformanceMode(mode: PerformanceMode) {
    // Never change the priority of the caller (usually the Android UI thread). The inference
    // worker applies its own conservative priority inside the worker context.
    _uiState.value = _uiState.value.copy(performanceMode = mode)
  }

  fun setActiveMode(mode: String) {
    _uiState.value = _uiState.value.copy(activeMode = mode)
  }

  fun setUserModeLevel(level: String) {
    _uiState.value = _uiState.value.copy(userModeLevel = level)
  }

  fun toggleAirplaneModeTest() {
    val current = _uiState.value.airplaneModeTestEnabled
    _uiState.value = _uiState.value.copy(
      airplaneModeTestEnabled = !current,
      statusNotice = if (!current) "نشانگر تست آفلاین فعال شد؛ این برنامه تنظیم شبکه یا حالت هواپیمای Android را تغییر نمی‌دهد." else "نشانگر تست آفلاین غیرفعال شد."
    )
  }

  fun attachFileUri(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      val inspected = FileInspector.inspectUri(getApplication(), uri)
      if (inspected != null) {
        withContext(Dispatchers.Main) {
          if (inspected.category == com.example.files.FileCategory.GGUF) {
            viewModelScope.launch {
              modelRegistry.registerImportedFile(uri)
            }
            loadGgufFromUri(uri, inspected.fileName, inspected.sizeFormatted)
          } else {
            // Precompute / Index in RAG in background immediately (Intelligent Prefetch)
            if (inspected.category == com.example.files.FileCategory.DOCUMENT ||
                inspected.category == com.example.files.FileCategory.CODE ||
                inspected.category == com.example.files.FileCategory.ZIP) {
              val sample = inspected.fullTextSample ?: inspected.extractedSummary
              ragEngine.indexDocument(inspected.fileName, sample)
              _uiState.value = _uiState.value.copy(ragSummary = ragEngine.getSummary())
            }

            _uiState.value = _uiState.value.copy(
              attachedFiles = _uiState.value.attachedFiles + inspected,
              statusNotice = "فایل ${inspected.fileName} نمایه شد و آماده تحلیل است."
            )
          }
        }
      }
    }
  }

  fun removeAttachedFile(file: AttachedFile) {
    _uiState.value = _uiState.value.copy(
      attachedFiles = _uiState.value.attachedFiles - file
    )
  }

  fun clearAttachedFiles() {
    _uiState.value = _uiState.value.copy(attachedFiles = emptyList())
  }

  fun inspectZipDetail(file: AttachedFile?) {
    _uiState.value = _uiState.value.copy(activeZipDetail = file)
  }

  fun toggleModelManager(show: Boolean) {
    _uiState.value = _uiState.value.copy(showModelManager = show)
  }

  fun toggleXiaomiTuning(show: Boolean) {
    _uiState.value = _uiState.value.copy(showXiaomiTuning = show)
  }

  fun toggleDrawer(show: Boolean) {
    _uiState.value = _uiState.value.copy(showDrawer = show)
  }

  fun dismissStatusNotice() {
    _uiState.value = _uiState.value.copy(statusNotice = null, routerNotice = null)
  }

  fun executeRagQuery(query: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val results = ragEngine.retrieve(query)
      withContext(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(ragResults = results)
      }
    }
  }

  fun clearRagIndex() {
    ragEngine.clearIndex()
    _uiState.value = _uiState.value.copy(
      ragSummary = ragEngine.getSummary(),
      ragResults = emptyList(),
      statusNotice = "حافظه وکتور محلی پاکسازی شد."
    )
  }

  fun speakMessageText(text: String) {
    _uiState.value = _uiState.value.copy(isSpeakingAudio = true)
    audioEngine.speakText(text)
  }

  fun stopAudioPlayback() {
    audioEngine.stopSpeech()
    _uiState.value = _uiState.value.copy(isSpeakingAudio = false)
  }

  fun loadModelInRegistry(modelId: String, capability: ModelCapability) {
    viewModelScope.launch(Dispatchers.IO) {
      val model = modelRegistry.storedModels.value.find { it.id == modelId }
      if (model != null) {
        val preflight = resourceGuard.canSafelyLoadModel(model.estimatedMemoryMb, 4096)
        if (!preflight.first) {
          withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(statusNotice = preflight.second)
          }
          return@launch
        }
      }

      val success = modelRegistry.loadModel(modelId, capability)
      withContext(Dispatchers.Main) {
        if (success) {
          modelLruCache.touch(model ?: return@withContext)
          _uiState.value = _uiState.value.copy(
            statusNotice = "مدل با موفقیت در رم بارگذاری و گرم (Keep-Warm) شد."
          )
        } else {
          _uiState.value = _uiState.value.copy(
            statusNotice = "بارگذاری ممکن نشد: فایل واقعی مدل روی دستگاه در دسترس نیست یا حافظهٔ امن کافی نیست."
          )
        }
      }
    }
  }

  fun unloadModelInRegistry(modelId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      modelRegistry.unloadModel(modelId)
      withContext(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(statusNotice = "مدل با موفقیت از رم خارج شد.")
      }
    }
  }

  fun loadGgufFromUri(uri: Uri, fileName: String, sizeFormatted: String, targetSlot: String = "TEXT") {
    viewModelScope.launch(Dispatchers.IO) {
      val modelSizeBytes = queryModelSizeBytes(uri) ?: parseFormattedSizeToBytes(sizeFormatted)
      if (modelSizeBytes == null) {
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(statusNotice = "اندازهٔ فایل مدل مشخص نیست؛ برای ایمنی بارگذاری نشد.")
        }
        return@launch
      }

      val mib = 1024L * 1024L
      val modelWeightsMbLong = modelSizeBytes / mib + (if (modelSizeBytes % mib == 0L) 0L else 1L)
      val modelWeightsMb = modelWeightsMbLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
      val preflight = resourceGuard.canSafelyLoadModel(
        modelWeightsMb = modelWeightsMb,
        contextTokens = _uiState.value.activeContextTokens.coerceAtLeast(1024)
      )
      if (!preflight.first) {
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(statusNotice = preflight.second)
        }
        return@launch
      }

      val parsed: GgufMetadata = GgufParser.parseFromUri(getApplication(), uri, sizeFormatted)
      if (!parsed.isValid) {
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(
            statusNotice = "فایل انتخاب‌شده GGUF معتبر نیست یا فرادادهٔ آن قابل‌خواندن نیست؛ مدل بارگذاری نشد."
          )
        }
        return@launch
      }

      if (targetSlot != "TEXT") {
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(
            statusNotice = "تولید تصویر پیکسلی در این نسخه پشتیبانی نمی‌شود (موتور diffusion صادقانه‌ای بسته‌بندی نشده است). فایل GGUF انتخاب‌شده یک مدل متنی است و از همین بخش قابل اجراست."
          )
        }
        return@launch
      }

      // Real load through llama.cpp. The descriptor is passed straight to native
      // code so multi-GB weights are memory-mapped, never copied.
      val pfd = try {
        getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")
      } catch (_: Exception) {
        null
      }
      if (pfd == null) {
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(
            statusNotice = "باز کردن فایل مدل ممکن نشد؛ مدل بارگذاری نشد."
          )
        }
        return@launch
      }

      withContext(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(statusNotice = "در حال بارگذاری وزن‌های مدل… (ممکن است چند ثانیه طول بکشد)")
      }

      val outcome = llmEngine.loadFromFileDescriptor(pfd.detachFd())

      when (outcome) {
        is RealLlmEngine.LoadOutcome.Success -> {
          val slot = LoadedSlotInfo(
            id = "slot_text",
            slotType = "TEXT",
            modelName = outcome.modelName,
            architecture = outcome.architecture.ifBlank { parsed.architecture },
            quantization = parsed.quantizationType,
            contextLength = if (outcome.contextTrain > 0) outcome.contextTrain else parsed.contextLength,
            sizeFormatted = sizeFormatted,
            uriString = uri.toString(),
            isLoaded = true
          )
          val memMb = if (outcome.memoryDeltaKb >= 0) outcome.memoryDeltaKb / 1024 else -1
          val memNote = if (memMb >= 0) " حافظهٔ اندازه‌گیری‌شدهٔ اپ بعد از بارگذاری: ${memMb} مگابایت." else ""
          withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
              textSlotModel = slot,
              statusNotice = "مدل «${slot.modelName}» بارگذاری شد و آمادهٔ استنتاج واقعی است.$memNote"
            )
          }
        }
        is RealLlmEngine.LoadOutcome.Failure -> {
          withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
              textSlotModel = GgufInferenceEngine.DEFAULT_TEXT_SLOT,
              statusNotice = "بارگذاری مدل ناموفق بود: ${outcome.reason}"
            )
          }
        }
      }
    }
  }

  private fun queryModelSizeBytes(uri: Uri): Long? = try {
    getApplication<Application>().contentResolver
      .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
      ?.use { cursor ->
        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst() && sizeColumn >= 0) cursor.getLong(sizeColumn).takeIf { it > 0L } else null
      }
  } catch (_: Exception) {
    null
  }

  private fun parseFormattedSizeToBytes(sizeFormatted: String): Long? {
    val match = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(B|KB|MB|GB|TB)", RegexOption.IGNORE_CASE)
      .find(sizeFormatted) ?: return null
    val value = match.groupValues[1].toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues[2].uppercase()) {
      "B" -> 1.0
      "KB" -> 1024.0
      "MB" -> 1024.0 * 1024.0
      "GB" -> 1024.0 * 1024.0 * 1024.0
      "TB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
      else -> return null
    }
    return (value * multiplier).toLong().takeIf { it > 0L }
  }

  fun setPresetTextModel(presetName: String, arch: String, quant: String, size: String) {
    // Catalog presets are recommendations only — no weights are bundled. Loading
    // a fake entry would claim inference that cannot happen.
    _uiState.value = _uiState.value.copy(
      statusNotice = "«$presetName» فقط یک پیشنهاد کاتالوگ است و فایل آن روی دستگاه نیست. برای اجرای واقعی، فایل GGUF مدل را از حافظهٔ گوشی وارد کنید."
    )
  }

  fun setPresetImageModel(presetName: String, quant: String, size: String) {
    _uiState.value = _uiState.value.copy(
      statusNotice = "«$presetName» در این نسخه قابل اجرا نیست: موتور تولید تصویر پیکسلی واقعی بسته‌بندی نشده است."
    )
  }

  fun sendMessage(promptText: String) {
    val session = _uiState.value.currentSession ?: return
    val trimmed = promptText.trim()
    val stagedFiles = _uiState.value.attachedFiles

    if (trimmed.isEmpty() && stagedFiles.isEmpty()) return

    // Reject new work while the measured memory / Android thermal guard is latched.
    val resourceSnapshot = _uiState.value.resourceSnapshot
    if (resourceSnapshot == null || !resourceSnapshot.isAiExecutionAllowed) {
      _uiState.value = _uiState.value.copy(
        statusNotice = "اجرای AI متوقف است: ${resourceSnapshot?.activeConstraintReason ?: "پایش اولیهٔ منابع هنوز آماده نیست"}. وضعیت دستگاه را بررسی کنید."
      )
      return
    }

    // 1. Adaptive Context calculation
    val adaptiveContext = ContextOptimizer.calculateOptimalContext(
      promptLength = trimmed.length,
      hasAttachedFiles = stagedFiles.isNotEmpty(),
      totalRamGb = _uiState.value.deviceProfile?.totalRamGb ?: 12f
    )
    _uiState.value = _uiState.value.copy(activeContextTokens = adaptiveContext)

    // 2. Run Intelligent Router
    val routed = AiRouter.routeRequest(trimmed, stagedFiles)
    _uiState.value = _uiState.value.copy(routerNotice = routed.explanationFa)

    if (routed.engine == TargetEngine.DIFFUSION_IMAGE) {
      activeGenerationJob?.cancel()
      executeOfflineImageGeneration(session.id, routed.sanitizedPrompt)
      return
    }

    if (routed.engine == TargetEngine.TEXT_TO_SPEECH) {
      speakMessageText(routed.sanitizedPrompt)
      return
    }

    cancelActiveImageGeneration()

    // 3. OPTIMISTIC UI: Instantly display the user message without waiting
    val userMsg = ChatMessageEntity(
      sessionId = session.id,
      role = "user",
      content = trimmed.ifEmpty { "بررسی پرونده‌های پیوست‌شده" },
      attachmentsJson = if (stagedFiles.isNotEmpty()) stagedFiles.joinToString(";") { "${it.category.name}:${it.fileName}:${it.sizeFormatted}" } else null
    )

    _uiState.value = _uiState.value.copy(
      messages = _uiState.value.messages + userMsg,
      attachedFiles = emptyList(),
      isStreaming = true,
      liveThinking = "",
      liveAnswer = "",
      liveThinkingDurationMs = 0L,
      liveTokensPerSecond = 0f
    )

    tokenBatcher.reset()

    // 4. Real offline inference (llama.cpp) — history-aware, streamed, cancellable.
    // The optimistic user message is already in state; history excludes it.
    val priorMessages = _uiState.value.messages.dropLast(1)
    val userContent = buildUserContentWithAttachments(trimmed, stagedFiles)

    viewModelScope.launch(Dispatchers.IO) {
      chatDao.insertMessage(userMsg)

      activeGenerationJob?.cancel()
      activeGenerationJob = viewModelScope.launch(Dispatchers.IO) {
        if (!llmEngine.isLoaded) {
          withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
              isStreaming = false,
              statusNotice = "هیچ مدل متنی واقعی بارگذاری نشده است. از بخش «مدل‌ها» یک فایل GGUF وارد کنید تا استنتاج آفلاین واقعی فعال شود."
            )
          }
          return@launch
        }

        XiaomiOptimizer.applyThreadPriority(_uiState.value.performanceMode)
        val history = buildChatHistory(priorMessages, userContent)

        val outcome = llmEngine.generate(history, _uiState.value.generationParams) { delta ->
          tokenBatcher.appendToken(delta)
        }

        when (outcome) {
          is RealLlmEngine.GenerateOutcome.Failure -> {
            tokenBatcher.reset()
            withContext(Dispatchers.Main) {
              _uiState.value = _uiState.value.copy(
                isStreaming = false,
                statusNotice = "استنتاج ناموفق بود: ${outcome.reason}"
              )
            }
          }
          else -> {
            val finalFullAnswer = outcome.let {
              when (it) {
                is RealLlmEngine.GenerateOutcome.Completed -> it.text
                is RealLlmEngine.GenerateOutcome.Cancelled -> it.text
                else -> ""
              }
            }
            val finalTps = (outcome as? RealLlmEngine.GenerateOutcome.Completed)?.tokensPerSecond ?: 0f
            val wasCancelled = outcome is RealLlmEngine.GenerateOutcome.Cancelled

            tokenBatcher.flushNow()
            val answerText = finalFullAnswer.ifEmpty { tokenBatcher.getAccumulated() }

            val assistantMsg = ChatMessageEntity(
              sessionId = session.id,
              role = "assistant",
              content = if (wasCancelled) "$answerText\n\n(تولید توسط کاربر متوقف شد)" else answerText,
              thinkingContent = null,
              thinkingDurationMs = 0L,
              tokensPerSecond = finalTps
            )
            chatDao.insertMessage(assistantMsg)

            if (session.title == "گفت‌وگوی آفلاین ۱" || session.title == "گفت‌وگوی جدید") {
              val newTitle = trimmed.take(24).ifEmpty { "تحلیل محلی" }
              chatDao.updateSession(session.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
            }

            withContext(Dispatchers.Main) {
              _uiState.value = _uiState.value.copy(
                isStreaming = false,
                liveThinking = "",
                liveAnswer = "",
                liveTokensPerSecond = finalTps
              )
            }
          }
        }
      }
    }
  }

  /**
   * Builds the model-facing user message: the prompt plus bounded excerpts of
   * any attached files, so the real offline model can actually reason over them.
   */
  private fun buildUserContentWithAttachments(prompt: String, stagedFiles: List<AttachedFile>): String {
    if (stagedFiles.isEmpty()) return prompt
    val sb = StringBuilder(prompt)
    for (f in stagedFiles) {
      sb.append("\n\n[پیوست: ").append(f.fileName).append(" — ").append(f.sizeFormatted).append("]\n")
      val body = f.fullTextSample?.take(6000)?.takeIf { it.isNotBlank() }
        ?: f.extractedSummary.take(2000)
      sb.append(body.ifBlank { "(محتوای متنی قابل استخراج نبود)" })
    }
    return sb.toString()
  }

  /**
   * Maps persisted chat messages to the model's chat template input. History is
   * bounded (recent turns, capped characters) to stay inside the context window.
   */
  private fun buildChatHistory(
    priorMessages: List<ChatMessageEntity>,
    userContent: String
  ): List<RealLlmEngine.ChatTurn> {
    val turns = ArrayList<RealLlmEngine.ChatTurn>(priorMessages.size + 1)
    for (m in priorMessages.takeLast(12)) {
      when (m.role) {
        "user" -> turns.add(RealLlmEngine.ChatTurn("user", m.content.take(4000)))
        "assistant" -> turns.add(RealLlmEngine.ChatTurn("assistant", m.content.take(4000)))
      }
    }
    turns.add(RealLlmEngine.ChatTurn("user", userContent))
    return turns
  }

  fun executeOfflineImageGeneration(sessionId: Long, promptText: String) {
    val guardSnapshot = _uiState.value.resourceSnapshot
    if (guardSnapshot == null || !guardSnapshot.isAiExecutionAllowed) {
      _uiState.update { current ->
        current.copy(
          statusNotice = "اجرای تصویر متوقف است: ${guardSnapshot?.activeConstraintReason ?: "پایش اولیهٔ منابع هنوز آماده نیست"}. وضعیت دستگاه را بررسی کنید."
        )
      }
      return
    }
    cancelActiveImageGeneration()
    val generationId = ++imageGenerationToken
    activeImageGenerationJob = viewModelScope.launch {
      try {
        val userMsg = ChatMessageEntity(
          sessionId = sessionId,
          role = "user",
          content = "🎨 [تولید تصویر آفلاین]: $promptText"
        )
        _uiState.value = _uiState.value.copy(
          messages = _uiState.value.messages + userMsg,
          isGeneratingImage = true,
          attachedFiles = emptyList()
        )

        withContext(Dispatchers.IO) {
          chatDao.insertMessage(userMsg)
        }

        val imagePath = withContext(Dispatchers.IO) {
          OfflineImageEngine.generateArtworkOffline(
            context = getApplication(),
            prompt = promptText,
            artStyle = "Cyberpunk Dark Pink",
            steps = 15
          ) { progress ->
            if (generationId == imageGenerationToken) {
              _uiState.update { current -> current.copy(imageProgress = progress) }
            }
          }
        }

        val assistantMsg = ChatMessageEntity(
          sessionId = sessionId,
          role = "assistant",
          content = "تصویر محلی بر اساس پرامپت شما آماده شد:\n\n> \"$promptText\"",
          thinkingContent = "خروجی توسط مسیر ترسیم محلی برنامه ساخته شد؛ اندازه‌گیری مصرف runtime مدل در دسترس نیست.",
          imageResultUri = imagePath
        )

        withContext(Dispatchers.IO) {
          chatDao.insertMessage(assistantMsg)
        }
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        _uiState.update { current ->
          current.copy(statusNotice = "تولید تصویر ناموفق بود: ${error.localizedMessage ?: "خطای ناشناخته"}")
        }
      } finally {
        if (generationId == imageGenerationToken) {
          activeImageGenerationJob = null
          _uiState.update { current -> current.copy(isGeneratingImage = false, imageProgress = null) }
        }
      }
    }
  }

  private fun cancelActiveImageGeneration() {
    imageGenerationToken++
    activeImageGenerationJob?.cancel()
    activeImageGenerationJob = null
    _uiState.update { current -> current.copy(isGeneratingImage = false, imageProgress = null) }
  }

  fun stopGeneration() {
    llmEngine.cancel()
    activeGenerationJob?.cancel()
    activeGenerationJob = null
    cancelActiveImageGeneration()
    tokenBatcher.reset()
    _uiState.update { current ->
      current.copy(
        isStreaming = false,
        isGeneratingImage = false,
        imageProgress = null,
        statusNotice = "تولید جاری متوقف شد."
      )
    }
  }

  fun setLanguage(lang: AppLanguage) {
    prefs.edit().putString("app_language", lang.name).apply()
    _uiState.value = _uiState.value.copy(appLanguage = lang)
  }

  // --- Generation parameters (persisted, hardware-safe ranges) --------------

  fun updateGenerationParams(params: GenerationParams) {
    val sanitized = params.sanitized()
    genParamsStore.save(sanitized)
    _uiState.value = _uiState.value.copy(generationParams = sanitized)
  }

  fun resetGenerationParams() {
    genParamsStore.reset()
    _uiState.value = _uiState.value.copy(generationParams = GenerationParams())
    _uiState.value = _uiState.value.copy(statusNotice = "پارامترهای تولید به حالت امن پیش‌فرض بازگشتند.")
  }

  fun toggleLanguage() {
    val next = if (_uiState.value.appLanguage == AppLanguage.FA) AppLanguage.EN else AppLanguage.FA
    setLanguage(next)
  }
}
