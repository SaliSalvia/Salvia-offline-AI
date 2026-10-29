package com.example.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCompactorTest {

  private fun messages(count: Int): List<ConversationCompactor.MessageRef> =
    (1..count).map { i ->
      ConversationCompactor.MessageRef(
        role = if (i % 2 == 0) "assistant" else "user",
        content = "message number $i with a reasonably long body to make the transcript grow."
      )
    }

  @Test
  fun belowThresholdNeverCompacts() {
    val plan = ConversationCompactor.plan(messages(4), currentChars = 100, charBudget = 100_000)
    assertFalse(plan.shouldCompact)
    assertTrue(plan.aged.isEmpty())
    assertEquals(4, plan.kept.size)
  }

  @Test
  fun emptyHistoryNeverCompacts() {
    val plan = ConversationCompactor.plan(emptyList(), currentChars = 100, charBudget = 100)
    assertFalse(plan.shouldCompact)
  }

  @Test
  fun aboveThresholdSplitsAgedAndKept() {
    val plan = ConversationCompactor.plan(messages(20), currentChars = 100, charBudget = 1_000)
    assertTrue(plan.shouldCompact)
    assertEquals(16, plan.aged.size)
    assertEquals(4, plan.kept.size)
    assertTrue(plan.aged.first().content.contains("number 1"))
    assertTrue(plan.kept.first().content.contains("number 17"))
    assertTrue(plan.kept.last().content.contains("number 20"))
  }

  @Test
  fun summaryPromptContainsPreviousSummaryAndAgedTurns() {
    val prompt = ConversationCompactor.buildSummaryPrompt(messages(5), "خلاصهٔ قبلی گفت‌وگو")
    assertTrue(prompt.contains("خلاصهٔ قبلی گفت‌وگو"))
    assertTrue(prompt.contains("number 1"))
    assertTrue(prompt.contains("number 5"))
  }
}
