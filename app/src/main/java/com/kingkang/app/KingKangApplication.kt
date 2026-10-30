package com.kingkang.app

import android.app.Application
import android.os.Build
import android.webkit.WebView

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            processName().endsWith(":browser")
        ) {
            WebView.setDataDirectorySuffix("browser")
        }
    }

    private fun processName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            packageName
        }
}
