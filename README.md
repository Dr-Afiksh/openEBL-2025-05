# Gimbal Controller — Android app

Native Android app that controls the ESP32-C6 Super Mini gimbal over BLE.
Implements the same three control modes described in `../GIMBAL_CONTROLLER_GUIDE.md`
(originally written as an MIT App Inventor walkthrough): joystick, phone-tilt,
and an arrow-button pad.

Talks the same Bluefruit Connect-style packets the firmware already understands
— so no firmware change is needed.

## Stack

- Kotlin + Jetpack Compose (Material 3)
- minSdk 26, targetSdk 34
- BLE via stdlib `android.bluetooth.*`
- Sensors via stdlib `SensorManager`
- Coroutines + `StateFlow`

No third-party BLE libraries.

## Project layout

```
app/src/main/kotlin/com/siepic/gimbal/
├── MainActivity.kt           # Activity + permission flow
├── ble/
│   ├── BleUuids.kt           # SERVICE/RX/TX UUIDs, device name
│   ├── PacketBuilder.kt      # !B, !A, !C (and !J) packet bytes + CRC
│   └── GimbalBleClient.kt    # scan -> connect -> GATT write
├── sensor/TiltSensor.kt      # accelerometer Flow
├── protocol/ProtocolMode.kt  # LEGACY_FLOAT_A | SIMPLE_J
├── viewmodel/GimbalViewModel.kt  # state + 50 ms tick
└── ui/
    ├── GimbalScreen.kt       # status bar, mode tabs, action row
    ├── JoystickPane.kt       # Compose Canvas with drag
    ├── TiltPane.kt           # ARM toggle, live readout
    ├── ButtonsPane.kt        # +-pattern arrow pad with press+release
    └── theme/                # Material 3 dark + orange accent
```

Unit tests live in `app/src/test/kotlin/...`.

## Build

You need the Android SDK installed locally and `ANDROID_HOME` (or
`local.properties` with `sdk.dir=…`) pointing at it. Then:

```
./gradlew assembleDebug         # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew test                  # runs PacketBuilderTest
./gradlew installDebug          # installs to a USB-attached device
```

If the build is fetching dependencies for the first time it needs network
access to `dl.google.com` (Android Gradle Plugin) and `repo.maven.apache.org`
(Maven Central). The Gradle distribution itself is downloaded from
`services.gradle.org` by the wrapper.

> Note: the CI environment used to author this project blocked
> `dl.google.com`, so the full `assembleDebug` was not exercised here. The
> packet-builder unit tests only depend on the JVM stdlib and JUnit, but the
> way Gradle is configured they're tied to the Android module so they pull
> AGP transitively. Run them locally to confirm.

## Use

1. Power up the gimbal — onboard LED blinks dim blue when advertising.
2. Install the APK (`adb install app-debug.apk` or sideload).
3. Open the app, tap **CONNECT**. First run will request Bluetooth permissions.
4. Try each mode:
   - **JOYSTICK** — drag the orange knob. Sends `!A` floats; firmware reuses
     its accelerometer handler. The knob auto-snaps to center on release.
   - **TILT** — tap **ARM** before tilting. Disarm before pocketing the phone.
   - **BUTTONS** — momentary arrow pad. Hold-to-move; release stops.
5. Bottom action row: SPEED (toggle preset), CENTER (re-home + LED off),
   LEVEL (cycle NeoPixel brightness), ON/OFF (toggle NeoPixel).

## BLE protocol

Matches `../GIMBAL_CONTROLLER_GUIDE.md` §13 verbatim:

| Packet | Bytes | Notes |
|---|---|---|
| `!B<btn><state><crc>` | 5 | btn '1'..'8'; state '0'/'1' |
| `!A<x><y><z><crc>` | 15 | three LE float32s, units = g |
| `!C<r><g><b><crc>` | 6 | NeoPixel color (firmware supports it; UI exposes it later) |
| `!J<x:i8><y:i8><crc>` | 5 | proposed simpler joystick path; **not** in current firmware |

CRC = `(~sum_of_preceding_bytes) & 0xFF`. The firmware doesn't validate the
CRC, but we compute it correctly.

## The `!J` switch

`TiltPane` has a switch that toggles the joystick mode between `!A` (floats,
default, works today) and `!J` (signed bytes, requires a firmware patch — see
the guide §11 "easier alternative"). Leave it on `!A` unless you've extended
the sketch's `RxCallbacks::onWrite` to recognize `!J`.

## Known limitations

- App is type-checked but has not been run on a device or against a real
  gimbal in this environment. Treat the first install as smoke-test territory.
- BLE writes are fire-and-forget. At 20 Hz on a slow link some writes will
  drop silently — acceptable for a teleop UI but not for command/ack flows.
- Single-screen, portrait-only, no preset save/recall, no auto-reconnect. Those
  are listed under guide §15 "after you ship v1".
