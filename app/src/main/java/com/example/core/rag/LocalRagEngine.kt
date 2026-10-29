package com.example.core.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ln

data class DocumentChunk(
  val id: String,
  val documentName: String,
  val chunkIndex: Int,
  val text: String,
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

/**
 * Fully offline lexical retrieval using Okapi BM25 over [SearchTextNormalizer]-folded
 * tokens.
 *
 * The previous engine hashed each chunk into a fixed 128-dim sign vector and ranked by
 * cosine similarity. Hash collisions made those scores noisy and the 0..1 cosine
 * cutoff in the caller arbitrary. BM25 is the classic, well-understood lexical ranker:
 * term frequency saturation, length normalization and document frequency weighting —
 * with no embedding model required, keeping the app 100% offline.
 *
 * [RetrievalResult.similarityScore] is the raw BM25 score (unbounded, > 0 only when at
 * least one query term actually occurs in the chunk), so callers should rank/filter
 * relative to the best hit rather than with a fixed cutoff.
 */
class LocalRagEngine {
  private data class ChunkRecord(
    val chunk: DocumentChunk,
    val termFreqs: Map<String, Int>,
    val tokenCount: Int
  )

  private val records = mutableListOf<ChunkRecord>()

  // Number of chunks containing each term (document frequency for BM25).
  // Kept in sync when a document is re-indexed so the statistics stay honest.
  private val docFreq = mutableMapOf<String, Int>()

  private val k1 = 1.5f
  private val b = 0.75f

  suspend fun indexDocument(documentName: String, content: String): Int = withContext(Dispatchers.Default) {
    val chunks = chunkText(content, chunkSize = 400, overlap = 80)
    val newRecords = chunks.mapIndexed { index, chunkText ->
      val tokens = SearchTextNormalizer.tokenize(chunkText)
      ChunkRecord(
        chunk = DocumentChunk(
          id = "${documentName}_chunk_$index",
          documentName = documentName,
          chunkIndex = index,
          text = chunkText,
          charCount = chunkText.length
        ),
        termFreqs = tokens.groupingBy { it }.eachCount(),
        tokenCount = tokens.size
      )
    }

    synchronized(records) {
      // Drop the stale statistics of a re-indexed document before adding the new ones.
      val removed = records.removeAll { it.chunk.documentName == documentName }
      for (rec in removed) {
        for ((term, tf) in rec.termFreqs) {
          if (tf > 0) {
            val left = (docFreq[term] ?: 0) - 1
            if (left > 0) docFreq[term] = left else docFreq.remove(term)
          }
        }
      }
      for (rec in newRecords) {
        for ((term, tf) in rec.termFreqs) {
          if (tf > 0) docFreq[term] = (docFreq[term] ?: 0) + 1
        }
      }
      records.addAll(newRecords)
    }

    newRecords.size
  }

  suspend fun retrieve(query: String, topK: Int = 3): List<RetrievalResult> = withContext(Dispatchers.Default) {
    if (records.isEmpty()) return@withContext emptyList()
    val queryTokens = SearchTextNormalizer.tokenize(query).distinct()
    if (queryTokens.isEmpty()) return@withContext emptyList()

    val scored = synchronized(records) {
      val n = records.size
      val avgLen = (records.sumOf { it.tokenCount }.toFloat() / n).coerceAtLeast(1f)
      records.map { rec ->
        var score = 0f
        for (term in queryTokens) {
          val tf = rec.termFreqs[term] ?: 0
          if (tf == 0) continue
          val df = docFreq[term] ?: 0
          if (df == 0) continue
          val idf = ln(1f + (n - df + 0.5f) / (df + 0.5f))
          val lenNorm = k1 * (1f - b + b * (rec.tokenCount / avgLen))
          score += idf * (tf * (k1 + 1f)) / (tf + lenNorm)
        }
        RetrievalResult(rec.chunk, score)
      }
    }

    scored.filter { it.similarityScore > 0f }
      .sortedByDescending { it.similarityScore }
      .take(topK)
  }

  fun getSummary(): RagIndexSummary = synchronized(records) {
    val docs = records.map { it.chunk.documentName }.distinct()
    RagIndexSummary(
      totalDocuments = docs.size,
      totalChunks = records.size,
      indexedDocumentNames = docs
    )
  }

  fun clearIndex() = synchronized(records) {
    records.clear()
    docFreq.clear()
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
}
