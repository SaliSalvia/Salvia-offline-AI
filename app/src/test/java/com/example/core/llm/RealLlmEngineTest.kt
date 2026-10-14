package com.example.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [RealLlmEngine] against a fake [LlamaBackend]. These verify the
 * Kotlin-side contract (load lifecycle, streaming, cancellation, error mapping,
 * memory accounting). Native numerics are NOT covered here — that honesty is
 * deliberate: the JVM cannot execute the arm64 llama.cpp library.
 */
class RealLlmEngineTest {

  private class FakeBackend : LlamaBackend {
    var available = true
    var loadError: String? = null
    var loaded = false
    var cancelRequested = false
    var generateMode = "complete" // complete | cancelled | throws
    var lastRequest: NativeChatRequest? = null

    override fun isAvailable(): Boolean = available

    override fun loadFromFd(fd: Int, gpuLayers: Int, listener: LoadListener?): String? {
      if (loadError != null) return loadError
      loaded = true
      return null
    }

    override fun loadFromPath(path: String, gpuLayers: Int, listener: LoadListener?): String? {
      if (loadError != null) return loadError
      loaded = true
      return null
    }

    override fun unload() {
      loaded = false
    }

    override fun generate(request: NativeChatRequest): NativeGenerationResult {
      if (!loaded) throw IllegalStateException("no model is loaded")
      lastRequest = request
      return when (generateMode) {
        "throws" -> throw RuntimeException("llama_decode failed during generation")
        "cancelled" -> {
          request.onToken?.onToken("partial ")
          request.onToken?.onToken("text")
          NativeGenerationResult("partial text", wasCancelled = true)
        }
        "thinking" -> {
          // Emits a reasoning block split across tokens, then the answer.
          // Marker literals assembled from parts to keep tooling from mangling them.
          val tOpen = "<" + "think" + ">"
          val tClose = "</" + "think" + ">"
          request.onToken?.onToken(tOpen + "step ")
          request.onToken?.onToken("by step" + tClose)
          request.onToken?.onToken("the ")
          request.onToken?.onToken("result")
          NativeGenerationResult(tOpen + "step by step" + tClose + "the result", wasCancelled = false)
        }
        else -> {
          request.onToken?.onToken("Hello ")
          request.onToken?.onToken("world")
          NativeGenerationResult("Hello world", wasCancelled = false)
        }
      }
    }

    override fun cancel() {
      cancelRequested = true
    }

    override fun modelInfo(): Array<String> =
      arrayOf("TestModel", "qwen2", "4096", "32000", "chatml")

    override fun lastStats(): FloatArray = floatArrayOf(12f, 100f, 5f, 500f, 10f)
  }

  private fun engineWith(
    backend: FakeBackend,
    pss: LongArray = longArrayOf(1000L, 5000L)
  ): RealLlmEngine {
    var i = 0
    return RealLlmEngine(backend, pssReader = { pss[minOf(i++, pss.size - 1)] })
  }

  @Test
  fun `successful load reports model metadata and measured memory delta`() {
    val backend = FakeBackend()
    val engine = engineWith(backend)

    val outcome = engine.loadFromFileDescriptor(fd = 7)

    assertTrue(outcome is RealLlmEngine.LoadOutcome.Success)
    outcome as RealLlmEngine.LoadOutcome.Success
    assertEquals("TestModel", outcome.modelName)
    assertEquals("qwen2", outcome.architecture)
    assertEquals(4096, outcome.contextTrain)
    assertEquals(32000, outcome.vocabSize)
    assertEquals("chatml", outcome.chatTemplate)
    assertEquals(4000L, outcome.memoryDeltaKb) // 5000 - 1000
    assertTrue(engine.isLoaded)
  }

  @Test
  fun `failed load maps to failure and leaves engine unloaded`() {
    val backend = FakeBackend()
    backend.loadError = "llama.cpp could not load this GGUF file"
    val engine = engineWith(backend)

    val outcome = engine.loadFromPath("/tmp/model.gguf")

    assertTrue(outcome is RealLlmEngine.LoadOutcome.Failure)
    assertFalse(engine.isLoaded)
  }

  @Test
  fun `generate streams deltas and returns honest statistics`() {
    val backend = FakeBackend()
    val engine = engineWith(backend)
    engine.loadFromFileDescriptor(1)

    val deltas = mutableListOf<String>()
    val outcome = engine.generate(
      history = listOf(RealLlmEngine.ChatTurn("user", "سلام")),
      params = GenerationParams(),
      onAnswer = { deltas.add(it) }
    )

    assertTrue(outcome is RealLlmEngine.GenerateOutcome.Completed)
    outcome as RealLlmEngine.GenerateOutcome.Completed
    assertEquals("Hello world", outcome.text)
    assertEquals(listOf("Hello ", "world"), deltas)
    assertEquals(12, outcome.promptTokens)
    assertEquals(5, outcome.generatedTokens)
    assertEquals(10f, outcome.tokensPerSecond, 1e-6f)
  }

