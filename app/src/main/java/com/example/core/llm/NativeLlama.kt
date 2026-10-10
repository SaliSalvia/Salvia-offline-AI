package com.example.core.llm

/**
 * Raw JNI surface of `libsalvia_llama.so`.
 *
 * Do not call from UI code — use [RealLlmEngine], which adds error mapping,
 * history handling and memory accounting. The native layer streams text as
 * complete UTF-8 characters through `onToken(String)` on the callback object
 * and reports weight-loading progress through `onLoadProgress(Float)`.
 */
internal object NativeLlama {

  fun tryLoad(): Boolean = try {
    System.loadLibrary("salvia_llama")
    nativeBootstrap()
  } catch (_: Throwable) {
    false
  }

  external fun nativeBootstrap(): Boolean

  /** Consumes [fd]. Returns null on success or an error message. */
  external fun nativeLoadFromFd(fd: Int, gpuLayers: Int, callback: Any?): String?

  external fun nativeLoadFromPath(path: String, gpuLayers: Int, callback: Any?): String?

  external fun nativeUnload()

  external fun nativeCancel()

  /** 0 = run completed, 1 = run cancelled. */
  external fun nativeLastRunStatus(): Int

  external fun nativeLastStats(): FloatArray

  external fun nativeModelInfo(): Array<String>?

  external fun nativeGenerate(
    roles: Array<String>,
    contents: Array<String>,
    maxTokens: Int,
    temperature: Float,
    topP: Float,
    topK: Int,
    repeatPenalty: Float,
    nCtx: Int,
    nThreads: Int,
    callback: Any?
  ): String?
}
