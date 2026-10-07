package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val activeModelName: String = "DeepSeek-R1-Distill-Qwen (Q4_K_M)",
  val isPinned: Boolean = false
)

@Entity(
  tableName = "chat_messages",
  foreignKeys = [
    ForeignKey(
      entity = ChatSessionEntity::class,
      parentColumns = ["id"],
      childColumns = ["sessionId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val sessionId: Long,
  val role: String, // "user", "assistant", "system"
  val content: String,
  val thinkingContent: String? = null,
  val thinkingDurationMs: Long = 0L,
  val tokensPerSecond: Float = 0f,
  val attachmentsJson: String? = null,
  val imageResultUri: String? = null,
  val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "model_slots")
data class ModelSlotEntity(
  @PrimaryKey
  val id: String, // "slot_text" or "slot_image"
  val slotType: String, // "TEXT" or "IMAGE"
  val modelName: String,
  val filePath: String? = null,
  val fileSizeFormatted: String = "0 MB",
  val architecture: String = "qwen2",
  val quantization: String = "Q4_K_M",
  val contextLength: Int = 4096,
  val isLoaded: Boolean = true,
  val activePreset: String = "Redmi Note 14 Pro Turbo"
)
