package com.example.core.security

import java.io.File
import java.io.IOException

/** Resource limits for inspecting untrusted ZIP archives. The inspector never extracts paths. */
object ZipSecurity {
  const val MAX_FILES_COUNT = 250
  const val MAX_TOTAL_UNCOMPRESSED_BYTES = 32L * 1024L * 1024L
  const val MAX_SINGLE_FILE_BYTES = 8L * 1024L * 1024L

  fun validateEntryPath(destinationDir: File, entryName: String): File {
    val destinationCanonical = destinationDir.canonicalPath
    val targetFile = File(destinationDir, entryName)
    val targetCanonical = targetFile.canonicalPath
    if (!targetCanonical.startsWith(destinationCanonical + File.separator) && targetCanonical != destinationCanonical) {
      throw SecurityException("Zip Slip path rejected: $entryName")
    }
    return targetFile
  }

  fun checkSizeLimit(currentTotalBytes: Long, newBytes: Long) {
    if (newBytes < 0L || currentTotalBytes < 0L || currentTotalBytes > MAX_TOTAL_UNCOMPRESSED_BYTES - newBytes) {
      throw IOException("ZIP inspection exceeded the total uncompressed size limit")
    }
  }

  fun checkSingleFileSizeLimit(fileBytes: Long) {
    if (fileBytes < 0L || fileBytes > MAX_SINGLE_FILE_BYTES) {
      throw IOException("ZIP inspection exceeded the per-file uncompressed size limit")
    }
  }

  fun checkFileCount(currentCount: Int) {
    if (currentCount >= MAX_FILES_COUNT) {
      throw IOException("ZIP inspection exceeded the maximum entry count")
    }
  }
}
