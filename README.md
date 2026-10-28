# LoRa BLE Disaster Messenger

An Android MVP that bridges a phone to an ESP32 LoRa relay over Bluetooth Low Energy. It provides a high-contrast emergency chat, BLE node discovery, serialized GATT setup, offline message history, and a foreground keepalive for receiving messages while the screen is locked.

## Features

- Android 12+ Nearby Devices and notification permission gate.
- BLE scan radar with RSSI smoothing, signal categories, and a 12-second scan window.
- Simulated `LoRa Node 1` for hardware-independent connection and echo testing.
- FIFO GATT command queue with a five-second watchdog, MTU 247 negotiation, service discovery, and CCCD notification subscription.
- Raw UTF-8 message send/receive over the bridge UUID contract.
- Chat bubbles, delivery states, emergency presets, and incoming-message haptics.
- Room-backed message history that survives process restarts.
- `connectedDevice` foreground service with persistent status and lock-screen incoming-message alerts.
- Exponential reconnect delays of 1, 2, 4, 8, 16, then 30 seconds.

The backend and service topology is documented in [ARCHITECTURE.md](ARCHITECTURE.md).

## Requirements

- JDK 17
- Android SDK/API 36 and Build Tools 36
- A physical Android 8.0+ device or emulator
- USB debugging enabled for deployment

## Build

From the repository root:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install and launch

Using the Android CLI:

```powershell
& 'C:\Users\rehan\AppData\AndroidCLI\android.exe' run
```

If Android displays an install confirmation, approve it on the device and rerun the command if necessary.

## Demo walkthrough

1. Grant Nearby Devices and notification permissions.
2. Tap **Scan for LoRa Node** and enable **Simulate Demo Node**.
3. Select **LoRa Node 1 (Simulated)** and wait for the HUD to reach `CONNECTED`.
4. Send a message or tap an emergency preset. The simulated node returns an echo, while outgoing messages progress through `Queued`, `Sent to Node`, and `Air Transmitted`.
5. With physical hardware, select the advertised node instead; the same UI uses the ESP32 GATT service.

## BLE contract

| Item | UUID |
|---|---|
| Primary service | `12345678-1234-1234-1234-1234567890AB` |
| RX (phone writes) | `12345678-1234-1234-1234-1234567890AC` |
| TX (node notifies) | `12345678-1234-1234-1234-1234567890AD` |
| CCCD | `00002902-0000-1000-8000-00805f9b34fb` |

The current MVP sends raw UTF-8 text. Structured telemetry framing, remote ACK reconciliation, Room migrations, and message export remain production follow-ups.

## Project layout

```text
app/src/main/java/com/example/loramessenger/
├── data/ble/       BLE scanner, GATT manager, queue, and shared runtime
├── data/db/        Room database, entity, and DAO
├── domain/         Relay, link, chat, and delivery models
├── service/        Foreground connected-device service
├── ui/main/        Compose dashboard and RelayViewModel
└── theme/          Emergency high-contrast Material 3 theme
```
