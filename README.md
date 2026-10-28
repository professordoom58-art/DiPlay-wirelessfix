# DiPlay — ESP32 iAP2 Bridge Transport Integration

This repository has been modified to integrate an external **ESP32 USB-to-Bluetooth bridge** as an alternative hardware transport layer for wireless CarPlay iAP2 orchestration.

---

## Overview of Changes

The modifications enable head units without native Android Bluetooth RFCOMM capability (or with restricted Bluetooth stacks) to offload the wireless CarPlay iAP2 RFCOMM handshake and link establishment to an external ESP32 microcontroller connected via USB.

---

## Detailed Modifications

### 1. Repository Configuration (`settings.gradle.kts`)
- Added **JitPack** (`https://jitpack.io`) to `dependencyResolutionManagement { repositories { ... } }` to allow resolution of third-party Android hardware libraries.

### 2. Dependency Management (`shared/build.gradle`)
- Integrated **`usb-serial-for-android`** (`com.github.mik3y:usb-serial-for-android:3.8.0`) to provide USB-to-UART serial driver communication with USB bridge chips.

### 3. USB Hardware Device Filters (`common/src/main/res/xml/usb_device_filter.xml`)
- Added USB vendor and product ID filters to automatically match and request permission for common ESP32 USB-to-UART chips:
  - **Silicon Labs CP2102**: `vendor-id="4292"`, `product-id="60000"`
  - **WCH CH340**: `vendor-id="6790"`, `product-id="29987"`

### 4. ESP32 Bridge Transport Engine (`shared/src/main/java/com/shilapi/xcertplay/transport/`)
Implemented four core transport components under `com.shilapi.xcertplay.transport`:

- **`EspBridgeHolder.kt`**:
  - Global singleton storing the active `EspBridgeClient` instance and feature enable flag (`isEnabled`).
- **`EspBridgeFrameCodec.kt`**:
  - CRC16-CCITT checksum calculation for frame integrity.
  - COBS (Consistent Overhead Byte Stuffing) encoder and decoder with zero-byte frame delimiters (`0x00`).
- **`EspBridgeClient.kt`**:
  - Manages USB serial connection at 921,600 baud, 8 data bits, 1 stop bit, no parity.
  - Clears DTR/RTS lines to prevent triggering the ESP32 hardware reset/bootloader.
  - Runs a background reader thread handling framing, CRC verification, and packet demultiplexing.
  - Implements the protocol handshake:
    - Sends `HELLO_REQ` (`0x01`) and receives local ESP32 Bluetooth MAC address.
    - Sends `CONNECT` frame (`0x20`) with target iPhone MAC address and awaits `LINK_UP` (`0x21`).
    - Dispatches data frames (`0x30`) to an incoming thread-safe FIFO queue.
- **`EspBridgeDuplexStream.kt`**:
  - Implements `BlockingDuplexByteStream` interface.
  - Bridges CarPlay iAP2 packet transmission and bounded-timeout reception over the ESP32 serial link.
  - Issues `DISCONNECT` frame (`0x23`) on stream close.

### 5. CarPlay Controller Orchestration Patch (`CarPlayController.kt`)
Modified `com.shilapi.xcertplay.orchestration.CarPlayController`:

- **Stream Abstraction**:
  - Generalized member variable `bluetoothStream` from concrete `BluetoothRfcommDuplexStream?` to `BlockingDuplexByteStream?`.
- **MAC Address Utility**:
  - Added `parseMacToByteArray(macStr: String)` to parse colon- or hyphen-separated hex MAC strings into 6-byte raw hardware addresses.
- **ESP32 Bridge Connection Branch**:
  - Intercepted the wireless Bluetooth connection phase during `runWireless`:
    - Checks if `EspBridgeHolder.isEnabled` and client is present.
    - Sends connection request with parsed target iPhone Bluetooth MAC address via ESP32.
    - Overrides `hostBluetoothMac` with the ESP32's local Bluetooth MAC address (`bridge.localMac`) so iOS accurately correlates the AirPlay wireless link with the iAP2 channel.
    - Wraps transport in `EspBridgeDuplexStream`.
    - Preserves native Android Bluetooth RFCOMM socket connection as the fallback branch.
