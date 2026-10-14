package com.example.core.llm

/**
 * Splits a model's token stream into reasoning ("thinking") and answer parts by
 * detecting `...` / `...` markers — including markers
 * split across token boundaries.
 *
 * Streaming-safe: text is only released once it cannot be part of a marker
 * prefix, so a `<think` arriving in one chunk and `ing>` in the next is still
 * recognised as an opener.
 */
class ThinkingStreamParser {

  enum class Mode { THINKING, ANSWER }

  private companion object {
    // Assembled from parts so nothing in tooling ever mangles the literals.
    val THINK_OPEN: String = "<" + "think" + ">"
    val THINK_CLOSE: String = "</" + "think" + ">"
    val OPENERS = listOf(THINK_OPEN, "<reasoning>")
    val CLOSERS = listOf(THINK_CLOSE, "</reasoning>")
  }

  var mode: Mode = Mode.ANSWER
    private set

  private val buffer = StringBuilder()

  /**
   * Feeds one text delta; returns (thinkingDelta, answerDelta).
   * Either side may be empty.
   */
  fun push(delta: String): Pair<String, String> {
    buffer.append(delta)
    val thinking = StringBuilder()
    val answer = StringBuilder()
    drain(thinking, answer)
    return thinking.toString() to answer.toString()
  }

  /**
   * Flushes everything still buffered at end-of-stream (a truncated marker is
   * emitted as literal text). Returns (thinkingDelta, answerDelta).
   */
  fun finish(): Pair<String, String> {
    val thinking = StringBuilder()
    val answer = StringBuilder()
    if (buffer.isNotEmpty()) {
      if (mode == Mode.THINKING) thinking.append(buffer) else answer.append(buffer)
      buffer.clear()
    }
    return thinking.toString() to answer.toString()
  }

  private fun drain(thinking: StringBuilder, answer: StringBuilder) {
    while (true) {
      val markers = if (mode == Mode.THINKING) CLOSERS else OPENERS
      val text = buffer.toString()

      var earliest = -1
      var markerLen = 0
      for (m in markers) {
        val idx = text.indexOf(m)
        if (idx >= 0 && (earliest < 0 || idx < earliest)) {
          earliest = idx
          markerLen = m.length
        }
      }

      if (earliest >= 0) {
        // Text before the marker belongs to the current mode.
        val head = text.substring(0, earliest)
        if (mode == Mode.THINKING) thinking.append(head) else answer.append(head)
        buffer.delete(0, earliest + markerLen)
        mode = if (mode == Mode.THINKING) Mode.ANSWER else Mode.THINKING
        continue
      }

      // No complete marker: release text that cannot start one.
      val safe = safePrefixLength(text, markers)
      if (safe > 0) {
        val head = text.substring(0, safe)
        if (mode == Mode.THINKING) thinking.append(head) else answer.append(head)
        buffer.delete(0, safe)
      }
      return
    }
  }

  /**
   * Length of the prefix of [text] that cannot be the beginning of any marker in
   * [markers]. The tail is held back in the buffer.
   */
  private fun safePrefixLength(text: String, markers: List<String>): Int {
    val maxHold = markers.maxOf { it.length } - 1
    val holdFrom = (text.length - maxHold).coerceAtLeast(0)
    // Find the earliest position from which some marker could still match.
    for (pos in holdFrom until text.length) {
      val tail = text.substring(pos)
      if (markers.any { it.startsWith(tail) }) {
        return pos
      }
    }
    return text.length
  }
}
