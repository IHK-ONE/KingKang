package com.kingkang.app

import android.app.Application
import android.os.Build
import android.webkit.WebView
import com.kingkang.app.device.DeviceAdbHelper
import com.kingkang.app.device.DeviceCommandBridge
import com.kingkang.app.device.ShizukuManager
import com.kingkang.app.device.UsbMonitor

/**
 * The app has one Application for every process. Only the browser process needs setup:
 * a per-process WebView data directory, which is what keeps its cookie jar and storage
 * separate from the Harness WebView that carries dsh's loopback session.
 *
 * The API 28 floor matches `WebView.setDataDirectorySuffix`; below it the browser screen
 * declines to run rather than sharing dsh's cookies.
 */
class KingKangApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val process = processName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && process.endsWith(":browser")) {
            WebView.setDataDirectorySuffix("browser")
            return
        }
        // Shizuku creates the user-service process under our package as the shell user.
        // It must not run the app's device monitors or command queue as well.
        if (process.endsWith(":kkadb")) return
        // Device abilities belong to the main process: its foreground service keeps them
        // alive while the agent runs, and no other process should bind them twice.
        UsbMonitor.init(this)
        ShizukuManager.init(this)
        DeviceCommandBridge.start(this)
        DeviceAdbHelper.ensureInstalled(this)
    }

    private fun processName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            packageName
        }
}
