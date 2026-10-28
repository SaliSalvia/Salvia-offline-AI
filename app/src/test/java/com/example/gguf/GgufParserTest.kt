package com.example.gguf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class GgufParserTest {

  @Test
  fun `large tokenizer array does not desynchronise following metadata`() {
    val bytes = ByteArrayOutputStream().use { out ->
      out.writeIntLe(0x46554747) // "GGUF"
      out.writeIntLe(3)          // version
      out.writeLongLe(0)         // tensor count
      out.writeLongLe(4)         // metadata key/value count

      out.writeGgufString("general.architecture")
      out.writeIntLe(8) // GGUF_TYPE_STRING
      out.writeGgufString("qwen3")

      out.writeGgufString("tokenizer.ggml.tokens")
      out.writeIntLe(9)       // GGUF_TYPE_ARRAY
      out.writeIntLe(8)       // element type: string
      out.writeLongLe(600)    // more than the previous 500-element cap
      repeat(600) { out.writeGgufString("token-$it") }

      out.writeGgufString("general.file_type")
      out.writeIntLe(4) // GGUF_TYPE_UINT32
      out.writeIntLe(13) // Q3_K_L

      out.writeGgufString("qwen3.context_length")
      out.writeIntLe(4)
      out.writeIntLe(40960)

      out.toByteArray()
    }

    val parsed = GgufParser.parseFromStream(ByteArrayInputStream(bytes), "4.43 GB")

    assertTrue("expected valid GGUF, got ${parsed.errorMessage}", parsed.isValid)
    assertEquals("qwen3", parsed.architecture)
    assertEquals("Q3_K_L", parsed.quantizationType)
    assertEquals(40960, parsed.contextLength)
  }

  private fun ByteArrayOutputStream.writeIntLe(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
  }

  private fun ByteArrayOutputStream.writeLongLe(value: Long) {
    for (shift in 0 until 64 step 8) {
      write(((value ushr shift) and 0xFF).toInt())
    }
  }

  private fun ByteArrayOutputStream.writeGgufString(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    writeLongLe(bytes.size.toLong())
    write(bytes)
  }
}
