package com.siepic.gimbal.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.siepic.gimbal.ble.ConnectionState
import com.siepic.gimbal.ble.GimbalBleClient
import com.siepic.gimbal.ble.PacketBuilder
import com.siepic.gimbal.protocol.ProtocolMode
import com.siepic.gimbal.sensor.TiltSensor
import com.siepic.gimbal.sensor.Vec3
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class Mode { JOYSTICK, TILT, BUTTONS, COLOR }

class GimbalViewModel(app: Application) : AndroidViewModel(app) {

    val ble = GimbalBleClient(app.applicationContext)
    private val tiltSensor = TiltSensor(app.applicationContext)

    private val _mode = MutableStateFlow(Mode.JOYSTICK)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _joy = MutableStateFlow(JoyState(0f, 0f))
    val joy: StateFlow<JoyState> = _joy.asStateFlow()

    private val _tilt = MutableStateFlow(Vec3(0f, 0f, 1f))
    val tilt: StateFlow<Vec3> = _tilt.asStateFlow()

    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    private val _protocol = MutableStateFlow(ProtocolMode.LEGACY_FLOAT_A)
    val protocol: StateFlow<ProtocolMode> = _protocol.asStateFlow()

    // Joystick safety knobs. Defaults are deliberately conservative after a
    // burnt-out servo: half deflection, no inversion, slew limit baked in.
    private val _invertX = MutableStateFlow(false)
    val invertX: StateFlow<Boolean> = _invertX.asStateFlow()

    private val _invertY = MutableStateFlow(false)
    val invertY: StateFlow<Boolean> = _invertY.asStateFlow()

