package com.example.core.llm

import android.content.SharedPreferences

/**
 * Sampling and hardware knobs for one generation run.
 *
 * All values are clamped by [sanitized] before they reach native code so a
 * misbehaving caller can never ask for a context size or thread count that
 * would stress the phone.
 */
data class GenerationParams(
  val temperature: Float = DEFAULT_TEMPERATURE,
  val topP: Float = DEFAULT_TOP_P,
  val topK: Int = DEFAULT_TOP_K,
  val maxTokens: Int = DEFAULT_MAX_TOKENS,
  val repeatPenalty: Float = DEFAULT_REPEAT_PENALTY,
  val contextTokens: Int = DEFAULT_CONTEXT_TOKENS,
  val threads: Int = DEFAULT_THREADS
) {

  fun sanitized(): GenerationParams = copy(
    temperature = temperature.coerceIn(0f, 2f),
    topP = topP.coerceIn(0.05f, 1f),
    topK = topK.coerceIn(0, 200),
    maxTokens = maxTokens.coerceIn(16, 4096),
    repeatPenalty = repeatPenalty.coerceIn(1f, 2f),
    contextTokens = contextTokens.coerceIn(512, 8192),
    threads = threads.coerceIn(1, 8)
  )

  companion object {
    // Conservative defaults for a Redmi Note 14 Pro class device (Helio G100-Ultra).
    const val DEFAULT_TEMPERATURE = 0.7f
    const val DEFAULT_TOP_P = 0.9f
    const val DEFAULT_TOP_K = 40
    const val DEFAULT_MAX_TOKENS = 512
    const val DEFAULT_REPEAT_PENALTY = 1.1f
    const val DEFAULT_CONTEXT_TOKENS = 2048
    const val DEFAULT_THREADS = 4

    const val KEY_TEMPERATURE = "gen_temperature"
    const val KEY_TOP_P = "gen_top_p"
    const val KEY_TOP_K = "gen_top_k"
    const val KEY_MAX_TOKENS = "gen_max_tokens"
    const val KEY_REPEAT_PENALTY = "gen_repeat_penalty"
    const val KEY_CONTEXT_TOKENS = "gen_context_tokens"
    const val KEY_THREADS = "gen_threads"
  }
}

/** Persistence for [GenerationParams] in SharedPreferences. */
class GenerationParamsStore(private val prefs: SharedPreferences) {

  fun load(): GenerationParams = GenerationParams(
    temperature = prefs.getFloat(GenerationParams.KEY_TEMPERATURE, GenerationParams.DEFAULT_TEMPERATURE),
    topP = prefs.getFloat(GenerationParams.KEY_TOP_P, GenerationParams.DEFAULT_TOP_P),
    topK = prefs.getInt(GenerationParams.KEY_TOP_K, GenerationParams.DEFAULT_TOP_K),
    maxTokens = prefs.getInt(GenerationParams.KEY_MAX_TOKENS, GenerationParams.DEFAULT_MAX_TOKENS),
    repeatPenalty = prefs.getFloat(GenerationParams.KEY_REPEAT_PENALTY, GenerationParams.DEFAULT_REPEAT_PENALTY),
    contextTokens = prefs.getInt(GenerationParams.KEY_CONTEXT_TOKENS, GenerationParams.DEFAULT_CONTEXT_TOKENS),
    threads = prefs.getInt(GenerationParams.KEY_THREADS, GenerationParams.DEFAULT_THREADS)
  ).sanitized()

  fun save(params: GenerationParams) {
    val p = params.sanitized()
    prefs.edit()
      .putFloat(GenerationParams.KEY_TEMPERATURE, p.temperature)
      .putFloat(GenerationParams.KEY_TOP_P, p.topP)
      .putInt(GenerationParams.KEY_TOP_K, p.topK)
      .putInt(GenerationParams.KEY_MAX_TOKENS, p.maxTokens)
      .putFloat(GenerationParams.KEY_REPEAT_PENALTY, p.repeatPenalty)
      .putInt(GenerationParams.KEY_CONTEXT_TOKENS, p.contextTokens)
      .putInt(GenerationParams.KEY_THREADS, p.threads)
      .apply()
  }

  fun reset() {
    prefs.edit()
      .remove(GenerationParams.KEY_TEMPERATURE)
      .remove(GenerationParams.KEY_TOP_P)
      .remove(GenerationParams.KEY_TOP_K)
      .remove(GenerationParams.KEY_MAX_TOKENS)
      .remove(GenerationParams.KEY_REPEAT_PENALTY)
      .remove(GenerationParams.KEY_CONTEXT_TOKENS)
      .remove(GenerationParams.KEY_THREADS)
      .apply()
  }
}
