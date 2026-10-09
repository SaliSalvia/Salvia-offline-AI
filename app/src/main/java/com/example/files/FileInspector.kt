package com.example.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.core.security.ZipSecurity
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class AttachedFile(
  val uri: String,
  val fileName: String,
  val mimeType: String,
  val sizeBytes: Long,
  val sizeFormatted: String,
  val category: FileCategory,
  val extractedSummary: String,
  val fullTextSample: String? = null,
  val zipEntriesCount: Int = 0,
  val zipFilesList: List<String> = emptyList(),
  val previewBitmap: Bitmap? = null
)

enum class FileCategory(val label: String, val iconName: String) {
  ZIP("آرشیو ZIP", "folder_zip"),
  IMAGE("تصویر / عکس", "image"),
  CODE("کد / برنامه نویسی", "code"),
  DOCUMENT("سند متنی", "description"),
  GGUF("مدل هوش مصنوعی GGUF", "memory"),
  OTHER("فایل داده", "insert_drive_file")
}

object FileInspector {
  private const val TAG = "FileInspector"

  fun inspectUri(context: Context, uri: Uri): AttachedFile? {
    return try {
      val (fileName, sizeBytes) = getFileNameAndSize(context, uri)
      val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
      val sizeFormatted = formatBytes(sizeBytes)
      val extension = fileName.substringAfterLast('.', "").lowercase()

      when {
        extension == "zip" || mimeType.contains("zip") -> {
          inspectZip(context, uri, fileName, sizeBytes, sizeFormatted)
        }
        extension in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif") || mimeType.startsWith("image/") -> {
          inspectImage(context, uri, fileName, sizeBytes, sizeFormatted, mimeType)
        }
        extension == "gguf" -> {
          AttachedFile(
            uri = uri.toString(),
            fileName = fileName,
            mimeType = "application/x-gguf",
            sizeBytes = sizeBytes,
            sizeFormatted = sizeFormatted,
            category = FileCategory.GGUF,
            extractedSummary = "فایل وزن‌های شبکه عصبی GGUF به حجم $sizeFormatted آماده بارگذاری در اسلات هوش مصنوعی."
          )
        }
        isCodeOrText(extension, mimeType) -> {
          inspectTextFile(context, uri, fileName, sizeBytes, sizeFormatted, extension)
        }
        else -> {
          AttachedFile(
            uri = uri.toString(),
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            sizeFormatted = sizeFormatted,
            category = FileCategory.OTHER,
            extractedSummary = "فایل $fileName با حجم $sizeFormatted بارگذاری شد."
          )
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error inspecting file $uri", e)
      null
    }
  }

  private fun inspectZip(
    context: Context,
    uri: Uri,
    fileName: String,
    sizeBytes: Long,
    sizeFormatted: String
  ): AttachedFile {
    val entries = mutableListOf<String>()
    val previewContents = StringBuilder()
    val previewExtensions = setOf("md", "txt", "py", "kt", "js", "ts", "json", "yaml", "yml", "xml", "csv")
    var count = 0
    var totalUncompressedBytes = 0L
    val buffer = ByteArray(8 * 1024)
    val maxPreviewBytesPerEntry = 16 * 1024
    val maxPreviewChars = 3_000

    context.contentResolver.openInputStream(uri)?.use { stream ->
      ZipInputStream(stream).use { zis ->
        var entry = zis.nextEntry
        while (entry != null && count < ZipSecurity.MAX_FILES_COUNT) {
          ZipSecurity.checkFileCount(count)
          count++
          val entryName = entry.name
          entries.add(entryName)

          val extension = entryName.substringAfterLast('.', "").lowercase()
          val capturePreview = !entry.isDirectory && extension in previewExtensions &&
            previewContents.length < maxPreviewChars
          val capturedBytes = if (capturePreview) ByteArrayOutputStream() else null
          var entryUncompressedBytes = 0L

          // Count bytes actually inflated by ZipInputStream. ZIP metadata sizes may be missing or
          // untrusted, so they are not used for enforcing the bomb limits.
          while (true) {
            val read = zis.read(buffer)
            if (read < 0) break
            if (read == 0) continue

            entryUncompressedBytes += read
            totalUncompressedBytes += read
            ZipSecurity.checkSingleFileSizeLimit(entryUncompressedBytes)
            ZipSecurity.checkSizeLimit(totalUncompressedBytes - read, read.toLong())

            if (capturedBytes != null && capturedBytes.size() < maxPreviewBytesPerEntry) {
              val remaining = maxPreviewBytesPerEntry - capturedBytes.size()
              capturedBytes.write(buffer, 0, minOf(read, remaining))
            }
          }

          if (capturedBytes != null && capturedBytes.size() > 0 && previewContents.length < maxPreviewChars) {
            val text = String(capturedBytes.toByteArray(), Charsets.UTF_8)
            val remainingChars = maxPreviewChars - previewContents.length
            val snippet = text.lineSequence().take(15).joinToString("\n").take(remainingChars)
            if (snippet.isNotBlank()) {
              previewContents.append("📄 فایل [$entryName]:\n")
              previewContents.append(snippet).append("\n---\n")
            }
          }
          entry = zis.nextEntry
        }
      }
    } ?: throw IOException("Cannot open ZIP stream")

    val uncompressedFormatted = formatBytes(totalUncompressedBytes)
    val summary = buildString {
      append("📦 محتوای بررسی‌شدهٔ ZIP [$fileName]: $count ورودی، حداکثر ${ZipSecurity.MAX_TOTAL_UNCOMPRESSED_BYTES / (1024 * 1024)} MB بازشده.\n")
      append("حجم واقعی بررسی‌شده: $uncompressedFormatted\n")
      append("فهرست ورودی‌های شاخص:\n")
      entries.take(15).forEach { append("  • $it\n") }
      if (entries.size >= ZipSecurity.MAX_FILES_COUNT) append("  ادامهٔ آرشیو برای حفظ منابع بررسی نشد.\n")
    }

    return AttachedFile(
      uri = uri.toString(),
      fileName = fileName,
      mimeType = "application/zip",
      sizeBytes = sizeBytes,
      sizeFormatted = sizeFormatted,
      category = FileCategory.ZIP,
      extractedSummary = summary,
      fullTextSample = if (previewContents.isNotEmpty()) previewContents.toString() else summary,
      zipEntriesCount = count,
      zipFilesList = entries
    )
  }

  private fun inspectImage(
    context: Context,
    uri: Uri,
    fileName: String,
    sizeBytes: Long,
    sizeFormatted: String,
    mimeType: String
  ): AttachedFile {
    var width = 0
    var height = 0
    var bmp: Bitmap? = null

    try {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(stream, null, options)
        width = options.outWidth
        height = options.outHeight
      }

      // Load a thumbnail for display
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val sampleOptions = BitmapFactory.Options().apply {
          inSampleSize = maxOf(1, maxOf(width, height) / 512)
        }
        bmp = BitmapFactory.decodeStream(stream, null, sampleOptions)
      }
    } catch (_: Exception) {}

