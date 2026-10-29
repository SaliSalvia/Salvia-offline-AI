package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.core.localization.AppLanguage
import com.example.core.asr.NativeAsrBackend
import com.example.core.asr.RealAsrEngine
import com.example.core.llm.ConversationCompactor
import com.example.core.llm.GenerationParams
import com.example.core.llm.GenerationParamsStore
import com.example.core.llm.NativeLlamaBackend
import com.example.core.llm.RealLlmEngine
import com.example.core.model.CompatibilityLevel
import com.example.core.model.ModelCapability
import com.example.core.model.ModelMetadata
import com.example.core.model.UniversalModelRegistry
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
import com.example.core.voice.MicRecorder
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import com.example.data.ChatSessionEntity
import com.example.files.AttachedFile
import com.example.files.FileInspector
import com.example.core.voice.PcmDecoder
import com.example.generator.GenerationProgress
import com.example.generator.OfflineImageEngine
import com.example.gguf.GgufInferenceEngine
import com.example.gguf.GgufMetadata
import com.example.gguf.GgufParser
import com.example.gguf.LoadedSlotInfo
import com.example.hardware.DeviceTuningProfile
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

/**
 * Fast-moving token stream, deliberately kept out of [ChatUiState].
 *
 * The whole chat screen used to be recomposed for every 30 ms token batch because
 * the live answer lived in the screen-wide state. Keeping it in its own flow lets
 * only the small streaming message observe tokens, so the app bar, drawer, tabs,
 * input bar and already-rendered messages stay idle while a model is generating.
 */
data class LiveStreamState(
  val thinking: String = "",
  val answer: String = "",
  val thinkingDurationMs: Long = 0L,
  val tokensPerSecond: Float = 0f
)

