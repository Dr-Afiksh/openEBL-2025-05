package com.siepic.gimbal.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class Vec3(val x: Float, val y: Float, val z: Float)

class TiltSensor(context: Context) {

    private val manager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /**
     * Hot flow of accelerometer readings, normalized to g (m/s² ÷ 9.80665) so the
     * firmware's pitch/roll math (atan2 with ax/ay/az ratios) gets the units it
     * expects. SENSOR_DELAY_GAME ≈ 20 ms updates, ample for our 50 ms tick.
     */
    fun readings(): Flow<Vec3> = callbackFlow {
        val sensor = accelerometer ?: run { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(
                    Vec3(
                        event.values[0] / SensorManager.STANDARD_GRAVITY,
                        event.values[1] / SensorManager.STANDARD_GRAVITY,
                        event.values[2] / SensorManager.STANDARD_GRAVITY,
                    )
                )
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { manager.unregisterListener(listener) }
    }
}
