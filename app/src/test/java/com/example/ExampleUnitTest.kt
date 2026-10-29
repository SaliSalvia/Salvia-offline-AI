package com.example

import com.example.core.localization.AppLanguage
import com.example.core.localization.AppStrings
import com.example.core.rag.LocalRagEngine
import com.example.core.translation.OfflineTranslator
import com.example.core.router.AiRouter
import com.example.core.router.TargetEngine
import com.example.core.security.ZipSecurity
import com.example.gguf.GgufParser
import com.example.hardware.PerformanceMode
import com.example.hardware.XiaomiOptimizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testGgufQuantTypeMapping() {
    assertEquals("Q4_K_M", GgufParser.mapGgufFileTypeToQuant(15))
    assertEquals("Q8_0", GgufParser.mapGgufFileTypeToQuant(7))
    assertEquals("F16", GgufParser.mapGgufFileTypeToQuant(1))
  }

  @Test
  fun testOptimalThreadCount() {
    val turboThreads = XiaomiOptimizer.getOptimalThreadCount(PerformanceMode.HYPER_TURBO)
    assertTrue(turboThreads in 1..6)
  }

  @Test
  fun testAiRouterRouting() {
    // Image generation now requires an explicit leading command ("بکش…",
    // "تصویر بساز…", "/draw…"). A message that merely contains the word
    // "تصویر" must reach the LLM, never the image pipeline.
    val drawTask = AiRouter.routeRequest("بکش یک گل رز در شب", emptyList())
    assertEquals(TargetEngine.DIFFUSION_IMAGE, drawTask.engine)

    val drawCommand = AiRouter.routeRequest("تصویر بساز یک گل رز در شب", emptyList())
    assertEquals(TargetEngine.DIFFUSION_IMAGE, drawCommand.engine)

    // Regression: "تصویرش را توضیح بده" used to be hijacked into DIFFUSION_IMAGE.
    val describeImage = AiRouter.routeRequest("تصویرش را توضیح بده", emptyList())
    assertEquals(TargetEngine.TEXT_LLM, describeImage.engine)

    val codeTask = AiRouter.routeRequest("یک تابع پایتون برای مرتب سازی بنویس", emptyList())
    assertEquals(TargetEngine.CODING_LLM, codeTask.engine)

    // Speech likewise needs an explicit leading command ("بخوان…"), while
    // "این کد را بخوان و باگ پیدا کن" is a question for the coding LLM —
    // it used to be hijacked into TEXT_TO_SPEECH.
    val ttsTask = AiRouter.routeRequest("بخوان این پیام را", emptyList())
    assertEquals(TargetEngine.TEXT_TO_SPEECH, ttsTask.engine)

    val readCodeTask = AiRouter.routeRequest("این کد را بخوان و باگ پیدا کن", emptyList())
    assertEquals(TargetEngine.CODING_LLM, readCodeTask.engine)
  }

  @Test
  fun testZipSecurityPathValidation() {
    val baseDir = File("/tmp/sandbox")
    val validFile = ZipSecurity.validateEntryPath(baseDir, "src/main.kt")
    assertTrue(validFile.canonicalPath.startsWith(baseDir.canonicalPath))

    try {
      ZipSecurity.validateEntryPath(baseDir, "../../etc/passwd")
      fail("Should have thrown SecurityException for path traversal")
    } catch (_: SecurityException) {
      // Expected
    }
  }

  @Test
  fun testZipInspectionResourceLimits() {
    ZipSecurity.checkSizeLimit(ZipSecurity.MAX_TOTAL_UNCOMPRESSED_BYTES - 1, 1)
    ZipSecurity.checkSingleFileSizeLimit(ZipSecurity.MAX_SINGLE_FILE_BYTES)

    assertThrows(java.io.IOException::class.java) {
      ZipSecurity.checkSizeLimit(ZipSecurity.MAX_TOTAL_UNCOMPRESSED_BYTES, 1)
    }
    assertThrows(java.io.IOException::class.java) {
      ZipSecurity.checkSingleFileSizeLimit(ZipSecurity.MAX_SINGLE_FILE_BYTES + 1)
    }
    assertThrows(java.io.IOException::class.java) {
      ZipSecurity.checkFileCount(ZipSecurity.MAX_FILES_COUNT)
    }
  }

  @Test
  fun testLocalRagEngineIndexingAndRetrieval() = runBlocking {
    val rag = LocalRagEngine()
    val chunkCount = rag.indexDocument("note.txt", "Redmi Note 14 Pro has a powerful MediaTek processor and 12GB of RAM.")
    assertTrue(chunkCount > 0)

    val results = rag.retrieve("MediaTek processor")
    assertTrue(results.isNotEmpty())
    assertTrue(results.first().chunk.text.contains("MediaTek"))
  }

  @Test
  fun testBilingualLocalizationStrings() {
    // Verify Persian strings
    assertEquals("💬 گفت‌وگو", AppStrings.tabChat(AppLanguage.FA))
    assertEquals("ارسال", AppStrings.send(AppLanguage.FA))
    assertEquals("توقف", AppStrings.stop(AppLanguage.FA))

    // Verify English strings
    assertEquals("💬 Chat", AppStrings.tabChat(AppLanguage.EN))
    assertEquals("Send", AppStrings.send(AppLanguage.EN))
    assertEquals("Stop", AppStrings.stop(AppLanguage.EN))
    assertEquals("Local AI Models Manager", AppStrings.modelsTitle(AppLanguage.EN))
  }

  @Test
  fun testOfflineTranslatorAccuracy() = runBlocking {
    val englishGreeting = "Hello"
    val translatedGreeting = OfflineTranslator.translateToPersian(englishGreeting)
    assertEquals("سلام", translatedGreeting)

    val englishText = "This model is running offline on your device"
    val translated = OfflineTranslator.translateToPersian(englishText)
    assertTrue(translated.isNotEmpty())
    assertTrue(translated.contains("آفلاین") || translated.contains("دستگاه") || translated.contains("مدل"))

    // Test code block preservation
    val codeMarkdown = "Here is the code:\n```python\nprint('hello')\n```"
    val translatedCode = OfflineTranslator.translateToPersian(codeMarkdown)
    assertTrue(translatedCode.contains("```python\nprint('hello')\n```"))
  }
}
