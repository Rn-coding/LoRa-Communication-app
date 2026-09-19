# Architecture Specification: Dual-Tier Smartphone-to-LoRa Transceiver Relay Framework

**Document Version:** 1.0.0-PROPOSED  
**Author:** Senior Research Engineer  
**Scope:** Android Mobile Application & Hardware Abstraction Boundary  
**Target Environment:** Android 8.0+ (API 26) through Android 15 (API 35+)

---

## 1. Executive Summary & Problem Formulation

### 1.1 Context & Disaster Paradigm
In post-disaster scenarios (earthquakes, cyclones, grid blackouts, infrastructure floods), terrestrial cellular base stations (eNodeB/gNodeB) and core ISP networks frequently suffer catastrophic failure. While modern smartphones possess substantial computing power and localized transceivers (Bluetooth, Wi-Fi), mobile operating systems (Android/iOS) strictly throttle long-range ad-hoc mesh networking due to aggressive background execution limits, Wi-Fi Direct power drain, and RF hardware constraints.

This project implements a **Dual-Tier Heterogeneous Relay Framework**:
*   **Tier-1 (Local Access Link):** Short-range, low-power pairing between commercial smartphones and an external relay node using **Bluetooth Low Energy (BLE)** GATT.
*   **Tier-2 (Long-Range Transport Link):** Long-distance, sub-GHz point-to-point/relay communication using an **ESP32 + Semtech SX1278 LoRa transceiver** operating in the unlicensed ISM bands (433 / 868 / 915 MHz), propagating vital text payloads over multi-kilometer spans.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 TIER-1: LOCAL ACCESS                                   │
│  ┌────────────────────────┐                   ┌────────────────────────┐               │
│  │   Android Smartphone   │ <=== (BLE 2.4G) =>│   ESP32 LoRa Node A    │               │
│  │     (GATT Client)      │                   │     (GATT Server)      │               │
│  └────────────────────────┘                   └───────────┬────────────┘               │
└───────────────────────────────────────────────────────────┼────────────────────────────┘
                                                            │ SPI Bus
┌───────────────────────────────────────────────────────────┼────────────────────────────┐
│                                 TIER-2: LORA RELAY        ▼                            │
│                                               ┌────────────────────────┐               │
│                                               │   SX1278 Transceiver   │               │
│                                               └───────────┬────────────┘               │
│                                                           │                            │
│                                            Sub-GHz RF (Several Kilometers)             │
│                                                           │                            │
│                                               ┌───────────▼────────────┐               │
│                                               │   SX1278 Transceiver   │               │
│                                               └───────────┬────────────┘               │
│                                                           │ SPI Bus                    │
│  ┌────────────────────────┐                   ┌───────────┴────────────┐               │
│  │   Android Smartphone   │ <=== (BLE 2.4G) =>│   ESP32 LoRa Node B    │               │
│  │     (GATT Client)      │                   │     (GATT Server)      │               │
│  └────────────────────────┘                   └────────────────────────┘               │
│                                 TIER-1: LOCAL ACCESS                                   │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Research-Level Challenges & Mitigation Strategies

### 2.1 Bandwidth & Latency Impedance Mismatch (Buffer Bloat Mitigation)
*   **The Problem:** BLE operates with high instantaneous throughput (~100–500 kbps practical), while LoRa operates at ultra-low bitrates (0.3 kbps – 5.5 kbps depending on Spreading Factor SF7–SF12 and Bandwidth 125/250/500 kHz). A user typing rapidly could enqueue 500 bytes in 100 ms, whereas transmitting that over LoRa at SF10/125kHz requires a Time-on-Air (ToA) of ~1.2 seconds per packet. Unregulated BLE transmission will inevitably overwhelm the ESP32's internal FIFO/ring buffer, causing buffer bloat and dropped emergency packets.
*   **Engineering Mitigation:**
    1.  **Application-Level Credit/ACK Flow Control:** The Android app will not fire sequential LoRa frames blindly. It relies on explicit TX acknowledgment states from the ESP32 (`TX_ACK`, `LORA_TX_IN_PROGRESS`, `LORA_TX_DONE`).
    2.  **Rate-Limiting Token Bucket:** The Android application implements a local transmission queue with backpressure. Messages are held in a pending local SQLite/Room queue until the ESP32 signals readiness to transmit the next frame.

### 2.2 BLE MTU Truncation vs. LoRa Maximum Payload Size
*   **The Problem:** Default BLE ATT MTU is 23 bytes (effective payload: 20 bytes). The SX1278 hardware packet buffer is 256 bytes (maximum FIFO capacity). If the app sends a 100-character disaster message over default BLE, Android will fragment it or drop bytes without negotiated MTU.
*   **Engineering Mitigation:**
    1.  **Dynamic MTU Negotiation:** Immediately upon GATT connection, the app will execute `requestMtu(247)` or `512` to allow single-write transfer of complete LoRa frames.
    2.  **Lightweight Application SAR (Segmentation and Reassembly):** For messages exceeding the maximum LoRa single-frame payload (recommended threshold: 200 bytes to preserve high Spreading Factor margins), the Android domain layer performs fragment sequencing with reassembly headers.

