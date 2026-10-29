package com.example.core.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Text-to-speech only (Android system TTS, offline). Speech-to-text is a real
 * whisper.cpp pipeline in [com.example.core.asr.RealAsrEngine] — there is no
 * simulated transcription anywhere in this app.
 *
 * Long texts are split with [chunkTextForSpeech] and enqueued, because several
 * system TTS engines silently truncate or fail on very long single
 * utterances. Every [speak] call reports its completion exactly once — spoke
 * to the end, was stopped, failed, or was never ready at all — so the UI must
 * never be left in an eternal "speaking" state.
 */
class AudioEngine(private val context: Context) : TtsEngine {
  private var tts: TextToSpeech? = null
  private var isTtsInitialized = false
  private var pendingOnFinished: (() -> Unit)? = null
  private var lastChunkId: String? = null
  private val mainHandler = Handler(Looper.getMainLooper())

  init {
    tts = TextToSpeech(context) { status ->
      if (status == TextToSpeech.SUCCESS) {
        tts?.language = Locale.forLanguageTag("fa")
        isTtsInitialized = true
      }
    }
    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
      override fun onStart(utteranceId: String?) {}

      override fun onDone(utteranceId: String?) {
        // Only the last queued chunk completes the request; intermediate
        // chunks just hand over to the next one in the queue.
        if (utteranceId == lastChunkId) {
          notifyFinished()
        }
      }

      @Deprecated("Deprecated in Java")
      override fun onError(utteranceId: String?) {
        // Any failing chunk aborts the whole queue — report completion.
        tts?.stop()
        notifyFinished()
      }
    })
  }

  override fun speak(text: String, onFinished: (() -> Unit)?) {
    pendingOnFinished = onFinished
    val engine = tts
    if (!isTtsInitialized || engine == null) {
      // No system TTS engine installed (or still initializing): report
      // completion immediately instead of leaving the UI waiting forever.
      notifyFinished()
      return
    }
    engine.stop()
    val chunks = chunkTextForSpeech(text)
    if (chunks.isEmpty()) {
      notifyFinished()
      return
    }
    lastChunkId = CHUNK_ID_PREFIX + (chunks.size - 1)
    chunks.forEachIndexed { index, chunk ->
      engine.speak(chunk, TextToSpeech.QUEUE_ADD, null, CHUNK_ID_PREFIX + index)
    }
  }

  override fun stop() {
    tts?.stop()
    notifyFinished()
  }

  override fun setSpeechRate(rate: Float) {
    tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
  }

  override val isReady: Boolean
    get() = isTtsInitialized && tts != null

  /** Compatibility aliases so existing call sites keep working. */
  fun speakText(text: String, onFinished: (() -> Unit)? = null) = speak(text, onFinished)
  fun stopSpeech() = stop()

  /** Runs the stored completion once on the main thread; later calls are no-ops. */
  private fun notifyFinished() {
    val callback = pendingOnFinished
    pendingOnFinished = null
    lastChunkId = null
    callback?.let { mainHandler.post(it) }
  }

  private companion object {
    const val CHUNK_ID_PREFIX = "DEEP_GGUF_TTS_"
  }
}
