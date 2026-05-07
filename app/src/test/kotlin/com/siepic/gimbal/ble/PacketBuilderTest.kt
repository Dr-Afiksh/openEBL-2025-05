package com.siepic.gimbal.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PacketBuilderTest {

    @Test
    fun buttonPacket_pressed_hasCorrectBytesAndCrc() {
        val pkt = PacketBuilder.buttonPacket('1', true)
        // !B 1 1 + crc = (~(0x21+0x42+0x31+0x31)) & 0xFF
        val expectedCrc = (0x21 + 0x42 + 0x31 + 0x31).inv() and 0xFF
        assertEquals(5, pkt.size)
        assertEquals(0x21.toByte(), pkt[0])
        assertEquals(0x42.toByte(), pkt[1])
        assertEquals(0x31.toByte(), pkt[2])
        assertEquals(0x31.toByte(), pkt[3])
        assertEquals(expectedCrc.toByte(), pkt[4])
    }

    @Test
    fun buttonPacket_released_usesAsciiZero() {
        val pkt = PacketBuilder.buttonPacket('5', false)
        assertEquals(0x35.toByte(), pkt[2])
        assertEquals(0x30.toByte(), pkt[3])
    }

    @Test
    fun accelPacket_zeroZeroOne_hasLittleEndianFloats() {
        val pkt = PacketBuilder.accelPacket(0f, 0f, 1f)
        assertEquals(15, pkt.size)
        assertEquals(0x21.toByte(), pkt[0])
        assertEquals(0x41.toByte(), pkt[1])
        // Three LE float32s: 0.0f -> 00 00 00 00; 1.0f -> 00 00 80 3F
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), pkt.copyOfRange(2, 6))
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), pkt.copyOfRange(6, 10))
        assertArrayEquals(
            byteArrayOf(0, 0, 0x80.toByte(), 0x3F.toByte()),
            pkt.copyOfRange(10, 14),
        )
        // CRC
        var sum = 0
        for (i in 0..13) sum += (pkt[i].toInt() and 0xFF)
        assertEquals((sum.inv() and 0xFF).toByte(), pkt[14])
    }

    @Test
    fun colorPacket_red_isFiveBytesPlusCrc() {
        val pkt = PacketBuilder.colorPacket(255, 0, 0)
        assertEquals(6, pkt.size)
        assertEquals(0x21.toByte(), pkt[0])
        assertEquals(0x43.toByte(), pkt[1])
        assertEquals(0xFF.toByte(), pkt[2])
        assertEquals(0x00.toByte(), pkt[3])
        assertEquals(0x00.toByte(), pkt[4])
    }

    @Test
    fun joystickPacket_extremes_clampToHundred() {
        val full = PacketBuilder.joystickPacket(1f, -1f)
        assertEquals(5, full.size)
        assertEquals(0x21.toByte(), full[0])
        assertEquals(0x4A.toByte(), full[1])
        assertEquals(100.toByte(), full[2])
        assertEquals((-100).toByte(), full[3])

        // out-of-range input is coerced
        val over = PacketBuilder.joystickPacket(2f, -2f)
        assertEquals(100.toByte(), over[2])
        assertEquals((-100).toByte(), over[3])
    }
}
