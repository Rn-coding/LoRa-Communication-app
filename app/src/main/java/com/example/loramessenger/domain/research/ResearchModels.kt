package com.example.loramessenger.domain.research

enum class EmergencyType(val label: String, val wireKey: String) { MEDICAL("Medical Emergency", "medical"), FIRE("Fire", "fire"), TRAPPED("Trapped Persons", "trapped"), FLOOD("Flood", "flood"), STRUCTURAL_DAMAGE("Structural Damage", "structural"), GENERAL_DISTRESS("General Distress", "distress") }
enum class SeverityLevel(val label: String, val wireKey: String) { LOW("Low", "low"), MEDIUM("Medium", "medium"), HIGH("High", "high"), CRITICAL("Critical", "critical") }
enum class AssistanceRequired(val label: String, val wireKey: String) { MEDICAL("Medical", "medical"), RESCUE("Rescue", "rescue"), FIRE_RESPONSE("Fire Response", "fire_response"), EVACUATION("Evacuation", "evacuation"), SUPPLIES("Supplies", "supplies"), OTHER("Other", "other") }
enum class EncodingStrategy(val label: String, val wireKey: String) { ADAPTIVE("Adaptive Selector", "adaptive"), PLAIN_TEXT("Plain Text Baseline", "plain_text"), COMPACT_TEXT("Compact Structured Text", "compact_text"), PACKED_BINARY("Packed Binary", "packed_binary"), COMPRESSED("Compressed Structured", "compressed") }
enum class SpreadingFactor(val value: Int) { SF7(7), SF8(8), SF9(9), SF10(10), SF11(11), SF12(12) }
enum class LoRaBandwidth(val khz: Int) { BW_125(125), BW_250(250), BW_500(500) }
enum class CodingRate(val label: String) { CR_4_5("4/5"), CR_4_6("4/6"), CR_4_7("4/7"), CR_4_8("4/8") }

data class StructuredEmergencyReport(val messageId: Int, val emergencyType: EmergencyType, val location: String, val severity: SeverityLevel, val peopleAffected: Int, val assistanceRequired: AssistanceRequired, val description: String? = null)
data class ExperimentConfig(val strategy: EncodingStrategy = EncodingStrategy.ADAPTIVE, val payloadBudget: Int = 32, val spreadingFactor: SpreadingFactor = SpreadingFactor.SF10, val bandwidth: LoRaBandwidth = LoRaBandwidth.BW_125, val codingRate: CodingRate = CodingRate.CR_4_5, val repetitions: Int = 1)
data class CandidateEvaluation(val candidateName: String, val encodedBytes: Int, val fitsBudget: Boolean, val calculatedAirtimeMs: Long)
data class ExperimentResult(val experimentId: Long = System.currentTimeMillis(), val messageId: Int, val selectedRepresentation: String, val originalMessageSize: Int, val encodedPayloadSize: Int, val payloadBudget: Int, val calculatedAirtimeMs: Long, val encodingTimeUs: Long, val selectionTimeUs: Long, val mandatoryFieldsPreserved: Boolean, val payloadFitsBudget: Boolean, val loraTxStatus: String, val candidateBreakdown: List<CandidateEvaluation>, val firmwareVersion: String = "v1.0-research", val timestamp: Long = System.currentTimeMillis(), val configurationConfirmed: Boolean = false, val configurationSignature: String? = null)
