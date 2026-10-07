package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
  @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
  fun getAllSessions(): Flow<List<ChatSessionEntity>>

  @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
  suspend fun getSessionById(sessionId: Long): ChatSessionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSession(session: ChatSessionEntity): Long

  @Update
  suspend fun updateSession(session: ChatSessionEntity)

  @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
  suspend fun deleteSession(sessionId: Long)

  @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
  fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: ChatMessageEntity): Long

  @Query("DELETE FROM chat_messages WHERE id = :messageId")
  suspend fun deleteMessage(messageId: Long)

  @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
  suspend fun clearMessagesForSession(sessionId: Long)
}

@Dao
interface ModelSlotDao {
  @Query("SELECT * FROM model_slots")
  fun getAllSlots(): Flow<List<ModelSlotEntity>>

  @Query("SELECT * FROM model_slots WHERE id = :id LIMIT 1")
  suspend fun getSlot(id: String): ModelSlotEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdateSlot(slot: ModelSlotEntity)
}
