package com.kingkang.app.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilitiesTest {

    @Test
    fun `no capabilities produce no prompt note`() {
        assertNull(DeviceCapabilities.buildNote(emptyList(), ethernet = false, shizuku = false))
    }

    @Test
    fun `otg interfaces are named and shizuku is not mentioned unless active`() {
        val note = DeviceCapabilities.buildNote(
            listOf("wlan1"),
            ethernet = false,
            shizuku = false
        )
        requireNotNull(note)
        assertTrue(note.contains("wlan1"))
        assertTrue(note.contains("OTG"))
        assertFalse(note.contains("Shizuku"))
        assertFalse(note.contains("未"))
    }

    @Test
    fun `ethernet transport is announced without a sysfs name`() {
        val note = DeviceCapabilities.buildNote(emptyList(), ethernet = true, shizuku = false)
        requireNotNull(note)
        assertTrue(note.contains("以太网"))
    }

    @Test
    fun `shizuku adds the absolute kk-adb path`() {
        val note = DeviceCapabilities.buildNote(emptyList(), ethernet = false, shizuku = true)
        requireNotNull(note)
        assertTrue(note.contains("/usr/local/bin/kk-adb"))
        assertTrue(note.contains("不是 root"))
    }
}
