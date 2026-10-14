package com.example.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.math.sin

/**
 * Text-to-speech only (Android system TTS, offline). Speech-to-text is a real
 * whisper.cpp pipeline in [com.example.core.asr.RealAsrEngine] — there is no
 * simulated transcription anywhere in this app.
 */
class AudioEngine(private val context: Context) {
  private var tts: TextToSpeech? = null
  private var isTtsInitialized = false
  private var audioTrack: AudioTrack? = null
  private var isSynthesizing = false

  init {
    tts = TextToSpeech(context) { status ->
      if (status == TextToSpeech.SUCCESS) {
        tts?.language = Locale.forLanguageTag("fa")
        isTtsInitialized = true
      }
    }
  }

  fun speakText(text: String, onFinished: (() -> Unit)? = null) {
    if (isTtsInitialized && tts != null) {
      tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "DEEP_GGUF_TTS")
    } else {
      // Fallback native sound synthesis
      playBeepTone()
    }
  }

  fun stopSpeech() {
    tts?.stop()
    try {
      audioTrack?.stop()
      audioTrack?.release()
      audioTrack = null
    } catch (_: Exception) {}
    isSynthesizing = false
  }

  private fun playBeepTone() {
    try {
      val sampleRate = 22050
      val numSamples = sampleRate / 2
      val sample = DoubleArray(numSamples)
      val generatedSnd = ByteArray(2 * numSamples)

      for (i in 0 until numSamples) {
        sample[i] = sin(2 * Math.PI * i / (sampleRate / 440.0))
      }

      var idx = 0
      for (dVal in sample) {
        val valShort = (dVal * 32767).toInt().toShort()
        generatedSnd[idx++] = (valShort.toInt() and 0x00ff).toByte()
        generatedSnd[idx++] = ((valShort.toInt() and 0xff00) ushr 8).toByte()
      }

      audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setBufferSizeInBytes(generatedSnd.size)
        .build()

      audioTrack?.write(generatedSnd, 0, generatedSnd.size)
      audioTrack?.play()
    } catch (_: Exception) {}
  }
}