    private val _amplitude = MutableStateFlow(0.5f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _color = MutableStateFlow(Triple(255, 128, 0)) // GimbalOrange-ish default
    val color: StateFlow<Triple<Int, Int, Int>> = _color.asStateFlow()

    val connection: StateFlow<ConnectionState> get() = ble.state
    val errors: StateFlow<String?> get() = ble.lastError

    private var tiltJob: Job? = null
    private var tickJob: Job? = null

    // Slew-rate limiter state. Range is [-1, +1]; MAX_SLEW_PER_SEC of 1.5 means
    // a full-deflection step is smoothed into ~0.67 s of motion — slow enough
    // for an SG90 to track without stalling.
    private var lastSentX = 0f
    private var lastSentY = 0f
    private var lastSendTimeMs = 0L
    private var lastSentColor: Triple<Int, Int, Int>? = null

    init {
        // 50 ms tick = 20 Hz, per guide §11. Sends nothing in BUTTONS mode (those
        // packets fire on touch events directly).
        tickJob = viewModelScope.launch {
            while (isActive) {
                if (ble.state.value == ConnectionState.CONNECTED) {
                    when (_mode.value) {
                        Mode.JOYSTICK -> sendJoystick()
                        Mode.TILT -> if (_armed.value) sendTilt()
                        Mode.BUTTONS -> { /* event-driven */ }
                        Mode.COLOR -> sendColorIfChanged()
                    }
                }
                delay(50)
            }
        }
    }

    private fun sendJoystick() {
        val (sx, sy) = _joy.value
        // Compose Y is screen-down-positive; flip so stick-up = +1 logically.
        val logicalX = sx
        val logicalY = -sy
        // Field-verified: this firmware expects axes swapped vs. our logical
        // frame. The shared safety pipeline applies invert/amplitude/slew to
        // whatever we feed it.
        val (outX, outY) = applySafety(logicalY, logicalX)
        val pkt = when (_protocol.value) {
            ProtocolMode.LEGACY_FLOAT_A -> PacketBuilder.accelPacket(outX, outY, 1f)
            ProtocolMode.SIMPLE_J -> PacketBuilder.joystickPacket(outX, outY)
        }
        ble.write(pkt)
    }

    fun setInvertX(v: Boolean) { _invertX.value = v }
    fun setInvertY(v: Boolean) { _invertY.value = v }
    fun setAmplitude(v: Float) { _amplitude.value = v.coerceIn(0f, 1f) }

    fun setColor(r: Int, g: Int, b: Int) {
        // Slider drags fire onValueChange continuously. Update state here; the
        // 20 Hz tick coalesces and writes only when the value actually changed,
        // which keeps the BLE write queue from filling on a fast drag.
        _color.value = Triple(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun sendColorIfChanged() {
        val current = _color.value
        if (current == lastSentColor) return
        val (r, g, b) = current
        ble.write(PacketBuilder.colorPacket(r, g, b))
        lastSentColor = current
    }

    private fun sendTilt() {
        val v = _tilt.value
        // Phone accelerometer's X/Y already match the firmware frame, so no
        // swap. z is left untouched so the firmware's atan2 still sees a
        // clean 1g vertical reference.
        val (outX, outY) = applySafety(v.x, v.y)
        ble.write(PacketBuilder.accelPacket(outX, outY, v.z))
    }

    /**
     * Shared safety pipeline for any 2D control input feeding the !A packet:
     *   user-controlled invertX/Y -> amplitude clamp (default 0.5x) ->
     *   slew-rate limit at MAX_SLEW_PER_SEC.
     *
     * Slew state is shared across modes so a switch from joystick to tilt
     * carries the last commanded value smoothly instead of stepping.
     */
    private fun applySafety(rawX: Float, rawY: Float): Pair<Float, Float> {
        var x = rawX
        var y = rawY
        if (_invertX.value) x = -x
        if (_invertY.value) y = -y
        val amp = _amplitude.value
        x *= amp
        y *= amp
        val now = System.currentTimeMillis()
        if (lastSendTimeMs == 0L) lastSendTimeMs = now
        val dtSec = ((now - lastSendTimeMs).coerceAtLeast(1)) / 1000f
        lastSendTimeMs = now
        val maxStep = MAX_SLEW_PER_SEC * dtSec
        val outX = (x - lastSentX).coerceIn(-maxStep, maxStep) + lastSentX
        val outY = (y - lastSentY).coerceIn(-maxStep, maxStep) + lastSentY
        lastSentX = outX
        lastSentY = outY
        return Pair(outX, outY)
    }

    fun setMode(m: Mode) {
        if (_mode.value == m) return
        _mode.value = m
        // Disarm tilt whenever we leave/re-enter — safety, matches guide §6.
        _armed.value = false
        stopTiltSensor()
        // Reset joystick when leaving so the firmware's tilt mode stops driving.
        if (m != Mode.JOYSTICK) _joy.value = JoyState(0f, 0f)
    }

    fun updateJoy(x: Float, y: Float) {
        // Deadzone — guide §14 pitfall: drift near zero produces servo jitter.
        val dx = if (abs(x) < 0.05f) 0f else x
        val dy = if (abs(y) < 0.05f) 0f else y
        _joy.value = JoyState(dx, dy)
    }

    fun releaseJoy() {
        _joy.value = JoyState(0f, 0f)
    }

    fun toggleArmed() {
        if (_armed.value) {
            _armed.value = false
            stopTiltSensor()
        } else {
            _armed.value = true
            startTiltSensor()
        }
    }

    fun setProtocol(p: ProtocolMode) { _protocol.value = p }

    private fun startTiltSensor() {
        tiltJob?.cancel()
        tiltJob = viewModelScope.launch {
            tiltSensor.readings().collect { _tilt.value = it }
        }
    }

    private fun stopTiltSensor() {
        tiltJob?.cancel()
        tiltJob = null
    }

    fun connectOrDisconnect() {
        when (ble.state.value) {
            ConnectionState.DISCONNECTED -> ble.startScan()
            ConnectionState.CONNECTED -> ble.disconnect()
            else -> { /* in-progress, ignore */ }
        }
    }

    fun pressButton(button: Char, pressed: Boolean) {
        ble.write(PacketBuilder.buttonPacket(button, pressed))
    }

    fun tapAction(button: Char) {
        // Action row: press + release back-to-back, since firmware acts on press.
        ble.write(PacketBuilder.buttonPacket(button, true))
        ble.write(PacketBuilder.buttonPacket(button, false))
    }

    fun clearError() = ble.clearError()

    override fun onCleared() {
        tickJob?.cancel()
        tiltJob?.cancel()
        super.onCleared()
    }

    data class JoyState(val x: Float, val y: Float)

    private companion object {
        const val MAX_SLEW_PER_SEC = 1.5f
    }
}
