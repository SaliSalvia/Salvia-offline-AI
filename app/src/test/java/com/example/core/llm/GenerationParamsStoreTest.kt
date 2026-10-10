package com.example.core.llm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GenerationParamsStoreTest {

  private fun store(): GenerationParamsStore {
    val prefs = ApplicationProvider.getApplicationContext<Context>()
      .getSharedPreferences("test_gen_params", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    return GenerationParamsStore(prefs)
  }

  @Test
  fun `save and load roundtrip preserves values`() {
    val store = store()
    val params = GenerationParams(
      temperature = 0.42f,
      topP = 0.8f,
      topK = 33,
      maxTokens = 256,
      repeatPenalty = 1.2f,
      contextTokens = 4096,
      threads = 3
    )
    store.save(params)
    assertEquals(params.sanitized(), store.load())
  }

  @Test
  fun `reset restores safe defaults`() {
    val store = store()
    store.save(GenerationParams(temperature = 1.9f, topK = 99))
    store.reset()
    assertEquals(GenerationParams(), store.load())
  }

  @Test
  fun `load clamps corrupted stored values`() {
    val store = store()
    val prefs = ApplicationProvider.getApplicationContext<Context>()
      .getSharedPreferences("test_gen_params", Context.MODE_PRIVATE)
    prefs.edit().putFloat(GenerationParams.KEY_TEMPERATURE, 55f).commit()
    assertEquals(2f, store.load().temperature, 1e-6f)
  }
}
