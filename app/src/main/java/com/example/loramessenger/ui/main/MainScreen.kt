package com.example.loramessenger.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import com.example.loramessenger.theme.LoRaMessengerTheme

@Composable
fun MainScreen(onItemClick: (NavKey) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val permissions = remember { requiredPermissions() }
  var permissionsGranted by remember {
    mutableStateOf(permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
  }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
    permissionsGranted = permissions.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
  }
  if (permissionsGranted) EmergencyMessengerShell(modifier) else PermissionFallbackCard(modifier) { launcher.launch(permissions.toTypedArray()) }
}

private fun requiredPermissions(): List<String> = buildList {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    add(Manifest.permission.BLUETOOTH_SCAN)
    add(Manifest.permission.BLUETOOTH_CONNECT)
  } else add(Manifest.permission.ACCESS_FINE_LOCATION)
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
private fun PermissionFallbackCard(modifier: Modifier = Modifier, onGrant: () -> Unit) {
  Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
      Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(16.dp))
        Text("Emergency Bluetooth access", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Nearby Devices permission lets this phone find and connect to a LoRa relay node. Notifications keep important link updates visible.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text("Grant Emergency Bluetooth Permissions") }
      }
    }
  }
}

@Composable
private fun EmergencyMessengerShell(modifier: Modifier = Modifier) {
  var draft by remember { mutableStateOf("") }
  Column(modifier.fillMaxSize()) {
    EmergencyNodeHud()
    Spacer(Modifier.height(16.dp))
    EmptyChatTimeline(Modifier.weight(1f))
    QuickMessageInput(draft, { draft = it })
  }
}

@Composable
private fun EmergencyNodeHud() {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
    Column(Modifier.padding(18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Emergency, null, tint = MaterialTheme.colorScheme.error)
        Text("  EMERGENCY NODE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f)); Text("433 MHz", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
      }
      Spacer(Modifier.height(14.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("NO NODE CONNECTED", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0x33FF5252), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp))
        Spacer(Modifier.weight(1f)); Text("Battery —", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun EmptyChatTimeline(modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null, tint = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(12.dp)); Text("No messages yet", style = MaterialTheme.typography.titleMedium)
    Text("Connect to a LoRa node to begin the emergency relay.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
  }
}

@Composable
private fun QuickMessageInput(draft: String, onDraftChange: (String) -> Unit) {
  HorizontalDivider(); Spacer(Modifier.height(10.dp))
  ElevatedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null); Text("  Scan for LoRa Node") }
  Spacer(Modifier.height(10.dp))
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(value = draft, onValueChange = onDraftChange, modifier = Modifier.weight(1f), label = { Text("Emergency message") }, singleLine = true)
    TextButton(onClick = {}, enabled = draft.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF09120F)
@Composable
private fun EmergencyMessengerShellPreview() { LoRaMessengerTheme { EmergencyMessengerShell(Modifier.padding(16.dp)) } }
