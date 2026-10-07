package com.example.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.BufferedReader
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
    var count = 0
    var totalUncompressedBytes = 0L

    context.contentResolver.openInputStream(uri)?.use { stream ->
      ZipInputStream(stream).use { zis ->
        var entry = zis.nextEntry
        while (entry != null && count < 250) {
          count++
          val entryName = entry.name
          val entrySize = entry.size
          if (entrySize > 0) totalUncompressedBytes += entrySize
          entries.add(entryName)

          // If entry is a readable text file like README, main.py, etc., inspect preview
          val ext = entryName.substringAfterLast('.', "").lowercase()
          if (!entry.isDirectory && (ext in listOf("md", "txt", "py", "kt", "js", "ts", "json", "yaml", "xml", "csv")) && previewContents.length < 3000) {
            try {
              val reader = BufferedReader(InputStreamReader(zis, Charsets.UTF_8))
              val lines = mutableListOf<String>()
              var lineCount = 0
              var line = reader.readLine()
              while (line != null && lineCount < 15) {
                lines.add(line)
                lineCount++
                line = reader.readLine()
              }
              if (lines.isNotEmpty()) {
                previewContents.append("📄 فایل [$entryName]:\n")
                previewContents.append(lines.joinToString("\n"))
                previewContents.append("\n---\n")
              }
            } catch (_: Exception) {}
          }
          entry = zis.nextEntry
        }
      }
    }

    val uncompressedFormatted = formatBytes(totalUncompressedBytes)
    val summary = buildString {
      append("📦 محتوای آرشیو ZIP [$fileName]: شامل $count فایل و دایرکتوری (حجم بعد از استخراج: $uncompressedFormatted).\n")
      append("فهرست فایل‌های کلیدی داخل آرشیو:\n")
      entries.take(15).forEach { append("  • $it\n") }
      if (entries.size > 15) append("  ... و ${entries.size - 15} فایل دیگر.\n")
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
    var lineCount = 0

    context.contentResolver.openInputStream(uri)?.use { stream ->
      BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
        var line = reader.readLine()
        while (line != null && textBuilder.length < 16000) {
          textBuilder.append(line).append("\n")
          lineCount++
          line = reader.readLine()
        }
      }
    }

    val content = textBuilder.toString()
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
