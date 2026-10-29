package com.example.core.voice

/** Pure PCM conversion helpers, JVM-testable without Android. */
object PcmConvert {

  /** 16-bit PCM to the normalized [-1, 1) floats whisper.cpp expects. */
  fun shortsToFloats(samples: ShortArray): FloatArray {
    if (samples.isEmpty()) return FloatArray(0)
    return FloatArray(samples.size) { i ->
      (samples[i].toInt() / 32768f).coerceIn(-1f, 1f)
    }
  }
}
