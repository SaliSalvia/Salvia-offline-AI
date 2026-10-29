package com.example.core.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmConvertTest {

  @Test
  fun emptyInputConvertsToEmptyArray() {
    assertEquals(0, PcmConvert.shortsToFloats(ShortArray(0)).size)
  }

  @Test
  fun fullScaleValuesMapCorrectly() {
    val floats = PcmConvert.shortsToFloats(shortArrayOf(-32768, 32767, 0))
    assertEquals(-1.0f, floats[0], 1e-6f)
    assertEquals(0.99997f, floats[1], 1e-4f)
    assertEquals(0.0f, floats[2], 1e-7f)
  }

  @Test
  fun outputLengthMatchesInput() {
    val floats = PcmConvert.shortsToFloats(ShortArray(1024) { it.toShort() })
    assertEquals(1024, floats.size)
  }
}
