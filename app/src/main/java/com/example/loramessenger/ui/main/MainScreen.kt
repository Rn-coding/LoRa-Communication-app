package com.example.loramessenger.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.loramessenger.domain.ChatMessage
import com.example.loramessenger.domain.LinkPhase
import com.example.loramessenger.domain.LinkState
import com.example.loramessenger.domain.RelayNode

@Composable
fun MainScreen(onItemClick: (NavKey) -> Unit, modifier: Modifier = Modifier, viewModel: RelayViewModel = viewModel()) {
  val context = LocalContext.current
  val permissions = remember { requiredPermissions() }
  var granted by remember { mutableStateOf(permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> granted = permissions.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } }
  if (!granted) PermissionFallbackCard(modifier) { launcher.launch(permissions.toTypedArray()) }
  else ResearchTabs(modifier, viewModel)
}

@Composable
private fun ResearchTabs(modifier: Modifier, viewModel: RelayViewModel) {
  var selected by remember { mutableStateOf(0) }
  Column(modifier.fillMaxSize()) {
    PrimaryTabRow(selectedTabIndex = selected) {
      Tab(selected == 0, onClick = { selected = 0 }, text = { Text("Disaster Chat") })
      Tab(selected == 1, onClick = { selected = 1 }, text = { Text("Research Mode") })
    }
    Box(Modifier.weight(1f).fillMaxWidth()) {
      if (selected == 0) RelayMessenger(Modifier.fillMaxSize().padding(horizontal = 16.dp), viewModel)
      else ResearchScreen(Modifier.fillMaxSize().padding(horizontal = 16.dp), viewModel)
    }
  }
}

private fun requiredPermissions(): List<String> = buildList {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT) } else add(Manifest.permission.ACCESS_FINE_LOCATION)
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
private fun PermissionFallbackCard(modifier: Modifier, onGrant: () -> Unit) {
  Column(modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
      Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(16.dp)); Text("Emergency Bluetooth access", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp)); Text("Nearby Devices permission lets this phone find and connect to a LoRa relay node.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp)); Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text("Grant Emergency Bluetooth Permissions") }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelayMessenger(modifier: Modifier, viewModel: RelayViewModel) {
  val link by viewModel.linkState.collectAsStateWithLifecycle()
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val nodes by viewModel.nodes.collectAsStateWithLifecycle()
  val scanning by viewModel.isScanning.collectAsStateWithLifecycle()
  val demo by viewModel.demoEnabled.collectAsStateWithLifecycle()
  var scannerOpen by remember { mutableStateOf(false) }
  var draft by remember { mutableStateOf("") }
  val haptic = LocalHapticFeedback.current
  val incomingCount = messages.count { !it.outgoing }
  LaunchedEffect(incomingCount) { if (incomingCount > 0) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) }

  Column(modifier.fillMaxSize()) {
    EmergencyNodeHud(if (scanning && link.phase == LinkPhase.DISCONNECTED) link.copy(phase = LinkPhase.SCANNING) else link, viewModel::disconnect)
    Spacer(Modifier.height(12.dp))
    ChatTimeline(messages, Modifier.weight(1f))
    QuickPresets(onSend = viewModel::send)
    QuickMessageInput(draft, { draft = it }, { viewModel.send(draft); draft = "" }, { scannerOpen = true; viewModel.startScan() }, link.phase == LinkPhase.READY)
  }
  if (scannerOpen) {
    NodeScannerBottomSheet(nodes, scanning, demo, onDismiss = { scannerOpen = false }, onDemoToggle = viewModel::setDemoEnabled, onNodeClick = { node -> scannerOpen = false; viewModel.connect(node) })
  }
}

@Composable
private fun EmergencyNodeHud(link: LinkState, onDisconnect: () -> Unit) {
  val (label, color) = when (link.phase) {
    LinkPhase.READY -> "CONNECTED: ${link.nodeName} (MTU: ${link.mtu ?: 247})" to Color(0xFF69F0AE)
    LinkPhase.SCANNING -> "SCANNING" to Color(0xFFFFB74D)
    LinkPhase.CONNECTING -> "CONNECTING..." to Color(0xFFFFB74D)
    LinkPhase.NEGOTIATING_MTU -> "NEGOTIATING MTU (247 bytes)..." to Color(0xFFFFB74D)
    LinkPhase.DISCOVERING_SERVICES -> "DISCOVERING SERVICES..." to Color(0xFFFFB74D)
    LinkPhase.ENABLING_NOTIFICATIONS -> "ENABLING NOTIFICATIONS..." to Color(0xFFFFB74D)
    LinkPhase.ERROR -> "LINK ERROR: ${link.detail ?: "Retry scan"}" to Color(0xFFFF8A80)
    LinkPhase.DISCONNECTED -> "NO NODE CONNECTED" to Color(0xFFFF8A80)
  }
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
    Column(Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Emergency, null, tint = MaterialTheme.colorScheme.error)
        Text("  EMERGENCY NODE", fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("433 MHz", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
      }
      Spacer(Modifier.height(12.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.background(color.copy(alpha = .12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 7.dp).weight(1f))
        if (link.phase == LinkPhase.READY) { Spacer(Modifier.width(4.dp)); TextButton(onClick = onDisconnect) { Text("Disconnect") } }
      }
    }
  }
}

