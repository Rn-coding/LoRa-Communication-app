package com.example.loramessenger.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MessageEntity::class, ExperimentRecordEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun messageDao(): MessageDao
  abstract fun experimentDao(): ExperimentDao

  companion object {
    @Volatile private var instance: AppDatabase? = null
    fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
      instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "lora-messenger.db").fallbackToDestructiveMigration(true).build().also { instance = it }
    }
  }
}
