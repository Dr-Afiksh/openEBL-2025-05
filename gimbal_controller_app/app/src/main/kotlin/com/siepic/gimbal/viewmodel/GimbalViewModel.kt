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

enum class Mode { JOYSTICK, TILT, BUTTONS }

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

    val connection: StateFlow<ConnectionState> get() = ble.state
    val errors: StateFlow<String?> get() = ble.lastError

    private var tiltJob: Job? = null
    private var tickJob: Job? = null

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
                    }
                }
                delay(50)
            }
        }
    }

    private fun sendJoystick() {
        val (x, y) = _joy.value
        // Up on stick = up on gimbal: invert Y so a positive stick-up maps to
        // negative pitch (tilt up) once the firmware does its atan2.
        val invY = -y
        val pkt = when (_protocol.value) {
            ProtocolMode.LEGACY_FLOAT_A -> PacketBuilder.accelPacket(x, invY, 1f)
            ProtocolMode.SIMPLE_J -> PacketBuilder.joystickPacket(x, invY)
        }
        ble.write(pkt)
    }

    private fun sendTilt() {
        val v = _tilt.value
        ble.write(PacketBuilder.accelPacket(v.x, v.y, v.z))
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
}
