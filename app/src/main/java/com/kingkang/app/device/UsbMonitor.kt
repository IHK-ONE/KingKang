package com.kingkang.app.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    data class State(
        val interfaces: List<String> = emptyList(),
        /** Android reports an Ethernet transport even when sysfs names are unavailable. */
        val ethernet: Boolean = false
    ) {
        val usable: Boolean get() = interfaces.isNotEmpty() || ethernet
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var receiver: BroadcastReceiver? = null
    private var scope: CoroutineScope? = null
    private var pollJob: Job? = null
    private var appContext: Context? = null
    private var sysfsRetryAt = 0L
    /** Set by the sysfs scan when the directory cannot be listed (SELinux or missing). */
    private var sysfsListFailed = false

    fun init(context: Context) {
        if (receiver != null) return
        val app = context.applicationContext
        appContext = app
        val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                sysfsRetryAt = 0L
                refreshNow()
            }
        }
        runCatching {
            ContextCompat.registerReceiver(app, listener, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = listener
        }
        refreshNow()
    }

    /**
     * Polling only while the UI can show the lamp. The attach broadcast still refreshes the
     * state, and returning to the app calls setActive(true) for an immediate read.
     */
    fun setActive(active: Boolean) {
        if (!active) {
            pollJob?.cancel()
            pollJob = null
            return
        }
        if (pollJob?.isActive == true) return
        val monitorScope = scope ?: return
        pollJob = monitorScope.launch {
            while (isActive) {
                refreshNow()
                delay(2_000)
            }
        }
    }

    fun refreshNow() {
        val now = System.currentTimeMillis()
        val interfaces = if (now >= sysfsRetryAt) {
            usbNetworkInterfaces().also {
                // A denied sysfs directory would otherwise produce an audit denial every
                // two seconds. Back off; the USB attach broadcast resets this.
                if (sysfsListFailed && it.isEmpty()) sysfsRetryAt = now + 60_000
            }
        } else {
            _state.value.interfaces
        }
        val ethernet = hasEthernetTransport()
        if (_state.value.interfaces != interfaces || _state.value.ethernet != ethernet) {
            _state.value = State(interfaces, ethernet)
        }
    }

    private fun hasEthernetTransport(): Boolean {
        val context = appContext ?: return false
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return runCatching {
            manager.allNetworks.any { network ->
                manager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            }
        }.getOrDefault(false)
    }

    /**
     * USB-backed netdevs. A Wi-Fi dongle's wlan interface and a USB Ethernet adapter both
     * expose `/sys/class/net/<name>/device` pointing into the USB device tree.
     */
    internal fun usbNetworkInterfaces(
        sysClassNet: File = File("/sys/class/net"),
        resolve: (File) -> String = { it.canonicalPath }
    ): List<String> {
        val entries = runCatching { sysClassNet.listFiles() }.getOrNull()
        sysfsListFailed = entries == null
        if (entries == null) return emptyList()
        return entries.asSequence()
            .mapNotNull { net ->
                val device = File(net, "device")
                if (!device.exists()) return@mapNotNull null
                val target = runCatching { resolve(device) }.getOrDefault("")
                if (target.contains("/usb")) net.name else null
            }
            .sorted()
            .toList()
    }
}