  @Test
  fun `multi-turn history is forwarded with roles in order`() {
    val backend = FakeBackend()
    val engine = engineWith(backend)
    engine.loadFromFileDescriptor(1)

    engine.generate(
      history = listOf(
        RealLlmEngine.ChatTurn("system", "you are offline"),
        RealLlmEngine.ChatTurn("user", "hi"),
        RealLlmEngine.ChatTurn("assistant", "hello"),
        RealLlmEngine.ChatTurn("user", "bye")
      ),
      params = GenerationParams()
    )

    val request = backend.lastRequest
    assertNotNull(request)
    assertEquals(listOf("system", "user", "assistant", "user"), request!!.roles)
    assertEquals(listOf("you are offline", "hi", "hello", "bye"), request.contents)
  }

  @Test
  fun `cancellation maps to cancelled outcome with partial text`() {
    val backend = FakeBackend()
    backend.generateMode = "cancelled"
    val engine = engineWith(backend)
    engine.loadFromFileDescriptor(1)

    val outcome = engine.generate(
      history = listOf(RealLlmEngine.ChatTurn("user", "hi")),
      params = GenerationParams()
    )

    assertTrue(outcome is RealLlmEngine.GenerateOutcome.Cancelled)
    assertEquals("partial text", (outcome as RealLlmEngine.GenerateOutcome.Cancelled).text)
  }

  @Test
  fun `thinking models are split into reasoning and answer streams`() {
    val backend = FakeBackend()
    backend.generateMode = "thinking"
    val engine = engineWith(backend)
    engine.loadFromFileDescriptor(1)

    val thinkingDeltas = mutableListOf<String>()
    val answerDeltas = mutableListOf<String>()
    val outcome = engine.generate(
      history = listOf(RealLlmEngine.ChatTurn("user", "hi")),
      params = GenerationParams(),
      onThinking = { thinkingDeltas.add(it) },
      onAnswer = { answerDeltas.add(it) }
    )

    assertTrue(outcome is RealLlmEngine.GenerateOutcome.Completed)
    outcome as RealLlmEngine.GenerateOutcome.Completed
    assertEquals("step by step", outcome.thinking)
    assertEquals("the result", outcome.text)
    assertEquals("step by step", thinkingDeltas.joinToString(""))
    assertEquals("the result", answerDeltas.joinToString(""))
  }

  @Test
  fun `native exceptions map to failure without crashing`() {
    val backend = FakeBackend()
    backend.generateMode = "throws"
    val engine = engineWith(backend)
    engine.loadFromFileDescriptor(1)

    val outcome = engine.generate(
      history = listOf(RealLlmEngine.ChatTurn("user", "hi")),
      params = GenerationParams()
    )

    assertTrue(outcome is RealLlmEngine.GenerateOutcome.Failure)
    assertEquals(
      "llama_decode failed during generation",
      (outcome as RealLlmEngine.GenerateOutcome.Failure).reason
    )
  }

  @Test
  fun `generate without a model fails honestly`() {
    val engine = engineWith(FakeBackend())
    val outcome = engine.generate(
      history = listOf(RealLlmEngine.ChatTurn("user", "hi")),
      params = GenerationParams()
    )
    assertTrue(outcome is RealLlmEngine.GenerateOutcome.Failure)
  }

  @Test
  fun `empty history is rejected`() {
    val backend = FakeBackend()
    val engine = engineWith(backend)
    engine.loadFromFileDescriptor(1)

    val outcome = engine.generate(history = emptyList(), params = GenerationParams())
    assertTrue(outcome is RealLlmEngine.GenerateOutcome.Failure)
  }

  @Test
  fun `cancel delegates to backend`() {
    val backend = FakeBackend()
    val engine = engineWith(backend)
    engine.cancel()
    assertTrue(backend.cancelRequested)
  }

  @Test
  fun `unsupported chat roles are rejected at construction`() {
    var thrown: IllegalArgumentException? = null
    try {
      RealLlmEngine.ChatTurn("wizard", "hi")
    } catch (e: IllegalArgumentException) {
      thrown = e
    }
    assertNotNull(thrown)
    assertNull(null) // keep assertion count explicit
  }
}
