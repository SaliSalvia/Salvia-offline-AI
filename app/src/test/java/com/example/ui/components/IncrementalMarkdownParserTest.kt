package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementalMarkdownParserTest {

  private val sample = buildString {
    appendLine("# عنوان اصلی")
    appendLine()
    appendLine("این یک پاراگراف **پررنگ** با `کد` درون‌خطی است.")
    appendLine()
    appendLine("- مورد اول")
    appendLine("- مورد دوم با **تأکید**")
    appendLine()
    appendLine("> نقل‌قول کوتاه")
    appendLine()
    appendLine("```kotlin")
    appendLine("fun main() {")
    appendLine()
    appendLine("  println(\"خط خالی بالا نباید بلوک کد را بشکند\")")
    appendLine("}")
    appendLine("```")
    appendLine()
    appendLine("## بخش دوم")
    appendLine()
    appendLine("پاراگراف پایانی بدون خط جدید.")
  }

  /** Sanity: the corpus really contains the tricky parts under test. */
  @Test
  fun corpusHasFenceWithInnerBlankLine() {
    val blocks = parseMarkdownBlocks(sample)
    val code = blocks.filterIsInstance<MarkdownBlock.Code>().single()
    assertEquals("kotlin", code.language)
    assertEquals(true, code.code.contains("println"))
    // The blank line inside the fence must survive as part of the code block.
    assertEquals(true, code.code.contains("\n\n"))
    assertEquals(2, blocks.filterIsInstance<MarkdownBlock.Header>().size)
    assertEquals(2, blocks.filterIsInstance<MarkdownBlock.Bullet>().size)
  }

  /**
   * Streaming a text prefix by prefix must produce exactly the same blocks as a
   * cold full parse of that prefix; otherwise cached paragraphs would drift.
   */
  @Test
  fun streamingPrefixesMatchColdParse() {
    val parser = IncrementalMarkdownParser()
    for (end in 0..sample.length) {
      val prefix = sample.substring(0, end)
      assertEquals("prefix length $end", parseMarkdownBlocks(prefix), parser.parse(prefix))
    }
  }

  @Test
  fun freshParserMatchesFullParseForEveryPrefix() {
    for (end in 0..sample.length) {
      val prefix = sample.substring(0, end)
      assertEquals("prefix length $end", parseMarkdownBlocks(prefix), IncrementalMarkdownParser().parse(prefix))
    }
  }

  @Test
  fun replacingTheTextResetsCachedBlocks() {
    val parser = IncrementalMarkdownParser()
    parser.parse("# اول\n\nمتن نخست")
    val second = "سلام\n\n- یک\n- دو\n\nپایان"
    assertEquals(parseMarkdownBlocks(second), parser.parse(second))
    // Shrinking the same buffer (edit/regenerate) must also be handled.
    val third = "# سومی\n\nمتن"
    assertEquals(parseMarkdownBlocks(third), parser.parse(third))
  }

  @Test
  fun repeatedIdenticalInputIsStable() {
    val parser = IncrementalMarkdownParser()
    val first = parser.parse(sample)
    val second = parser.parse(sample)
    assertEquals(first, second)
    assertEquals(parseMarkdownBlocks(sample), second)
  }

  @Test
  fun caretIsAppendedOnlyWhenRequested() {
    val plain = formatInlineMarkdown("سلام")
    val withCaret = formatInlineMarkdown("سلام", caret = true)
    assertEquals("سلام", plain.text)
    assertEquals("سلام\u258D", withCaret.text)
  }

  @Test
  fun emptyTextProducesNoBlocks() {
    assertEquals(emptyList<MarkdownBlock>(), parseMarkdownBlocks(""))
    assertEquals(emptyList<MarkdownBlock>(), IncrementalMarkdownParser().parse(""))
  }
}
