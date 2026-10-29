package com.example.core.voice

/**
 * The seam every speech-synthesis implementation must satisfy. Today the only
 * implementation is Android's offline system TTS ([AudioEngine]); when a
 * neural offline voice (e.g. piper) ships, it plugs in here without touching
 * the ViewModel or the UI.
 */
interface TtsEngine {
  /**
   * Speaks [text]. [onFinished] must be invoked exactly once — when the
   * speech finishes, fails, or is stopped — so the UI can never get stuck.
   */
  fun speak(text: String, onFinished: (() -> Unit)? = null)

  /** Stops the current utterance; its completion is reported immediately. */
  fun stop()

  /** Playback speed. Implementations must clamp to a safe range. */
  fun setSpeechRate(rate: Float)

  /** True once the underlying engine is initialized and usable. */
  val isReady: Boolean
}

/**
 * Splits [text] into speakable chunks of at most [maxChunkLength] characters,
 * preferring sentence boundaries (".", "!", "?", "؟", "۔", newline). Long
 * sentences without any boundary are hard-split at the closest space. Pure
 * and JVM-testable: no Android dependencies.
 */
fun chunkTextForSpeech(text: String, maxChunkLength: Int = 400): List<String> {
  require(maxChunkLength > 0) { "maxChunkLength must be positive" }
  val trimmed = text.trim()
  if (trimmed.isEmpty()) return emptyList()
  if (trimmed.length <= maxChunkLength) return listOf(trimmed)

  // 1) Sentence-level pieces at natural boundaries.
  val sentences = mutableListOf<String>()
  val builder = StringBuilder()
  for (ch in trimmed) {
    builder.append(ch)
    if (ch == '.' || ch == '!' || ch == '?' || ch == '؟' || ch == '۔' || ch == '\n') {
      sentences.add(builder.toString())
      builder.setLength(0)
    }
  }
  if (builder.isNotEmpty()) sentences.add(builder.toString())

  // 2) Hard-split over-long sentences at the closest space.
  val atoms = mutableListOf<String>()
  for (sentence in sentences) {
    if (sentence.length <= maxChunkLength) {
      atoms.add(sentence)
      continue
    }
    var start = 0
    while (start < sentence.length) {
      var end = minOf(sentence.length, start + maxChunkLength)
      if (end < sentence.length) {
        val lastSpace = sentence.lastIndexOf(' ', end)
        if (lastSpace > start) end = lastSpace + 1
      }
      atoms.add(sentence.substring(start, end))
      start = end
    }
  }

  // 3) Group atoms back into chunks up to the limit.
  val chunks = mutableListOf<String>()
  val current = StringBuilder()
  for (atom in atoms) {
    if (current.isNotEmpty() && current.length + atom.length > maxChunkLength) {
      chunks.add(current.toString().trim())
      current.setLength(0)
    }
    current.append(atom)
  }
  if (current.isNotBlank()) chunks.add(current.toString().trim())
  return chunks
}
