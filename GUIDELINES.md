# Implementation Guidelines & Conditions: LoRa BLE Disaster Messenger

**Document Version:** 1.0.0  
**Enforcement Level:** MANDATORY for all Implementing Agents  
**Target:** Senior Software / Firmware Integration Standards

---

## 1. Core Operating Conditions (Non-Negotiable)

1. **Step-Wise Verification Gate:**
   - The implementing agent MUST complete one phase at a time as laid out in `PLAN.md`.
   - The agent MUST NEVER jump ahead or write code for future phases prematurely.
   - At the conclusion of every phase, the code MUST compile cleanly (`./gradlew assembleDebug`), be deployed or deployable via `android run`, and produce a visible change the human reviewer can inspect on a physical phone.

2. **Decoupled Hardware Simulation Condition:**
   - Because the hardware team works independently, the agent MUST provide a mock/simulation toggle or stub in the debug layer. If no physical ESP32 is within range, the human reviewer must still be able to test the UI, scanning animation, mock node connection, and test message echoing.

3. **Strict Zero-GATT-133 Concurrency Rule:**
   - Android's native `BluetoothGatt` is non-concurrent. Calling `writeCharacteristic`, `readCharacteristic`, or `discoverServices` while another GATT operation is pending causes fatal `GATT 133` disconnects.
   - **Condition:** ALL Bluetooth GATT calls MUST pass through a serialized FIFO command queue (`Channel` / `Mutex`). Direct invocations on `bluetoothGatt` from ViewModels or Composables are strictly forbidden.

4. **Android 12–15 Permission Hygiene:**
   - The app must support Android 12 through Android 15 gracefully.
   - `BLUETOOTH_SCAN` must include `android:usesPermissionFlags="neverForLocation"` in `AndroidManifest.xml`.
   - `ACCESS_FINE_LOCATION` must be gated with `android:maxSdkVersion="30"`.
   - Runtime permission requests must be graceful, informative, and never crash the app if declined.

---

## 2. Architectural Guardrails & Coding Standards

### 2.1 Layer Separation
*   **Presentation (`ui/`):**
    *   100% Jetpack Compose with Material 3.
    *   No Android framework BLE references (`BluetoothGatt`, `BluetoothDevice`) in UI files. The UI only observes Kotlin Coroutine `StateFlow` primitives.
    *   Unidirectional Data Flow: UI emits User Intents (`ChatIntent`, `ScanIntent`), ViewModel reduces to `UiState`.
*   **Domain (`domain/`):**
    *   Pure Kotlin. No Android imports (`android.*`) in domain logic wherever possible.
    *   Contains framing serializers, message models, and connection state machines.
*   **Data (`data/`):**
    *   `BleGattManager`: Encapsulates GATT lifecycle and callback dispatch.
    *   `GattCommandQueue`: Atomic FIFO queue with timeouts.
    *   `LocalMessageCache`: Room database / Flow for disaster durability.

### 2.2 Bluetooth GATT Best Practices
*   **MTU Negotiation:**
    *   Always call `requestMtu(247)` immediately after `STATE_CONNECTED` and before `discoverServices()`.
*   **CCCD Subscription:**
    *   To receive notifications from the ESP32 TX Characteristic (`...90AD`), setting `setCharacteristicNotification(true)` is NOT enough. The agent MUST also write the Client Characteristic Configuration Descriptor (CCCD: `00002902-0000-1000-8000-00805f9b34fb`) with `ENABLE_NOTIFICATION_VALUE`.
*   **Watchdogs & Timeouts:**
    *   Every GATT operation must be bounded by a 5-second watchdog timer. If Android OS drops the callback, the queue must fail gracefully without hanging indefinitely.

### 2.3 Disaster UI/UX Requirements
*   **High Contrast:** Use dark background with high-contrast text and vivid status indicators (Green: Connected, Amber: Busy/Scanning, Red: Link Broken).
*   **Haptics & Feedback:** In disaster situations, screens may be dirty or users may be wearing gloves. Incoming messages must produce haptic vibration feedback.
*   **One-Tap Emergency Dispatch:** Provide quick emergency broadcast presets (`Medical Aid`, `Rescue Needed`, `Safe`) so a user under stress does not have to type lengthy text on a keyboard.

---

## 3. Human Evaluation Protocol (After Each Phase)

Upon concluding each phase, the implementing agent must execute the following evaluation protocol:

1. **Static Analysis & Compilation:**
   - Execute Gradle build to ensure zero compile warnings or errors:
     ```powershell
     .\gradlew assembleDebug
     ```
2. **Git Commit & Milestone Tag:**
   - Commit changes with semantic commit messages referencing the phase:
     ```bash
     git add .
     git commit -m "feat(phase-X): <description of verified milestone>"
     ```
3. **Human Walkthrough Handoff:**
   - Instruct the human reviewer how to run the app on their phone (`& 'C:\Users\rehan\AppData\AndroidCLI\android.exe' run`).
   - Detail the exact 2 to 3 visual elements and interactions to verify on the physical device before starting the next phase.
