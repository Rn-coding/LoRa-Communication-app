package com.example.loramessenger.data.export

import com.example.loramessenger.data.db.ExperimentRecordEntity
import org.json.JSONObject

object CsvExporter {
  private val headers = listOf("Experiment_ID", "Timestamp", "Message_ID", "Emergency_Type", "Severity", "Location", "Budget_Bytes", "Strategy", "Selected_Representation", "Original_Bytes", "Encoded_Bytes", "Airtime_Ms", "Encoding_Time_Us", "Selection_Time_Us", "Preserved_Integrity", "Fits_Budget", "LoRa_TX_Status", "SF", "BW", "CR")

  fun export(records: List<ExperimentRecordEntity>): String = buildString {
    appendLine(headers.joinToString(","))
    records.forEach { record ->
      val report = runCatching { JSONObject(record.reportJson) }.getOrDefault(JSONObject())
      val config = runCatching { JSONObject(record.configJson) }.getOrDefault(JSONObject())
      val row = listOf(record.experimentId, record.timestamp, record.messageId, report.optString("e_type"), report.optString("sev"), report.optString("loc"), record.payloadBudget, config.optString("strat"), record.selectedRepresentation, record.originalBytes, record.encodedBytes, record.airtimeMs, record.encodingTimeUs, record.selectionTimeUs, record.preservedIntegrity, record.fitsBudget, record.loraTxStatus, record.spreadingFactor, record.bandwidthKhz, record.codingRate)
      appendLine(row.joinToString(",") { csv(it.toString()) })
    }
  }

  private fun csv(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value
}
