package com.siepic.gimbal.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bluefruit Connect-style packets accepted by the C6 gimbal firmware.
 *
 *   !B<button:1ASCII><state:1ASCII><crc>            5 bytes
 *   !A<x:f32 LE><y:f32 LE><z:f32 LE><crc>           15 bytes
 *   !C<R><G><B><crc>                                6 bytes
 *
 * CRC = (~sum_of_preceding_bytes) & 0xFF. Firmware doesn't validate it but we
 * compute it correctly.
 *
 * joystickPacket() emits a !J<x:i8><y:i8><crc> packet that the *current*
 * firmware does NOT understand — it's scaffolding for a future firmware change
 * that swaps the joystick analog path off the !A handler. Don't enable it
 * unless you've patched the sketch.
 */
object PacketBuilder {

    fun buttonPacket(button: Char, pressed: Boolean): ByteArray {
        val state = if (pressed) '1'.code.toByte() else '0'.code.toByte()
        val body = byteArrayOf('!'.code.toByte(), 'B'.code.toByte(), button.code.toByte(), state)
        return body + crc(body)
    }

    fun accelPacket(x: Float, y: Float, z: Float): ByteArray {
        val buf = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
        buf.put('!'.code.toByte())
        buf.put('A'.code.toByte())
        buf.putFloat(x)
        buf.putFloat(y)
        buf.putFloat(z)
        val body = buf.array()
        return body + crc(body)
    }

    fun colorPacket(r: Int, g: Int, b: Int): ByteArray {
        val body = byteArrayOf(
            '!'.code.toByte(),
            'C'.code.toByte(),
            (r and 0xFF).toByte(),
            (g and 0xFF).toByte(),
            (b and 0xFF).toByte(),
        )
        return body + crc(body)
    }

    fun joystickPacket(x: Float, y: Float): ByteArray {
        val xi = (x.coerceIn(-1f, 1f) * 100f).toInt().toByte()
        val yi = (y.coerceIn(-1f, 1f) * 100f).toInt().toByte()
        val body = byteArrayOf('!'.code.toByte(), 'J'.code.toByte(), xi, yi)
        return body + crc(body)
    }

    private fun crc(bytes: ByteArray): Byte {
        var sum = 0
        for (b in bytes) sum += (b.toInt() and 0xFF)
        return (sum.inv() and 0xFF).toByte()
    }
}
