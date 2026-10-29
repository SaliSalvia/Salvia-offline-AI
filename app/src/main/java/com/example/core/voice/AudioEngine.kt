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
 * Every [speakText] call reports its [onFinished] callback exactly once,
 * whether the engine spoke to the end, was stopped, failed, or was never
 * ready at all — the UI must never be left in an eternal "speaking" state.
 */
class AudioEngine(private val context: Context) {
  private var tts: TextToSpeech? = null
  private var isTtsInitialized = false
  private var pendingOnFinished: (() -> Unit)? = null
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
        if (utteranceId == UTTERANCE_ID) {
          notifyFinished()
        }
      }

      @Deprecated("Deprecated in Java")
      override fun onError(utteranceId: String?) {
        if (utteranceId == UTTERANCE_ID) {
          notifyFinished()
        }
      }
    })
  }

  fun speakText(text: String, onFinished: (() -> Unit)? = null) {
    pendingOnFinished = onFinished
    if (isTtsInitialized && tts != null) {
      tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    } else {
      // No system TTS engine installed (or still initializing): report
      // completion immediately instead of playing a mystery beep while the
      // UI waits forever for a callback that would never arrive.
      notifyFinished()
    }
  }

  fun stopSpeech() {
    tts?.stop()
    notifyFinished()
  }

  /** Runs the stored completion once on the main thread; later calls are no-ops. */
  private fun notifyFinished() {
    val callback = pendingOnFinished
    pendingOnFinished = null
    callback?.let { mainHandler.post(it) }
  }

  private companion object {
    const val UTTERANCE_ID = "DEEP_GGUF_TTS"
  }
}
