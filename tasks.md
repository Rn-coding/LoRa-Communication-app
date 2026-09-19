# Implementation Progress Checklist

**Master Plan:** [PLAN.md](PLAN.md)  
**Architecture Specification:** [ARCHITECTURE.md](ARCHITECTURE.md)  
**Operating Guidelines:** [GUIDELINES.md](GUIDELINES.md)

---

## Phase 1: Disaster UI Shell & Dynamic Permissions Gate
- [x] Create Android Compose project structure & Git repository
- [x] Configure build environment, JDK 17, and Android CLI
- [ ] Configure Android 12-15 Bluetooth & Notification permissions in Manifest
- [ ] Build Emergency Node HUD (Connection status badge, LoRa channel indicator)
- [ ] Build Permissions Request Rationale card in Compose
- [ ] Build Chat Timeline & Message Input Bar shell
- [ ] **Human Verification Gate:** Deploy to phone & verify UI shell + runtime permissions prompt

---

## Phase 2: BLE Discovery Radar & Node Detection
- [ ] Implement `BleScanner` with strict filters (Service UUID `...90AB` and `"LoRa Node"` prefix)
- [ ] Implement signal strength (RSSI) smoothing & categorization
- [ ] Build `NodeScannerBottomSheet` with scanning pulse animation & node list
- [ ] Add Mock/Simulated Node toggle for offline development without physical hardware
- [ ] **Human Verification Gate:** Deploy to phone & view live scanning radar discovering nodes

---

## Phase 3: Sequential GATT Engine & Connection HUD
- [ ] Implement atomic `GattCommandQueue` (FIFO with 5s watchdog timeout to prevent GATT 133)
- [ ] Implement `BleGattManager` (Connect, MTU negotiation to 247 bytes, Service discovery)
- [ ] Subscribe to TX Characteristic notifications (`...90AD`) with CCCD descriptor write
- [ ] Integrate live connection status transitions into top Node HUD
- [ ] Implement clean disconnect and link recovery state machine
- [ ] **Human Verification Gate:** Deploy to phone & 1-tap connect to ESP32 / view MTU & connection status

---

## Phase 4: Bi-Directional Messaging & Disaster Broadcast Dashboard
- [ ] Implement outbound message transmitter (Write to RX Characteristic `...90AC` in UTF-8)
- [ ] Implement inbound notification listener (Handle TX Characteristic notifications)
- [ ] Build Chat Bubble UI with 3-stage delivery status indicators
- [ ] Add 1-tap Emergency SOS broadcast presets (`[🚨 Medical Aid]`, `[⚠️ Rescue Needed]`, `[✅ Safe]`)
- [ ] Implement haptic vibration alerts for incoming LoRa messages
- [ ] **Human Verification Gate:** Deploy to phone & send/receive live text messages with ESP32

---

## Phase 5: Resilience, Offline Persistence & Foreground Keepalive
- [ ] Implement Room SQLite database for persistent offline message storage
- [ ] Implement `BleRelayForegroundService` to preserve BLE link when screen is locked
- [ ] Implement exponential backoff auto-reconnection watchdog
- [ ] **Human Verification Gate:** Lock phone, test background message reception, test auto-reconnect
