package com.example.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the pure [chunkTextForSpeech] splitter that keeps long texts
 * within system TTS engine limits without losing any content.
 */
class TtsChunkerTest {

  @Test
  fun emptyOrBlankTextProducesNoChunks() {
    assertTrue(chunkTextForSpeech("").isEmpty())
    assertTrue(chunkTextForSpeech("   \n\t ").isEmpty())
  }

  @Test
  fun shortTextIsReturnedAsSingleChunk() {
    assertEquals(listOf("سلام دنیا"), chunkTextForSpeech("  سلام دنیا  "))
  }

  @Test
  fun longPersianTextSplitsAtSentenceBoundariesWithoutLoss() {
    val sentence = "این یک جملهٔ آزمایشی برای تقسیم متن بلند است."
    val text = (1..40).joinToString(" ") { sentence }
    val chunks = chunkTextForSpeech(text)
    assertTrue("expected multiple chunks", chunks.size > 1)
    assertTrue("all chunks must respect the limit", chunks.all { it.length <= 400 })
    val originalLetters = text.filterNot { it == ' ' }
    val chunkedLetters = chunks.joinToString("").filterNot { it == ' ' }
    assertEquals(originalLetters, chunkedLetters)
  }

  @Test
  fun textWithoutBoundariesIsHardSplitAtSpaces() {
    val text = (1..120).joinToString(" ") { "abcdefgh" }
    val chunks = chunkTextForSpeech(text)
    assertTrue(chunks.size > 1)
    assertTrue(chunks.all { it.length <= 400 })
    val originalLetters = text.filterNot { it == ' ' }
    val chunkedLetters = chunks.joinToString("").filterNot { it == ' ' }
    assertEquals(originalLetters, chunkedLetters)
  }

  @Test
  fun respectsCustomMaxLength() {
    val text = "aaaaaaaaaa. bbbbbbbbbb. cccccccccc."
    val chunks = chunkTextForSpeech(text, 12)
    assertEquals(3, chunks.size)
    assertTrue(chunks.all { it.length <= 12 })
  }
}
