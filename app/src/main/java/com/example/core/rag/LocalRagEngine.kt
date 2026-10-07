package com.example.core.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

data class DocumentChunk(
  val id: String,
  val documentName: String,
  val chunkIndex: Int,
  val text: String,
  val vector: FloatArray,
  val charCount: Int
)

data class RetrievalResult(
  val chunk: DocumentChunk,
  val similarityScore: Float
)

data class RagIndexSummary(
  val totalDocuments: Int,
  val totalChunks: Int,
  val indexedDocumentNames: List<String>
)

class LocalRagEngine {
  private val vectorStore = mutableListOf<DocumentChunk>()
  private val embeddingDimension = 128

  suspend fun indexDocument(documentName: String, content: String): Int = withContext(Dispatchers.Default) {
    val chunks = chunkText(content, chunkSize = 400, overlap = 80)
    val indexedChunks = mutableListOf<DocumentChunk>()

    chunks.forEachIndexed { index, chunkText ->
      val vector = computeLocalEmbedding(chunkText)
      val chunk = DocumentChunk(
        id = "${documentName}_chunk_$index",
        documentName = documentName,
        chunkIndex = index,
        text = chunkText,
        vector = vector,
        charCount = chunkText.length
      )
      indexedChunks.add(chunk)
    }

    synchronized(vectorStore) {
      vectorStore.removeAll { it.documentName == documentName }
      vectorStore.addAll(indexedChunks)
    }

    indexedChunks.size
  }

  suspend fun retrieve(query: String, topK: Int = 3): List<RetrievalResult> = withContext(Dispatchers.Default) {
    if (vectorStore.isEmpty()) return@withContext emptyList()
    val queryVector = computeLocalEmbedding(query)

    val scored = synchronized(vectorStore) {
      vectorStore.map { chunk ->
        val sim = cosineSimilarity(queryVector, chunk.vector)
        RetrievalResult(chunk, sim)
      }
    }

    scored.sortedByDescending { it.similarityScore }.take(topK)
  }

  fun getSummary(): RagIndexSummary = synchronized(vectorStore) {
    val docs = vectorStore.map { it.documentName }.distinct()
    RagIndexSummary(
      totalDocuments = docs.size,
      totalChunks = vectorStore.size,
      indexedDocumentNames = docs
    )
  }

  fun clearIndex() = synchronized(vectorStore) {
    vectorStore.clear()
  }

  private fun chunkText(text: String, chunkSize: Int, overlap: Int): List<String> {
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
      val end = minOf(start + chunkSize, text.length)
      val slice = text.substring(start, end).trim()
      if (slice.isNotEmpty()) {
        chunks.add(slice)
      }
      if (end >= text.length) break
      start += (chunkSize - overlap).coerceAtLeast(50)
    }
    return chunks
  }

  /**
   * Computes a deterministic normalized semantic representation vector
   * for local offline Persian and English text matching.
   */
  private fun computeLocalEmbedding(text: String): FloatArray {
    val vector = FloatArray(embeddingDimension) { 0f }
    val normalized = text.lowercase().trim()
    val words = normalized.split(Regex("[\\s,;:.،؛؟!]+")).filter { it.length > 1 }

    for (word in words) {
      val hash = word.hashCode()
      val index = Math.abs(hash) % embeddingDimension
      val sign = if (hash >= 0) 1.0f else -1.0f
      vector[index] += sign * 1.5f

      // Also hash character n-grams for typo & morphology tolerance
      for (i in 0 until word.length - 1) {
        val bigram = word.substring(i, i + 2)
        val bIndex = Math.abs(bigram.hashCode()) % embeddingDimension
        vector[bIndex] += 0.5f
      }
    }

    // L2 Normalize
    var sumSq = 0f
    for (v in vector) sumSq += v * v
    val mag = sqrt(sumSq)
    if (mag > 0.00001f) {
      for (i in vector.indices) {
        vector[i] /= mag
      }
    }
    return vector
  }

  private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
    var dot = 0f
    for (i in 0 until minOf(v1.size, v2.size)) {
      dot += v1[i] * v2[i]
    }
    return dot.coerceIn(-1f, 1f)
  }
}