@Composable
private fun ChatTimeline(messages: List<ChatMessage>, modifier: Modifier) {
  val listState = rememberLazyListState()
  LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
  if (messages.isEmpty()) {
    Column(modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterHorizontally) {
      Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null, tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.height(10.dp))
      Text("No messages yet", style = MaterialTheme.typography.titleMedium); Text("Scan for a LoRa node or use Demo Mode.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  } else LazyColumn(modifier = modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) { items(messages, key = { it.id }) { MessageBubble(it) } }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
    Card(colors = CardDefaults.cardColors(containerColor = if (message.outgoing) Color(0xFF1E4B3A) else MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
      Column(Modifier.padding(12.dp).width(240.dp)) {
        if (!message.outgoing) Text(message.sender, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(message.text)
        if (message.outgoing) Text("✓ ${message.status?.label ?: "Queued"}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
      }
    }
  }
}

@Composable
private fun QuickPresets(onSend: (String) -> Unit) {
  val presets = listOf("🚨 Medical Aid Needed", "⚠️ Rescue Required", "📍 Coordinates", "✅ All OK")
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { presets.forEach { FilterChip(selected = false, onClick = { onSend(it) }, label = { Text(it) }) } }
}

@Composable
private fun QuickMessageInput(draft: String, onDraftChange: (String) -> Unit, onSend: () -> Unit, onScan: () -> Unit, canSend: Boolean) {
  HorizontalDivider(); Spacer(Modifier.height(8.dp))
  ElevatedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Radar, null); Text("  Scan for LoRa Node") }
  Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(draft, onDraftChange, Modifier.weight(1f), label = { Text("Emergency message") }, singleLine = true, enabled = canSend)
    TextButton(onClick = onSend, enabled = canSend && draft.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeScannerBottomSheet(nodes: List<RelayNode>, scanning: Boolean, demoEnabled: Boolean, onDismiss: () -> Unit, onDemoToggle: (Boolean) -> Unit, onNodeClick: (RelayNode) -> Unit) {
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) { RadarPulse(scanning); Spacer(Modifier.width(12.dp)); Column { Text("LoRa Node Radar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(if (scanning) "Scanning nearby BLE relays…" else "Scan complete") } }
      Spacer(Modifier.height(16.dp)); Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Column(Modifier.weight(1f)) { Text("Simulate Demo Node", fontWeight = FontWeight.Bold); Text("Test connect, send, and echo without hardware", style = MaterialTheme.typography.bodySmall) }; Switch(demoEnabled, onDemoToggle) }
      Spacer(Modifier.height(12.dp)); if (nodes.isEmpty()) Text("No LoRa nodes found yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 18.dp))
      nodes.forEach { node -> NodeRow(node, onNodeClick) }
    }
  }
}

@Composable
private fun RadarPulse(scanning: Boolean) {
  val transition: InfiniteTransition = rememberInfiniteTransition(label = "radar")
  val scale by transition.animateFloat(1f, if (scanning) 1.35f else 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing)), label = "radar-scale")
  Box(Modifier.size(46.dp).scale(scale).background(MaterialTheme.colorScheme.primary.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Radar, null, tint = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun NodeRow(node: RelayNode, onNodeClick: (RelayNode) -> Unit) {
  ElevatedButton(onClick = { onNodeClick(node) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Icon(if (node.isSimulated) Icons.Default.BluetoothConnected else Icons.AutoMirrored.Filled.BluetoothSearching, null); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${signalQuality(node.rssi)} • ${node.rssi} dBm", style = MaterialTheme.typography.labelSmall) }; Text(signalBars(node.rssi))
  }
}

private fun signalQuality(rssi: Int) = when { rssi > -60 -> "Excellent"; rssi >= -80 -> "Fair"; else -> "Weak" }
private fun signalBars(rssi: Int) = when { rssi > -60 -> "▂▄▆█"; rssi >= -80 -> "▂▄▆"; else -> "▂▄" }
