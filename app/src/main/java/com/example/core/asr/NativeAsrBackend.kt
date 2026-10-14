package com.example.core.asr

/** Production [AsrBackend] bound to whisper.cpp through [NativeWhisper]. */
class NativeAsrBackend : AsrBackend {

  @Volatile
  private var available: Boolean? = null

  override fun isAvailable(): Boolean {
    val cached = available
    if (cached != null) return cached
    val loaded = NativeWhisper.tryLoad()
    available = loaded
    return loaded
  }

  override fun loadFromFd(fd: Int): String? {
    if (!isAvailable()) return "native library unavailable"
    return try {
      NativeWhisper.nativeWhisperLoadFromFd(fd)
    } catch (t: Throwable) {
      t.message ?: "whisper model load failed"
    }
  }

  override fun loadFromPath(path: String): String? {
    if (!isAvailable()) return "native library unavailable"
    return try {
      NativeWhisper.nativeWhisperLoadFromPath(path)
    } catch (t: Throwable) {
      t.message ?: "whisper model load failed"
    }
  }

  override fun unload() {
    if (!isAvailable()) return
    try {
      NativeWhisper.nativeWhisperFree()
    } catch (_: Throwable) {
    }
  }

  override fun transcribe(
    samples: FloatArray,
    nThreads: Int,
    language: String?,
    translate: Boolean
  ): String? {
    if (!isAvailable()) return "native library unavailable"
    return try {
      NativeWhisper.nativeWhisperTranscribe(samples, samples.size, nThreads, language, translate)
    } catch (t: Throwable) {
      t.message ?: "transcription failed"
    }
  }

  override fun transcript(): String {
    if (!isAvailable()) return ""
    return try {
      NativeWhisper.nativeWhisperTranscript()
    } catch (_: Throwable) {
      ""
    }
  }

  override fun lastLanguage(): String {
    if (!isAvailable()) return ""
    return try {
      NativeWhisper.nativeWhisperLastLanguage()
    } catch (_: Throwable) {
      ""
    }
  }

  override fun modelInfo(): Array<String> {
    if (!isAvailable()) return emptyArray()
    return try {
      val label = NativeWhisper.nativeWhisperModelLabel()
      if (label.isEmpty()) emptyArray() else arrayOf(label)
    } catch (_: Throwable) {
      emptyArray()
    }
  }
}
