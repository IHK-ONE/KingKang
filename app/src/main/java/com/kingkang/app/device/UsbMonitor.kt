package com.kingkang.app.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches the USB bus for network adapters attached through OTG. Most dongles expose the
 * CDC communication/data classes; the few vendor-specific ones are recognised by their
 * USB vendor id. The flag is informational: KingKang cannot bring an interface up itself
 * without root, but Android and Kali can use it once the system does.
 */
object UsbMonitor {

    private val _networkPresent = MutableStateFlow(false)
    val networkPresent: StateFlow<Boolean> = _networkPresent.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    fun init(context: Context) {
        if (receiver != null) return
        val app = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refresh(context ?: app)
            }
        }
        runCatching {
            ContextCompat.registerReceiver(app, listener, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = listener
        }
        refresh(app)
    }

    private fun refresh(context: Context) {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val present = manager?.deviceList?.values?.any(::isNetworkAdapter) == true
        _networkPresent.value = present
    }

    fun isNetworkAdapter(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_COMM) return true
        for (index in 0 until device.interfaceCount) {
            val interfaceClass = device.getInterface(index).interfaceClass
            if (interfaceClass == UsbConstants.USB_CLASS_COMM ||
                interfaceClass == UsbConstants.USB_CLASS_CDC_DATA
            ) {
                return true
            }
        }
        return device.vendorId in ETHERNET_VENDORS
    }

    /** Common USB Ethernet controller vendors; these often report a vendor-specific class. */
    private val ETHERNET_VENDORS = setOf(
        0x0B95, // ASIX
        0x0BDA, // Realtek
        0x0424, // Microchip / SMSC
        0x9710, // MosChip
        0x1D6B, // Linux Foundation (CDC Ethernet gadgets)
        0x2357, // TP-Link
        0x0FE6  // ICS Advent / Davicom
    )
}
