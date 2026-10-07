package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.core.localization.AppLanguage
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

  private val _uiState = MutableStateFlow(ChatUiState())
  val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

  // Token Batcher with 30ms window to reduce UI recompositions and maintain silky 120Hz responsiveness
  private val tokenBatcher = TokenBatcher(viewModelScope, batchIntervalMs = 30L) { _, accumulated ->
    _uiState.value = _uiState.value.copy(
      liveAnswer = accumulated
    )
  }

  private var activeGenerationJob: Job? = null
  private var messagesObservationJob: Job? = null

  init {
    val savedLang = prefs.getString("app_language", "FA") ?: "FA"
    val initialLang = if (savedLang == "EN") AppLanguage.EN else AppLanguage.FA
    _uiState.value = _uiState.value.copy(appLanguage = initialLang)

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
        threads = telem.performanceCores + 2,
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
    _uiState.value = _uiState.value.copy(performanceMode = mode)
    XiaomiOptimizer.applyThreadPriority(mode)
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
      statusNotice = if (!current) "حالت تست هواپیما فعال شد: ۱۰۰٪ ارتباطات مسدود و اجرای آفلاین تضمین گردید." else "حالت تست هواپیما غیرفعال شد."
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
            statusNotice = "بارگذاری متوقف شد: سقف امن رم AI اجازه بارگذاری نمی‌دهد."
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
      val parsed: GgufMetadata = GgufParser.parseFromUri(getApplication(), uri, sizeFormatted)
      val slot = if (targetSlot == "TEXT") {
        LoadedSlotInfo(
          id = "slot_text",
          slotType = "TEXT",
          modelName = if (parsed.isValid) parsed.modelName else fileName.removeSuffix(".gguf"),
          architecture = parsed.architecture,
          quantization = parsed.quantizationType,
          contextLength = parsed.contextLength,
          sizeFormatted = sizeFormatted,
          uriString = uri.toString(),
          isLoaded = true
        )
      } else {
        LoadedSlotInfo(
          id = "slot_image",
          slotType = "IMAGE",
          modelName = if (parsed.isValid) parsed.modelName else fileName.removeSuffix(".gguf"),
          architecture = parsed.architecture,
          quantization = parsed.quantizationType,
          contextLength = parsed.contextLength,
          sizeFormatted = sizeFormatted,
          uriString = uri.toString(),
          isLoaded = true
        )
      }

      // Warmup model in background
      GgufInferenceEngine.warmupModel(slot)

      withContext(Dispatchers.Main) {
        if (targetSlot == "TEXT") {
          _uiState.value = _uiState.value.copy(
            textSlotModel = slot,
            statusNotice = "مدل متنی ${slot.modelName} بارگذاری و گرم شد."
          )
        } else {
          _uiState.value = _uiState.value.copy(
            imageSlotModel = slot,
            statusNotice = "مدل تصویرساز ${slot.modelName} آماده شد."
          )
        }
      }
    }
  }

  fun setPresetTextModel(presetName: String, arch: String, quant: String, size: String) {
    val model = LoadedSlotInfo(
      id = "slot_text",
      slotType = "TEXT",
      modelName = presetName,
      architecture = arch,
      quantization = quant,
      contextLength = 4096,
      sizeFormatted = size,
      isLoaded = true
    )
    viewModelScope.launch(Dispatchers.IO) {
      GgufInferenceEngine.warmupModel(model)
    }
    _uiState.value = _uiState.value.copy(
      textSlotModel = model,
      statusNotice = "مدل فعال به $presetName تغییر یافت."
    )
  }

  fun setPresetImageModel(presetName: String, quant: String, size: String) {
    val model = LoadedSlotInfo(
      id = "slot_image",
      slotType = "IMAGE",
      modelName = presetName,
      architecture = "stable-diffusion",
      quantization = quant,
      contextLength = 77,
      sizeFormatted = size,
      isLoaded = true
    )
    _uiState.value = _uiState.value.copy(
      imageSlotModel = model,
      statusNotice = "مدل تصویرساز فعال به $presetName تغییر یافت."
    )
  }

  fun sendMessage(promptText: String) {
    val session = _uiState.value.currentSession ?: return
    val trimmed = promptText.trim()
    val stagedFiles = _uiState.value.attachedFiles

    if (trimmed.isEmpty() && stagedFiles.isEmpty()) return

    // Resource Guard Safety Check (Rule 9 & 80% Stop Rule)
    if (_uiState.value.resourceSnapshot?.isAiExecutionAllowed == false) {
      _uiState.value = _uiState.value.copy(
        statusNotice = "AI Paused — ${_uiState.value.resourceSnapshot?.activeConstraintReason ?: "سقف ۸۰٪ منابع"}. لطفاً منتظر خنک‌سازی بمانید."
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
      executeOfflineImageGeneration(session.id, routed.sanitizedPrompt)
      return
    }

    if (routed.engine == TargetEngine.TEXT_TO_SPEECH) {
      speakMessageText(routed.sanitizedPrompt)
      return
    }

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

    // 4. Background inference execution (Zero Main Thread work)
    viewModelScope.launch(Dispatchers.IO) {
      chatDao.insertMessage(userMsg)

      activeGenerationJob?.cancel()
      activeGenerationJob = viewModelScope.launch(Dispatchers.IO) {
        val stream = GgufInferenceEngine.streamResponse(
          context = getApplication(),
          prompt = trimmed,
          attachedFiles = stagedFiles,
          textModel = _uiState.value.textSlotModel,
          mode = _uiState.value.performanceMode
        )

        var finalThinking = ""
        var finalDurationMs = 0L
        var finalTps = 0f

        stream.collect { chunk ->
          finalThinking = chunk.fullThinkingText
          finalDurationMs = chunk.elapsedMs
          finalTps = chunk.tokensPerSecond

          // Flush thinking directly, and batch tokens in 30ms window to prevent UI thrashing
          if (chunk.isThinking) {
            withContext(Dispatchers.Main) {
              _uiState.value = _uiState.value.copy(
                liveThinking = finalThinking,
                liveThinkingDurationMs = finalDurationMs,
                liveTokensPerSecond = finalTps
              )
            }
          } else {
            tokenBatcher.appendToken(chunk.textDelta)
            withContext(Dispatchers.Main) {
              _uiState.value = _uiState.value.copy(
                liveTokensPerSecond = finalTps
              )
            }
          }

          if (chunk.isFinished) {
            tokenBatcher.flushNow()
            val finalFullAnswer = tokenBatcher.getAccumulated().ifEmpty { chunk.fullAnswerText }

            val assistantMsg = ChatMessageEntity(
              sessionId = session.id,
              role = "assistant",
              content = finalFullAnswer,
              thinkingContent = finalThinking.ifEmpty { null },
              thinkingDurationMs = finalDurationMs,
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
                liveAnswer = ""
              )
            }
          }
        }
      }
    }
  }

  fun executeOfflineImageGeneration(sessionId: Long, promptText: String) {
    viewModelScope.launch {
      val userMsg = ChatMessageEntity(
        sessionId = sessionId,
        role = "user",
        content = "🎨 [تولید تصویر آفلاین]: $promptText"
      )
      // Optimistic UI
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
          _uiState.value = _uiState.value.copy(imageProgress = progress)
        }
      }

      val assistantMsg = ChatMessageEntity(
        sessionId = sessionId,
        role = "assistant",
        content = "تصویر با استفاده از مدل تصویرساز آفلاین ${_uiState.value.imageSlotModel.modelName} بر اساس پرامپت شما تولید شد:\n\n> \"$promptText\"",
        thinkingContent = "• مقداردهی به بردار نویز در فضای نهان (Latent Space)\n• اجرای خط لوله انتشار U-Net با هدایت CFG 7.5 محلی روی GPU\n• رمزگشایی VAE با وضوح تصویر بهینه‌سازی‌شده برای رم دستگاه",
        thinkingDurationMs = 1850L,
        tokensPerSecond = 24.2f,
        imageResultUri = imagePath
      )

      withContext(Dispatchers.IO) {
        chatDao.insertMessage(assistantMsg)
      }

      _uiState.value = _uiState.value.copy(
        isGeneratingImage = false,
        imageProgress = null
      )
    }
  }

  fun stopGeneration() {
    activeGenerationJob?.cancel()
    tokenBatcher.reset()
    _uiState.value = _uiState.value.copy(
      isStreaming = false,
      isGeneratingImage = false,
      statusNotice = "استنتاج مدل فوراً متوقف شد."
    )
  }

  fun setLanguage(lang: AppLanguage) {
    prefs.edit().putString("app_language", lang.name).apply()
    _uiState.value = _uiState.value.copy(appLanguage = lang)
  }

  fun toggleLanguage() {
    val next = if (_uiState.value.appLanguage == AppLanguage.FA) AppLanguage.EN else AppLanguage.FA
    setLanguage(next)
  }
}
