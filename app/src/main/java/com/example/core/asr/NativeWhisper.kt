package com.example.core.asr

/**
 * Raw JNI surface of the whisper.cpp half of `libsalvia_llama.so`.
 * Use [RealAsrEngine] instead of calling this directly.
 */
internal object NativeWhisper {

  fun tryLoad(): Boolean = try {
    System.loadLibrary("salvia_llama")
    true
  } catch (_: Throwable) {
    false
  }

  /** Consumes [fd]. Returns null on success or an error message. */
  external fun nativeWhisperLoadFromFd(fd: Int): String?

  external fun nativeWhisperLoadFromPath(path: String): String?

  external fun nativeWhisperFree()

  external fun nativeWhisperTranscribe(
    samples: FloatArray,
    nSamples: Int,
    nThreads: Int,
    language: String?,
    translate: Boolean
  ): String?

  external fun nativeWhisperTranscript(): String

  external fun nativeWhisperLastLanguage(): String

  external fun nativeWhisperModelLabel(): String
}
