package com.example.core.rag

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRagEngineTest {

  @Test
  fun persianQueryRanksRelevantChunkFirst() = runBlocking {
    val rag = LocalRagEngine()
    rag.indexDocument("weather.txt", "امروز هوا آفتابی و گرم بود و باد ملایمی می‌وزید.")
    rag.indexDocument("cpu.txt", "پردازندهٔ این گوشی هشت هسته دارد و فرکانس آن بالا است.")

    val results = rag.retrieve("پردازندهٔ گوشی چند هسته دارد؟")

    assertTrue(results.isNotEmpty())
    assertEquals("cpu.txt", results.first().chunk.documentName)
  }

  @Test
  fun zwnjVariantsRetrieveTheSameChunk() = runBlocking {
    val rag = LocalRagEngine()
    rag.indexDocument("note.txt", "این قابلیت به‌صورت کامل می‌شود کار می‌کند.")

    val results = rag.retrieve("آیا این قابلیت کامل میشود؟")

    assertTrue(results.isNotEmpty())
  }

  @Test
  fun unrelatedQueryReturnsNoHits() = runBlocking {
    val rag = LocalRagEngine()
    rag.indexDocument("cpu.txt", "پردازندهٔ گوشی هشت هسته دارد.")

    val results = rag.retrieve("زرشک و پلو")

    assertTrue(results.isEmpty())
  }

  @Test
  fun reindexKeepsStatisticsHonest() = runBlocking {
    val rag = LocalRagEngine()
    rag.indexDocument("a.txt", "mediakit processor memory")
    rag.indexDocument("a.txt", "camera sensor optics")

    assertEquals(1, rag.retrieve("optics").size)
    assertTrue(rag.retrieve("processor").isEmpty())
  }

  @Test
  fun normalizerFoldsPersianVariants() {
    assertEquals("میشود", SearchTextNormalizer.normalize("می‌شود"))
    assertEquals("کتاب", SearchTextNormalizer.normalize("كتاب"))
    assertEquals("هدیه", SearchTextNormalizer.normalize("هدیه"))
    assertEquals("1384", SearchTextNormalizer.normalize("۱۳۸۴"))
    assertEquals("hello world", SearchTextNormalizer.normalize("Hello WORLD"))
  }
}
