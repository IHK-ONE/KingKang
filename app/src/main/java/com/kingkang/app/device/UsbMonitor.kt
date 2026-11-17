package com.kingkang.app.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Reports only USB network interfaces that the kernel has actually brought up.
 *
 * Presence on the USB bus, a vendor id or a CDC descriptor is not enough: NetHunter can
 * use MT7612U / RTL8812AU only when the running kernel has their driver and firmware and
 * has created a netdev. This check reads `/sys/class/net/<if>/device`; when the resolved
 * device path lives under `/usb`, that interface is backed by USB and visible to Kali in
 * the same network namespace. No vendor table is involved, so an adapter that merely
 * enumerates but is not driven stays dark.
 */
object UsbMonitor {

    data class State(val interfaces: List<String> = emptyList()) {
        val usable: Boolean get() = interfaces.isNotEmpty()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var receiver: BroadcastReceiver? = null
    private var scope: CoroutineScope? = null

    fun init(context: Context) {
        if (receiver != null) return
        val app = context.applicationContext
        val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshNow()
            }
        }
        runCatching {
            ContextCompat.registerReceiver(app, listener, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = listener
        }
        // A driver may bind a few seconds after the attach broadcast; keep checking so the
        // lamp reflects the kernel state, not the cable event.
        monitorScope.launch {
            while (isActive) {
                refreshNow()
                delay(2_000)
            }
        }
    }

    fun refreshNow() {
        val interfaces = usbNetworkInterfaces()
        if (_state.value.interfaces != interfaces) {
            _state.value = State(interfaces)
        }
    }

    /**
     * USB-backed netdevs. A Wi-Fi dongle's wlan interface and a USB Ethernet adapter both
     * expose `/sys/class/net/<name>/device` pointing into the USB device tree.
     */
    internal fun usbNetworkInterfaces(
        sysClassNet: File = File("/sys/class/net")
    ): List<String> {
        val entries = runCatching { sysClassNet.listFiles() }.getOrNull() ?: return emptyList()
        return entries.asSequence()
            .mapNotNull { net ->
                val device = File(net, "device")
                if (!device.exists()) return@mapNotNull null
                val target = runCatching { device.canonicalPath }.getOrDefault("")
                if (target.contains("/usb")) net.name else null
            }
            .sorted()
            .toList()
    }
}
