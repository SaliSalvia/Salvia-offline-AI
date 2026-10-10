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
      val thinking: String,
      val promptTokens: Int,
      val promptMs: Long,
      val generatedTokens: Int,
      val generatedMs: Long,
      val tokensPerSecond: Float
    ) : GenerateOutcome()

    data class Cancelled(val text: String, val thinking: String) : GenerateOutcome()

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
   * Runs one chat turn. Text is split live into reasoning and answer streams:
   * [onThinking] receives `...` content, [onAnswer] receives the
   * user-facing answer. Both always receive complete UTF-8 text pieces.
   */
  fun generate(
    history: List<ChatTurn>,
    params: GenerationParams,
    onThinking: (String) -> Unit = {},
    onAnswer: (String) -> Unit = {}
  ): GenerateOutcome {
    if (!isLoaded) {
      return GenerateOutcome.Failure("no model loaded")
    }
    if (history.isEmpty()) {
      return GenerateOutcome.Failure("empty conversation")
    }

    val parser = ThinkingStreamParser()
    val fullThinking = StringBuilder()
    val fullAnswer = StringBuilder()
    fun dispatch(chunk: String) {
      if (chunk.isEmpty()) return
      val (thinking, answer) = parser.push(chunk)
      if (thinking.isNotEmpty()) {
        fullThinking.append(thinking)
        onThinking(thinking)
      }
      if (answer.isNotEmpty()) {
        fullAnswer.append(answer)
        onAnswer(answer)
      }
    }

    val request = NativeChatRequest(
      roles = history.map { it.role },
      contents = history.map { it.content },
      params = params.sanitized(),
      onToken = TokenListener { text -> dispatch(text) }
    )

    val result = try {
      backend.generate(request)
    } catch (t: Throwable) {
      return GenerateOutcome.Failure(t.message ?: "native generation failed")
    }

    // Flush whatever the parser still holds (truncated markers become text).
    val (tailThinking, tailAnswer) = parser.finish()
    if (tailThinking.isNotEmpty()) {
      fullThinking.append(tailThinking)
      onThinking(tailThinking)
    }
    if (tailAnswer.isNotEmpty()) {
      fullAnswer.append(tailAnswer)
      onAnswer(tailAnswer)
    }

    val stats = try {
      backend.lastStats()
    } catch (_: Throwable) {
      FloatArray(5)
    }

    val answerText = fullAnswer.toString()
    val thinkingText = fullThinking.toString()

    return if (result.wasCancelled) {
      GenerateOutcome.Cancelled(answerText, thinkingText)
    } else {
      GenerateOutcome.Completed(
        text = answerText,
        thinking = thinkingText,
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
