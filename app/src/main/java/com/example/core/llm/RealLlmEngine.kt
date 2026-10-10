package com.example.core.llm

/**
 * Real on-device LLM engine, backed by llama.cpp.
 *
 * Responsibilities:
 *  * honest load/unload lifecycle with measured memory deltas (PSS);
 *  * multi-turn chat through the model's own chat template;
 *  * token streaming via [TokenListener] plus a final [GenerateOutcome];
 *  * cancellation that interrupts native decode promptly.
 *
 * The heavy lifting lives behind [LlamaBackend] so JVM tests can verify every
 * branch with a fake; only [NativeLlamaBackend] touches the JNI layer.
 */
class RealLlmEngine(
  private val backend: LlamaBackend,
  private val pssReader: () -> Long = { -1L }
) {

  sealed class LoadOutcome {
    data class Success(
      val modelName: String,
      val architecture: String,
      val contextTrain: Int,
      val vocabSize: Int,
      val chatTemplate: String,
      val memoryDeltaKb: Long
    ) : LoadOutcome()

    data class Failure(val reason: String) : LoadOutcome()
  }

  sealed class GenerateOutcome {
    data class Completed(
      val text: String,
      val promptTokens: Int,
      val promptMs: Long,
      val generatedTokens: Int,
      val generatedMs: Long,
      val tokensPerSecond: Float
    ) : GenerateOutcome()

    data class Cancelled(val text: String) : GenerateOutcome()

    data class Failure(val reason: String) : GenerateOutcome()
  }

  data class ChatTurn(val role: String, val content: String) {
    init {
      require(role == "system" || role == "user" || role == "assistant") {
        "unsupported chat role: $role"
      }
    }
  }

  @Volatile
  var isLoaded: Boolean = false
    private set

  @Volatile
  var loadedModelName: String? = null
    private set

  fun isBackendAvailable(): Boolean = backend.isAvailable()

  /** Loads a GGUF model from a caller-owned file descriptor (consumed). */
  fun loadFromFileDescriptor(fd: Int, gpuLayers: Int = 0): LoadOutcome {
    val before = pssReader()
    val error = backend.loadFromFd(fd, gpuLayers, null)
    return finishLoad(error, before)
  }

  fun loadFromPath(path: String, gpuLayers: Int = 0): LoadOutcome {
    val before = pssReader()
    val error = backend.loadFromPath(path, gpuLayers, null)
    return finishLoad(error, before)
  }

  private fun finishLoad(error: String?, pssBefore: Long): LoadOutcome {
    if (error != null) {
      isLoaded = false
      loadedModelName = null
      return LoadOutcome.Failure(error)
    }
    val info = backend.modelInfo()
    val after = pssReader()
    isLoaded = true
    loadedModelName = info.getOrNull(0)?.takeIf { it.isNotBlank() }
    return LoadOutcome.Success(
      modelName = info.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "GGUF model",
      architecture = info.getOrNull(1) ?: "unknown",
      contextTrain = info.getOrNull(2)?.toIntOrNull() ?: 0,
      vocabSize = info.getOrNull(3)?.toIntOrNull() ?: 0,
      chatTemplate = info.getOrNull(4) ?: "",
      memoryDeltaKb = if (pssBefore >= 0 && after >= 0) after - pssBefore else -1L
    )
  }

  fun unload() {
    backend.unload()
    isLoaded = false
    loadedModelName = null
  }

  /**
   * Runs one chat turn. [onDelta] receives complete UTF-8 text pieces while the
   * native engine is producing tokens; the full result is returned at the end.
   */
  fun generate(
    history: List<ChatTurn>,
    params: GenerationParams,
    onDelta: (String) -> Unit = {}
  ): GenerateOutcome {
    if (!isLoaded) {
      return GenerateOutcome.Failure("no model loaded")
    }
    if (history.isEmpty()) {
      return GenerateOutcome.Failure("empty conversation")
    }

    val request = NativeChatRequest(
      roles = history.map { it.role },
      contents = history.map { it.content },
      params = params.sanitized(),
      onToken = TokenListener { text -> if (text.isNotEmpty()) onDelta(text) }
    )

    val result = try {
      backend.generate(request)
    } catch (t: Throwable) {
      return GenerateOutcome.Failure(t.message ?: "native generation failed")
    }

    val stats = try {
      backend.lastStats()
    } catch (_: Throwable) {
      FloatArray(5)
    }

    return if (result.wasCancelled) {
      GenerateOutcome.Cancelled(result.text)
    } else {
      GenerateOutcome.Completed(
        text = result.text,
        promptTokens = stats.getOrElse(0) { 0f }.toInt(),
        promptMs = stats.getOrElse(1) { 0f }.toLong(),
        generatedTokens = stats.getOrElse(2) { 0f }.toInt(),
        generatedMs = stats.getOrElse(3) { 0f }.toLong(),
        tokensPerSecond = stats.getOrElse(4) { 0f }
      )
    }
  }

  fun cancel() = backend.cancel()
}
