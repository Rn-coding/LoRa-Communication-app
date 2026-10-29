package com.example.loramessenger.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExperimentDao {
  @Query("SELECT * FROM experiment_records ORDER BY timestamp DESC") fun getAllExperiments(): Flow<List<ExperimentRecordEntity>>
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(record: ExperimentRecordEntity)
  @Query("DELETE FROM experiment_records") suspend fun deleteAll()
}
