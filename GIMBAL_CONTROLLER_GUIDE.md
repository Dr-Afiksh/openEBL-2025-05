# Gimbal Controller — MIT App Inventor Build Guide

A complete walkthrough for building an Android app that controls your ESP32-C6 gimbal over BLE. Three control modes (joystick, phone tilt, arrow buttons), all in one app.

**Time to build:** ~1 hour, working from scratch.

---

## 1. Setup (one-time)

1. Go to https://appinventor.mit.edu and click **Create Apps!**
2. Sign in with a Google account.
3. **Project → Start new project** → name it `GimbalControl`.
4. Install the **Bluetooth Low Energy extension**:
   - In the Designer (left palette), scroll down to **Extension**
   - Click **Import extension** → **URL**
   - Paste: `https://mit-cml.github.io/extensions/edu.mit.appinventor.ble.aix`
   - Click **Import**. A `BluetoothLE` block should now appear in the Extension drawer.
5. Install the **MIT AI2 Companion** app from the Play Store on your phone — this lets you live-test without building an APK every time.

---

## 2. Designer — Components and Properties

Drop these onto Screen1 in the order shown. Properties listed are non-default; leave anything not mentioned alone.

### Screen1
- **Title:** `Gimbal C6`
- **AlignHorizontal:** Center
- **BackgroundColor:** Black
- **ScreenOrientation:** Portrait

### Top status bar — `HorizontalArrangement1`
- Width: Fill parent, Height: 50 px, BackgroundColor: Dark Gray
- Inside it:
  - **Label** `LblStatus` — Text: `DISCONNECTED`, TextColor: Red, FontBold: yes, Width: Fill parent
  - **Button** `BtnConnect` — Text: `CONNECT`, BackgroundColor: Orange, TextColor: Black

### Mode selector — `HorizontalArrangement2`
- Width: Fill parent, Height: 60 px
- Inside it, three buttons (each Width: Fill parent, Height: Fill parent):
  - **Button** `BtnModeJoy` — Text: `JOYSTICK`, BackgroundColor: Orange
  - **Button** `BtnModeTilt` — Text: `TILT`, BackgroundColor: Dark Gray
  - **Button** `BtnModeBtn` — Text: `BUTTONS`, BackgroundColor: Dark Gray

### Three control panes (only one visible at a time)

**`VertJoystick`** (VerticalArrangement, Width: Fill parent, Height: Fill parent, AlignHorizontal: Center, AlignVertical: Center, Visible: yes)
- **Canvas** `JoyCanvas` — Width: 300 px, Height: 300 px, BackgroundColor: Dark Gray, PaintColor: Orange
  - Inside the canvas, drag a **Ball** component:
    - **Ball** `JoyBall` — Radius: 30, PaintColor: Orange, X: 135, Y: 135
- **Label** `LblJoyXY` — Text: `X:0  Y:0`, FontSize: 18, TextColor: Light Gray

**`VertTilt`** (VerticalArrangement, Width: Fill parent, Height: Fill parent, AlignHorizontal: Center, AlignVertical: Center, Visible: no)
- **Label** Text: `TILT YOUR PHONE TO AIM`, FontSize: 16, TextColor: Orange, FontBold: yes
- **Label** `LblTilt` — Text: `Pitch:0  Roll:0`, FontSize: 18, TextColor: Light Gray, FontBold: yes
- **Button** `BtnArm` — Text: `ARM (DISARMED)`, BackgroundColor: Red, TextColor: White, Width: 240 px, Height: 60 px

**`VertButtons`** (VerticalArrangement, Width: Fill parent, Height: Fill parent, AlignHorizontal: Center, AlignVertical: Center, Visible: no)
- Drop a **TableArrangement** (3 columns × 3 rows). Inside, place buttons in a +-pattern:
  ```
  [ ] [Up] [ ]
  [Lt][  ] [Rt]
  [ ] [Dn] [ ]
  ```
- **Button** `BtnUp` — Text: `▲`, FontSize: 32, Width: 80 px, Height: 80 px, BackgroundColor: Orange
- **Button** `BtnDown` — Text: `▼`, FontSize: 32, Width: 80 px, Height: 80 px, BackgroundColor: Orange
- **Button** `BtnLeft` — Text: `◀`, FontSize: 32, Width: 80 px, Height: 80 px, BackgroundColor: Orange
- **Button** `BtnRight` — Text: `▶`, FontSize: 32, Width: 80 px, Height: 80 px, BackgroundColor: Orange
- (Empty buttons: just leave the corner cells empty)

