package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonPinkLight
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

sealed class MarkdownBlock {
  data class Paragraph(val text: String) : MarkdownBlock()
  data class Header(val level: Int, val text: String) : MarkdownBlock()
  data class Bullet(val text: String) : MarkdownBlock()
  data class Quote(val text: String) : MarkdownBlock()
  data class Code(val language: String, val code: String) : MarkdownBlock()
}

/** Glyph painted at the end of a live answer, like a terminal cursor. */
private const val STREAM_CARET = "\u258D"

/**
 * Incremental markdown parser built for token streaming.
 *
 * A streamed answer arrives in ~30 ms batches, so re-parsing the whole text 33
 * times per second re-allocated every line and every span of an ever-growing
 * answer. This parser behaves as a line-by-line state machine: finished lines
 * are folded into a committed block list once, and a single call only parses the
 * last, still-changing line. An open code fence is never committed, so a code
 * listing keeps rendering as one block while it streams.
 */
internal class IncrementalMarkdownParser {
  private var committedPrefix: String = ""
  private val blocks = mutableListOf<MarkdownBlock>()

  // State of a fence that is still open inside the committed region. It is rolled
  // back at the end of every parse, so a call always starts outside a fence.
  private var fenceOpen = false
  private var fenceLang = ""
  private val fenceBuffer = StringBuilder()
  private var fenceStartBlockCount = 0
  private var fenceStartOffset = 0

  fun parse(text: String): List<MarkdownBlock> {
    if (!text.startsWith(committedPrefix)) reset()

    // Only lines terminated by a newline are final; the last one may still grow.
    val completeEnd = text.lastIndexOf('\n') + 1
    var pos = committedPrefix.length
    fenceOpen = false

    while (pos < completeEnd) {
      val lineEnd = text.indexOf('\n', pos)
      if (lineEnd < 0 || lineEnd >= completeEnd) break
      val openingFence = !fenceOpen
      acceptLine(text.substring(pos, lineEnd))
      if (openingFence && fenceOpen) {
        fenceStartBlockCount = blocks.size
        fenceStartOffset = pos
      }
      pos = lineEnd + 1
    }

    if (fenceOpen) {
      // Keep the whole listing (from its opening fence) out of the cache.
      while (blocks.size > fenceStartBlockCount) blocks.removeAt(blocks.lastIndex)
      fenceBuffer.clear()
      fenceOpen = false
      pos = fenceStartOffset
    }
    committedPrefix = text.substring(0, pos)

    val tailBlocks = parseMarkdownBlocks(text.substring(pos))
    if (blocks.isEmpty()) return tailBlocks
    return ArrayList<MarkdownBlock>(blocks.size + tailBlocks.size).apply {
      addAll(blocks)
      addAll(tailBlocks)
    }
  }

  private fun reset() {
    committedPrefix = ""
    blocks.clear()
    fenceOpen = false
    fenceLang = ""
    fenceBuffer.clear()
    fenceStartBlockCount = 0
    fenceStartOffset = 0
  }

  private fun acceptLine(line: String) {
    val trimmed = line.trim()
    if (trimmed.startsWith("```")) {
      if (fenceOpen) {
        blocks.add(MarkdownBlock.Code(fenceLang, fenceBuffer.toString().trimEnd()))
        fenceBuffer.clear()
        fenceOpen = false
      } else {
        fenceOpen = true
        fenceLang = trimmed.removePrefix("```").trim()
      }
      return
    }

    if (fenceOpen) {
      fenceBuffer.append(line).append("\n")
      return
    }

    when {
      trimmed.startsWith("### ") -> blocks.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ")))
      trimmed.startsWith("## ") -> blocks.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ")))
      trimmed.startsWith("# ") -> blocks.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ")))
      trimmed.startsWith("- ") || trimmed.startsWith("• ") || trimmed.startsWith("* ") -> {
        val bulletText = trimmed.removePrefix("- ").removePrefix("• ").removePrefix("* ")
        blocks.add(MarkdownBlock.Bullet(bulletText))
      }
      trimmed.startsWith("> ") -> blocks.add(MarkdownBlock.Quote(trimmed.removePrefix("> ")))
      trimmed.isNotEmpty() -> blocks.add(MarkdownBlock.Paragraph(line))
      else -> {
        // Empty line
      }
    }
  }
}

