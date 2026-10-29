package com.example.core.llm

/**
 * Pure planning logic for long-conversation compaction.
 *
 * [com.example.viewmodel.MainViewModel]'s history window only ever sent the last
 * 12 turns to the model; anything older silently disappeared from the prompt.
 * This planner decides when the transcript has outgrown a fraction of the prompt
 * budget and splits it into "aged" turns (to be folded into a running LLM summary)
 * and "kept" turns (sent verbatim). The summary generation itself happens in the
 * ViewModel with the real engine; everything here is JVM-testable without Android.
 */
object ConversationCompactor {

  data class MessageRef(val role: String, val content: String)

  data class Plan(
    val shouldCompact: Boolean,
    /** Oldest→newest messages that must be folded into the running summary. */
    val aged: List<MessageRef>,
    /** Oldest→newest messages kept verbatim after compaction. */
    val kept: List<MessageRef>
  )

  const val DEFAULT_TRIGGER_FRACTION = 0.62f
  const val DEFAULT_KEEP_LAST_TURNS = 4

  /**
   * @param priorMessages the session transcript, oldest→newest
   * @param currentChars  length of the user turn about to be sent
   * @param charBudget    the prompt character budget actually available in context
   */
  fun plan(
    priorMessages: List<MessageRef>,
    currentChars: Int,
    charBudget: Int,
    keepLastTurns: Int = DEFAULT_KEEP_LAST_TURNS,
    triggerFraction: Float = DEFAULT_TRIGGER_FRACTION
  ): Plan {
    val usable = priorMessages.filter { it.role == "user" || it.role == "assistant" }
    if (usable.isEmpty()) return Plan(shouldCompact = false, aged = emptyList(), kept = emptyList)

    val totalChars = usable.sumOf { it.content.length + 16 } + currentChars
    val threshold = (charBudget * triggerFraction).toInt()
    val should = totalChars > threshold && usable.size > keepLastTurns
    if (!should) return Plan(shouldCompact = false, aged = emptyList(), kept = usable)

    return Plan(
      shouldCompact = true,
      aged = usable.dropLast(keepLastTurns),
      kept = usable.takeLast(keepLastTurns)
    )
  }

  /** Persian summarization prompt folding the aged turns plus the previous summary. */
  fun buildSummaryPrompt(aged: List<MessageRef>, previousSummary: String?): String {
    val sb = StringBuilder()
    sb.append("شما دستیار خلاصه‌سازی گفت‌وگو هستید. نکات کلیدی گفت‌وگوی زیر را در حداکثر ۲۵۰ واژه به فارسی خلاصه کن؛ نام‌ها، اعداد و تصمیم‌های مهم را حفظ کن. فقط خلاصه را بنویس.\n\n")
    previousSummary?.takeIf { it.isNotBlank() }?.let {
      sb.append("[خلاصهٔ قبلی گفت‌وگو]\n").append(it.take(4_000)).append("\n\n")
    }
    sb.append("[پیام‌های قدیمی گفت‌وگو]\n")
    var budget = 12_000
    for (m in aged) {
      if (budget <= 0) break
      val line = "«${m.role}»: ${m.content}"
      sb.append(line.take(budget)).append('\n')
      budget -= line.length
    }
    return sb.toString()
  }
}
