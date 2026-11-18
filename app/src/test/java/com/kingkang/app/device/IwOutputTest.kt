package com.kingkang.app.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IwOutputTest {

    private val dev = """
        phy#1
        	Interface wlan1
        		ifindex 12
        		wdev 0x100000001
        		addr 00:11:22:33:44:55
        		type monitor
        		channel 6 (2437 MHz), width: 20 MHz
        	phy#0
        	Interface wlan0
        		ifindex 9
        		addr aa:bb:cc:dd:ee:ff
        		type managed
        		channel 36 (5180 MHz), width: 80 MHz
    """.trimIndent()

    @Test
    fun parses_interface_phy_and_type() {
        val parsed = IwOutput.parseDev(dev)
        assertEquals(2, parsed.size)
        assertEquals(IwOutput.Interface("wlan1", "phy1", "monitor"), parsed[0])
        assertEquals(IwOutput.Interface("wlan0", "phy0", "managed"), parsed[1])
    }

    @Test
    fun monitor_mode_is_read_from_the_supported_list_only() {
        val info = """
            Wiphy phy1
            	Supported interface modes:
            		 * IBSS
            		 * managed
            		 * AP
            		 * monitor
            	Band 1:
        """.trimIndent()
        assertTrue(IwOutput.supportsMonitor(info))
    }

    @Test
    fun a_phy_without_monitor_is_not_reported() {
        val info = """
            Wiphy phy0
            	Supported interface modes:
            		 * managed
            		 * P2P-client
            	Band 1:
        """.trimIndent()
        assertFalse(IwOutput.supportsMonitor(info))
    }

    @Test
    fun phy_names_are_whitelisted_before_reaching_a_shell() {
        assertTrue(IwOutput.isPhyName("phy12"))
        assertFalse(IwOutput.isPhyName("phy1; rm -rf /"))
        assertFalse(IwOutput.isPhyName("wlan0"))
    }
}
