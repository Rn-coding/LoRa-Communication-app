package com.example.loramessenger.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.loramessenger.domain.research.ExperimentResult

@Entity(tableName = "experiment_records")
data class ExperimentRecordEntity(@PrimaryKey val experimentId: Long, val timestamp: Long, val messageId: Int, val reportJson: String, val configJson: String, val selectedRepresentation: String, val candidateMetricsJson: String, val originalBytes: Int, val encodedBytes: Int, val payloadBudget: Int, val airtimeMs: Long, val encodingTimeUs: Long, val selectionTimeUs: Long, val preservedIntegrity: Boolean, val fitsBudget: Boolean, val loraTxStatus: String, val spreadingFactor: Int, val bandwidthKhz: Int, val codingRate: String)
