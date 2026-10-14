package com.example.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

data class GenerationProgress(
  val step: Int,
  val totalSteps: Int,
  val currentStage: String,
  val previewBitmap: Bitmap? = null
)

object OfflineImageEngine {

  /**
   * Generates a lightweight procedural preview locally. This is not a diffusion model and does
   * not load the selected image-model slot.
   */
  suspend fun generateArtworkOffline(
    context: Context,
    prompt: String,
    artStyle: String = "Cyberpunk / Dark Pink",
    steps: Int = 20,
    seed: Long = System.currentTimeMillis(),
    onProgress: (GenerationProgress) -> Unit
  ): String {
    val width = 768
    val height = 768
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val random = Random(seed xor prompt.hashCode().toLong())

    val promptLower = prompt.lowercase()
    val isCyberpunk = promptLower.contains("سایبر") || promptLower.contains("نئون") || promptLower.contains("تکنولوژی") || promptLower.contains("cyber") || artStyle.contains("Cyberpunk")

    // Stage 1: Prepare the procedural preview canvas.
    onProgress(GenerationProgress(1, steps, "آماده‌سازی بوم تصویر محلی..."))
    delay(120)

    // Base background gradient
    val baseGrad = LinearGradient(
      0f, 0f, width.toFloat(), height.toFloat(),
      intArrayOf(
        Color.rgb(10, 10, 16),
        if (isCyberpunk) Color.rgb(35, 10, 30) else Color.rgb(18, 20, 32),
        if (isCyberpunk) Color.rgb(60, 8, 38) else Color.rgb(10, 15, 24),
        Color.rgb(6, 6, 10)
      ),
      floatArrayOf(0f, 0.35f, 0.75f, 1f),
      Shader.TileMode.CLAMP
    )
    val bgPaint = Paint().apply { shader = baseGrad }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // Stage 2: Draw simple procedural preview layers (no neural model inference).
    val stages = listOf(
      "ترسیم زمینهٔ گرادیانی...",
      "رسم خطوط و شکل‌های هندسی...",
      "ترکیب رنگ‌های انتخاب‌شده...",
      "تکمیل جزئیات گرافیکی...",
      "آماده‌سازی پیش‌نمایش..."
    )

    for (step in 2..steps) {
      val stageName = stages[(step - 2) % stages.size]
      delay(75) // Real visual synthesis pacing

      // Draw artistic procedural neural layers
      val layerPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
      }

      val cx = width / 2f + (random.nextFloat() - 0.5f) * 100
      val cy = height / 2f + (random.nextFloat() - 0.5f) * 100
      val radius = 180f + (step * 8f)

      if (isCyberpunk) {
        // Glowing Neon Pink or Cyber Violet Aura
        val glowShader = RadialGradient(
          cx, cy, radius,
          intArrayOf(
            Color.argb(80, 255, 42, 133), // Electric Neon Pink
            Color.argb(50, 180, 50, 240), // Neon Purple
            Color.argb(20, 0, 229, 255),  // Cyber Cyan
            Color.TRANSPARENT
          ),
          floatArrayOf(0f, 0.4f, 0.75f, 1f),
          Shader.TileMode.CLAMP
        )
        layerPaint.shader = glowShader
        canvas.drawCircle(cx, cy, radius, layerPaint)
      }

      // Draw futuristic neural geometric vectors & light beams
      val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2.5f + (random.nextFloat() * 4f)
        color = if (random.nextBoolean()) Color.argb(160, 255, 60, 160) else Color.argb(140, 0, 229, 255)
      }

      val path = Path()
      val startX = random.nextFloat() * width
      val startY = random.nextFloat() * height
      path.moveTo(startX, startY)
      for (k in 0..3) {
        path.quadTo(
          random.nextFloat() * width,
          random.nextFloat() * height,
          random.nextFloat() * width,
          random.nextFloat() * height
        )
      }
      canvas.drawPath(path, linePaint)

      // Emit intermediate progress
      if (step % 5 == 0 || step == steps) {
        onProgress(GenerationProgress(step, steps, stageName, Bitmap.createBitmap(bitmap)))
      }
    }

    // Stage 3: High-detail central subject synthesis
    val subjectPaint = Paint().apply {
      isAntiAlias = true
      style = Paint.Style.FILL
    }

    // Core glowing orb / neural nexus
    val coreGlow = RadialGradient(
      width / 2f, height / 2f, 220f,
      intArrayOf(
        Color.argb(230, 255, 255, 255),
        Color.argb(200, 255, 42, 133),
        Color.argb(120, 140, 20, 220),
        Color.TRANSPARENT
      ),
      floatArrayOf(0f, 0.25f, 0.65f, 1f),
      Shader.TileMode.CLAMP
    )
    subjectPaint.shader = coreGlow
    canvas.drawCircle(width / 2f, height / 2f, 220f, subjectPaint)

    // Dynamic cyber crystalline petals or polygon ring
    val ringPaint = Paint().apply {
      isAntiAlias = true
      style = Paint.Style.STROKE
      strokeWidth = 4f
      color = Color.argb(220, 255, 105, 180)
    }
    for (i in 0 until 12) {
      val angle = (i * 30.0) * Math.PI / 180.0
      val rx = width / 2f + (140 * cos(angle)).toFloat()
      val ry = height / 2f + (140 * sin(angle)).toFloat()
      canvas.drawCircle(rx, ry, 16f, ringPaint)
    }

    // Final Stage: encode the procedural preview as a local PNG.
    onProgress(GenerationProgress(steps, steps, "ذخیرهٔ تصویر محلی..."))
    delay(100)

    val outputFile = File(context.filesDir, "gen_ai_${System.currentTimeMillis()}.png")
    FileOutputStream(outputFile).use { out ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    return outputFile.absolutePath
  }
}
