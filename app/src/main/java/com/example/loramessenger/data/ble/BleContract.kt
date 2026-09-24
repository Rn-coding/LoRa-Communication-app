package com.example.loramessenger.data.ble

import java.util.UUID

object BleContract {
  val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-1234567890AB")
  val RX_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-1234567890AC")
  val TX_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-1234567890AD")
  val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
  const val SIMULATED_NODE_ID = "SIMULATED_LORA_NODE_1"
}
