package com.kingkang.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The WAV wrapper must match what dsh's speech Remote validates before decoding. */
class VoiceInputTest {

    private fun intAt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun shortAt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    @Test
    fun `the header describes 16 kHz mono pcm16`() {
        val wav = wavFile(ByteArray(3200))
        assertEquals(3244, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals(1, shortAt(wav, 20))
        assertEquals(1, shortAt(wav, 22))
        assertEquals(16_000, intAt(wav, 24))
        assertEquals(32_000, intAt(wav, 28))
        assertEquals(2, shortAt(wav, 32))
        assertEquals(16, shortAt(wav, 34))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(3200, intAt(wav, 40))
    }

    @Test
    fun `the riff size counts the header and payload`() {
        val wav = wavFile(ByteArray(1))
        assertEquals(37, intAt(wav, 4))
        assertEquals(1, intAt(wav, 40))
    }
}