@Composable
fun MarkdownContent(
  rawText: String,
  modifier: Modifier = Modifier,
  trailingCaret: Boolean = false
) {
  val parser = remember { IncrementalMarkdownParser() }
  val blocks = remember(rawText) { parser.parse(rawText) }
  val lastBlock = blocks.lastOrNull()
  val caretInline = trailingCaret &&
    (lastBlock is MarkdownBlock.Paragraph || lastBlock is MarkdownBlock.Bullet)

  Column(modifier = modifier.fillMaxWidth()) {
    blocks.forEachIndexed { index, block ->
      val caret = caretInline && index == blocks.lastIndex
      when (block) {
        is MarkdownBlock.Header -> {
          val fontSize = when (block.level) {
            1 -> 20.sp
            2 -> 17.sp
            else -> 15.sp
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = block.text,
            color = TextPink,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = (fontSize.value + 6).sp,
            modifier = Modifier.padding(vertical = 3.dp)
          )
        }
        is MarkdownBlock.Bullet -> {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 2.5.dp)
          ) {
            Text(
              text = "•",
              color = NeonPinkPrimary,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(end = 8.dp)
            )
            InlineMarkdownText(
              text = block.text,
              caret = caret,
              color = TextPrimary,
              fontSize = 14.sp,
              lineHeight = 23.sp
            )
          }
        }
        is MarkdownBlock.Quote -> {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp)
          ) {
            Box(
              modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NeonPinkPrimary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            InlineMarkdownText(
              text = block.text,
              caret = false,
              color = TextSecondary,
              fontSize = 13.5.sp,
              lineHeight = 21.sp
            )
          }
        }
        is MarkdownBlock.Code -> {
          Spacer(modifier = Modifier.height(4.dp))
          CodeBlockView(
            code = block.code,
            language = block.language
          )
          Spacer(modifier = Modifier.height(4.dp))
        }
        is MarkdownBlock.Paragraph -> {
          InlineMarkdownText(
            text = block.text,
            caret = caret,
            color = TextPrimary,
            fontSize = 14.sp,
            lineHeight = 23.sp,
            modifier = Modifier.padding(vertical = 3.5.dp)
          )
        }
      }
    }

    // Fallback caret: the answer is still empty, or it ends with a code block.
    if (trailingCaret && !caretInline) {
      Text(
        text = STREAM_CARET,
        color = NeonPinkPrimary,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 1.dp)
      )
    }
  }
}

/**
 * Inline-styled markdown text. The annotated string is memoised per text value,
 * so a recomposition caused by an unrelated state change no longer rebuilds the
 * spans of every visible paragraph.
 */
@Composable
private fun InlineMarkdownText(
  text: String,
  caret: Boolean,
  color: Color,
  fontSize: TextUnit,
  lineHeight: TextUnit,
  modifier: Modifier = Modifier
) {
  val annotated = remember(text, caret) { formatInlineMarkdown(text, caret) }
  Text(
    text = annotated,
    color = color,
    fontSize = fontSize,
    lineHeight = lineHeight,
    modifier = modifier
  )
}

internal fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
  if (text.isEmpty()) return emptyList()
  val lines = text.lines()
  val blocks = mutableListOf<MarkdownBlock>()
  var inCodeBlock = false
  var codeLang = ""
  val codeBuffer = StringBuilder()

  for (line in lines) {
    val trimmed = line.trim()
    if (trimmed.startsWith("```")) {
      if (inCodeBlock) {
        // End code block
        blocks.add(MarkdownBlock.Code(codeLang, codeBuffer.toString().trimEnd()))
        codeBuffer.clear()
        inCodeBlock = false
      } else {
        // Start code block
        inCodeBlock = true
        codeLang = trimmed.removePrefix("```").trim()
      }
      continue
    }

    if (inCodeBlock) {
      codeBuffer.append(line).append("\n")
      continue
    }

    when {
      trimmed.startsWith("### ") -> blocks.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ")))
      trimmed.startsWith("## ") -> blocks.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ")))
      trimmed.startsWith("# ") -> blocks.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ")))
      trimmed.startsWith("- ") || trimmed.startsWith("• ") || trimmed.startsWith("* ") -> {
        val bulletText = trimmed.removePrefix("- ").removePrefix("• ").removePrefix("* ")
        blocks.add(MarkdownBlock.Bullet(bulletText))
      }
      trimmed.startsWith("> ") -> blocks.add(MarkdownBlock.Quote(trimmed.removePrefix("> ")))
      trimmed.isNotEmpty() -> blocks.add(MarkdownBlock.Paragraph(line))
      else -> {
        // Empty line
      }
    }
  }

  if (inCodeBlock && codeBuffer.isNotEmpty()) {
    blocks.add(MarkdownBlock.Code(codeLang, codeBuffer.toString()))
  }

  return blocks
}

internal fun formatInlineMarkdown(input: String, caret: Boolean = false): AnnotatedString =
  buildAnnotatedString {
    val parts = input.split("**")
    var isBold = false
    for (part in parts) {
      if (isBold) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = NeonPinkLight)) {
          append(part)
        }
      } else {
        // Check inline code backticks
        val codeParts = part.split("`")
        var isCode = false
        for (cPart in codeParts) {
          if (isCode) {
            withStyle(
              SpanStyle(
                fontFamily = FontFamily.Monospace,
                color = TextPink,
                fontWeight = FontWeight.Medium
              )
            ) {
              append(" $cPart ")
            }
          } else {
            append(cPart)
          }
          isCode = !isCode
        }
      }
      isBold = !isBold
    }

    if (caret) {
      withStyle(SpanStyle(color = NeonPinkPrimary, fontWeight = FontWeight.Bold)) {
        append(STREAM_CARET)
      }
    }
  }
