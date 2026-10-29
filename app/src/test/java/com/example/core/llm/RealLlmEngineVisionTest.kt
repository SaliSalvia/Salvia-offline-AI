package com.example.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the vision seam: a backend without vision must honestly
 * default to "not supported" (so other fake backends keep compiling), and the
 * engine must stream a vision answer through the same parser path as text.
 */
class RealLlmEngineVisionTest {

  private class FakeVisionBackend : LlamaBackend {
    var visionRuns = 0
    var lastQuestion: String? = null
    var lastImage: ByteArray? = null

    override fun isAvailable() = true
    override fun loadFromFd(fd: Int, gpuLayers: Int, listener: LoadListener?) = null
    override fun loadFromPath(path: String, gpuLayers: Int, listener: LoadListener?) = null
    override fun unload() {}
    override fun generate(request: NativeChatRequest) = NativeGenerationResult("text", false)
    override fun cancel() {}
    override fun modelInfo() = arrayOf("test-model", "llama", "4096", "1000", "chatml")
    override fun lastStats() = floatArrayOf(12f, 2f, 3f, 1.5f, 2f)

    override fun isVisionReady() = true
    override fun generateVision(request: VisionChatRequest): NativeGenerationResult {
      visionRuns++
      lastQuestion = request.question
      lastImage = request.imageBytes
      request.onToken?.onToken("پاسخ بینایی")
      return NativeGenerationResult("پاسخ بینایی", false)
    }
  }

  @Test
  fun backendWithoutVisionDefaultsToUnavailable() {
    val plain = object : LlamaBackend {
      override fun isAvailable() = true
      override fun loadFromFd(fd: Int, gpuLayers: Int, listener: LoadListener?) = null
      override fun loadFromPath(path: String, gpuLayers: Int, listener: LoadListener?) = null
      override fun unload() {}
      override fun generate(request: NativeChatRequest) = NativeGenerationResult("", false)
      override fun cancel() {}
      override fun modelInfo() = emptyArray<String>()
      override fun lastStats() = FloatArray(5)
    }
    assertFalse(plain.isVisionReady())
  }

  @Test
  fun visionStreamsAnswerAndReportsCompletion() {
    val fake = FakeVisionBackend()
    val engine = RealLlmEngine(fake)
    engine.loadFromPath("/fake.gguf")

    val streamed = StringBuilder()
    val outcome = engine.generateVision(
      question = "این تصویر چه نشان می‌دهد؟",
      imageBytes = byteArrayOf(1, 2, 3),
      params = GenerationParams(),
      onAnswer = { streamed.append(it) }
    )

    assertTrue(outcome is RealLlmEngine.GenerateOutcome.Completed)
    assertEquals("پاسخ بینایی", streamed.toString())
    assertEquals(1, fake.visionRuns)
    assertEquals("این تصویر چه نشان می‌دهد؟", fake.lastQuestion)
    assertTrue(fake.lastImage?.contentEquals(byteArrayOf(1, 2, 3)) == true)
  }
}
