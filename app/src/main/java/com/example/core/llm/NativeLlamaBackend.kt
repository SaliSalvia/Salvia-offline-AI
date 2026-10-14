package com.example.core.llm

/** Production [LlamaBackend] bound to llama.cpp through [NativeLlama]. */
class NativeLlamaBackend : LlamaBackend {

  @Volatile
  private var available: Boolean? = null

  override fun isAvailable(): Boolean {
    val cached = available
    if (cached != null) return cached
    val loaded = NativeLlama.tryLoad()
    available = loaded
    return loaded
  }

  override fun loadFromFd(fd: Int, gpuLayers: Int, listener: LoadListener?): String? {
    if (!isAvailable()) return "native library unavailable"
    return try {
      NativeLlama.nativeLoadFromFd(fd, gpuLayers, listener)
    } catch (t: Throwable) {
      t.message ?: "native model load failed"
    }
  }

  override fun loadFromPath(path: String, gpuLayers: Int, listener: LoadListener?): String? {
    if (!isAvailable()) return "native library unavailable"
    return try {
      NativeLlama.nativeLoadFromPath(path, gpuLayers, listener)
    } catch (t: Throwable) {
      t.message ?: "native model load failed"
    }
  }

  override fun unload() {
    if (!isAvailable()) return
    try {
      NativeLlama.nativeUnload()
    } catch (_: Throwable) {
      // Unload must never crash the app.
    }
  }

  override fun generate(request: NativeChatRequest): NativeGenerationResult {
    if (!isAvailable()) {
      throw IllegalStateException("native library unavailable")
    }
    val params = request.params.sanitized()
    val text = NativeLlama.nativeGenerate(
      request.roles.toTypedArray(),
      request.contents.toTypedArray(),
      params.maxTokens,
      params.temperature,
      params.topP,
      params.topK,
      params.repeatPenalty,
      params.contextTokens,
      params.threads,
      request.onToken
    ) ?: ""
    val cancelled = NativeLlama.nativeLastRunStatus() == 1
    return NativeGenerationResult(text = text, wasCancelled = cancelled)
  }

  override fun cancel() {
    if (!isAvailable()) return
    try {
      NativeLlama.nativeCancel()
    } catch (_: Throwable) {
    }
  }

  override fun modelInfo(): Array<String> {
    if (!isAvailable()) return emptyArray()
    return try {
      NativeLlama.nativeModelInfo() ?: emptyArray()
    } catch (_: Throwable) {
      emptyArray()
    }
  }

  override fun lastStats(): FloatArray {
    if (!isAvailable()) return FloatArray(5)
    return try {
      NativeLlama.nativeLastStats()
    } catch (_: Throwable) {
      FloatArray(5)
    }
  }
}
