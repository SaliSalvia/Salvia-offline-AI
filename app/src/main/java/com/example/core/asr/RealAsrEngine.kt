package com.example.core.asr

/**
 * Real offline speech-to-text engine (whisper.cpp).
 *
 * Lifecycle mirrors [com.example.core.llm.RealLlmEngine]: honest outcomes,
 * injectable backend for JVM tests, no fabricated transcripts — a failure is
 * always reported as a failure.
 */
class RealAsrEngine(
  private val backend: AsrBackend,
  private val pssReader: () -> Long = { -1L }
) {

  sealed class LoadOutcome {
    data class Success(val modelLabel: String, val memoryDeltaKb: Long) : LoadOutcome()
    data class Failure(val reason: String) : LoadOutcome()
  }

  sealed class TranscribeOutcome {
    data class Success(val result: AsrResult) : TranscribeOutcome()
    data class Failure(val reason: String) : TranscribeOutcome()
  }

  @Volatile
  var isLoaded: Boolean = false
    private set

  @Volatile
  var loadedModelLabel: String? = null
    private set

  fun isBackendAvailable(): Boolean = backend.isAvailable()

  /** Loads a whisper GGUF model from a caller-owned file descriptor (consumed). */
  fun loadFromFileDescriptor(fd: Int, labelHint: String? = null): LoadOutcome {
    val before = pssReader()
    val error = backend.loadFromFd(fd)
    return finishLoad(error, before, labelHint)
  }

  fun loadFromPath(path: String, labelHint: String? = null): LoadOutcome {
    val before = pssReader()
    val error = backend.loadFromPath(path)
    return finishLoad(error, before, labelHint)
  }

  private fun finishLoad(error: String?, pssBefore: Long, labelHint: String? = null): LoadOutcome {
    if (error != null) {
      isLoaded = false
      loadedModelLabel = null
      return LoadOutcome.Failure(error)
    }
    val info = backend.modelInfo()
    val after = pssReader()
    isLoaded = true
    loadedModelLabel = info.getOrNull(0)?.takeIf { it.isNotBlank() }
      ?: labelHint?.takeIf { it.isNotBlank() }
      ?: "whisper model"
    return LoadOutcome.Success(
      modelLabel = loadedModelLabel!!,
      memoryDeltaKb = if (pssBefore >= 0 && after >= 0) after - pssBefore else -1L
    )
  }

  fun unload() {
    backend.unload()
    isLoaded = false
    loadedModelLabel = null
  }

  /**
   * Transcribes 16 kHz mono float PCM. [durationSeconds] of the input is echoed
   * into the result for UI display.
   */
  fun transcribe(
    samples: FloatArray,
    nThreads: Int = 4,
    language: String? = null,
    translate: Boolean = false
  ): TranscribeOutcome {
    if (!isLoaded) {
      return TranscribeOutcome.Failure("no whisper model loaded")
    }
    if (samples.isEmpty()) {
      return TranscribeOutcome.Failure("empty audio")
    }

    val error = try {
      backend.transcribe(samples, nThreads.coerceIn(1, 8), language, translate)
    } catch (t: Throwable) {
      return TranscribeOutcome.Failure(t.message ?: "transcription failed")
    }
    if (error != null) {
      return TranscribeOutcome.Failure(error)
    }

    val text = backend.transcript().trim()
    return TranscribeOutcome.Success(
      AsrResult(
        text = text,
        language = backend.lastLanguage().ifBlank { language ?: "auto" },
        durationSeconds = samples.size / 16000f
      )
    )
  }
}
