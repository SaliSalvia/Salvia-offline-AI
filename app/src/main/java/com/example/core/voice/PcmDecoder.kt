package com.example.core.voice

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes an audio file (m4a/aac/mp3/wav/ogg — anything MediaExtractor supports)
 * into 16 kHz mono float32 PCM, the exact format whisper.cpp consumes.
 *
 * Hardware-safe caps: decoding stops at [MAX_MINUTES] of audio so a huge file
 * can never exhaust memory.
 */
object PcmDecoder {

  const val TARGET_SAMPLE_RATE = 16000
  const val MAX_MINUTES = 10

  data class DecodedAudio(val samples: FloatArray, val sourceSampleRate: Int) {
    val durationSeconds: Float get() = samples.size / TARGET_SAMPLE_RATE.toFloat()
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = samples.size
  }

  /** Returns null when the file has no decodable audio track or decoding fails. */
  fun decodeTo16kMono(context: Context, uri: Uri): DecodedAudio? {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    return try {
      extractor.setDataSource(context, uri, null)

      var trackIndex = -1
      var inputFormat: MediaFormat? = null
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("audio/")) {
          trackIndex = i
          inputFormat = format
          break
        }
      }
      if (trackIndex < 0 || inputFormat == null) return null
      extractor.selectTrack(trackIndex)

      val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
      codec = MediaCodec.createDecoderByType(mime)
      codec.configure(inputFormat, null, null, 0)
      codec.start()

      // Accumulate raw PCM16 samples at the source rate/channels first.
      val rawPcm = ArrayList<Short>(1 shl 16)
      val bufferInfo = MediaCodec.BufferInfo()
      var inputDone = false
      var outputDone = false
      val maxRawSamples = MAX_MINUTES * 60 * 48_000 // hard cap even for 48 kHz sources

      while (!outputDone && rawPcm.size < maxRawSamples) {
        if (!inputDone) {
          val inIndex = codec.dequeueInputBuffer(10_000)
          if (inIndex >= 0) {
            val inBuf = codec.getInputBuffer(inIndex)!!
            val sampleSize = extractor.readSampleData(inBuf, 0)
            if (sampleSize < 0) {
              codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }

        val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
        when {
          outIndex >= 0 -> {
            val outBuf = codec.getOutputBuffer(outIndex)!!
            if (bufferInfo.size > 0) {
              val pcm = outBuf.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
              val count = min(pcm.remaining(), maxRawSamples - rawPcm.size)
              for (i in 0 until count) {
                rawPcm.add(pcm.get())
              }
            }
            codec.releaseOutputBuffer(outIndex, false)
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
              outputDone = true
            }
          }
          outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            // Format may refine sample-rate/channel info; keep decoding.
          }
        }
      }

      var sampleRate = 44_100
      var channels = 1
      try {
        val outFormat = codec.outputFormat
        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
      } catch (_: Exception) {
      }

      if (rawPcm.isEmpty()) return null

      // Downmix to mono.
      val mono = FloatArray(rawPcm.size / channels)
      for (i in mono.indices) {
        var sum = 0f
        for (c in 0 until channels) {
          sum += rawPcm[i * channels + c] / 32768f
        }
        mono[i] = sum / channels
      }

      // Linear resample to 16 kHz.
      val resampled = resampleLinear(mono, sampleRate, TARGET_SAMPLE_RATE)
      DecodedAudio(samples = resampled, sourceSampleRate = sampleRate)
    } catch (_: Exception) {
      null
    } finally {
      try {
        codec?.stop()
        codec?.release()
      } catch (_: Exception) {
      }
      try {
        extractor.release()
      } catch (_: Exception) {
      }
    }
  }

  internal fun resampleLinear(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
    if (fromRate == toRate || input.isEmpty()) return input
    val ratio = fromRate.toDouble() / toRate.toDouble()
    val outLength = (input.size / ratio).roundToInt().coerceAtLeast(1)
    val out = FloatArray(outLength)
    for (i in 0 until outLength) {
      val pos = i * ratio
      val idx = pos.toInt().coerceAtMost(input.size - 1)
      val next = min(idx + 1, input.size - 1)
      val frac = (pos - idx).toFloat().coerceIn(0f, 1f)
      out[i] = input[idx] * (1f - frac) + input[next] * frac
    }
    return out
  }
}
