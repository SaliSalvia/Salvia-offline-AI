package com.example.gguf

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GgufParser {
  private const val TAG = "GgufParser"
  private const val GGUF_MAGIC = 0x46554747 // 'G', 'G', 'U', 'F' in Little Endian

  // GGUF Metadata Value Types
  private const val GGUF_TYPE_UINT8 = 0
  private const val GGUF_TYPE_INT8 = 1
  private const val GGUF_TYPE_UINT16 = 2
  private const val GGUF_TYPE_INT16 = 3
  private const val GGUF_TYPE_UINT32 = 4
  private const val GGUF_TYPE_INT32 = 5
  private const val GGUF_TYPE_FLOAT32 = 6
  private const val GGUF_TYPE_BOOL = 7
  private const val GGUF_TYPE_STRING = 8
  private const val GGUF_TYPE_ARRAY = 9
  private const val GGUF_TYPE_UINT64 = 10
  private const val GGUF_TYPE_INT64 = 11
  private const val GGUF_TYPE_FLOAT64 = 12

  fun parseFromUri(context: Context, uri: Uri, fallbackSizeFormatted: String): GgufMetadata {
    return try {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        parseFromStream(stream, fallbackSizeFormatted)
      } ?: GgufMetadata(
        isValid = false,
        errorMessage = "Cannot open file stream from Uri"
      )
    } catch (e: Exception) {
      Log.e(TAG, "Error parsing GGUF uri: $uri", e)
      GgufMetadata(
        isValid = false,
        errorMessage = "GGUF parse failed: ${e.message}"
      )
    }
  }

  fun parseFromStream(stream: InputStream, fileSizeFormatted: String): GgufMetadata {
    return try {
      val headerBytes = ByteArray(4)
      if (readFully(stream, headerBytes) != 4) {
        return GgufMetadata(isValid = false, errorMessage = "File too small")
      }

      val magicBuffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
      val magic = magicBuffer.int

      if (magic != GGUF_MAGIC) {
        // If not exact magic, could be GGML or raw weights, or mock file
        return GgufMetadata(
          isValid = false,
          errorMessage = "Not a valid GGUF file (Magic mismatch: 0x${Integer.toHexString(magic)})"
        )
      }

      // Read Version (uint32)
      val verBuffer = ByteBuffer.wrap(readExactBytes(stream, 4)).order(ByteOrder.LITTLE_ENDIAN)
      val version = verBuffer.int

      // Read Tensor Count (uint64)
      val tensorBuffer = ByteBuffer.wrap(readExactBytes(stream, 8)).order(ByteOrder.LITTLE_ENDIAN)
      val tensorCount = tensorBuffer.long

      // Read KV Count (uint64)
      val kvBuffer = ByteBuffer.wrap(readExactBytes(stream, 8)).order(ByteOrder.LITTLE_ENDIAN)
      val kvCount = kvBuffer.long

      val kvMap = mutableMapOf<String, String>()
      var arch = "unknown"
      var modelName = "GGUF Model"
      var contextLen = 4096
      var embLen = 4096
      var blockCount = 28
      var headCount = 16
      var fileType = 12 // Default to Q4_K_M

      // Parse metadata keys (limit to first 120 keys to avoid excessive stream reads)
      val maxKeysToRead = minOf(kvCount, 120L)
      for (i in 0 until maxKeysToRead) {
        val key = readGgufString(stream) ?: break
        val typeBuf = ByteBuffer.wrap(readExactBytes(stream, 4)).order(ByteOrder.LITTLE_ENDIAN)
        val valueType = typeBuf.int
        val valueStr = readGgufValueAsString(stream, valueType)

        if (valueStr != null) {
          kvMap[key] = valueStr
          when {
            key == "general.architecture" -> arch = valueStr
            key == "general.name" -> modelName = valueStr
            key.endsWith(".context_length") -> contextLen = valueStr.toIntOrNull() ?: contextLen
            key.endsWith(".embedding_length") -> embLen = valueStr.toIntOrNull() ?: embLen
            key.endsWith(".block_count") -> blockCount = valueStr.toIntOrNull() ?: blockCount
            key.endsWith(".attention.head_count") -> headCount = valueStr.toIntOrNull() ?: headCount
            key == "general.file_type" -> fileType = valueStr.toIntOrNull() ?: fileType
          }
        }
      }

      val quantName = mapGgufFileTypeToQuant(fileType)

      GgufMetadata(
        isValid = true,
        version = version,
        tensorCount = tensorCount,
        kvCount = kvCount,
        architecture = arch,
        modelName = if (modelName == "GGUF Model" && arch != "unknown") "$arch-$quantName" else modelName,
        quantizationType = quantName,
        contextLength = contextLen,
        embeddingLength = embLen,
        blockCount = blockCount,
        headCount = headCount,
        fileSizeFormatted = fileSizeFormatted,
        rawKv = kvMap
      )
    } catch (e: Exception) {
      Log.e(TAG, "Parsing stream exception", e)
      GgufMetadata(
        isValid = false,
        errorMessage = "Corrupt GGUF structure: ${e.message}"
      )
    }
  }

  private fun readGgufString(stream: InputStream): String? {
    val lenBytes = readExactBytes(stream, 8)
    val lenBuf = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN)
    val length = lenBuf.long
    if (length <= 0 || length > 1024 * 1024) return null
    val strBytes = readExactBytes(stream, length.toInt())
    return String(strBytes, Charsets.UTF_8)
  }

  private fun readGgufValueAsString(stream: InputStream, type: Int): String? {
    return when (type) {
      GGUF_TYPE_UINT8 -> {
        val b = stream.read()
        b.toString()
      }
      GGUF_TYPE_INT8 -> {
        val b = stream.read().toByte()
        b.toString()
      }
      GGUF_TYPE_UINT16, GGUF_TYPE_INT16 -> {
        val buf = ByteBuffer.wrap(readExactBytes(stream, 2)).order(ByteOrder.LITTLE_ENDIAN)
        buf.short.toString()
      }
      GGUF_TYPE_UINT32, GGUF_TYPE_INT32 -> {
        val buf = ByteBuffer.wrap(readExactBytes(stream, 4)).order(ByteOrder.LITTLE_ENDIAN)
        buf.int.toString()
      }
      GGUF_TYPE_FLOAT32 -> {
        val buf = ByteBuffer.wrap(readExactBytes(stream, 4)).order(ByteOrder.LITTLE_ENDIAN)
        buf.float.toString()
      }
      GGUF_TYPE_BOOL -> {
        val b = stream.read()
        (b != 0).toString()
      }
      GGUF_TYPE_STRING -> {
        readGgufString(stream)
      }
      GGUF_TYPE_UINT64, GGUF_TYPE_INT64 -> {
        val buf = ByteBuffer.wrap(readExactBytes(stream, 8)).order(ByteOrder.LITTLE_ENDIAN)
        buf.long.toString()
      }
      GGUF_TYPE_FLOAT64 -> {
        val buf = ByteBuffer.wrap(readExactBytes(stream, 8)).order(ByteOrder.LITTLE_ENDIAN)
        buf.double.toString()
      }
      GGUF_TYPE_ARRAY -> {
        // Array type: type (4 bytes) + count (8 bytes)
        val elemTypeBuf = ByteBuffer.wrap(readExactBytes(stream, 4)).order(ByteOrder.LITTLE_ENDIAN)
        val elemType = elemTypeBuf.int
        val countBuf = ByteBuffer.wrap(readExactBytes(stream, 8)).order(ByteOrder.LITTLE_ENDIAN)
        val count = countBuf.long
        // Skip elements or sample first few
        skipArrayElements(stream, elemType, count)
        "Array[$count elements]"
      }
      else -> {
        null
      }
    }
  }

  private fun skipArrayElements(stream: InputStream, elemType: Int, count: Long) {
    val safeCount = minOf(count, 500L)
    for (i in 0 until safeCount) {
      readGgufValueAsString(stream, elemType)
    }
  }

  private fun readExactBytes(stream: InputStream, count: Int): ByteArray {
    val bytes = ByteArray(count)
    readFully(stream, bytes)
    return bytes
  }

  private fun readFully(stream: InputStream, b: ByteArray): Int {
    var offset = 0
    while (offset < b.size) {
      val read = stream.read(b, offset, b.size - offset)
      if (read < 0) break
      offset += read
    }
    return offset
  }

  fun mapGgufFileTypeToQuant(fileType: Int): String {
    return when (fileType) {
      0 -> "F32"
      1 -> "F16"
      2 -> "Q4_0"
      3 -> "Q4_1"
      7 -> "Q8_0"
      8 -> "Q5_0"
      9 -> "Q5_1"
      10 -> "Q2_K"
      11 -> "Q3_K_S"
      12 -> "Q3_K_M"
      13 -> "Q3_K_L"
      14 -> "Q4_K_S"
      15 -> "Q4_K_M"
      16 -> "Q5_K_S"
      17 -> "Q5_K_M"
      18 -> "Q6_K"
      19 -> "IQ2_XXS"
      20 -> "IQ2_XS"
      21 -> "IQ3_XXS"
      28 -> "BF16"
      else -> "Q4_K_M"
    }
  }
}
