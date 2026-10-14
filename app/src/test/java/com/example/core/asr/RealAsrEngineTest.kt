package com.example.core.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [RealAsrEngine] against a fake [AsrBackend]. Native whisper
 * numerics are NOT covered here (the arm64 library cannot run on the JVM) —
 * failures are always surfaced honestly, never replaced by sample text.
 */
class RealAsrEngineTest {

  private open class FakeAsrBackend : AsrBackend {
    var available = true
    var loadError: String? = null
    var transcribeError: String? = null
    var loaded = false
    var emptyModelInfo = false
    var transcriptToReturn = "سلام دنیا"
    var languageToReturn = "fa"
    var lastSamplesSize = 0

    override fun isAvailable(): Boolean = available

    override fun loadFromFd(fd: Int): String? {
      if (loadError != null) return loadError
      loaded = true
      return null
    }

    override fun loadFromPath(path: String): String? {
      if (loadError != null) return loadError
      loaded = true
      return null
    }

    override fun unload() {
      loaded = false
    }

    override fun transcribe(
      samples: FloatArray,
      nThreads: Int,
      language: String?,
      translate: Boolean
    ): String? {
      if (!loaded) return "no whisper model is loaded"
      lastSamplesSize = samples.size
      return transcribeError
    }

    override fun transcript(): String = transcriptToReturn
    override fun lastLanguage(): String = languageToReturn
    override fun modelInfo(): Array<String> =
      if (loaded && !emptyModelInfo) arrayOf("whisper.cpp") else emptyArray()
  }

  private fun engine(backend: FakeAsrBackend, pss: LongArray = longArrayOf(500L, 2500L)): RealAsrEngine {
    var i = 0
    return RealAsrEngine(backend, pssReader = { pss[minOf(i++, pss.size - 1)] })
  }

  @Test
  fun `successful load reports label and memory delta`() {
    val backend = FakeAsrBackend()
    val eng = engine(backend)
    val outcome = eng.loadFromFileDescriptor(3)
    assertTrue(outcome is RealAsrEngine.LoadOutcome.Success)
    outcome as RealAsrEngine.LoadOutcome.Success
    assertEquals("whisper.cpp", outcome.modelLabel)
    assertEquals(2000L, outcome.memoryDeltaKb)
    assertTrue(eng.isLoaded)
  }

  @Test
  fun `load failure leaves engine unloaded`() {
    val backend = FakeAsrBackend()
    backend.loadError = "whisper.cpp could not load this model file"
    val eng = engine(backend)
    val outcome = eng.loadFromFileDescriptor(3)
    assertTrue(outcome is RealAsrEngine.LoadOutcome.Failure)
    assertFalse(eng.isLoaded)
  }

  @Test
  fun `label hint is used when backend has no label`() {
    val backend = FakeAsrBackend()
    backend.emptyModelInfo = true
    val eng = engine(backend)
    val outcome = eng.loadFromPath("/x/ggml-base.bin", labelHint = "whisper-base.gguf")
    assertEquals("whisper-base.gguf", (outcome as RealAsrEngine.LoadOutcome.Success).modelLabel)
  }

  @Test
  fun `successful transcription returns real text and language`() {
    val backend = FakeAsrBackend()
    val eng = engine(backend)
    eng.loadFromFileDescriptor(1)

    val outcome = eng.transcribe(FloatArray(16000)) // 1 second of audio

    assertTrue(outcome is RealAsrEngine.TranscribeOutcome.Success)
    outcome as RealAsrEngine.TranscribeOutcome.Success
    assertEquals("سلام دنیا", outcome.result.text)
    assertEquals("fa", outcome.result.language)
    assertEquals(1f, outcome.result.durationSeconds, 0.01f)
    assertEquals(16000, backend.lastSamplesSize)
  }

  @Test
  fun `transcription failure is reported, never fabricated`() {
    val backend = FakeAsrBackend()
    backend.transcribeError = "whisper_full failed"
    val eng = engine(backend)
    eng.loadFromFileDescriptor(1)

    val outcome = eng.transcribe(FloatArray(1024))
    assertTrue(outcome is RealAsrEngine.TranscribeOutcome.Failure)
    assertEquals("whisper_full failed", (outcome as RealAsrEngine.TranscribeOutcome.Failure).reason)
  }

  @Test
  fun `transcribe without model fails honestly`() {
    val eng = engine(FakeAsrBackend())
    val outcome = eng.transcribe(FloatArray(1024))
    assertTrue(outcome is RealAsrEngine.TranscribeOutcome.Failure)
  }

  @Test
  fun `empty audio is rejected`() {
    val backend = FakeAsrBackend()
    val eng = engine(backend)
    eng.loadFromFileDescriptor(1)
    val outcome = eng.transcribe(FloatArray(0))
    assertTrue(outcome is RealAsrEngine.TranscribeOutcome.Failure)
  }
}