### Bottom action row — `HorizontalArrangement3`
- Width: Fill parent, Height: 70 px
- Four buttons, each Width: Fill parent, Height: Fill parent:
  - **Button** `BtnAct1` — Text: `SPEED`, BackgroundColor: Dark Gray, TextColor: White
  - **Button** `BtnAct2` — Text: `CENTER`, BackgroundColor: Dark Gray, TextColor: White
  - **Button** `BtnAct3` — Text: `LEVEL`, BackgroundColor: Dark Gray, TextColor: White
  - **Button** `BtnAct4` — Text: `ON/OFF`, BackgroundColor: Dark Gray, TextColor: White

### Non-visible components
Drag these into the workspace; they appear below the screen preview.
- **BluetoothLE1** (from the Extension drawer)
- **AccelerometerSensor1** — Enabled: no (we'll turn it on in Tilt mode only)
- **Clock1** — TimerInterval: 50 (ms), TimerEnabled: no, TimerAlwaysFires: yes
- **Notifier1**

---

## 3. Constants — used throughout the blocks

These are the BLE protocol IDs from your gimbal sketch. Save yourself typing them repeatedly by defining them once in the Blocks editor.

In Blocks editor, under **Variables**, create:

```
initialize global SERVICE_UUID to "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
initialize global RX_UUID      to "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
initialize global DEVICE_NAME  to "C6_Gimbal"
initialize global isConnected     to false
initialize global currentMode     to "joystick"   // "joystick" | "tilt" | "buttons"
initialize global joyX            to 0            // -1.0 .. +1.0
initialize global joyY            to 0
initialize global joyTouching     to false
initialize global heldUp          to false
initialize global heldDown        to false
initialize global heldLeft        to false
initialize global heldRight       to false
initialize global tiltArmed       to false
```

---

## 4. Blocks — Connection logic

```
when BtnConnect.Click
  if not (get global isConnected) then
    call BluetoothLE1.StartScanning
    set LblStatus.Text to "SCANNING..."
    set LblStatus.TextColor to color [orange]
  else
    call BluetoothLE1.Disconnect
end
```

```
when BluetoothLE1.DeviceFound
  // Iterate the scan list and look for our device by name
  for each device in (call BluetoothLE1.DeviceList)
    if (call BluetoothLE1.DeviceName device) = (get global DEVICE_NAME) then
      call BluetoothLE1.StopScanning
      call BluetoothLE1.Connect with device
    end
  end
end
```

```
when BluetoothLE1.Connected
  set global isConnected to true
  set LblStatus.Text to "CONNECTED"
  set LblStatus.TextColor to color [green]
  set BtnConnect.Text to "DISCONNECT"
  set Clock1.TimerEnabled to true   // start sending packets
end
```

```
when BluetoothLE1.Disconnected
  set global isConnected to false
  set LblStatus.Text to "DISCONNECTED"
  set LblStatus.TextColor to color [red]
  set BtnConnect.Text to "CONNECT"
  set Clock1.TimerEnabled to false
end
```

```
when BluetoothLE1.ConnectionFailed
  call Notifier1.ShowAlert with message "Connection failed: " + (get reason)
  set LblStatus.Text to "DISCONNECTED"
  set LblStatus.TextColor to color [red]
end
```

---

## 5. Blocks — The packet builder (the most important part)

This procedure sends a Bluefruit Connect Control Pad packet to the gimbal. Bluefruit packets are: `!B<button><state><CRC>` where button is `'1'`–`'8'` and state is `'0'`/`'1'`.

```
to procedure SendButton (button, pressed)
  // button: text "1".."8"
  // pressed: boolean
  if not (get global isConnected) then  return
  
  // Build payload bytes: 0x21, 0x42, button-ASCII, state-ASCII
  initialize local b0 to 33                          // '!'
  initialize local b1 to 66                          // 'B'
  initialize local b2 to (call GetCharCode of button)  // see helper below
  initialize local b3 to (if pressed then 49 else 48)  // '1' or '0'
  
  // CRC = (~(b0+b1+b2+b3)) & 0xFF
  initialize local sum to (b0 + b1 + b2 + b3)
  initialize local crc to bitwise-and (bitwise-not sum) with 255
  
  call BluetoothLE1.WriteBytes with
    serviceUuid = (get global SERVICE_UUID)
    characteristicUuid = (get global RX_UUID)
    signed = false
    bytes = make a list (b0, b1, b2, b3, crc)
end
```

Helper — convert a single character "1".."8" to its ASCII code:

```
to procedure GetCharCode (ch)
  // returns ASCII code of first char of `ch`
  // App Inventor: use (call obfuscated-text-to-list ch) or just hard-code:
  if ch = "1" then return 49
  if ch = "2" then return 50
  if ch = "3" then return 51
  if ch = "4" then return 52
  if ch = "5" then return 53
  if ch = "6" then return 54
  if ch = "7" then return 55
  if ch = "8" then return 56
  return 48
end
```

(In App Inventor, use the **Math → bitwise-and / bitwise-not** blocks. They're under the gear icon on the basic Math drawer.)

For the **Color Picker / Accelerometer** packets, use a similar pattern but with different identifiers — see the BLE protocol summary at the bottom.

---

## 6. Blocks — Mode switching

```
when BtnModeJoy.Click
  set global currentMode to "joystick"
  set VertJoystick.Visible to true
  set VertTilt.Visible to false
  set VertButtons.Visible to false
  set BtnModeJoy.BackgroundColor to color [orange]
  set BtnModeTilt.BackgroundColor to color [dark gray]
  set BtnModeBtn.BackgroundColor to color [dark gray]
  set AccelerometerSensor1.Enabled to false
  // Disarm tilt for safety
  set global tiltArmed to false
end

when BtnModeTilt.Click
  set global currentMode to "tilt"
  set VertJoystick.Visible to false
  set VertTilt.Visible to true
  set VertButtons.Visible to false
  set BtnModeJoy.BackgroundColor to color [dark gray]
  set BtnModeTilt.BackgroundColor to color [orange]
  set BtnModeBtn.BackgroundColor to color [dark gray]
end

when BtnModeBtn.Click
  set global currentMode to "buttons"
  set VertJoystick.Visible to false
  set VertTilt.Visible to false
  set VertButtons.Visible to true
  set BtnModeJoy.BackgroundColor to color [dark gray]
  set BtnModeTilt.BackgroundColor to color [dark gray]
  set BtnModeBtn.BackgroundColor to color [orange]
  set AccelerometerSensor1.Enabled to false
  set global tiltArmed to false
end
```

---

## 7. Blocks — Joystick (Canvas)

The Canvas component fires `Touched` and `Dragged` events with X/Y coordinates relative to the canvas.

```
when JoyCanvas.TouchDown (x, y)
  set global joyTouching to true
  call UpdateJoy with x, y
end

when JoyCanvas.Dragged (startX, startY, prevX, prevY, currentX, currentY, draggedAnySprite)
  call UpdateJoy with currentX, currentY
end

when JoyCanvas.TouchUp (x, y)
  set global joyTouching to false
  set global joyX to 0
  set global joyY to 0
  // Snap ball back to center
  call JoyBall.MoveTo with x = 135, y = 135
  set LblJoyXY.Text to "X:0  Y:0"
end
```

```
to procedure UpdateJoy (x, y)
  // Canvas is 300x300, center is 150,150, ball radius is 30
  // Normalize to -1..+1
  initialize local dx to (x - 150) / 120
  initialize local dy to (y - 150) / 120
  // Clamp to circle (max magnitude 1)
  initialize local mag to sqrt(dx*dx + dy*dy)
  if mag > 1 then
    set dx to dx / mag
    set dy to dy / mag
  end
  set global joyX to dx
  set global joyY to dy
  // Move the ball (subtract its radius for centering)
  call JoyBall.MoveTo with
    x = 150 + (dx * 120) - 30
    y = 150 + (dy * 120) - 30
  // Update readout: round to 2 decimals
  set LblJoyXY.Text to
    "X:" + (format-as-decimal dx 2) + "  Y:" + (format-as-decimal dy 2)
end
```

---

## 8. Blocks — Phone tilt (Accelerometer)

```
when BtnArm.Click
  set global tiltArmed to (not get global tiltArmed)
  if get global tiltArmed then
    set BtnArm.Text to "ARMED — TAP TO DISARM"
    set BtnArm.BackgroundColor to color [green]
    set AccelerometerSensor1.Enabled to true
  else
    set BtnArm.Text to "ARM (DISARMED)"
    set BtnArm.BackgroundColor to color [red]
    set AccelerometerSensor1.Enabled to false
  end
end

when AccelerometerSensor1.AccelerationChanged (xAccel, yAccel, zAccel)
  // Display raw pitch/roll for the user
  set LblTilt.Text to
    "Pitch:" + (format-as-decimal yAccel 1) + "  Roll:" + (format-as-decimal xAccel 1)
end
```

The Clock timer (next section) reads accelerometer values and sends them.

---

## 9. Blocks — Buttons mode (with press AND release)

The gimbal sketch tracks held state, so we MUST send both press and release events.

App Inventor Button has `TouchDown` and `TouchUp` events:

```
when BtnUp.TouchDown
  set global heldUp to true
  call SendButton with "5", true
when BtnUp.TouchUp
  set global heldUp to false
  call SendButton with "5", false

when BtnDown.TouchDown
  set global heldDown to true
  call SendButton with "6", true
when BtnDown.TouchUp
  set global heldDown to false
  call SendButton with "6", false

when BtnLeft.TouchDown
  set global heldLeft to true
  call SendButton with "7", true
when BtnLeft.TouchUp
  set global heldLeft to false
  call SendButton with "7", false

when BtnRight.TouchDown
  set global heldRight to true
  call SendButton with "8", true
when BtnRight.TouchUp
  set global heldRight to false
  call SendButton with "8", false
```

---

## 10. Blocks — Action row (the four bottom buttons)

These are momentary presses. Send press + release back-to-back, since the firmware only acts on press.

```
when BtnAct1.Click
  call SendButton with "1", true
  call SendButton with "1", false

when BtnAct2.Click
  call SendButton with "2", true
  call SendButton with "2", false

when BtnAct3.Click
  call SendButton with "3", true
  call SendButton with "3", false

when BtnAct4.Click
  call SendButton with "4", true
  call SendButton with "4", false
```

---

## 11. Blocks — The Clock timer (the heart of joystick + tilt mode)

Buttons mode is event-driven (TouchDown/Up sends packets directly). But Joystick and Tilt modes need to send updates *continuously* while you're moving — that's what Clock1 does. Set TimerInterval to 50 ms (= 20 Hz update rate, plenty smooth).

```
when Clock1.Timer
  if not (get global isConnected) then  return
  
  if get global currentMode = "joystick" then
    call SendAccelPacket with
      x = get global joyX
      y = get global joyY * -1   // invert so up-on-stick = up-on-gimbal
      z = 1
  
  else if get global currentMode = "tilt" and get global tiltArmed then
    call SendAccelPacket with
      x = AccelerometerSensor1.XAccel / 9.8   // normalize to ~g
      y = AccelerometerSensor1.YAccel / 9.8
      z = AccelerometerSensor1.ZAccel / 9.8
  end
  
  // Buttons mode: nothing to do here, events handle it
end
```

The clever bit: **the joystick reuses the gimbal's Accelerometer (`!A`) packet handler.** No firmware change needed. Drag the stick → the app sends fake accelerometer readings → the gimbal interprets them as tilt commands. This is why the protocol summary I gave you earlier suggested it — it's the cleanest way to add analog control without modifying the sketch.

Building the `!A` packet:

```
to procedure SendAccelPacket (x, y, z)
  if not (get global isConnected) then  return
  
  // !A header (2 bytes), 3 floats (12 bytes), CRC (1 byte) = 15 bytes total
  initialize local payload to make a list (33, 65)   // '!' 'A'
  
  // Append 3 little-endian float32s
  call AppendFloat32LE to payload with x
  call AppendFloat32LE to payload with y
  call AppendFloat32LE to payload with z
  
  // Compute CRC over the 14 bytes so far
  initialize local sum to 0
  for each b in payload
    set sum to sum + b
  end
  initialize local crc to bitwise-and (bitwise-not sum) with 255
  add crc to payload
  
  call BluetoothLE1.WriteBytes with
    serviceUuid = (get global SERVICE_UUID)
    characteristicUuid = (get global RX_UUID)
    signed = false
    bytes = payload
end
```

### The float-to-bytes helper

This is the trickiest block in the whole project. App Inventor doesn't have a built-in IEEE 754 float encoder, so we have to construct one. Here's the algorithm in plain blocks form — you'll implement it as a procedure that appends 4 bytes to a list:

```
to procedure AppendFloat32LE (list, value)
  // Special cases
  if value = 0 then
    add 0, 0, 0, 0 to list
    return
  end
  
  initialize local sign to (if value < 0 then 1 else 0)
  initialize local v to abs(value)
  
  // Find exponent: floor(log2(v))
  initialize local exp to floor(log2(v))
  initialize local mantissa to v / pow(2, exp) - 1   // normalized mantissa, 0..1
  
  // 23-bit mantissa
  initialize local mantBits to floor(mantissa * pow(2, 23))
  initialize local biasedExp to exp + 127
  
  // Pack: sign(1) | exp(8) | mantissa(23) — total 32 bits
  initialize local bits to
    bitwise-or
      bitwise-or (sign * pow(2, 31)) (biasedExp * pow(2, 23))
      mantBits
  
  // Split into 4 little-endian bytes
  add (bitwise-and bits 255) to list                 // byte 0 (LSB)
  add (bitwise-and (floor(bits / 256)) 255) to list  // byte 1
  add (bitwise-and (floor(bits / 65536)) 255) to list  // byte 2
  add (bitwise-and (floor(bits / 16777216)) 255) to list  // byte 3 (MSB)
end
```

App Inventor's number type is double-precision float, so this works for the range we care about (values between -1 and +1). It's not bulletproof for edge cases (NaN, infinity, denormals) but those won't come up here.

**Easier alternative if this float math feels intimidating:** modify the gimbal sketch to accept a **simpler custom packet** like `!J<x_int8><y_int8><crc>` where x and y are signed bytes from -100 to +100. Adding a parser for that is ~10 lines of C. Then App Inventor only needs to send 5 bytes with no floats. Let me know if you want this route — much simpler from the App Inventor side.

---

## 12. Test it

1. Click **Connect** (top-right of App Inventor) → **AI Companion** → scan QR code with the AI Companion app on your phone.
2. The app launches live on your phone.
3. Power up the gimbal, hit CONNECT in the app.
4. Try each mode.

When happy, **Build → Android App (APK)** to get an installable file you can put on any phone.

---

## 13. BLE protocol cheat sheet

For reference, the gimbal accepts these packet types over the Nordic UART Service:

| Packet | Bytes | Purpose |
|---|---|---|
| `!B<btn><state><crc>` | 5 | Control Pad — buttons '1'–'8', state '0'/'1' |
| `!A<x><y><z><crc>` | 15 | Accelerometer — three 32-bit little-endian floats |
| `!C<r><g><b><crc>` | 6 | Color Picker — for NeoPixel version only |

Service UUID: `6e400001-b5a3-f393-e0a9-e50e24dcca9e`
RX characteristic (write): `6e400002-b5a3-f393-e0a9-e50e24dcca9e`

CRC = `(~sum_of_preceding_bytes) & 0xFF`. The firmware doesn't validate CRC, so sending `0` works in a pinch — but compute it properly for forward compatibility.

---

## 14. Common pitfalls

- **App says "Bluetooth not enabled"** → grant Location and Bluetooth permissions in phone Settings → Apps → AI Companion (or your built APK).
- **Scan finds nothing** → make sure the gimbal sketch is running (onboard LED blinking blue) and you're not already connected from another phone or Bluefruit Connect.
- **Connects but commands do nothing** → check the SERVICE_UUID and RX_UUID variables match exactly (copy-paste, don't retype).
- **Joystick is jittery on the gimbal end** → the float encoder is producing values close to zero with some drift. Add a deadzone in `UpdateJoy`: if abs(dx) < 0.05, set to 0; same for dy.
- **App freezes** → too many BLE writes per second. Stick to TimerInterval ≥ 50 ms (max 20 Hz updates).
- **APK build fails** → App Inventor's free build server has occasional outages. Retry, or use Connect → AI Companion for live testing in the meantime.

---

## 15. After you ship v1

Things you'll probably want to add once it's working:

- **Save last connected device ID** so it auto-reconnects on launch.
- **Vibrate on connect / disconnect** as haptic feedback.
- **Visualize the gimbal's actual position** by having the firmware notify the app over the TX characteristic. Then draw a little crosshair that mirrors where the camera is pointing.
- **Battery status** of the phone vs. external supply — the firmware would need to report supply voltage.
- **Recordable presets** — tap to save current pan/tilt as preset 1, long-press to recall. Implement in firmware (NVS storage) or app (just send a sequence of commands).
