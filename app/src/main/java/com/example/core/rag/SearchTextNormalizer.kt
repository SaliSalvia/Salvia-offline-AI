package com.example.core.rag

/**
 * Shared text normalizer for BM25 indexing and querying.
 *
 * Persian text is notoriously inconsistent across sources: the same word can be
 * written with Arabic ي/ك instead of Persian ی/ک, with or without ZWNJ
 * («می‌شود» vs «میشود»), with Arabic diacritics or tatweel, or with Persian/Arabic
 * digits instead of Latin ones. Without folding these variants, lexical retrieval
 * misses perfectly relevant chunks.
 */
object SearchTextNormalizer {

  fun normalize(text: String): String {
    if (text.isEmpty()) return text
    val sb = StringBuilder(text.length)
    for (ch in text) {
      when {
        // Tatweel (ـ) and Arabic diacritics (U+064B..U+0652) carry no lexical content.
        ch == '\u0640' -> {}
        ch >= '\u064B' && ch <= '\u0652' -> {}
        // ZWNJ (U+200C): «می‌شود» must match «میشود».
        ch == '\u200C' -> {}
        // Arabic letters frequently mistyped for their Persian counterparts.
        ch == '\u064A' -> sb.append('\u06CC') // ي → ی
        ch == '\u0643' -> sb.append('\u06A9') // ك → ک
        ch == '\u0629' -> sb.append('\u0647') // ة → ه
        // Persian (U+06F0..) and Arabic (U+0660..) digits → Latin digits.
        ch >= '\u06F0' && ch <= '\u06F9' -> sb.append('0' + (ch - '\u06F0'))
        ch >= '\u0660' && ch <= '\u0669' -> sb.append('0' + (ch - '\u0660'))
        else -> sb.append(ch)
      }
    }
    return sb.toString().lowercase()
  }

  private val tokenSplit = Regex("[^\\p{L}\\p{N}']+")

  /** Normalized word tokens; single characters (e.g. «و») carry no retrieval signal. */
  fun tokenize(text: String): List<String> =
    normalize(text).split(tokenSplit).filter { it.length > 1 }
}