data class ChatUiState(
  val currentScreen: AppScreen = AppScreen.CHAT,
  val sessions: List<ChatSessionEntity> = emptyList(),
  val currentSession: ChatSessionEntity? = null,
  val messages: List<ChatMessageEntity> = emptyList(),
  val isStreaming: Boolean = false,
  val attachedFiles: List<AttachedFile> = emptyList(),
  val textSlotModel: LoadedSlotInfo = GgufInferenceEngine.DEFAULT_TEXT_SLOT,
  val imageSlotModel: LoadedSlotInfo = GgufInferenceEngine.DEFAULT_IMAGE_SLOT,
  val activeMode: String = "TEXT", // "TEXT" or "IMAGE"
  val performanceMode: PerformanceMode = PerformanceMode.HYPER_TURBO,
  val telemetry: HardwareTelemetry? = null,
  val deviceProfile: DetailedDeviceProfile? = null,
  val deviceTuning: DeviceTuningProfile? = null,
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
  val isTranscribing: Boolean = false,
  val isRecordingMic: Boolean = false,
  val asrModelLabel: String? = null,
  val lastTranscription: String? = null,
  val modelLoadPercent: Int? = null,
  val routerNotice: String? = null,
  val advisorInsights: List<String> = emptyList(),
  val activeContextTokens: Int = GenerationParams.DEFAULT_CONTEXT_TOKENS,
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
  // Real offline speech-to-text (whisper.cpp).
  val asrEngine = RealAsrEngine(NativeAsrBackend(), pssReader = { android.os.Debug.getPss() })
  private val genParamsStore = GenerationParamsStore(prefs)
  private val micRecorder = MicRecorder()

  // Running per-session summaries produced by long-conversation compaction.
  private val compactionSummaries = mutableMapOf<Long, String>()

  private val _uiState = MutableStateFlow(ChatUiState())
  val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

  private val _liveStream = MutableStateFlow(LiveStreamState())
  val liveStream: StateFlow<LiveStreamState> = _liveStream.asStateFlow()

  // Token Batcher with 30ms window to reduce UI recompositions and maintain silky 120Hz responsiveness
  private val tokenBatcher = TokenBatcher(viewModelScope, batchIntervalMs = 30L) { _, accumulated ->
    _liveStream.update { it.copy(answer = accumulated) }
  }

  // Same batching for the reasoning stream of thinking models.
  private val thinkingBatcher = TokenBatcher(viewModelScope, batchIntervalMs = 60L) { _, accumulated ->
    _liveStream.update { it.copy(thinking = accumulated) }
  }

  private var activeGenerationJob: Job? = null
  private var activeImageGenerationJob: Job? = null
  private var imageGenerationToken: Long = 0L
  private var messagesObservationJob: Job? = null

  init {
    val savedLang = prefs.getString("app_language", "FA") ?: "FA"
    val initialLang = if (savedLang == "EN") AppLanguage.EN else AppLanguage.FA
    val initialParams = genParamsStore.load()
    _uiState.value = _uiState.value.copy(
      appLanguage = initialLang,
      generationParams = initialParams,
      activeContextTokens = initialParams.contextTokens
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
        // Caution range: keep full speed, but drop unused models and trim caches in background.
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
          statusNotice = "حافظهٔ آزاد سیستم دوباره کافی شد؛ هوش مصنوعی آماده است."
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
      val mode = _uiState.value.performanceMode
      val tuning = XiaomiOptimizer.getDeviceTuningProfile(mode)
      val insights = SustainableTuner.getAdvisorInsights(
        currentModelName = _uiState.value.textSlotModel.modelName,
        contextLength = _uiState.value.activeContextTokens,
        threads = _uiState.value.generationParams.threads,
        tempCelsius = telem.batteryTempCelsius,
        availRamGb = telem.availRamGb
      )
      withContext(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(
          telemetry = telem,
          deviceProfile = profile,
          deviceTuning = tuning,
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
    // The selected profile now changes the real llama.cpp worker count instead of only
    // relabeling the UI. Generation and prompt-ingestion counts are kept separate.
    viewModelScope.launch(Dispatchers.IO) {
      val tuning = XiaomiOptimizer.getDeviceTuningProfile(mode)
      val updatedParams = _uiState.value.generationParams
        .copy(threads = tuning.generationThreads)
        .sanitized()
      genParamsStore.save(updatedParams)
      withContext(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(
          performanceMode = mode,
          generationParams = updatedParams,
          deviceTuning = tuning,
          statusNotice = "پروفایل ${mode.title}: ${tuning.generationThreads} نخ تولید و ${tuning.promptThreads} نخ پردازش پرامپت برای اجرای بعدی اعمال شد."
        )
      }
    }
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
      val inspected = FileInspector.inspectUri(getApplication(), uri) ?: return@launch

      unsupportedModelMessage(inspected.fileName)?.let { message ->
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(statusNotice = message)
        }
        return@launch
      }

      if (inspected.category == com.example.files.FileCategory.GGUF) {
        // Register first so the Models tab and the native loader share one identity.
        modelRegistry.registerImportedFile(uri)
        loadGgufFromUri(uri, inspected.fileName, inspected.sizeFormatted)
        return@launch
      }

      // Precompute / index text, code and ZIP documents before the user sends a prompt.
      if (inspected.category == com.example.files.FileCategory.DOCUMENT ||
          inspected.category == com.example.files.FileCategory.CODE ||
          inspected.category == com.example.files.FileCategory.ZIP) {
        val sample = inspected.fullTextSample ?: inspected.extractedSummary
        ragEngine.indexDocument(inspected.fileName, sample)
      }

      withContext(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(
          attachedFiles = _uiState.value.attachedFiles + inspected,
          ragSummary = ragEngine.getSummary(),
          statusNotice = "فایل ${inspected.fileName} نمایه شد و آماده تحلیل است."
        )
      }
    }
  }

  /** Clear rejection instead of silently attaching a model format this build cannot execute. */
  private fun unsupportedModelMessage(fileName: String): String? {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
      "safetensors", "ckpt", "pt", "pth", "vae", "sft" ->
        "این فایل یک مدل diffusion (SDXL/Flux) است. llama.cpp آن را اجرا نمی‌کند و Sali-Localo در این نسخه فقط GGUF متنی و Whisper را اجرا می‌کند."
      "rar", "7z" ->
        "آرشیو RAR/7Z پشتیبانی نمی‌شود. فقط ZIP قابل بررسی است؛ برای مدل، فایل GGUF را مستقیم انتخاب کنید."
      "onnx", "mnn", "tflite" ->
        "این فرمت مدل در این نسخه runtime ندارد. فقط GGUF (llama.cpp) و Whisper GGUF اجرا می‌شوند."
      else -> null
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
    audioEngine.speakText(text) {
      // Posted on the main looper by AudioEngine once speech really ends,
      // fails, or is stopped — the button state can never get stuck again.
      _uiState.update { it.copy(isSpeakingAudio = false) }
    }
  }

  fun stopAudioPlayback() {
    audioEngine.stopSpeech()
    _uiState.value = _uiState.value.copy(isSpeakingAudio = false)
  }

  fun loadModelInRegistry(modelId: String, capability: ModelCapability) {
    val model = modelRegistry.storedModels.value.find { it.id == modelId }
    if (capability != ModelCapability.TEXT && capability != ModelCapability.CODE) {
      _uiState.value = _uiState.value.copy(
        statusNotice = "در این نسخه فقط استنتاج متنی GGUF (llama.cpp) فعال است؛ مدل تصویری/چندوجهی بارگذاری نمی‌شود."
      )
      return
    }
    if (model == null || !model.filePath.startsWith("content:")) {
      _uiState.value = _uiState.value.copy(
        statusNotice = "این مورد فقط یک پیشنهاد کاتالوگ است و فایل GGUF آن روی دستگاه نیست. ابتدا فایل را از حافظهٔ گوشی وارد کنید."
      )
      return
    }
    // The button used to flip an isLoaded flag without touching llama.cpp. Route it
    // through the exact same native loader that the file picker uses.
    loadGgufFromUri(
      uri = Uri.parse(model.filePath),
      fileName = model.fileName,
      sizeFormatted = model.fileSizeFormatted,
      targetSlot = "TEXT"
    )
  }

  fun unloadModelInRegistry(modelId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val wasActive = _uiState.value.textSlotModel.uriString?.let { uri ->
        modelRegistry.storedModels.value.find { it.id == modelId }?.filePath == uri
      } == true
      if (!wasActive) {
        // A non-active entry in the list must never tear down the model that is
        // currently serving chat; just clear its bookkeeping flag.
        modelRegistry.unloadModel(modelId)
        return@launch
      }
      // Native unload waits for the generation mutex; cancel first so the UI is not
      // left looking frozen while a long reply finishes.
      stopGeneration(showNotice = false)
      llmEngine.unload()
      modelRegistry.unloadModel(modelId)
      withContext(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(
          textSlotModel = GgufInferenceEngine.DEFAULT_TEXT_SLOT,
          statusNotice = "مدل از حافظه خارج شد و منابع آزاد شدند."
        )
      }
    }
  }

  fun loadGgufFromUri(uri: Uri, fileName: String, sizeFormatted: String, targetSlot: String = "TEXT") {
    viewModelScope.launch(Dispatchers.IO) {
      if (targetSlot != "TEXT") {
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(
            statusNotice = "تولید تصویر پیکسلی در این نسخه پشتیبانی نمی‌شود (موتور diffusion بسته‌بندی نشده است). فایل GGUF متنی را از بخش گفت‌وگو یا مدل‌ها بارگذاری کنید."
          )
        }
        return@launch
      }

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
      val actualSizeFormatted = FileInspector.formatBytes(modelSizeBytes)
      val requestedContext = _uiState.value.generationParams.contextTokens
      val loadedModelMb = if (llmEngine.isLoaded) {
        (parseFormattedSizeToBytes(_uiState.value.textSlotModel.sizeFormatted) ?: 0L)
          .div(mib)
          .coerceAtMost(Int.MAX_VALUE.toLong())
          .toInt()
      } else {
        0
      }
      val preflight = resourceGuard.canSafelyLoadModel(
        modelWeightsMb = modelWeightsMb,
        contextTokens = requestedContext,
        weightsAreMmap = true,
        loadedModelWeightsMb = loadedModelMb
      )
      if (!preflight.first) {
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(statusNotice = preflight.second)
        }
        return@launch
      }

      val parsed: GgufMetadata = GgufParser.parseFromUri(getApplication(), uri, actualSizeFormatted)
      if (!parsed.isValid) {
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(
            statusNotice = "فایل انتخاب‌شده GGUF معتبر نیست یا فرادادهٔ آن قابل‌خواندن نیست؛ مدل بارگذاری نشد."
          )
        }
        return@launch
      }

      // Stop any in-flight generation and release the old mapping before asking
      // llama.cpp to load the replacement. This keeps peak RAM to one model and
      // avoids waiting on the generation mutex behind a running answer.
      stopGeneration(showNotice = false)
      if (llmEngine.isLoaded) {
        llmEngine.unload()
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
        _uiState.value = _uiState.value.copy(
          modelLoadPercent = 0,
          statusNotice = "در حال بارگذاری ${parsed.modelName} (${actualSizeFormatted})…"
        )
      }

      val fd = try {
        pfd.detachFd()
      } catch (t: Throwable) {
        pfd.close()
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(
            modelLoadPercent = null,
            statusNotice = "دسترسی به فایل مدل ممکن نشد: ${t.message ?: "خطای ناشناخته"}"
          )
        }
        return@launch
      }

      val outcome = llmEngine.loadFromFileDescriptor(fd) { percent ->
        // Keep progress in the dedicated progress bar; do not spam the snackbar with
        // a new status notice every 10%.
        _uiState.update { it.copy(modelLoadPercent = percent.toInt().coerceIn(0, 100)) }
      }

      when (outcome) {
        is RealLlmEngine.LoadOutcome.Success -> {
          // Never advertise the model's training window as the runtime context: the
          // user-selected (and memory-checked) value is what llama.cpp actually allocates.
          val effectiveContext = if (outcome.contextTrain in 1 until requestedContext) {
            outcome.contextTrain
          } else {
            requestedContext
          }
          val updatedParams = _uiState.value.generationParams.copy(contextTokens = effectiveContext).sanitized()
          genParamsStore.save(updatedParams)

          val slot = LoadedSlotInfo(
            id = "slot_text",
            slotType = "TEXT",
            modelName = outcome.modelName,
            architecture = outcome.architecture.ifBlank { parsed.architecture },
            quantization = parsed.quantizationType,
            contextLength = if (outcome.contextTrain > 0) outcome.contextTrain else parsed.contextLength,
            sizeFormatted = actualSizeFormatted,
            uriString = uri.toString(),
            isLoaded = true
          )
          modelRegistry.markModelLoadedByUri(uri.toString(), ModelCapability.TEXT)

          val memMb = if (outcome.memoryDeltaKb >= 0) outcome.memoryDeltaKb / 1024 else -1
          val memNote = if (memMb >= 0) " PSS اندازه‌گیری‌شدهٔ اپ: ${memMb}MB." else ""
          val contextNote = if (effectiveContext != requestedContext) {
            " context به ${effectiveContext} توکن (محدودیت مدل) کاهش یافت."
          } else {
            ""
          }
          val warningNote = preflight.second.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
          withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
              textSlotModel = slot,
              activeContextTokens = effectiveContext,
              generationParams = updatedParams,
              modelLoadPercent = null,
              statusNotice = "مدل «${slot.modelName}» بارگذاری شد و آمادهٔ استنتاج است.$memNote$contextNote$warningNote"
            )
          }
        }
        is RealLlmEngine.LoadOutcome.Failure -> {
          modelRegistry.storedModels.value.filter { it.isLoaded }.forEach { modelRegistry.unloadModel(it.id) }
          withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
              textSlotModel = GgufInferenceEngine.DEFAULT_TEXT_SLOT,
              modelLoadPercent = null,
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

  fun rejectImageModelImport() {
    _uiState.value = _uiState.value.copy(
      statusNotice = "مدل تصویری/diffusion در این نسخه اجرا نمی‌شود. Sali-Localo فقط GGUF متنی را با llama.cpp اجرا می‌کند؛ فایل SDXL/Flux (safetensors/ckpt) قابل بارگذاری نیست."
    )
  }

  fun sendMessage(promptText: String) {
    val session = _uiState.value.currentSession ?: return
    if (activeGenerationJob?.isActive == true || _uiState.value.isStreaming || _uiState.value.isGeneratingImage) {
      _uiState.value = _uiState.value.copy(
        statusNotice = "یک تولید در حال اجراست؛ ابتدا آن را متوقف کنید یا پاسخ را کامل کنید."
      )
      return
    }
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

    // 1. Use the context llama.cpp actually allocated for this model. The old
    // ContextOptimizer value was displayed in the UI but never passed to native code.
    val requestedParams = _uiState.value.generationParams
    val loadedContext = _uiState.value.textSlotModel.contextLength
    val effectiveContext = if (loadedContext in 1 until requestedParams.contextTokens) {
      loadedContext
    } else {
      requestedParams.contextTokens
    }
    val effectiveParams = requestedParams.copy(contextTokens = effectiveContext).sanitized()
    _uiState.value = _uiState.value.copy(activeContextTokens = effectiveParams.contextTokens)

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
      isStreaming = true
    )

    tokenBatcher.reset()
    thinkingBatcher.reset()
    _liveStream.value = LiveStreamState()

    // 4. Real offline inference (llama.cpp) — history-aware, streamed, cancellable.
    // The optimistic user message is already in state; history excludes it.
    val priorMessages = _uiState.value.messages.dropLast(1)

    viewModelScope.launch(Dispatchers.IO) {
      // BM25 scores are corpus-relative and unbounded (unlike the old cosine), so a fixed
      // cutoff is meaningless. Keep hits meaningfully close to the best hit instead.
      val retrievedHits = ragEngine.retrieve(trimmed, topK = 3)
      val bestHitScore = retrievedHits.maxOfOrNull { it.similarityScore } ?: 0f
      val ragHits = retrievedHits.filter { it.similarityScore > 0f && it.similarityScore >= 0.35f * bestHitScore }
      val userContent = buildUserContentWithAttachments(trimmed, stagedFiles, ragHits)
      chatDao.insertMessage(userMsg)

      activeGenerationJob?.cancel()
      activeGenerationJob = viewModelScope.launch(Dispatchers.IO) {
        if (!llmEngine.isLoaded) {
          withContext(Dispatchers.Main) {
            tokenBatcher.reset()
            thinkingBatcher.reset()
            _liveStream.value = LiveStreamState()
            _uiState.value = _uiState.value.copy(
              isStreaming = false,
              statusNotice = "هیچ مدل متنی واقعی بارگذاری نشده است. از بخش «مدل‌ها» یک فایل GGUF وارد کنید تا استنتاج آفلاین واقعی فعال شود."
            )
          }
          return@launch
        }

        XiaomiOptimizer.applyThreadPriority(_uiState.value.performanceMode)

        // Long-conversation compaction: once the transcript outgrows a fraction of
        // the prompt budget, fold the aged turns into a running LLM-generated
        // summary instead of letting the history window silently drop them.
        val promptTokenBudget = (effectiveParams.contextTokens - effectiveParams.maxTokens - 64).coerceAtLeast(32)
        val promptCharBudget = (promptTokenBudget * 2.0f).toInt().coerceAtLeast(64)
        val compactionPlan = ConversationCompactor.plan(
          priorMessages = priorMessages.map { ConversationCompactor.MessageRef(it.role, it.content) },
          currentChars = userContent.length,
          charBudget = promptCharBudget
        )
        val sessionSummary = if (compactionPlan.shouldCompact) {
          runConversationCompaction(session.id, compactionPlan.aged)
        } else {
          compactionSummaries[session.id]
        }

        val history = buildChatHistory(
          priorMessages = priorMessages,
          userContent = userContent,
          contextTokens = effectiveParams.contextTokens,
          maxTokens = effectiveParams.maxTokens,
          summaryText = sessionSummary,
          maxTurns = if (compactionPlan.shouldCompact) ConversationCompactor.DEFAULT_KEEP_LAST_TURNS else 12
        )

        val outcome = llmEngine.generate(
          history = history,
          params = effectiveParams,
          onThinking = { delta -> thinkingBatcher.appendToken(delta) },
          onAnswer = { delta -> tokenBatcher.appendToken(delta) }
        )

        when (outcome) {
          is RealLlmEngine.GenerateOutcome.Failure -> {
            tokenBatcher.reset()
            thinkingBatcher.reset()
            withContext(Dispatchers.Main) {
              _liveStream.value = LiveStreamState()
              _uiState.value = _uiState.value.copy(
                isStreaming = false,
                statusNotice = "استنتاج ناموفق بود: ${outcome.reason}"
              )
            }
          }
          else -> {
            val completed = outcome as? RealLlmEngine.GenerateOutcome.Completed
            val cancelled = outcome as? RealLlmEngine.GenerateOutcome.Cancelled
            val finalFullAnswer = (completed?.text ?: cancelled?.text ?: "")
              .ifEmpty { tokenBatcher.getAccumulated() }
            val finalThinking = (completed?.thinking ?: cancelled?.thinking ?: "")
              .ifEmpty { thinkingBatcher.getAccumulated() }
            val finalTps = completed?.tokensPerSecond ?: 0f
            val wasCancelled = cancelled != null

            tokenBatcher.flushNow()
            thinkingBatcher.flushNow()

            val assistantMsg = ChatMessageEntity(
              sessionId = session.id,
              role = "assistant",
              content = if (wasCancelled) "$finalFullAnswer\n\n(تولید توسط کاربر متوقف شد)" else finalFullAnswer,
              thinkingContent = finalThinking.ifBlank { null },
              thinkingDurationMs = completed?.promptMs ?: 0L,
              tokensPerSecond = finalTps
            )
            chatDao.insertMessage(assistantMsg)

            if (session.title == "گفت‌وگوی آفلاین ۱" || session.title == "گفت‌وگوی جدید") {
              val newTitle = trimmed.take(24).ifEmpty { "تحلیل محلی" }
              chatDao.updateSession(session.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
            }

            withContext(Dispatchers.Main) {
              _uiState.value = _uiState.value.copy(isStreaming = false)
              _liveStream.value = LiveStreamState(tokensPerSecond = finalTps)
            }
          }
        }
      }
    }
  }

  /**
   * Builds the model-facing user message: the prompt plus bounded excerpts of
   * any attached files, so the real offline model can actually reason over them.
   * When the local RAG index has relevant chunks for the prompt, they are
   * injected as references (bounded) — retrieval that actually reaches the model.
   */
  private fun buildUserContentWithAttachments(
    prompt: String,
    stagedFiles: List<AttachedFile>,
    ragHits: List<RetrievalResult> = emptyList()
  ): String {
    if (stagedFiles.isEmpty() && ragHits.isEmpty()) return prompt
    val sb = StringBuilder(prompt)

    if (ragHits.isNotEmpty()) {
      sb.append("\n\n[مراجع بازیابی‌شده از اسناد محلی کاربر]\n")
      var budget = 1_800
      for (hit in ragHits) {
        if (budget <= 0) break
        val excerpt = hit.chunk.text.take(minOf(700, budget))
        sb.append("(از «").append(hit.chunk.documentName).append("») ").append(excerpt).append("\n")
        budget -= excerpt.length
      }
    }

    var attachmentBudget = 4_000
    for (f in stagedFiles) {
      if (attachmentBudget <= 0) break
      sb.append("\n\n[پیوست: ").append(f.fileName).append(" — ").append(f.sizeFormatted).append("]\n")
      val body = (f.fullTextSample?.take(attachmentBudget)?.takeIf { it.isNotBlank() }
        ?: f.extractedSummary.take(minOf(800, attachmentBudget))).ifBlank {
        "(محتوای متنی قابل استخراج نبود)"
      }
      sb.append(body)
      attachmentBudget -= body.length
    }
    return sb.toString()
  }

  /**
   * Maps persisted chat messages to the model's chat template input, newest-first,
   * and stops before the prompt can overflow the allocated context. A 2.0 chars/token
   * allowance is deliberately conservative for mixed Persian/English text.
   */
  private fun buildChatHistory(
    priorMessages: List<ChatMessageEntity>,
    userContent: String,
    contextTokens: Int,
    maxTokens: Int,
    summaryText: String? = null,
    maxTurns: Int = 12
  ): List<RealLlmEngine.ChatTurn> {
    val promptTokenBudget = (contextTokens - maxTokens - 64).coerceAtLeast(32)
    val charBudget = (promptTokenBudget * 2.0f).toInt().coerceAtLeast(64)

    val turns = ArrayDeque<RealLlmEngine.ChatTurn>()
    var usedChars = 0

    // The current user turn always survives; only its tail can be clipped when the
    // prompt is genuinely larger than the context (e.g. a huge attachment).
    val latest = userContent.take(charBudget)
    turns.addLast(RealLlmEngine.ChatTurn("user", latest))
    usedChars += latest.length + 16

    // Compacted memory of aged turns sits before the verbatim window.
    summaryText?.takeIf { it.isNotBlank() }?.let { s ->
      val clipped = s.take((charBudget * 0.25f).toInt().coerceAtLeast(256))
      turns.addFirst(RealLlmEngine.ChatTurn("system", "خلاصهٔ گفت‌وگوی قدیمی‌تر این نشست:\n$clipped"))
      usedChars += clipped.length + 16
    }

    for (m in priorMessages.takeLast(maxTurns).asReversed()) {
      if (m.role != "user" && m.role != "assistant") continue
      val remaining = charBudget - usedChars - 16
      if (remaining < 64) break
      val content = m.content.take(minOf(4000, remaining))
      if (content.isBlank()) continue
      turns.addFirst(RealLlmEngine.ChatTurn(m.role, content))
      usedChars += content.length + 16
    }

    return turns.toList()
  }

  /**
   * Folds aged turns into the running session summary via the local LLM. On
   * failure the previous summary is kept and the send continues — an honest
   * fallback, and never worse than the silent drop that used to happen.
   */
  private suspend fun runConversationCompaction(
    sessionId: Long,
    aged: List<ConversationCompactor.MessageRef>
  ): String? {
    val previous = compactionSummaries[sessionId]
    val prompt = ConversationCompactor.buildSummaryPrompt(aged, previous)
    val baseParams = _uiState.value.generationParams
    val params = baseParams.copy(
      maxTokens = 256.coerceAtMost(baseParams.maxTokens),
      temperature = 0.3f
    ).sanitized()

    val summaryBuilder = StringBuilder()
    val outcome = llmEngine.generate(
      history = listOf(RealLlmEngine.ChatTurn("user", prompt)),
      params = params,
      onThinking = { },
      onAnswer = { delta -> summaryBuilder.append(delta) }
    )
    val summary = (outcome as? RealLlmEngine.GenerateOutcome.Completed)?.text
      ?.takeIf { it.isNotBlank() }
    return if (summary != null) {
      compactionSummaries[sessionId] = summary
      withContext(Dispatchers.Main) {
        _uiState.update {
          it.copy(statusNotice = "گفت‌وگوی طولانی جمع‌بندی شد (${aged.size} پیام قدیمی در خلاصهٔ نشست ادغام شد).")
        }
      }
      summary
    } else {
      previous
    }
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

  fun stopGeneration(showNotice: Boolean = true) {
    llmEngine.cancel()
    asrEngine.cancel()
    activeGenerationJob?.cancel()
    activeGenerationJob = null
    cancelActiveImageGeneration()
    tokenBatcher.reset()
    thinkingBatcher.reset()
    _liveStream.value = LiveStreamState()
    _uiState.update { current ->
      current.copy(
        isStreaming = false,
        isGeneratingImage = false,
        imageProgress = null,
        statusNotice = if (showNotice) "تولید جاری متوقف شد." else current.statusNotice
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
    val modelContext = _uiState.value.textSlotModel.contextLength
    val capped = if (modelContext in 1 until sanitized.contextTokens) {
      sanitized.copy(contextTokens = modelContext).sanitized()
    } else {
      sanitized
    }
    genParamsStore.save(capped)
    _uiState.value = _uiState.value.copy(
      generationParams = capped,
      activeContextTokens = capped.contextTokens,
      statusNotice = if (capped.contextTokens != sanitized.contextTokens) {
        "context به حداکثر پنجرهٔ مدل بارگذاری‌شده (${modelContext}) محدود شد."
      } else {
        _uiState.value.statusNotice
      }
    )
  }

  fun resetGenerationParams() {
    genParamsStore.reset()
    _uiState.value = _uiState.value.copy(
      generationParams = GenerationParams(),
      activeContextTokens = GenerationParams.DEFAULT_CONTEXT_TOKENS,
      statusNotice = "پارامترهای تولید به حالت امن پیش‌فرض بازگشتند."
    )
  }

  // --- Offline speech-to-text (real whisper.cpp pipeline) -------------------

  fun loadWhisperModelFromUri(uri: Uri, fileName: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val sizeBytes = queryModelSizeBytes(uri)
      if (sizeBytes == null) {
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(statusNotice = "اندازهٔ فایل مدل صوتی مشخص نیست؛ بارگذاری نشد.")
        }
        return@launch
      }
      val modelWeightsMb = ((sizeBytes + 1024 * 1024 - 1) / (1024 * 1024)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
      val preflight = resourceGuard.canSafelyLoadModel(modelWeightsMb, 1024)
      if (!preflight.first) {
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(statusNotice = preflight.second)
        }
        return@launch
      }

      val pfd = try {
        getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")
      } catch (_: Exception) {
        null
      }
      if (pfd == null) {
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(statusNotice = "باز کردن فایل مدل صوتی ممکن نشد.")
        }
        return@launch
      }

      withContext(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(statusNotice = "در حال بارگذاری مدل Whisper…")
      }

      when (val outcome = asrEngine.loadFromFileDescriptor(pfd.detachFd(), labelHint = fileName)) {
        is RealAsrEngine.LoadOutcome.Success -> {
          val memMb = if (outcome.memoryDeltaKb >= 0) outcome.memoryDeltaKb / 1024 else -1
          val memNote = if (memMb >= 0) " (${memMb} MB حافظهٔ اندازه‌گیری‌شده)" else ""
          withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
              asrModelLabel = outcome.modelLabel,
              statusNotice = "مدل صوتی «${outcome.modelLabel}» بارگذاری شد.$memNote"
            )
          }
        }
        is RealAsrEngine.LoadOutcome.Failure -> {
          withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
              asrModelLabel = null,
              statusNotice = "بارگذاری مدل صوتی ناموفق بود: ${outcome.reason}"
            )
          }
        }
      }
    }
  }

  fun unloadWhisperModel() {
    viewModelScope.launch(Dispatchers.IO) {
      asrEngine.unload()
      withContext(Dispatchers.Main) {
        _uiState.value = _uiState.value.copy(
          asrModelLabel = null,
          statusNotice = "مدل صوتی از حافظه خارج شد."
        )
      }
    }
  }

  /**
   * Asks an in-flight Whisper transcription to stop; the running job then
   * fails honestly as "transcription cancelled" and the UI state resets.
   */
  fun cancelTranscription() {
    asrEngine.cancel()
  }

  fun transcribeAudioUri(uri: Uri, fileName: String) {
    viewModelScope.launch(Dispatchers.IO) {
      if (!asrEngine.isLoaded) {
        withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(
            statusNotice = "ابتدا یک مدل Whisper (GGUF) را وارد کنید تا رونویسی واقعی صوت فعال شود."
          )
        }
        return@launch
      }

      _uiState.update { it.copy(isTranscribing = true, lastTranscription = null) }

      val decoded = PcmDecoder.decodeTo16kMono(getApplication(), uri)
      if (decoded == null) {
        _uiState.update {
          it.copy(isTranscribing = false, statusNotice = "فایل صوتی قابل رمزگشایی نبود یا مسیریاب صوتی ندارد: $fileName")
        }
        return@launch
      }

      applyTranscribeOutcome(
        asrEngine.transcribe(
          samples = decoded.samples,
          nThreads = _uiState.value.generationParams.threads,
          language = null,
          translate = false
        )
      )
    }
  }

  /** Shared outcome handling for file-based and live-microphone transcription. */
  private fun applyTranscribeOutcome(outcome: RealAsrEngine.TranscribeOutcome) {
    when (outcome) {
      is RealAsrEngine.TranscribeOutcome.Success -> {
        val minutes = (outcome.result.durationSeconds / 60).toInt()
        val seconds = (outcome.result.durationSeconds % 60).toInt()
        _uiState.update {
          it.copy(
            isTranscribing = false,
            lastTranscription = outcome.result.text.ifBlank { "(متنی شناسایی نشد)" },
            statusNotice = "رونویسی واقعی کامل شد — زبان: ${outcome.result.language} — طول صوت: ${minutes}:${seconds.toString().padStart(2, '0')}"
          )
        }
      }
      is RealAsrEngine.TranscribeOutcome.Failure -> {
        _uiState.update {
          it.copy(isTranscribing = false, statusNotice = "رونویسی ناموفق بود: ${outcome.reason}")
        }
      }
    }
  }

  // --- Live microphone capture → real whisper.cpp transcription -----------

  /** Starts live capture. The UI must have obtained RECORD_AUDIO first. */
  fun startMicRecording() {
    if (micRecorder.isRecording) return
    val started = micRecorder.start()
    _uiState.value = _uiState.value.copy(
      isRecordingMic = started,
      statusNotice = if (started) {
        "ضبط میکروفون شروع شد؛ برای پایان و رونویسی، دکمهٔ توقف ضبط را بزنید."
      } else {
        "شروع ضبط ممکن نشد؛ میکروفون اشغال است یا مجوز RECORD_AUDIO داده نشده است."
      }
    )
  }

  /** Stops live capture and runs the real Whisper transcription on the result. */
  fun stopMicRecordingAndTranscribe() {
    if (!micRecorder.isRecording) return
    val samples = micRecorder.stop()
    _uiState.value = _uiState.value.copy(isRecordingMic = false)
    if (samples.isEmpty()) {
      _uiState.value = _uiState.value.copy(statusNotice = "چیزی ضبط نشد یا ضبط خیلی کوتاه بود.")
      return
    }
    if (!asrEngine.isLoaded) {
      _uiState.value = _uiState.value.copy(
        statusNotice = "ابتدا یک مدل Whisper (GGUF) را وارد کنید تا رونویسی ضبط میکروفون فعال شود."
      )
      return
    }
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isTranscribing = true, lastTranscription = null) }
      applyTranscribeOutcome(
        asrEngine.transcribe(
          samples = samples,
          nThreads = _uiState.value.generationParams.threads,
          language = null,
          translate = false
        )
      )
    }
  }

  fun dismissTranscription() {
    _uiState.value = _uiState.value.copy(lastTranscription = null)
  }

  fun toggleLanguage() {
    val next = if (_uiState.value.appLanguage == AppLanguage.FA) AppLanguage.EN else AppLanguage.FA
    setLanguage(next)
  }
}
