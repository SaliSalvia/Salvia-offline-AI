package com.example.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationParamsTest {

  @Test
  fun `defaults stay inside hardware-safe ranges`() {
    val p = GenerationParams().sanitized()
    assertEquals(GenerationParams.DEFAULT_TEMPERATURE, p.temperature, 1e-6f)
    assertTrue(p.contextTokens in 512..8192)
    assertTrue(p.threads in 1..8)
    assertTrue(p.maxTokens in 16..4096)
  }

  @Test
  fun `sanitized clamps hostile values`() {
    val p = GenerationParams(
      temperature = 99f,
      topP = 0f,
      topK = -50,
      maxTokens = 1_000_000,
      repeatPenalty = 0.1f,
      contextTokens = 1,
      threads = 64
    ).sanitized()

    assertEquals(2f, p.temperature, 1e-6f)
    assertEquals(0.05f, p.topP, 1e-6f)
    assertEquals(0, p.topK)
    assertEquals(4096, p.maxTokens)
    assertEquals(1f, p.repeatPenalty, 1e-6f)
    assertEquals(512, p.contextTokens)
    assertEquals(8, p.threads)
  }

  @Test
  fun `sanitized clamps values below the lower bound`() {
    val p = GenerationParams(
      temperature = -1f,
      topP = -3f,
      repeatPenalty = 10f,
      contextTokens = 100_000
    ).sanitized()

    assertEquals(0f, p.temperature, 1e-6f)
    assertEquals(0.05f, p.topP, 1e-6f)
    assertEquals(2f, p.repeatPenalty, 1e-6f)
    assertEquals(8192, p.contextTokens)
  }
}
