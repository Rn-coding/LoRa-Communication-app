package com.example.loramessenger.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.loramessenger.domain.RelayNode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class BleScanner(private val context: Context) {
  fun scan(): Flow<RelayNode> = callbackFlow {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
      close(SecurityException("Bluetooth scan permission is not granted"))
      return@callbackFlow
    }
    val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
    if (scanner == null) {
      close(IllegalStateException("Bluetooth is unavailable or disabled"))
      return@callbackFlow
    }
    val callback = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        val name = result.scanRecord?.deviceName ?: result.device.name ?: "Unknown BLE device"
        val advertisesRelay = result.scanRecord?.serviceUuids?.any { it.uuid == BleContract.SERVICE_UUID } == true
        val recognizedName = name.startsWith("LoRa Node", ignoreCase = true) || name.startsWith("ESP32", ignoreCase = true)
        if (advertisesRelay || recognizedName) trySend(RelayNode(result.device.address, name, result.rssi))
      }

      override fun onScanFailed(errorCode: Int) { close(IllegalStateException("BLE scan failed ($errorCode)")) }
    }
    scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
    launch { delay(SCAN_DURATION_MS); close() }
    awaitClose { scanner.stopScan(callback) }
  }

  private companion object { const val SCAN_DURATION_MS = 12_000L }
}
