package com.example.loramessenger.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.loramessenger.data.ble.BleContract
import com.example.loramessenger.data.ble.BleGattManager
import com.example.loramessenger.data.ble.BleScanner
import com.example.loramessenger.domain.ChatMessage
import com.example.loramessenger.domain.DeliveryStatus
import com.example.loramessenger.domain.LinkPhase
import com.example.loramessenger.domain.LinkState
import com.example.loramessenger.domain.RelayNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class RelayViewModel(application: Application) : AndroidViewModel(application) {
  private val scanner = BleScanner(application.applicationContext)
  private val gatt = BleGattManager(application.applicationContext, viewModelScope)
  private val _nodes = MutableStateFlow<List<RelayNode>>(emptyList())
  val nodes: StateFlow<List<RelayNode>> = _nodes.asStateFlow()
  val linkState: StateFlow<LinkState> = gatt.linkState
  private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
  private val _demoEnabled = MutableStateFlow(false)
  val demoEnabled: StateFlow<Boolean> = _demoEnabled.asStateFlow()

  init {
    viewModelScope.launch { gatt.incoming.collect { receive(it) } }
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

  fun connect(node: RelayNode) = viewModelScope.launch { _isScanning.value = false; gatt.connect(node) }
  fun disconnect() = viewModelScope.launch { gatt.disconnect() }

  fun send(text: String) {
    if (text.isBlank() || linkState.value.phase != LinkPhase.READY) return
    val id = System.nanoTime()
    _messages.update { it + ChatMessage(id, text.trim(), outgoing = true, status = DeliveryStatus.QUEUED) }
    viewModelScope.launch {
      if (!gatt.send(text.trim())) return@launch
      updateStatus(id, DeliveryStatus.SENT_TO_NODE)
      delay(350)
      updateStatus(id, DeliveryStatus.AIR_TRANSMITTED)
    }
  }

  private fun updateStatus(id: Long, status: DeliveryStatus) { _messages.update { messages -> messages.map { if (it.id == id) it.copy(status = status) else it } } }
  private fun receive(text: String) { _messages.update { it + ChatMessage(System.nanoTime(), text, outgoing = false, sender = linkState.value.nodeName ?: "LoRa Node") } }
  private fun simulatedNode() = RelayNode(BleContract.SIMULATED_NODE_ID, "LoRa Node 1 (Simulated)", -48, isSimulated = true)
}