### 2.3 Android BLE Subsystem Fragility (GATT 133 / Sequential Queue Mandate)
*   **The Problem:** The native Android BLE stack (`BluetoothGatt`) is strictly asynchronous but non-concurrent. Issuing any GATT operation (read, write, request MTU, read RSSI, descriptor write) while another operation is pending results in silent drops, undefined state, or the infamous `GATT 133 (GATT_ERROR)`.
*   **Engineering Mitigation:**
    *   **Atomic Command Dispatcher:** All BLE operations are serialized through a strict First-In-First-Out (FIFO) sequential queue backed by Kotlin Coroutines (`Mutex` / `Channel`). No GATT request is initiated until the prior operation's callback (`onCharacteristicWrite`, `onDescriptorWrite`, etc.) has returned or timed out (watchdog timer).

### 2.4 Asymmetric Channel Reliability & Multi-Level Delivery Confirmation
*   **The Problem:** In disaster communication, the user must know whether a message was:
    1.  Sent to the local ESP32 node.
    2.  Radiated into the air via LoRa.
    3.  Received by the peer LoRa node.
*   **Engineering Mitigation (Three-Stage Status Tracking):**
    *   **Stage 1: LOCAL_QUEUED / BLE_DELIVERED** (Confirmed by Android GATT Write callback).
    *   **Stage 2: LORA_BROADCAST_DONE** (Confirmed by ESP32 via BLE notification after SX1278 `TX_DONE` interrupt).
    *   **Stage 3: REMOTE_ACKNOWLEDGED** (Optional, confirmed by peer node returning a lightweight LoRa ACK packet, pushed to Android via BLE notification).

### 2.5 Power Management & Background Survival
*   **The Problem:** Android OS aggressively terminates background services to conserve battery. In a disaster, the smartphone must receive incoming messages without the screen continuously active.
*   **Engineering Mitigation:**
    *   **Foreground Service (`FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`):** A dedicated background bridge service bound to an ongoing low-priority notification, maintaining the BLE GATT connection and handling notifications while the phone is locked.
    *   **Adaptive Connection Intervals:** Negotiate low-latency parameters (`CONNECTION_PRIORITY_HIGH`) during active transmission, dropping to balanced/low-power (`CONNECTION_PRIORITY_BALANCED`) in idle monitoring.

---

## 3. Hardware-Software Interface Specification (The BLE Bridge Contract)

To ensure decoupled collaboration with the hardware team, the interface conforms to a strict, standardized contract.

### 3.1 GATT Profile Definition

| Component | UUID | Type | Properties | Description |
|---|---|---|---|---|
| **Service** | `12345678-1234-1234-1234-1234567890AB` | Primary | - | Main Emergency LoRa Bridge Service |
| **RX Characteristic** | `12345678-1234-1234-1234-1234567890AC` | Data Sink | `WRITE`, `WRITE_NO_RESPONSE` | Phone writes outgoing messages to ESP32 |
| **TX Characteristic** | `12345678-1234-1234-1234-1234567890AD` | Data Source | `NOTIFY` | ESP32 pushes incoming LoRa data and telemetry to Phone |
| **CCCD Descriptor** | `00002902-0000-1000-8000-00805f9b34fb` | Config | `READ`, `WRITE` | Client Characteristic Configuration (Enable Notifications) |

*(Note: UUIDs are completely parameterized in configuration to support production reassignment).*

### 3.2 Wire Frame Protocol Options (App <-> ESP32)

To accommodate firmware complexity on the hardware team's side, the Android app supports two switchable framing modes:

#### Mode A: Raw Stream Mode (Minimalist MVP)
*   **Android Write:** UTF-8 string bytes written directly to RX characteristic (`"Emergency at Building B"`).
*   **ESP32 Notification:** UTF-8 string bytes pushed directly to TX characteristic (`"Hello from Node 2"`).
*   *Advantage:* Instant zero-overhead compatibility for quick hardware testing.

#### Mode B: Structured Telemetry Frame Mode (Production Robust)
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  MAGIC (0xEA) |   MSG_TYPE    |           PACKET_ID           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       SOURCE_NODE_ID          |        DEST_NODE_ID           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|      TTL      |  PAYLOAD_LEN  |        PAYLOAD ...            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+                               +
|                           ... (UTF-8 Text)                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           CRC16-CCITT         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
*   `MAGIC`: `0xEA` synchronization byte.
*   `MSG_TYPE`: `0x01` (Emergency Text), `0x02` (Ping/Beacon), `0x03` (ACK), `0x10` (Telemetry).
*   `PACKET_ID`: 16-bit monotonic counter for deduplication.
*   `SOURCE_NODE_ID` / `DEST_NODE_ID`: Addressing fields (`0xFFFF` = Broadcast).
*   `PAYLOAD_LEN` + `PAYLOAD`: Actual text payload.
*   `CRC16`: Integrity protection.

