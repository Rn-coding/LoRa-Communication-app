package com.example.loramessenger.ui.main

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.loramessenger.domain.LinkPhase
import com.example.loramessenger.domain.research.*

@Composable
fun ResearchScreen(modifier: Modifier, viewModel: RelayViewModel) {
  val context = LocalContext.current
  val link by viewModel.linkState.collectAsState()
  val result by viewModel.researchResult.collectAsState()
  val busy by viewModel.researchBusy.collectAsState()
  val error by viewModel.researchError.collectAsState()
  var messageId by remember { mutableStateOf("1042") }
  var emergencyType by remember { mutableStateOf(EmergencyType.FIRE) }
  var location by remember { mutableStateOf("") }
  var severity by remember { mutableStateOf(SeverityLevel.CRITICAL) }
  var people by remember { mutableStateOf("1") }
  var assistance by remember { mutableStateOf(AssistanceRequired.MEDICAL) }
  var description by remember { mutableStateOf("") }
  var strategy by remember { mutableStateOf(EncodingStrategy.ADAPTIVE) }
  var budget by remember { mutableStateOf(32) }
  var sf by remember { mutableStateOf(SpreadingFactor.SF10) }
  var bandwidth by remember { mutableStateOf(LoRaBandwidth.BW_125) }
  var codingRate by remember { mutableStateOf(CodingRate.CR_4_5) }
  var repetitions by remember { mutableStateOf(1) }
  var configExpanded by remember { mutableStateOf(true) }

  fun fillTest() { messageId = "1042"; emergencyType = EmergencyType.FIRE; location = "PRP Block C, Floor 2"; severity = SeverityLevel.CRITICAL; people = "12"; assistance = AssistanceRequired.MEDICAL; description = "Smoke near corridor" }
  val report = StructuredEmergencyReport(messageId.toIntOrNull() ?: 0, emergencyType, location, severity, people.toIntOrNull() ?: 0, assistance, description.ifBlank { null })
  val config = ExperimentConfig(strategy, budget, sf, bandwidth, codingRate, repetitions)

  LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    item { Spacer(Modifier.height(8.dp)); Text("Research Mode", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Send canonical emergency data to the node for representation selection.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    item {
      ResearchCard("A. Structured Emergency Report") {
        OutlinedTextField(messageId, { messageId = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Message ID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        Spacer(Modifier.height(8.dp)); Text("Emergency type", style = MaterialTheme.typography.labelLarge); ChipRow(EmergencyType.entries, emergencyType, { emergencyType = it }, { it.label })
        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("Location") }, placeholder = { Text("e.g. PRP Block C, Floor 2") }, singleLine = true)
        Spacer(Modifier.height(8.dp)); Text("Severity", style = MaterialTheme.typography.labelLarge); ChipRow(SeverityLevel.entries, severity, { severity = it }, { it.label })
        OutlinedTextField(people, { people = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("People affected") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        Spacer(Modifier.height(8.dp)); Text("Assistance required", style = MaterialTheme.typography.labelLarge); ChipRow(AssistanceRequired.entries, assistance, { assistance = it }, { it.label })
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Additional description") }, minLines = 2)
        TextButton(onClick = ::fillTest) { Text("Fill Test Emergency") }
      }
    }
    item {
      ResearchCard("B. Experiment Configuration") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("RF and selector parameters"); TextButton(onClick = { configExpanded = !configExpanded }) { Text(if (configExpanded) "Collapse" else "Expand") } }
        if (configExpanded) {
          Text("Encoding strategy", style = MaterialTheme.typography.labelLarge); ChipRow(EncodingStrategy.entries, strategy, { strategy = it }, { it.label })
          Text("Payload budget (bytes)", style = MaterialTheme.typography.labelLarge); ChipRow(listOf(16, 24, 32, 48, 64), budget, { budget = it }, { it.toString() })
          Text("Spreading factor", style = MaterialTheme.typography.labelLarge); ChipRow(SpreadingFactor.entries, sf, { sf = it }, { "SF${it.value}" })
          Text("Bandwidth", style = MaterialTheme.typography.labelLarge); ChipRow(LoRaBandwidth.entries, bandwidth, { bandwidth = it }, { "${it.khz} kHz" })
          Text("Coding rate", style = MaterialTheme.typography.labelLarge); ChipRow(CodingRate.entries, codingRate, { codingRate = it }, { it.label })
          Text("Repetitions: $repetitions", style = MaterialTheme.typography.labelLarge); ChipRow((1..5).toList(), repetitions, { repetitions = it }, { it.toString() })
        }
      }
    }
    item {
      ResearchCard("C. Run Experiment") {
        if (link.phase != LinkPhase.READY) Text("Connect to a LoRa node before running an experiment.", color = MaterialTheme.colorScheme.error)
        Button(onClick = { viewModel.runExperiment(report, config) }, enabled = link.phase == LinkPhase.READY && !busy && location.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "ESP32 Evaluating Candidates…" else "Run Experiment") }
        if (busy) Text("Sending canonical payload… Receiving telemetry…", color = MaterialTheme.colorScheme.secondary)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      }
    }
    result?.let { experiment ->
      item { ResearchResultCard(experiment) }
      item { Button(onClick = { viewModel.exportExperiments { csv -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_TEXT, csv) }, "Export experiments CSV")) } }, modifier = Modifier.fillMaxWidth()) { Text("Export Experiments CSV") } }
    }
    item { Spacer(Modifier.height(20.dp)) }
  }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, onSelected: (T) -> Unit, label: (T) -> String) {
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { options.forEach { option -> FilterChip(selected == option, { onSelected(option) }, label = { Text(label(option)) }) } }
}

@Composable
private fun ResearchCard(title: String, content: @Composable () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); HorizontalDivider(); content() } }
}

@Composable
private fun ResearchResultCard(result: ExperimentResult) {
  ResearchCard("D. Experiment Results") {
    Text("Selected: ${result.selectedRepresentation}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text("Original ${result.originalMessageSize} B • Encoded ${result.encodedPayloadSize} B • Budget ${result.payloadBudget} B")
    Text("Airtime ${result.calculatedAirtimeMs} ms • Encoding ${result.encodingTimeUs} μs • Selection ${result.selectionTimeUs} μs")
    Text("Integrity: ${if (result.mandatoryFieldsPreserved) "PASS" else "FAIL"} • Budget: ${if (result.payloadFitsBudget) "PASS" else "FAIL"} • TX: ${result.loraTxStatus}")
    Text("RF configuration: ${if (result.configurationConfirmed) "CONFIRMED" else "NOT CONFIRMED"}", color = if (result.configurationConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    HorizontalDivider(); Text("Candidate comparison", fontWeight = FontWeight.Bold)
    result.candidateBreakdown.forEach { candidate -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(if (candidate.candidateName == result.selectedRepresentation) "✓ ${candidate.candidateName}" else candidate.candidateName); Text("${candidate.encodedBytes} B • ${candidate.calculatedAirtimeMs} ms • ${if (candidate.fitsBudget) "fits" else "over budget"}") } }
  }
}
