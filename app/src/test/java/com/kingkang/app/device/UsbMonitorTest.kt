package com.kingkang.app.device

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UsbMonitorTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `only interfaces whose device path lives under usb are returned`() {
        val root = temp.newFolder("net")
        val eth = File(root, "eth0").apply { mkdirs() }
        File(eth, "device").mkdirs()
        val wlan = File(root, "wlan1").apply { mkdirs() }
        File(wlan, "device").mkdirs()

        val interfaces = UsbMonitor.usbNetworkInterfaces(root) { device ->
            when (device.parentFile?.name) {
                "wlan1" -> "/sys/devices/platform/usb1/1-1/1-1:1.0"
                "eth0" -> "/sys/devices/platform/virtio0"
                else -> "/sys/devices/virtual/net/dummy0"
            }
        }

        assertEquals(listOf("wlan1"), interfaces)
    }

    @Test
    fun `a missing sysfs root is empty, not an exception`() {
        val missing = File(temp.root, "missing")
        assertEquals(emptyList<String>(), UsbMonitor.usbNetworkInterfaces(missing))
    }
}
