package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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

@Composable
fun MarkdownContent(
  rawText: String,
  modifier: Modifier = Modifier
) {
  val blocks = parseMarkdownBlocks(rawText)

  Column(modifier = modifier.fillMaxWidth()) {
    blocks.forEachIndexed { index, block ->
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
            modifier = Modifier.padding(vertical = 3.dp)
          )
        }
        is MarkdownBlock.Bullet -> {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 2.dp)
          ) {
            Text(
              text = "•",
              color = NeonPinkPrimary,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(end = 8.dp)
            )
            Text(
              text = formatInlineMarkdown(block.text),
              color = TextPrimary,
              fontSize = 14.sp,
              lineHeight = 22.sp
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
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NeonPinkPrimary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = formatInlineMarkdown(block.text),
              color = TextSecondary,
              fontSize = 13.5.sp,
              lineHeight = 20.sp
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
          Text(
            text = formatInlineMarkdown(block.text),
            color = TextPrimary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(vertical = 3.dp)
          )
        }
      }
    }
  }
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
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

private fun formatInlineMarkdown(input: String) = buildAnnotatedString {
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
}
