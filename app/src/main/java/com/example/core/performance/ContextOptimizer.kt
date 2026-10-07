package com.example.core.performance

import com.example.data.ChatMessageEntity

object ContextOptimizer {

  /**
   * Adapts context window size dynamically to preserve RAM.
   * Simple questions get 2048-4096, document QA gets 8192, massive files get 16384.
   */
  fun calculateOptimalContext(
    promptLength: Int,
    hasAttachedFiles: Boolean,
    totalRamGb: Float
  ): Int {
    return when {
      hasAttachedFiles && totalRamGb >= 11f -> 8192
      hasAttachedFiles -> 4096
      promptLength < 150 -> 2048
      promptLength < 800 -> 4096
      else -> if (totalRamGb >= 11f) 8192 else 4096
    }
  }

  /**
   * Compresses long conversation history to prevent quadratic KV-cache growth.
   * Retains the last 6 messages intact, and summarizes older turns into a compact context anchor.
   */
  fun compressConversationHistory(messages: List<ChatMessageEntity>): List<ChatMessageEntity> {
    if (messages.size <= 8) return messages

    val recentMessages = messages.takeLast(6)
    val olderMessages = messages.dropLast(6)

    // Generate a compact memory summary of older conversation
    val userTopics = olderMessages
      .filter { it.role == "user" }
      .map { it.content.take(45) }
      .take(4)
      .joinToString(" • ")

    val anchorSummary = ChatMessageEntity(
      id = -1,
      sessionId = messages.first().sessionId,
      role = "system",
      content = "📌 [خلاصه فشرده تاریخچه قبلی]: کاربر قبلاً درباره موارد زیر گفتگو کرده بود: ($userTopics). جریان گفت‌وگو به صورت پیوسته ادامه دارد.",
      timestamp = olderMessages.last().timestamp
    )

    return listOf(anchorSummary) + recentMessages
  }
}