Incoming frames from the ESP32 in Mode B also prepend link telemetry:
`[MAGIC 0xEB][FRAME_TYPE][PACKET_ID][RSSI (int8)][SNR (int8)][LORA_STATUS][PAYLOAD...]`

---

## 4. Software Architecture & Layered Decomposition

The Android application follows **Clean Architecture** with **Unidirectional Data Flow (UDF / MVI)** using Jetpack Compose and Kotlin Coroutines/Flows.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER                              │
│  ┌───────────────────────┐             ┌────────────────────────────┐  │
│  │     MainScreen UI     │             │     NodeScannerDialog      │  │
│  │   (Jetpack Compose)   │             │     (Jetpack Compose)      │  │
│  └───────────▲───────────┘             └─────────────▲──────────────┘  │
│              │ State / Events                        │                 │
│  ┌───────────┴───────────────────────────────────────┴──────────────┐  │
│  │                    ChatViewModel / MainViewModel                 │  │
│  │            (Exposes UIState Flow, Handles User Intents)          │  │
│  └───────────────────────────────────▲──────────────────────────────┘  │
└──────────────────────────────────────┼─────────────────────────────────┘
                                       │ Domain UseCases
┌──────────────────────────────────────┼─────────────────────────────────┐
│                           DOMAIN LAYER                                 │
│  ┌─────────────────────────┐ ┌───────┴─────────────────┐ ┌───────────┐ │
│  │   SendMessageUseCase    │ │ ObserveMessagesUseCase  │ │ NodeState │ │
│  └───────────┬─────────────┘ └───────┬─────────────────┘ └─────┬─────┘ │
│              │                       │                         │       │
│  ┌───────────┴───────────────────────┴─────────────────────────┴────┐  │
│  │                      RelayEngine / Protocol Router               │  │
│  │          (Packet Framing, SAR, Deduplication, ACK Tracking)      │  │
│  └───────────────────────────────────▲──────────────────────────────┘  │
└──────────────────────────────────────┼─────────────────────────────────┘
                                       │ Repositories
┌──────────────────────────────────────┼─────────────────────────────────┐
│                            DATA LAYER                                  │
│  ┌───────────────────────────────────┴──────────────────────────────┐  │
│  │                    BleRelayRepositoryImpl                        │  │
│  └─────────────┬───────────────────────────────────────┬────────────┘  │
│                │                                       │               │
│  ┌─────────────▼────────────┐            ┌─────────────▼────────────┐  │
│  │   Local Message Cache    │            │     BleGattManager       │  │
│  │     (Room / SQLite)      │            │  (Sequential Queue HAL)  │  │
│  └──────────────────────────┘            └─────────────┬────────────┘  │
└────────────────────────────────────────────────────────┼───────────────┘
                                                         │ Android BLE API
                                           ┌─────────────▼────────────┐
                                           │      BluetoothAdapter    │
                                           │       BluetoothGatt      │
                                           └──────────────────────────┘
```

### 4.1 Layer Responsibilities

1.  **Presentation Layer (`ui.*`)**:
    *   Composable screens for Chat timeline, Node radar / BLE Scanner, and Connection HUD.
    *   Displays node connection state, RSSI signal indicators, battery status, and delivery acknowledgments.
2.  **Domain Layer (`domain.*`)**:
    *   Contains business logic agnostic of Android framework: packet encoding/decoding, sequence ID allocation, ACK timeout watchdogs, and relay state machine.
3.  **Data Layer (`data.*`)**:
    *   `BleGattManager`: Hardware Abstraction Layer (HAL) encapsulating Bluetooth scanning, GATT lifecycle, MTU negotiation, sequential command queuing, and notification listening.
    *   `MessageRepository`: Coordinates message persistence with network state.
4.  **Service Layer (`service.*`)**:
    *   `BleRelayService`: Android Foreground Service maintaining persistent connection lifecycle, handling automatic reconnection, and presenting persistent status notifications.

---

## 5. Sequential GATT Command Pipeline (The Core Concurrency Engine)

To eliminate the `GATT 133` error and callback races, all BLE interactions are modeled as **Atomic Transactions** processed sequentially:

```
           Enqueue Command
