package com.example.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingStreamParserTest {

  // Assembled from parts so tooling never mangles the tag literals.
  private val tOpen = "<" + "think" + ">"
  private val tClose = "</" + "think" + ">"

  private fun runAll(vararg chunks: String): Pair<String, String> {
    val parser = ThinkingStreamParser()
    val thinking = StringBuilder()
    val answer = StringBuilder()
    for (c in chunks) {
      val (t, a) = parser.push(c)
      thinking.append(t)
      answer.append(a)
    }
    val (ft, fa) = parser.finish()
    thinking.append(ft)
    answer.append(fa)
    return thinking.toString() to answer.toString()
  }

  @Test
  fun `plain answer without markers is all answer`() {
    val (thinking, answer) = runAll("hello ", "world")
    assertEquals("", thinking)
    assertEquals("hello world", answer)
  }

  @Test
  fun `think tags split thinking from answer`() {
    val (thinking, answer) = runAll(tOpen + "step 1 step 2" + tClose, "The answer is 42.")
    assertEquals("step 1 step 2", thinking)
    assertEquals("The answer is 42.", answer)
  }

  @Test
  fun `reasoning tags also work`() {
    val (thinking, answer) = runAll("<reasoning>hmm", " tricky</reasoning>done")
    assertEquals("hmm tricky", thinking)
    assertEquals("done", answer)
  }

  @Test
  fun `marker split across chunks is recognised`() {
    val (thinking, answer) = runAll("<" + "thi", "nk" + ">deep", " thought</" + "thi", "nk" + ">out")
    assertEquals("deep thought", thinking)
    assertEquals("out", answer)
  }

  @Test
  fun `text before the first marker is answer`() {
    val (thinking, answer) = runAll("intro " + "<" + "thi", "nk" + ">after")
    // "intro " is answer; text after the opener stays thinking until a closer.
    assertEquals("intro ", answer)
    assertEquals("after", thinking)
  }

  @Test
  fun `truncated marker at end of stream is emitted literally`() {
    val (thinking, answer) = runAll(tOpen + "incomplete <thi")
    assertEquals("incomplete <thi", thinking)
    assertEquals("", answer)
  }

  @Test
  fun `multiple thinking blocks are preserved`() {
    val (thinking, answer) = runAll(
      tOpen + "a" + tClose,
      "ans1",
      tOpen + "b" + tClose,
      "ans2"
    )
    assertEquals("ab", thinking)
    assertEquals("ans1ans2", answer)
  }

  @Test
  fun `streaming emission happens before finish`() {
    val parser = ThinkingStreamParser()
    val (t1, a1) = parser.push("plain text")
    assertEquals("", t1)
    assertTrue(a1.length >= 9)
  }
}