    val summary = "🖼️ تصویر $fileName (${width}x${height} پیکسل - $sizeFormatted). آماده آنالیز چندوجهی آفلاین و پاسخ به پرسش‌های بینایی هوش مصنوعی."

    return AttachedFile(
      uri = uri.toString(),
      fileName = fileName,
      mimeType = mimeType,
      sizeBytes = sizeBytes,
      sizeFormatted = sizeFormatted,
      category = FileCategory.IMAGE,
      extractedSummary = summary,
      fullTextSample = "[محتوای چندوجهی تصویر: $fileName ابعاد: ${width}x${height}]",
      previewBitmap = bmp
    )
  }

  private fun inspectTextFile(
    context: Context,
    uri: Uri,
    fileName: String,
    sizeBytes: Long,
    sizeFormatted: String,
    extension: String
  ): AttachedFile {
    val textBuilder = StringBuilder()
    val maxChars = 16_000

    context.contentResolver.openInputStream(uri)?.use { stream ->
      BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
        val buffer = CharArray(2_048)
        while (textBuilder.length < maxChars) {
          val length = reader.read(buffer, 0, minOf(buffer.size, maxChars - textBuilder.length))
          if (length < 0) break
          textBuilder.append(buffer, 0, length)
        }
      }
    }

    val content = textBuilder.toString()
    val lineCount = if (content.isEmpty()) 0 else content.count { it == '\n' } + (if (content.endsWith('\n')) 0 else 1)
    val isCode = extension in listOf("kt", "java", "py", "js", "ts", "cpp", "c", "rs", "go", "php", "html", "css", "sql")
    val category = if (isCode) FileCategory.CODE else FileCategory.DOCUMENT

    val summary = "📄 سند $fileName ($lineCount خط - $sizeFormatted) بارگذاری شد و در دسترس مدل برای مطالعه و تحلیل قرار گرفت."

    return AttachedFile(
      uri = uri.toString(),
      fileName = fileName,
      mimeType = if (isCode) "text/x-code" else "text/plain",
      sizeBytes = sizeBytes,
      sizeFormatted = sizeFormatted,
      category = category,
      extractedSummary = summary,
      fullTextSample = content
    )
  }

  private fun isCodeOrText(ext: String, mime: String): Boolean {
    val textExtensions = listOf(
      "txt", "md", "json", "csv", "xml", "yaml", "yml", "log", "ini", "conf",
      "py", "kt", "java", "c", "cpp", "h", "hpp", "js", "ts", "html", "css", "sql", "sh"
    )
    return ext in textExtensions || mime.startsWith("text/") || mime.contains("json") || mime.contains("javascript")
  }

  private fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
    var name = "file"
    var size = 0L
    try {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
          if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
          if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
        }
      }
    } catch (_: Exception) {}
    return Pair(name, size)
  }

  fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
      gb >= 1.0 -> String.format("%.2f GB", gb)
      mb >= 1.0 -> String.format("%.1f MB", mb)
      kb >= 1.0 -> String.format("%.1f KB", kb)
      else -> "$bytes B"
    }
  }
}
