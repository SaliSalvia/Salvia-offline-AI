package com.example.core.security

import java.io.File
import java.io.IOException

object ZipSecurity {
  const val MAX_FILES_COUNT = 5000
  const val MAX_TOTAL_UNCOMPRESSED_BYTES = 150L * 1024L * 1024L // 150 MB safety limit
  const val MAX_SINGLE_FILE_BYTES = 50L * 1024L * 1024L // 50 MB safety limit

  fun validateEntryPath(destinationDir: File, entryName: String): File {
    val destinationCanonical = destinationDir.canonicalPath
    val targetFile = File(destinationDir, entryName)
    val targetCanonical = targetFile.canonicalPath

    // Check for Zip Slip / Path Traversal vulnerability
    if (!targetCanonical.startsWith(destinationCanonical + File.separator) && targetCanonical != destinationCanonical) {
      throw SecurityException("خطای امنیتی Zip Slip: مسیر غیرمجاز در آرشیو زیپ ($entryName)")
    }
    return targetFile
  }

  fun checkSizeLimit(currentTotalBytes: Long, newBytes: Long) {
    if (currentTotalBytes + newBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
      throw IOException("خطای امنیتی Zip Bomb: حجم بازگشایی آرشیو از سقف امن ۱۵۰ مگابایت عبور کرد")
    }
  }

  fun checkFileCount(currentCount: Int) {
    if (currentCount >= MAX_FILES_COUNT) {
      throw IOException("تعداد فایل‌های داخل آرشیو زیپ از حد مجاز ($MAX_FILES_COUNT فایل) بیشتر است")
    }
  }
}