[Caller] ───────────────────► ┌──────────────────────────────────────┐
                              │      Concurrent Command Channel      │
                              └──────────────────┬───────────────────┘
                                                 │ Dequeue Next
                                                 ▼
                              ┌──────────────────────────────────────┐
                              │       GATT Operation Dispatcher      │
                              │        (Mutex / Active Lock)         │
                              └──────────────────┬───────────────────┘
                                                 │ Executes Single Op
                                                 ▼
                              ┌──────────────────────────────────────┐
                              │           Android GATT API           │
                              │      (e.g., writeCharacteristic)     │
                              └──────────────────┬───────────────────┘
                                                 │
                                                 │ Async Callback
                                                 ▼
                              ┌──────────────────────────────────────┐
                              │       onCharacteristicWrite()        │
                              └──────────────────┬───────────────────┘
                                                 │ Release Mutex & Complete
                                                 ▼
                              ┌──────────────────────────────────────┐
                              │   Process Next Command in Channel    │
                              └──────────────────────────────────────┘
```

### 5.1 Command Definition Architecture
Commands are modeled as sealed classes:
*   `ConnectCommand(address: String)`
*   `DiscoverServicesCommand`
*   `RequestMtuCommand(mtu: Int)`
*   `EnableNotificationCommand(characteristicUuid: UUID)`
*   `WriteCharacteristicCommand(characteristicUuid: UUID, payload: ByteArray)`
*   `DisconnectCommand`

Each command includes an execution timeout (e.g. 5000 ms). If Android OS fails to return the expected callback within the timeout window, the transaction aborts safely, resets the connection state machine, and does not deadlock the queue.

---

## 6. Connection Resiliency & Reconnection State Machine

Disaster environments are physically dynamic: the user may walk out of range of their ESP32 relay module or swap batteries. The system implements a 5-state finite state machine (FSM):

```
    ┌───────────────┐
    │  DISCONNECTED │◄─────────────────────────────────────────┐
    └───────┬───────┘                                          │
            │ User Initiates / Auto-Connect Triggered          │
            ▼                                                  │
    ┌───────────────┐                                          │
    │   SCANNING    │──────────── Scan Timeout (No Node) ──────┤
    └───────┬───────┘                                          │
            │ Found Target ESP32                               │
            ▼                                                  │
    ┌───────────────┐                                          │
    │  CONNECTING   │──────────── Connection Failed / 133 ─────┤
    └───────┬───────┘                                          │
            │ onConnectionStateChange(STATE_CONNECTED)         │
            ▼                                                  │
    ┌───────────────┐                                          │
    │ CONFIGURING   │ (MTU Request -> Discover -> Enable Notify│
    └───────┬───────┘                                          │
            │ Pipeline Config Completed Successfully           │
            ▼                                                  │
    ┌───────────────┐                                          │
    │   CONNECTED   │                                          │
    │ (READY / IDLE)│──────────── Link Severed / Dropped ──────┘
    └───────────────┘             (Exponential Backoff Reconnect)
```

---

## 7. Step-Wise Modular Implementation Plan

```
[Phase 1: Foundations] ──► [Phase 2: GATT Core] ──► [Phase 3: Protocol & Data] ──► [Phase 4: UI & E2E Testing]
```

### Phase 1: Foundations & Architecture Baseline (Current Milestone)
*   Finalize `ARCHITECTURE.md` and Hardware Interface Contract.
*   Configure permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`).
*   Establish dependency setup in `libs.versions.toml` (Room, ViewModel Compose, Coroutines).

### Phase 2: BLE HAL & Sequential GATT Engine
*   Implement `BleScanner` with strict filters (matching Service UUID or Device Name prefix `LoRa Node`).
*   Implement the `GattCommandQueue` to serialize all GATT operations.
*   Implement `BleGattManager` handling:
    *   Connection lifecycle.
    *   MTU negotiation (247+ bytes).
    *   Service & Characteristic discovery.
    *   CCCD notification subscription on TX Characteristic.
    *   Exponential backoff reconnection watchdog.

### Phase 3: Binary Framing, SAR, and Domain Layer
*   Implement Packet Serializer/Deserializer (supporting both Raw Mode and Structured Frame Mode).
*   Implement Local Message Repository (Room DB / Flow) with message state tracking (`PENDING`, `BLE_SENT`, `AIR_TRANSMITTED`, `RECEIVED`).
*   Implement `BleRelayForegroundService` to keep GATT connection alive in background.

### Phase 4: Jetpack Compose Interface & System Integration
*   Build the Disaster Dashboard:
    *   Node connection status banner (Connected / Disconnected / Reconnecting).
    *   Signal indicators (BLE RSSI & Remote LoRa RSSI/SNR).
    *   Interactive chat stream with delivered/pending checkmarks.
    *   Quick Broadcast SOS template buttons ("Emergency at Location", "Request Medical Aid", "I am Safe").
*   End-to-end testing with physical hardware and ADB.
