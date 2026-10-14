package com.example.gguf

/**
 * Describes what occupies one model slot in the app UI.
 *
 * Honest by design: an empty slot is shown as empty. There are no fabricated
 * model names or capabilities — real text inference lives in
 * [com.example.core.llm.RealLlmEngine] (llama.cpp), and slots only record what
 * the user actually loaded from storage.
 */
data class LoadedSlotInfo(
  val id: String, // "slot_text" or "slot_image"
  val slotType: String, // "TEXT" or "IMAGE"
  val modelName: String,
  val architecture: String,
  val quantization: String,
  val contextLength: Int,
  val sizeFormatted: String,
  val uriString: String? = null,
  val isLoaded: Boolean = true
)

object GgufInferenceEngine {

  /** Placeholder for the text slot before the user loads a real GGUF model. */
  val DEFAULT_TEXT_SLOT = LoadedSlotInfo(
    id = "slot_text",
    slotType = "TEXT",
    modelName = "بدون مدل متنی",
    architecture = "—",
    quantization = "—",
    contextLength = 0,
    sizeFormatted = "—",
    isLoaded = false
  )

  /**
   * Placeholder for the image slot. Pixel image generation is not part of this
   * build (no honest diffusion runtime is bundled); the slot exists so the UI
   * can say so plainly instead of pretending.
   */
  val DEFAULT_IMAGE_SLOT = LoadedSlotInfo(
    id = "slot_image",
    slotType = "IMAGE",
    modelName = "بدون مدل تصویری",
    architecture = "—",
    quantization = "—",
    contextLength = 0,
    sizeFormatted = "—",
    isLoaded = false
  )
}
