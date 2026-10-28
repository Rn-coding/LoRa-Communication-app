package com.example.loramessenger.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
  @Query("SELECT * FROM messages ORDER BY timestamp ASC")
  fun observeAll(): Flow<List<MessageEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(message: MessageEntity)

  @Query("UPDATE messages SET status = :status WHERE id = :id")
  suspend fun updateStatus(id: Long, status: String)
}
