package com.example.core.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Live microphone capture for real (whisper.cpp) transcription.
 *
 * Records 16 kHz mono 16-bit PCM — the exact input format RealAsrEngine expects —
 * on a dedicated reader thread, with a hard cap matching PcmDecoder's guard so a
 * forgotten recording cannot exhaust memory.
 */
class MicRecorder(
  private val sampleRate: Int = 16_000,
  private val maxDurationSeconds: Int = 600
) {
  @Volatile
  var isRecording: Boolean = false
    private set

  private var recorder: AudioRecord? = null
  private var readerThread: Thread? = null
  private val chunks = mutableListOf<ShortArray>()
  private var recordedShorts: Int = 0

  /** Caller must hold RECORD_AUDIO; returns false when the mic cannot be opened. */
  @SuppressLint("MissingPermission")
  fun start(): Boolean {
    if (isRecording) return true
    val minBuffer = AudioRecord.getMinBufferSize(
      sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    if (minBuffer <= 0) return false
    val rec = try {
      AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        maxOf(minBuffer, 4096)
      )
    } catch (_: Exception) {
      return false
    }
    if (rec.state != AudioRecord.STATE_INITIALIZED) {
      rec.release()
      return false
    }

    synchronized(this) {
      chunks.clear()
      recordedShorts = 0
    }
    isRecording = true
    recorder = rec
    rec.startRecording()

    val maxShorts = sampleRate * maxDurationSeconds
    readerThread = Thread {
      val chunk = ShortArray(1600) // ~100 ms at 16 kHz
      while (isRecording) {
        val n = rec.read(chunk, 0, chunk.size)
        if (n <= 0) break
        synchronized(this) {
          if (recordedShorts + n > maxShorts) return@synchronized
          chunks.add(chunk.copyOf(n))
          recordedShorts += n
        }
      }
    }.apply { start() }
    return true
  }

  /** Stops capture and returns normalized [-1, 1) floats, empty on failure. */
  fun stop(): FloatArray {
    isRecording = false
    try {
      readerThread?.join(2_000)
    } catch (_: InterruptedException) {
    }
    readerThread = null
    recorder?.let { rec ->
      try {
        rec.stop()
      } catch (_: Exception) {
      }
      rec.release()
    }
    recorder = null

    val all = synchronized(this) {
      val out = ShortArray(recordedShorts)
      var pos = 0
      for (c in chunks) {
        System.arraycopy(c, 0, out, pos, c.size)
        pos += c.size
      }
      chunks.clear()
      recordedShorts = 0
      out
    }
    return PcmConvert.shortsToFloats(all)
  }
}
