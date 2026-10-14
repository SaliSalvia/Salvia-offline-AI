package com.example.core.llm

/**
 * Thin seam over the native llama.cpp runtime so every Kotlin-side behaviour
 * (streaming, cancellation, error mapping, statistics) can be unit-tested with
 * a fake backend on the JVM. Production uses [NativeLlamaBackend].
 */
interface LlamaBackend {

  /** True when the native library loaded successfully on this device. */
  fun isAvailable(): Boolean

  /**
   * Loads a GGUF model from a caller-owned file descriptor. The descriptor is
   * consumed by this call. Returns null on success, or a short error message.
   */
  fun loadFromFd(fd: Int, gpuLayers: Int, listener: LoadListener?): String?

  /** Loads a GGUF model from a filesystem path. Null on success. */
  fun loadFromPath(path: String, gpuLayers: Int, listener: LoadListener?): String?

  fun unload()

  /** Runs one full chat turn. Streams deltas through [NativeChatRequest.onToken]. */
  fun generate(request: NativeChatRequest): NativeGenerationResult

  /** Requests cancellation of the in-flight [generate] call. */
  fun cancel()

  /** Model metadata: [name, architecture, ctxTrain, vocabSize, chatTemplate]. */
  fun modelInfo(): Array<String>

  /** [promptTokens, promptMs, generatedTokens, generatedMs, tokensPerSec] of the last run. */
  fun lastStats(): FloatArray
}

fun interface LoadListener {
  /** Progress from 0 to 100 while model weights are being read. */
  fun onLoadProgress(percent: Float)
}

fun interface TokenListener {
  /** One decoded text delta (always complete UTF-8). */
  fun onToken(text: String)
}

data class NativeChatRequest(
  val roles: List<String>,
  val contents: List<String>,
  val params: GenerationParams,
  val onToken: TokenListener? = null
)

data class NativeGenerationResult(
  val text: String,
  val wasCancelled: Boolean
)
