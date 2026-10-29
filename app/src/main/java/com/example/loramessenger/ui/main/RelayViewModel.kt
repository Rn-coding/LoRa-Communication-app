package com.example.loramessenger.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.loramessenger.data.ble.BleContract
import com.example.loramessenger.data.ble.BleRelayRuntime
import com.example.loramessenger.data.ble.BleScanner
import com.example.loramessenger.data.db.AppDatabase
import com.example.loramessenger.data.db.MessageEntity
import com.example.loramessenger.data.db.ExperimentRecordEntity
import com.example.loramessenger.data.export.CsvExporter
import com.example.loramessenger.data.research.ResearchProtocol
import com.example.loramessenger.service.BleRelayForegroundService
import com.example.loramessenger.domain.ChatMessage
import com.example.loramessenger.domain.DeliveryStatus
import com.example.loramessenger.domain.LinkPhase
import com.example.loramessenger.domain.LinkState
import com.example.loramessenger.domain.RelayNode
import com.example.loramessenger.domain.research.ExperimentConfig
import com.example.loramessenger.domain.research.ExperimentResult
import com.example.loramessenger.domain.research.StructuredEmergencyReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class RelayViewModel(application: Application) : AndroidViewModel(application) {
  private val scanner = BleScanner(application.applicationContext)
  private val gatt = BleRelayRuntime.manager(application.applicationContext)
  private val messageDao = AppDatabase.get(application.applicationContext).messageDao()
  private val experimentDao = AppDatabase.get(application.applicationContext).experimentDao()
  private val _nodes = MutableStateFlow<List<RelayNode>>(emptyList())
  val nodes: StateFlow<List<RelayNode>> = _nodes.asStateFlow()
  val linkState: StateFlow<LinkState> = gatt.linkState
  val messages: StateFlow<List<ChatMessage>> = messageDao.observeAll().map { entities -> entities.map(MessageEntity::toDomain) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
  private val _demoEnabled = MutableStateFlow(false)
  val demoEnabled: StateFlow<Boolean> = _demoEnabled.asStateFlow()
  private val _researchResult = MutableStateFlow<ExperimentResult?>(null)
  val researchResult: StateFlow<ExperimentResult?> = _researchResult.asStateFlow()
  private val _researchBusy = MutableStateFlow(false)
  val researchBusy: StateFlow<Boolean> = _researchBusy.asStateFlow()
  private val _researchError = MutableStateFlow<String?>(null)
  val researchError: StateFlow<String?> = _researchError.asStateFlow()
  private var researchWaiter: CompletableDeferred<ExperimentResult>? = null

  init {
    viewModelScope.launch {
      gatt.incoming.collect { payload ->
        if (ResearchProtocol.isResearchResponse(payload)) {
          ResearchProtocol.parseResponse(payload)?.let { result ->
            _researchResult.value = result
            researchWaiter?.complete(result)
          }
        } else receive(payload)
      }
    }
  }

  fun startScan() = viewModelScope.launch {
    _isScanning.value = true
    _nodes.value = if (_demoEnabled.value) listOf(simulatedNode()) else emptyList()
    try {
      scanner.scan().collect { found ->
        _nodes.update { existing ->
          val old = existing.firstOrNull { it.id == found.id }
          val smoothed = old?.let { ((it.rssi * 0.65) + (found.rssi * 0.35)).roundToInt() } ?: found.rssi
          (existing.filterNot { it.id == found.id } + found.copy(rssi = smoothed)).sortedByDescending { it.rssi }
        }
      }
    } finally { _isScanning.value = false }
  }

  fun setDemoEnabled(enabled: Boolean) {
    _demoEnabled.value = enabled
    _nodes.update { current -> if (enabled) (current.filterNot { it.isSimulated } + simulatedNode()) else current.filterNot { it.isSimulated } }
  }

  fun connect(node: RelayNode) = viewModelScope.launch { _isScanning.value = false; BleRelayForegroundService.start(getApplication<Application>().applicationContext, node) }
  fun disconnect() = viewModelScope.launch { BleRelayForegroundService.stop(getApplication<Application>().applicationContext) }

  fun send(text: String) {
    if (text.isBlank() || linkState.value.phase != LinkPhase.READY) return
    val id = System.nanoTime()
    val queued = ChatMessage(id, text.trim(), outgoing = true, status = DeliveryStatus.QUEUED)
    viewModelScope.launch { messageDao.insert(MessageEntity.fromDomain(queued)) }
    viewModelScope.launch {
      if (!gatt.send(text.trim())) return@launch
      updateStatus(id, DeliveryStatus.SENT_TO_NODE)
      delay(350)
      updateStatus(id, DeliveryStatus.AIR_TRANSMITTED)
    }
  }

  fun runExperiment(report: StructuredEmergencyReport, config: ExperimentConfig) {
    if (_researchBusy.value || linkState.value.phase != LinkPhase.READY) return
    _researchBusy.value = true; _researchResult.value = null; _researchError.value = null
    viewModelScope.launch {
      val waiter = CompletableDeferred<ExperimentResult>()
      researchWaiter = waiter
      val payload = ResearchProtocol.request(report, config)
      val sent = gatt.send(payload)
      val result = if (sent) withTimeoutOrNull(15_000) { waiter.await() } else null
      researchWaiter = null
      if (result == null) _researchError.value = if (sent) "Timed out waiting for ESP32 research response" else "Unable to send research request"
      else experimentDao.insert(result.toEntity(payload, config))
      _researchBusy.value = false
    }
  }

  fun exportExperiments(onCsvReady: (String) -> Unit) = viewModelScope.launch { onCsvReady(CsvExporter.export(experimentDao.getAllExperiments().first())) }

  private fun updateStatus(id: Long, status: DeliveryStatus) { viewModelScope.launch { messageDao.updateStatus(id, status.name) } }
  private fun receive(text: String) { viewModelScope.launch { messageDao.insert(MessageEntity.fromDomain(ChatMessage(System.nanoTime(), text, outgoing = false, sender = linkState.value.nodeName ?: "LoRa Node"))) } }
  private fun simulatedNode() = RelayNode(BleContract.SIMULATED_NODE_ID, "LoRa Node 1 (Simulated)", -48, isSimulated = true)

  private fun ExperimentResult.toEntity(request: String, config: ExperimentConfig): ExperimentRecordEntity {
    val requestJson = org.json.JSONObject(request)
    val reportJson = org.json.JSONObject(requestJson.toString()).apply { remove("cfg") }.toString()
    val configJson = requestJson.optJSONObject("cfg")?.toString().orEmpty()
    val candidateJson = org.json.JSONArray(candidateBreakdown.map { org.json.JSONObject().apply { put("name", it.candidateName); put("bytes", it.encodedBytes); put("fits", it.fitsBudget); put("airtime_ms", it.calculatedAirtimeMs) } }).toString()
    return ExperimentRecordEntity(experimentId, timestamp, messageId, reportJson, configJson, selectedRepresentation, candidateJson, originalMessageSize, encodedPayloadSize, payloadBudget, calculatedAirtimeMs, encodingTimeUs, selectionTimeUs, mandatoryFieldsPreserved, payloadFitsBudget, loraTxStatus, config.spreadingFactor.value, config.bandwidth.khz, config.codingRate.label)
  }
}
