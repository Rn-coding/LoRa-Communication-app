# Master Implementation Plan: LoRa BLE Disaster Messenger MVP

**Document Version:** 1.0.0  
**Role of Author:** Senior Research Engineer & Systems Architect  
**Audience:** Autonomous Implementation Agent & Human Reviewer  
**Core Constraint:** Every single phase MUST produce an incrementally functional, deployable, and human-viewable Android application that can be launched and verified on a physical phone.

---

## High-Level Phase Overview & Human Verification Gateways

```
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 1: Disaster UI Shell & Dynamic Permissions Gate                  │
│ [Deliverable: App launches, requests BLE permissions, displays UI HUD] │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ Human verification: UI & Perms verified
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 2: BLE Discovery Radar & Node Detection                          │
│ [Deliverable: Live BLE scanning, filters ESP32 nodes, signal meters]   │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ Human verification: Nodes discovered on screen
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 3: GATT Sequential Engine & Connection HUD                       │
│ [Deliverable: 1-Tap pairing, MTU negotiation, TX notify subscription]  │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ Human verification: Live connection state & MTU
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 4: Bi-Directional Messaging & Disaster Broadcast Dashboard      │
│ [Deliverable: Send text to ESP32, receive LoRa notifications, SOS]     │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │ Human verification: Real-time chat & hardware test
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 5: Resilience, Offline Persistence & Foreground Keepalive        │
│ [Deliverable: Room DB, auto-reconnect FSM, background notification]    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Disaster UI Shell & Dynamic Permissions Gate

### 1.1 Objective
Establish the foundational Android application scaffold, edge-to-edge Material 3 theme tailored for emergency contrast, and a foolproof runtime permission gate for Android 12+ (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`).

### 1.2 Agent Tasks
1. **Dependency Configuration:**
   - Update `gradle/libs.versions.toml` and `app/build.gradle.kts` to include:
     - `androidx.lifecycle:lifecycle-viewmodel-compose`
     - `androidx.compose.material:material-icons-extended`
     - `com.google.accompanist:accompanist-permissions` (or custom robust Compose permission launcher)
2. **Android Manifest Configuration:**
   - Declare Bluetooth permissions with `android:maxSdkVersion="30"` for `ACCESS_FINE_LOCATION` and `android:usesPermissionFlags="neverForLocation"` for `BLUETOOTH_SCAN`.
   - Declare `BLUETOOTH_CONNECT`, `BLUETOOTH_ADMIN`, and `POST_NOTIFICATIONS`.
3. **UI Implementation:**
   - Construct the top **Emergency Node HUD** (displays Connection Status pill: `DISCONNECTED`, `SCANNING`, `CONNECTED`, Battery indicator placeholder, and LoRa frequency band).
   - Construct the **Permission Fallback Card**: If permissions are missing, show a clean, human-readable rationale screen with a single `"Grant Emergency Bluetooth Permissions"` button.
   - Construct the empty **Chat Timeline** and **Quick Message Input Bar**.

### 1.3 Human Verification Gate (What the user sees on their phone)
- **Deployment Command:** `& 'C:\Users\rehan\AppData\AndroidCLI\android.exe' run`
- **Visual Checks:**
  1. The app opens cleanly with high-contrast emergency theming (dark/light disaster styling).
  2. Dialog prompts user for Bluetooth and Nearby Devices permissions.
  3. Once granted, the top Node HUD appears with status badge `"No Node Connected"`, alongside the message input field and a prominent `"Scan for LoRa Node"` action button.

---

## Phase 2: BLE Discovery Radar & Node Detection

### 2.1 Objective
Implement the low-level Bluetooth scanning engine with strict filtering for ESP32 LoRa transceivers, signal strength (RSSI) smoothing, and a human-friendly visual Node Radar dialog.

### 2.2 Agent Tasks
1. **Hardware Abstraction - Scanner Module (`data/ble/BleScanner.kt`):**
   - Wrap `BluetoothLeScanner` with a Kotlin Coroutines `Flow<ScanResult>`.
   - Implement `ScanFilter` targeting:
     - Primary Service UUID: `12345678-1234-1234-1234-1234567890AB`
     - Device Name prefix filter: `"LoRa Node"`, `"ESP32"`, or generic fallback.
   - Apply `ScanSettings.SCAN_MODE_LOW_LATENCY` during active user scanning with an automatic 12-second timeout to conserve power.
2. **Domain State Management (`ui/scanner/NodeScannerViewModel.kt`):**
   - Maintain a deduplicated list of discovered nodes with MAC address, Advertised Name, and live RSSI indicator.
3. **Scanner UI (`ui/components/NodeScannerBottomSheet.kt`):**
   - Modal bottom sheet displaying a live radar animation and the list of detected nodes.
   - Display signal quality meters (Excellent > -60dBm, Fair -60 to -80dBm, Weak < -80dBm).
   - Provide a `"Simulate Demo Node"` toggle in debug builds for instant testing even before physical hardware is powered on.

### 2.3 Human Verification Gate (What the user sees on their phone)
- **Deployment Command:** `& 'C:\Users\rehan\AppData\AndroidCLI\android.exe' run`
- **Visual Checks:**
  1. Tap `"Scan for LoRa Node"`.
  2. The Node Radar sheet appears with scanning pulse animation.
  3. Power on the ESP32 node (or enable demo mode): `"LoRa Node 1"` immediately populates the list with live RSSI signal bars.
  4. User can tap the node to trigger connection.

---

