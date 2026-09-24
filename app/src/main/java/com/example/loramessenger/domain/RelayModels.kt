package com.example.loramessenger.domain

data class RelayNode(
  val id: String,
  val name: String,
  val rssi: Int,
  val isSimulated: Boolean = false,
)

enum class LinkPhase { DISCONNECTED, SCANNING, CONNECTING, NEGOTIATING_MTU, DISCOVERING_SERVICES, ENABLING_NOTIFICATIONS, READY, ERROR }

data class LinkState(
  val phase: LinkPhase = LinkPhase.DISCONNECTED,
  val nodeName: String? = null,
  val mtu: Int? = null,
  val detail: String? = null,
)

enum class DeliveryStatus(val label: String) { QUEUED("Queued"), SENT_TO_NODE("Sent to Node"), AIR_TRANSMITTED("Air Transmitted") }

data class ChatMessage(
  val id: Long,
  val text: String,
  val outgoing: Boolean,
  val timestamp: Long = System.currentTimeMillis(),
  val status: DeliveryStatus? = null,
  val sender: String = "LoRa Node",
)
