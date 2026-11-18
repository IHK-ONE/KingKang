package com.kingkang.app.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilitiesTest {

    @Test
    fun `root mode without device capabilities produces no prompt note`() {
        assertNull(
            DeviceCapabilities.buildNote(
                emptyList(),
                ethernet = false,
                shizuku = false,
                limitedNetwork = false
            )
        )
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

    @Test
    fun `pRoot mode tells the model that raw networking is unavailable`() {
        val note = DeviceCapabilities.buildNote(
            emptyList(),
            ethernet = false,
            shizuku = false,
            limitedNetwork = true
        )
        requireNotNull(note)
        assertTrue(note.contains("PRoot"))
        assertTrue(note.contains("原始套接字"))
        assertTrue(note.contains("nc"))
    }

    @Test
    fun `an OTG interface in pRoot mode carries the networking caveat`() {
        val note = DeviceCapabilities.buildNote(
            listOf("wlan1"),
            ethernet = false,
            shizuku = false,
            limitedNetwork = true
        )
        requireNotNull(note)
        assertTrue(note.contains("wlan1"))
        assertTrue(note.contains("PRoot 网络限制"))
    }
}
