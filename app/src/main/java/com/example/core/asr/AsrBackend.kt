package com.example.core.asr

/**
 * Seam over the native whisper.cpp runtime so ASR behaviour is JVM-testable.
 * Production uses [NativeAsrBackend].
 */
interface AsrBackend {

  fun isAvailable(): Boolean

  /** Consumes [fd]. Returns null on success or an error message. */
  fun loadFromFd(fd: Int): String?

  fun loadFromPath(path: String): String?

  fun unload()

  /**
   * Transcribes 16 kHz mono float PCM. Returns null on success (the text is
   * read via [transcript] + [lastLanguage]) or an error message.
   */
  fun transcribe(samples: FloatArray, nThreads: Int, language: String?, translate: Boolean): String?

  /** Full transcript text of the last successful [transcribe] call. */
  fun transcript(): String

  /** ISO language code detected/used by the last run (e.g. "fa"). */
  fun lastLanguage(): String

  /** Metadata: [modelLabel] or empty when no model is loaded. */
  fun modelInfo(): Array<String>
}

/** Result of one transcription run. */
data class AsrResult(
  val text: String,
  val language: String,
  val durationSeconds: Float
)
