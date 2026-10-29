package com.example.loramessenger.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.loramessenger.domain.LinkPhase
import com.example.loramessenger.domain.LinkState
import com.example.loramessenger.domain.RelayNode
import com.example.loramessenger.data.research.ResearchProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BleGattManager(private val context: Context, private val scope: CoroutineScope) {
  private val queue = GattCommandQueue(scope)
  private var gatt: BluetoothGatt? = null
  private var connectedNode: RelayNode? = null
  private var txCharacteristic: BluetoothGattCharacteristic? = null
  private var rxCharacteristic: BluetoothGattCharacteristic? = null
  private var simulated = false
  private var shouldReconnect = false
  private var reconnectAttempt = 0
  private var reconnectJob: Job? = null

  private val _linkState = MutableStateFlow(LinkState())
  val linkState: StateFlow<LinkState> = _linkState.asStateFlow()
  private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val incoming: SharedFlow<String> = _incoming.asSharedFlow()

  suspend fun connect(node: RelayNode) {
    disconnect()
    connectedNode = node
    simulated = node.isSimulated
    shouldReconnect = true
    reconnectAttempt = 0
    if (simulated) { connectSimulated(node); return }
    connectTransport(node)
  }

  private suspend fun connectTransport(node: RelayNode) {
    if (!shouldReconnect) return
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      _linkState.value = LinkState(LinkPhase.ERROR, node.name, detail = "Bluetooth connection permission is not granted")
      return
    }
    _linkState.value = LinkState(LinkPhase.CONNECTING, node.name)
    try {
      val device = context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(node.id)
      if (device == null) error("Bluetooth adapter unavailable")
      queue.enqueue("connect") { device.connectGatt(context, false, callback).also { gatt = it } != null }
    } catch (error: Exception) {
      _linkState.value = LinkState(LinkPhase.ERROR, node.name, detail = error.message)
      scheduleReconnect(node)
    }
  }

  suspend fun disconnect() {
    shouldReconnect = false
    reconnectJob?.cancel()
    reconnectJob = null
    if (simulated) {
      simulated = false
      connectedNode = null
      _linkState.value = LinkState()
      return
    }
    val activeGatt = gatt
    if (activeGatt != null) queue.enqueueImmediate { activeGatt.disconnect(); activeGatt.close() }
    gatt = null; txCharacteristic = null; rxCharacteristic = null; connectedNode = null
    _linkState.value = LinkState()
  }

  suspend fun send(text: String): Boolean {
    if (_linkState.value.phase != LinkPhase.READY) return false
    if (simulated) {
      scope.launch {
        delay(if (ResearchProtocol.isResearchRequest(text)) 800 else 700)
        _incoming.emit(if (ResearchProtocol.isResearchRequest(text)) ResearchProtocol.simulateResponse(text) else "Echo from LoRa Node 1: $text")
      }
      return true
    }
    val characteristic = rxCharacteristic ?: return false
    return try {
      queue.enqueue("write characteristic") {
        val activeGatt = gatt ?: return@enqueue false
        activeGatt.writeCharacteristicCompat(characteristic, text.encodeToByteArray())
      }
      true
    } catch (_: Exception) { false }
  }

  private fun connectSimulated(node: RelayNode) = scope.launch {
    _linkState.value = LinkState(LinkPhase.CONNECTING, node.name); delay(450)
    _linkState.value = LinkState(LinkPhase.NEGOTIATING_MTU, node.name); delay(450)
    _linkState.value = LinkState(LinkPhase.DISCOVERING_SERVICES, node.name, 247); delay(450)
    _linkState.value = LinkState(LinkPhase.ENABLING_NOTIFICATIONS, node.name, 247); delay(450)
    _linkState.value = LinkState(LinkPhase.READY, node.name, 247)
  }

  private fun requestMtu() = scope.launch {
    val activeGatt = gatt ?: return@launch
    _linkState.value = LinkState(LinkPhase.NEGOTIATING_MTU, connectedNode?.name)
    try { queue.enqueue("request MTU") { activeGatt.requestMtu(247) } }
    catch (error: Exception) { fail(error) }
  }

  private fun discoverServices() = scope.launch {
    val activeGatt = gatt ?: return@launch
    _linkState.value = LinkState(LinkPhase.DISCOVERING_SERVICES, connectedNode?.name, 247)
    try { queue.enqueue("discover services") { activeGatt.discoverServices() } }
    catch (error: Exception) { fail(error) }
  }

  private fun enableNotifications() = scope.launch {
    val activeGatt = gatt ?: return@launch
    val tx = txCharacteristic ?: run { fail(IllegalStateException("LoRa TX characteristic unavailable")); return@launch }
    val descriptor = tx.getDescriptor(BleContract.CCCD_UUID) ?: run { fail(IllegalStateException("TX CCCD unavailable")); return@launch }
    _linkState.value = LinkState(LinkPhase.ENABLING_NOTIFICATIONS, connectedNode?.name, 247)
    try {
      queue.enqueueImmediate { check(activeGatt.setCharacteristicNotification(tx, true)) }
      queue.enqueue("write CCCD") {
        activeGatt.writeDescriptorCompat(descriptor)
      }
      _linkState.value = LinkState(LinkPhase.READY, connectedNode?.name, 247)
    } catch (error: Exception) { fail(error) }
  }

  private fun fail(error: Throwable) { _linkState.value = LinkState(LinkPhase.ERROR, connectedNode?.name, detail = error.message) }

  private val callback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(callbackGatt: BluetoothGatt, status: Int, newState: Int) {
      val success = status == BluetoothGatt.GATT_SUCCESS
      queue.complete(success)
      if (success && newState == BluetoothProfile.STATE_CONNECTED) { reconnectAttempt = 0; requestMtu() }
      else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        val node = connectedNode
        gatt = null; txCharacteristic = null; rxCharacteristic = null
        _linkState.value = LinkState(LinkPhase.DISCONNECTED, connectedNode?.name, detail = if (status == BluetoothGatt.GATT_SUCCESS) null else "GATT error $status")
        if (shouldReconnect && node != null && !node.isSimulated) scheduleReconnect(node)
      }
    }

    override fun onMtuChanged(callbackGatt: BluetoothGatt, mtu: Int, status: Int) {
      queue.complete(status == BluetoothGatt.GATT_SUCCESS)
      if (status == BluetoothGatt.GATT_SUCCESS) discoverServices() else fail(IllegalStateException("MTU request failed ($status)"))
    }

    override fun onServicesDiscovered(callbackGatt: BluetoothGatt, status: Int) {
      queue.complete(status == BluetoothGatt.GATT_SUCCESS)
      if (status != BluetoothGatt.GATT_SUCCESS) return fail(IllegalStateException("Service discovery failed ($status)"))
      val service = callbackGatt.getService(BleContract.SERVICE_UUID)
      rxCharacteristic = service?.getCharacteristic(BleContract.RX_UUID)
      txCharacteristic = service?.getCharacteristic(BleContract.TX_UUID)
      if (rxCharacteristic == null || txCharacteristic == null) fail(IllegalStateException("LoRa bridge service is incomplete")) else enableNotifications()
    }

    override fun onDescriptorWrite(callbackGatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) { queue.complete(status == BluetoothGatt.GATT_SUCCESS) }
    override fun onCharacteristicWrite(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) { queue.complete(status == BluetoothGatt.GATT_SUCCESS) }
    override fun onCharacteristicChanged(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) { if (characteristic.uuid == BleContract.TX_UUID) _incoming.tryEmit(value.toString(Charsets.UTF_8)) }
    @Deprecated("Called on Android 12 and lower")
    @Suppress("DEPRECATION")
    override fun onCharacteristicChanged(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) { if (characteristic.uuid == BleContract.TX_UUID) _incoming.tryEmit(characteristic.value?.toString(Charsets.UTF_8).orEmpty()) }
  }

  private fun scheduleReconnect(node: RelayNode) {
    if (!shouldReconnect || node.isSimulated || reconnectJob?.isActive == true) return
    val delayMs = minOf(30_000L, 1_000L shl reconnectAttempt.coerceAtMost(4))
    reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(30)
    _linkState.value = LinkState(LinkPhase.CONNECTING, node.name, detail = "Retrying in ${delayMs / 1_000}s")
    reconnectJob = scope.launch {
      delay(delayMs)
      reconnectJob = null
      connectTransport(node)
    }
  }

  @Suppress("DEPRECATION")
  private fun BluetoothGatt.writeCharacteristicCompat(characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
    else { characteristic.value = value; writeCharacteristic(characteristic) }

  @Suppress("DEPRECATION")
  private fun BluetoothGatt.writeDescriptorCompat(descriptor: BluetoothGattDescriptor): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
    else { descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; writeDescriptor(descriptor) }
}
