# Implementation Progress Checklist

**Master Plan:** [PLAN.md](PLAN.md)  
**Architecture Specification:** [ARCHITECTURE.md](ARCHITECTURE.md)  
**Operating Guidelines:** [GUIDELINES.md](GUIDELINES.md)

---

## Phase 1: Disaster UI Shell & Dynamic Permissions Gate
- [x] Create Android Compose project structure & Git repository
- [x] Configure build environment, JDK 17, and Android CLI
- [x] Configure Android 12-15 Bluetooth & Notification permissions in Manifest
- [x] Build Emergency Node HUD (Connection status badge, LoRa channel indicator)
- [x] Build Permissions Request Rationale card in Compose
- [x] Build Chat Timeline & Message Input Bar shell
- [x] **Human Verification Gate:** Deploy to phone & verify UI shell + runtime permissions prompt

---

## Phase 2: BLE Discovery Radar & Node Detection
- [x] Implement `BleScanner` with strict filters (Service UUID `...90AB` and `"LoRa Node"` prefix)
- [x] Implement signal strength (RSSI) smoothing & categorization
- [x] Build `NodeScannerBottomSheet` with scanning pulse animation & node list
- [x] Add Mock/Simulated Node toggle for offline development without physical hardware
- [x] **Human Verification Gate:** Deploy to phone & view live scanning radar discovering nodes

---

## Phase 3: Sequential GATT Engine & Connection HUD
- [x] Implement atomic `GattCommandQueue` (FIFO with 5s watchdog timeout to prevent GATT 133)
- [x] Implement `BleGattManager` (Connect, MTU negotiation to 247 bytes, Service discovery)
- [x] Subscribe to TX Characteristic notifications (`...90AD`) with CCCD descriptor write
- [x] Integrate live connection status transitions into top Node HUD
- [x] Implement clean disconnect and link recovery state machine
- [x] **Human Verification Gate:** Deploy to phone & 1-tap connect to ESP32 / view MTU & connection status

---

## Phase 4: Bi-Directional Messaging & Disaster Broadcast Dashboard
- [x] Implement outbound message transmitter (Write to RX Characteristic `...90AC` in UTF-8)
- [x] Implement inbound notification listener (Handle TX Characteristic notifications)
- [x] Build Chat Bubble UI with 3-stage delivery status indicators
- [x] Add 1-tap Emergency SOS broadcast presets (`[🚨 Medical Aid]`, `[⚠️ Rescue Needed]`, `[✅ Safe]`)
- [x] Implement haptic vibration alerts for incoming LoRa messages
- [x] **Human Verification Gate:** Deploy to phone & send/receive live text messages with ESP32

---

## Phase 5: Resilience, Offline Persistence & Foreground Keepalive
- [x] Implement Room SQLite database for persistent offline message storage
- [x] Implement `BleRelayForegroundService` to preserve BLE link when screen is locked
- [x] Implement exponential backoff auto-reconnection watchdog
- [ ] **Human Verification Gate:** Lock phone, test background message reception, test auto-reconnect
