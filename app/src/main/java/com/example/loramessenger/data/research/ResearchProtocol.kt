package com.example.loramessenger.data.research

import com.example.loramessenger.domain.research.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.max

object ResearchProtocol {
  fun request(report: StructuredEmergencyReport, config: ExperimentConfig): String = JSONObject().apply {
    put("type", "research_request"); put("msg_id", report.messageId); put("e_type", report.emergencyType.wireKey); put("loc", report.location); put("sev", report.severity.wireKey); put("pop", report.peopleAffected); put("asst", report.assistanceRequired.wireKey); put("desc", report.description ?: "")
    put("cfg", JSONObject().apply { put("strat", config.strategy.wireKey); put("budget", config.payloadBudget); put("sf", config.spreadingFactor.value); put("bw", config.bandwidth.khz); put("cr", config.codingRate.label); put("rep", config.repetitions) })
  }.toString()

  fun isResearchRequest(payload: String) = runCatching { JSONObject(payload).optString("type") == "research_request" }.getOrDefault(false)

  fun isResearchResponse(payload: String) = runCatching { JSONObject(payload).optString("type") == "research_response" }.getOrDefault(false)

  fun parseResponse(payload: String): ExperimentResult? = runCatching {
    val json = JSONObject(payload); if (json.optString("type") != "research_response") return null
    val candidates = buildList {
      val array = json.optJSONArray("candidates") ?: JSONArray()
      for (index in 0 until array.length()) { val item = array.getJSONObject(index); add(CandidateEvaluation(item.optString("name"), item.optInt("bytes"), item.optBoolean("fits"), item.optLong("airtime_ms"))) }
    }
    val signature = json.optString("config_signature").takeIf { it.isNotBlank() }
    ExperimentResult(messageId = json.optInt("msg_id"), selectedRepresentation = json.optString("selected"), originalMessageSize = json.optInt("orig_size"), encodedPayloadSize = json.optInt("enc_size"), payloadBudget = json.optInt("budget"), calculatedAirtimeMs = json.optLong("airtime_ms"), encodingTimeUs = json.optLong("enc_us"), selectionTimeUs = json.optLong("sel_us"), mandatoryFieldsPreserved = json.optBoolean("valid"), payloadFitsBudget = json.optBoolean("fits"), loraTxStatus = json.optString("tx_status"), candidateBreakdown = candidates, firmwareVersion = json.optString("firmware", "v1.0-research"), configurationConfirmed = json.optBoolean("config_applied", false) && signature != null, configurationSignature = signature)
  }.getOrNull()

  fun simulateResponse(payload: String): String {
    val request = JSONObject(payload); val config = request.optJSONObject("cfg") ?: JSONObject(); val budget = config.optInt("budget", 32); val sf = config.optInt("sf", 10); val bw = config.optInt("bw", 125); val strategy = config.optString("strat", "adaptive")
    val sourceLength = request.toString().length; val descriptionLength = request.optString("desc").length; val locationLength = request.optString("loc").length
    val sizes = linkedMapOf("Plain Text" to max(110, sourceLength + descriptionLength), "Compact Text" to 34 + locationLength + descriptionLength / 2, "Packed Binary" to 18 + (request.optInt("pop") / 16), "Compressed" to max(28, (sourceLength * .34).toInt()))
    val candidates = sizes.map { (name, bytes) -> JSONObject().apply { put("name", name); put("bytes", bytes); put("fits", bytes <= budget); put("airtime_ms", airtime(bytes, sf, bw)) } }
    val selected = when (strategy) { "plain_text" -> "Plain Text"; "compact_text" -> "Compact Text"; "packed_binary" -> "Packed Binary"; "compressed" -> "Compressed"; else -> sizes.filterValues { it <= budget }.minByOrNull { it.value }?.key ?: sizes.minBy { it.value }.key }
    val encoded = sizes.getValue(selected)
    return JSONObject().apply { put("type", "research_response"); put("msg_id", request.optInt("msg_id")); put("selected", selected); put("orig_size", sourceLength); put("enc_size", encoded); put("budget", budget); put("airtime_ms", airtime(encoded, sf, bw)); put("enc_us", 420); put("sel_us", 115); put("valid", true); put("fits", encoded <= budget); put("tx_status", "COMPLETED"); put("config_applied", true); put("config_signature", "sim-${request.optInt("msg_id")}-${sf}-${bw}"); put("firmware", "v1.0-research-sim"); put("candidates", JSONArray(candidates)) }.toString()
  }

  private fun airtime(bytes: Int, sf: Int, bandwidthKhz: Int): Long { val symbolMs = (1 shl sf).toDouble() / bandwidthKhz; val symbols = 12 + ceil(bytes * 8 / 4.5); return (symbols * symbolMs).toLong() }
}