## Phase 3: Sequential GATT Engine & Connection HUD

### 3.1 Objective
Implement the atomic, sequential `GattCommandQueue` that eliminates `GATT 133` errors, negotiates high MTU, discovers the custom LoRa service, and subscribes to notifications on the TX characteristic.

### 3.2 Agent Tasks
1. **Sequential GATT Command Queue (`data/ble/GattCommandQueue.kt`):**
   - Implement a FIFO execution pipeline using Kotlin Coroutines `Channel` and `Mutex`.
   - Wrap operations (`connect`, `discoverServices`, `requestMtu`, `writeDescriptor`) with a 5000ms watchdog timer.
2. **GATT Manager (`data/ble/BleGattManager.kt`):**
   - Implement `BluetoothGattCallback` with state machine propagation:
     - `STATE_CONNECTING` -> `STATE_CONNECTED` -> `NEGOTIATING_MTU` (requests 247 bytes) -> `DISCOVERING_SERVICES` -> `ENABLING_NOTIFICATIONS` (writes `0x0001` to CCCD `0x2902`) -> `STATE_READY`.
   - Handle unexpected disconnection with graceful cleanup and auto-reconnect attempt.
3. **UI Integration (`ui/main/MainScreen.kt`):**
   - Update the top Node HUD with live status transitions:
     - Shows yellow pill: `"Negotiating MTU (247 bytes)..."`
     - Transitions to vibrant green badge: `"Connected: LoRa Node 1 (MTU: 247)"`
   - Show a `"Disconnect"` button when connected.

### 3.3 Human Verification Gate (What the user sees on their phone)
- **Deployment Command:** `& 'C:\Users\rehan\AppData\AndroidCLI\android.exe' run`
- **Visual Checks:**
  1. Select `"LoRa Node 1"` in scanner.
  2. Watch the top status bar smoothly transition: `Connecting` -> `Connected` -> `Services Discovered`.
  3. HUD confirms active MTU (e.g. 247) and active link to ESP32.
  4. Tapping `"Disconnect"` cleanly tears down GATT and returns to idle state.

---

## Phase 4: Bi-Directional Messaging & Disaster Broadcast Dashboard

### 4.1 Objective
Deliver the complete end-to-end messaging pipeline: write outbound text to the ESP32 RX characteristic, listen for inbound LoRa packets on the TX characteristic notification, and provide quick emergency broadcast presets.

### 4.2 Agent Tasks
1. **Transceiver Channel (`data/ble/BleTransceiver.kt`):**
   - Outgoing: Encode string into UTF-8 (Raw Mode) or Structured Frame and queue `WriteCharacteristicCommand` to RX UUID (`...90AC`).
   - Incoming: Parse `onCharacteristicChanged` notifications from TX UUID (`...90AD`) and dispatch to the chat stream.
2. **Disaster Chat UI (`ui/chat/ChatTimeline.kt`):**
   - Bubble UI distinguishing:
     - Outgoing user messages (aligned right, status: `Queued`, `Sent to Node`, `Air Transmitted`).
     - Incoming LoRa messages (aligned left, tagged with sender Node ID and timestamp).
   - Disaster Emergency Presets Bar above input field:
     - `[🚨 Medical Aid Needed]`
     - `[⚠️ Search & Rescue Required]`
     - `[📍 Sending GPS Coordinates]`
     - `[✅ I am Safe]`
3. **Haptic & Audio Feedback:**
   - Emergency vibration chirp when an incoming LoRa packet arrives.

### 4.3 Human Verification Gate (What the user sees on their phone)
- **Deployment Command:** `& 'C:\Users\rehan\AppData\AndroidCLI\android.exe' run`
- **Visual Checks:**
  1. User types `"Emergency at Sector 4"` and taps **Send**.
  2. Message immediately appears in chat bubble with checkmark confirming write to ESP32.
  3. When ESP32 fires a notification (or testing with mock/second phone), the bubble pops up instantly in the chat with haptic feedback.
  4. Tapping `[🚨 Medical Aid Needed]` sends formatted distress broadcast in 1 tap.

---

## Phase 5: Resilience, Offline Persistence & Foreground Keepalive

### 5.1 Objective
Ensure military-grade reliability during severe disasters: store all message history locally (Room SQLite database), keep the BLE bridge alive when the phone is locked, and auto-reconnect if the user walks away from the node.

### 5.2 Agent Tasks
1. **Offline Storage (`data/db/`):**
   - Add Room Database dependencies.
   - Store `MessageEntity` with `id`, `text`, `sender`, `timestamp`, `deliveryStatus`, `rssi`.
   - Support conversation search and export message log.
2. **Foreground Service (`service/BleRelayService.kt`):**
   - Bind BLE lifecycle to an ongoing Android Notification (`"LoRa Transceiver Active - Connected to Node 1"`).
   - Use `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`.
3. **Auto-Reconnect Watchdog:**
   - If BLE connection drops, enter exponential backoff retry (1s, 2s, 5s, 10s) and notify user if link remains broken.

### 5.3 Human Verification Gate (What the user sees on their phone)
- **Deployment Command:** `& 'C:\Users\rehan\AppData\AndroidCLI\android.exe' run`
- **Visual Checks:**
  1. Lock phone screen -> send message from ESP32 -> phone vibrates and shows incoming message notification on lock screen.
  2. Kill and reopen app -> entire chat history is preserved from Room DB.
  3. Power off ESP32 and power back on -> app automatically re-establishes connection within seconds without human intervention.
