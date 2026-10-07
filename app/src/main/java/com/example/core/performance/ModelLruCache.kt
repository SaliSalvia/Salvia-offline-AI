package com.example.core.performance

import android.content.Context
import com.example.core.model.ModelMetadata
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Intelligent LRU Model Cache.
 * Prevents redundant Load/Unload cycles between chat interactions.
 * Keeps the most recently used model warm in memory while respecting device RAM limits.
 */
class ModelLruCache(
  private val maxLoadedModelsCount: Int = 2,
  private val maxTotalMemoryMb: Int = 8000 // Safe upper bound for 12GB device class
) {
  private val mutex = Mutex()
  private val lruOrder = mutableListOf<String>() // model IDs in access order
  private val loadedInstances = mutableMapOf<String, ModelMetadata>()

  suspend fun touch(model: ModelMetadata) = mutex.withLock {
    lruOrder.remove(model.id)
    lruOrder.add(0, model.id)
    loadedInstances[model.id] = model
  }

  suspend fun canAccommodate(newModelMb: Int, currentFreeRamMb: Int): Boolean = mutex.withLock {
    val currentOccupied = loadedInstances.values.sumOf { it.estimatedMemoryMb }
    (currentOccupied + newModelMb) <= maxTotalMemoryMb && (newModelMb < currentFreeRamMb * 0.85)
  }

  suspend fun evictIfNeeded(newModelMb: Int, currentFreeRamMb: Int): List<ModelMetadata> = mutex.withLock {
    val evicted = mutableListOf<ModelMetadata>()
    var currentOccupied = loadedInstances.values.sumOf { it.estimatedMemoryMb }

    // Evict least recently used models if limits exceeded
    while ((lruOrder.size >= maxLoadedModelsCount || (currentOccupied + newModelMb) > maxTotalMemoryMb) && lruOrder.size > 1) {
      val lruId = lruOrder.lastOrNull() ?: break
      val toEvict = loadedInstances.remove(lruId)
      lruOrder.remove(lruId)
      if (toEvict != null) {
        evicted.add(toEvict)
        currentOccupied -= toEvict.estimatedMemoryMb
      }
    }
    evicted
  }

  suspend fun isWarm(modelId: String): Boolean = mutex.withLock {
    loadedInstances.containsKey(modelId)
  }

  suspend fun getLoadedModels(): List<ModelMetadata> = mutex.withLock {
    loadedInstances.values.toList()
  }

  suspend fun clear() = mutex.withLock {
    lruOrder.clear()
    loadedInstances.clear()
  }
}
