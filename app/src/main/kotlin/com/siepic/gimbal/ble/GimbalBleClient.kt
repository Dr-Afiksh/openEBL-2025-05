package com.siepic.gimbal.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

/**
 * Thin wrapper around the platform BLE APIs:
 *   scan -> match by name -> connect -> discover -> hold a write reference to the RX char.
 *
 * Writes are best-effort, fire-and-forget — matches the firmware which uses
 * WRITE_NO_RESPONSE and never echoes. Concurrent writes are queued by the OS.
 */
@SuppressLint("MissingPermission")
class GimbalBleClient(private val appContext: Context) {

    private val tag = "GimbalBleClient"

    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    private val mainHandler = Handler(Looper.getMainLooper())

    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    fun hasRequiredPermissions(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms.all {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startScan() {
        val a = adapter ?: run { _lastError.value = "Bluetooth not available"; return }
        if (!a.isEnabled) { _lastError.value = "Bluetooth is off"; return }
        val scanner = a.bluetoothLeScanner ?: run {
            _lastError.value = "BLE scanner unavailable"; return
        }
        if (_state.value != ConnectionState.DISCONNECTED) return

        _state.value = ConnectionState.SCANNING
        scanner.startScan(scanCallback)

        // 15-second scan timeout — the firmware advertises continuously when not
        // connected, so anything longer is the device being out of range.
        mainHandler.postDelayed({
            if (_state.value == ConnectionState.SCANNING) {
                stopScan()
                _state.value = ConnectionState.DISCONNECTED
                _lastError.value = "Device '${BleUuids.DEVICE_NAME}' not found"
            }
        }, 15_000)
    }

    private fun stopScan() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name
            if (name == BleUuids.DEVICE_NAME) {
                stopScan()
                connect(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = ConnectionState.DISCONNECTED
            _lastError.value = "Scan failed (code $errorCode)"
        }
    }

    private fun connect(device: BluetoothDevice) {
        _state.value = ConnectionState.CONNECTING
        gatt = device.connectGatt(appContext, false, gattCallback)
    }

    fun disconnect() {
        gatt?.disconnect()
        // onConnectionStateChange will null out the rest.
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    rxChar = null
                    gatt?.close()
                    gatt = null
                    _state.value = ConnectionState.DISCONNECTED
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        _lastError.value = "Disconnected (status $status)"
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _lastError.value = "Service discovery failed ($status)"
                g.disconnect()
                return
            }
            val service = g.getService(BleUuids.SERVICE_UUID)
            val ch = service?.getCharacteristic(BleUuids.RX_UUID)
            if (ch == null) {
                _lastError.value = "RX characteristic not found"
                g.disconnect()
                return
            }
            rxChar = ch
            _state.value = ConnectionState.CONNECTED
        }
    }

    fun write(bytes: ByteArray): Boolean {
        val g = gatt ?: return false
        val ch = rxChar ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val rc = g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            rc == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            ch.value = bytes
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }.also { ok ->
            if (!ok) Log.w(tag, "writeCharacteristic returned false (${bytes.size} bytes)")
        }
    }

    fun clearError() { _lastError.value = null }
}
