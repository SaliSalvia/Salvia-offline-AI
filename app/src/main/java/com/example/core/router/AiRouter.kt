package com.example.core.router

import com.example.core.model.ModelCapability
import com.example.files.AttachedFile
import com.example.files.FileCategory

enum class TargetEngine {
  TEXT_LLM,
  CODING_LLM,
  VISION_LLM,
  DIFFUSION_IMAGE,
  OCR_EXTRACTOR,
  LOCAL_RAG,
  SPEECH_TO_TEXT,
  TEXT_TO_SPEECH
}

data class RoutedTask(
  val engine: TargetEngine,
  val requiredCapability: ModelCapability,
  val explanationFa: String,
  val sanitizedPrompt: String
)

object AiRouter {

  fun routeRequest(prompt: String, attachedFiles: List<AttachedFile>): RoutedTask {
    val pLower = prompt.lowercase().trim()

    // 1. Attached image check -> Vision or OCR
    val hasImage = attachedFiles.any { it.category == FileCategory.IMAGE }
    if (hasImage) {
      if (pLower.contains("متن") || pLower.contains("ocr") || pLower.contains("بخوان") || pLower.contains("استخراج")) {
        return RoutedTask(
          engine = TargetEngine.OCR_EXTRACTOR,
          requiredCapability = ModelCapability.OCR,
          explanationFa = "استخراج متن و کاراکترهای تصویر با OCR محلی و ارسال به مدل متنی",
          sanitizedPrompt = prompt
        )
      }
      return RoutedTask(
        engine = TargetEngine.VISION_LLM,
        requiredCapability = ModelCapability.VISION,
        explanationFa = "تحلیل محتوای چندوجهی تصویر با انکودر بینایی محلی",
        sanitizedPrompt = prompt
      )
    }

    // 2. Attached ZIP or large documents -> Local RAG or Document AI
    val hasZip = attachedFiles.any { it.category == FileCategory.ZIP }
    val hasDocs = attachedFiles.any { it.category == FileCategory.DOCUMENT || it.category == FileCategory.CODE }
    if (hasZip || hasDocs) {
      return RoutedTask(
        engine = TargetEngine.LOCAL_RAG,
        requiredCapability = ModelCapability.EMBEDDING,
        explanationFa = "پارس پرونده‌های محلی، نمایه‌سازی وکتور و بازیابی مبتنی بر RAG بدون اینترنت",
        sanitizedPrompt = prompt
      )
    }

    // 3. Image Generation cues
    // Deliberately startsWith-only and command-shaped: a loose `contains` match here
    // used to hijack ordinary chat like "تصویرش را توضیح بده" or "طراحی‌ات را بهتر کن"
    // into the procedural image pipeline instead of answering with the LLM.
    if (pLower.startsWith("تصویر بساز") || pLower.startsWith("بکش") || pLower.startsWith("عکس بساز") ||
        pLower.startsWith("طراحی کن") || pLower.startsWith("/draw") || pLower.startsWith("paint") ||
        pLower.startsWith("generate image")) {
      val cleaned = prompt
        .removePrefix("تصویر بساز")
        .removePrefix("بکش")
        .removePrefix("عکس بساز")
        .removePrefix("طراحی کن")
        .removePrefix("/draw")
        .removePrefix("paint")
        .removePrefix("generate image")
        .trim()
      return RoutedTask(
        engine = TargetEngine.DIFFUSION_IMAGE,
        requiredCapability = ModelCapability.IMAGE_GENERATION,
        explanationFa = "هدایت به خط لوله انتشار تصویر آفلاین (Diffusion Engine)",
        sanitizedPrompt = if (cleaned.isNotEmpty()) cleaned else prompt
      )
    }

    // 4. TTS cues
    // Deliberately startsWith-only: a loose `contains` match here used to hijack
    // ordinary chat like "این کد را بخوان و باگ پیدا کن" into the speech pipeline
    // instead of answering with the LLM. Only an explicit command at the very
    // start of the message is treated as a speech request.
    if (pLower.startsWith("بخوان") || pLower.startsWith("صدا کن") || pLower.startsWith("/speak") ||
        pLower.startsWith("تبدیل به گفتار") || pLower.startsWith("پخش صوتی")) {
      return RoutedTask(
        engine = TargetEngine.TEXT_TO_SPEECH,
        requiredCapability = ModelCapability.TEXT_TO_SPEECH,
        explanationFa = "سنتز گفتار و تولید امواج صوتی با موتور آفلاین TTS",
        sanitizedPrompt = prompt
      )
    }

    // 5. Code cues
    if (pLower.contains("کد") || pLower.contains("برنامه") || pLower.contains("تابع") ||
        pLower.contains("class") || pLower.contains("python") || pLower.contains("kotlin") || pLower.contains("bug")) {
      return RoutedTask(
        engine = TargetEngine.CODING_LLM,
        requiredCapability = ModelCapability.CODE,
        explanationFa = "هدایت به مدل استدلال تخصصی برنامه‌نویسی و سورس‌کد",
        sanitizedPrompt = prompt
      )
    }

    // 6. Default to standard Text reasoning LLM
    return RoutedTask(
      engine = TargetEngine.TEXT_LLM,
      requiredCapability = ModelCapability.TEXT,
      explanationFa = "هدایت به موتور استدلال زبانی DeepSeek-R1 / Qwen2.5",
      sanitizedPrompt = prompt
    )
  }
}
