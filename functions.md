We'll make a Kotlin Android app, probably using Android Studio.
The BLE Manager will have functions conceptually like:
scanForEsp32()
connectToEsp32(device)
discoverServices()
sendMessage(message)
startReceivingMessages()
disconnect()
So when the user presses SEND:
User presses SEND
       ↓
Chat screen gets "Hello"
       ↓
BLEManager.sendMessage("Hello")
       ↓
Android BluetoothGatt
       ↓
RX characteristic
       ↓
ESP32
For receiving
The ESP32 sends a BLE notification:
ESP32
  ↓
TX characteristic
  ↓
Android BluetoothGattCallback
  ↓
BLEManager
  ↓
Chat screen
  ↓
Display "Hello"
How the app knows which ESP32 to connect to
We'll give the ESP32 a known BLE service UUID and characteristic UUIDs.
For example:
private val SERVICE_UUID =
    UUID.fromString("12345678-1234-1234-1234-1234567890AB")

private val RX_UUID =
    UUID.fromString("12345678-1234-1234-1234-1234567890AC")

private val TX_UUID =
    UUID.fromString("12345678-1234-1234-1234-1234567890AD")
These same UUIDs are programmed into the ESP32.
Then the app scans for BLE devices and finds:
LoRa Node 1
It connects and searches for our service.
What happens inside the app
Imagine the user types:
Emergency at Building B
and taps Send.
Our code will ultimately do something similar to:
fun sendMessage(message: String) {
    rxCharacteristic.value =
        message.toByteArray(Charsets.UTF_8)

    bluetoothGatt.writeCharacteristic(rxCharacteristic)
}
The ESP32 receives those bytes.
We then have:
"Emergency at Building B"
             ↓
          Android
             ↓
            BLE
             ↓
           ESP32
The ESP32 can then pass the message to the SX1278.
And we don't have to build everything from scratch
Android already provides the low-level Bluetooth functionality:
BLE scanning
device discovery
GATT connection
service discovery
characteristic reads/writes
notifications
We write our own code on top of those APIs to turn them into our messenger's functionality.
So our application architecture can eventually be:
                 ANDROID APP
┌─────────────────────────────────────┐
│                                     │
│             Chat UI                 │
│                ↓                    │
│        Message Manager              │
│                ↓                    │
│          BLE Manager                │
│                ↓                    │
│       Android BLE/GATT APIs         │
└────────────────┬────────────────────┘
                 │
              BLE
                 │
                 ▼
              ESP32
                 │
                SPI
                 │
                 ▼
              SX1278
                 │
                LoRa
                 │
                 ▼
            Other Node